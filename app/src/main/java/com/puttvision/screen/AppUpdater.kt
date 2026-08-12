package com.puttvision.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val githubToken: String? = null
)

class AppUpdater(
    private val activity: Activity,
    private val fallbackManifestUrl: String = "https://puttvision-update.vercel.app/update.json"
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val tokenStore = SecureTokenStore(activity)
    private val prefs = activity.getSharedPreferences("puttvision_updater", Context.MODE_PRIVATE)
    private val checkInFlight = AtomicBoolean(false)
    private val downloadInFlight = AtomicBoolean(false)

    private companion object {
        const val KEY_PENDING_APK = "pending_apk_path"
    }

    fun check(silent: Boolean = true) {
        if (!checkInFlight.compareAndSet(false, true)) return
        executor.execute {
            try {
                val info = fetchInfo()
                val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
                val currentName = packageInfo.versionName ?: currentCode.toString()

                if (info.versionCode > currentCode) {
                    onUi { showUpdateDialog(info) }
                } else if (!silent) {
                    onUi {
                        activity.pvMessageDialog("PuttVision", "최신 버전입니다. (v$currentName)").show()
                    }
                }
            } catch (t: Throwable) {
                if (!silent) {
                    onUi {
                        activity.pvMessageDialog("업데이트 확인 실패", t.message ?: "네트워크 오류").show()
                    }
                }
            } finally {
                checkInFlight.set(false)
            }
        }
    }

    private fun fetchInfo(): UpdateInfo {
        val publicInfo = runCatching { fetchFallbackManifest() }.getOrNull()
        val token = tokenStore.loadToken()
        val privateInfo = if (!token.isNullOrBlank()) {
            runCatching { fetchGitHubRelease(token) }.getOrNull()
        } else null
        return listOfNotNull(publicInfo, privateInfo)
            .maxByOrNull { it.versionCode }
            ?: error("업데이트 채널에 연결할 수 없습니다")
    }

    /**
     * Uses the same private-repo credential as one-tap deploy, so source and APK can stay private.
     * release-apk.yml publishes assets named puttvision.apk and puttvision.apk.sha256.
     */
    private fun fetchGitHubRelease(token: String): UpdateInfo {
        val release = githubJson(
            "https://api.github.com/repos/mercurial0416-lgtm/puttvision-screen/releases/latest",
            token
        )
        val tag = release.getString("tag_name")
        val versionCode = tag.substringAfter("pv-", "").toIntOrNull()
            ?: error("Release tag 형식 오류: $tag")
        val rawName = release.optString("name").ifBlank { tag }
        val versionName = rawName.removePrefix("PuttVision ").removePrefix("v").ifBlank { tag }

        val assets = release.getJSONArray("assets")
        var apkApiUrl: String? = null
        var shaApiUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.getString("name")) {
                "puttvision.apk" -> apkApiUrl = asset.getString("url")
                "puttvision.apk.sha256" -> shaApiUrl = asset.getString("url")
            }
        }
        val apk = apkApiUrl ?: error("Release에 puttvision.apk가 없습니다.")
        val sha = shaApiUrl?.let { fetchGithubAssetText(it, token).trim().substringBefore(' ') }

        return UpdateInfo(versionCode, versionName, apk, sha, token)
    }

    private fun fetchFallbackManifest(): UpdateInfo {
        val c = (URL(fallbackManifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("업데이트 서버 HTTP $code: ${text.take(180)}")
            val j = JSONObject(text)
            return UpdateInfo(
                j.getInt("versionCode"),
                j.getString("versionName").removePrefix("PuttVision ").removePrefix("v"),
                j.getString("apkUrl"),
                j.optString("sha256").takeIf { it.isNotBlank() }
            )
        } finally {
            c.disconnect()
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        activity.pvMessageDialog(
            title = "PuttVision ${info.versionName} 업데이트",
            message = "새 버전이 있습니다. 지금 업데이트할까요?",
            positiveLabel = "업데이트",
            onPositive = { downloadAndInstall(info) },
            negativeLabel = "나중에"
        ).show()
    }

    private fun downloadAndInstall(info: UpdateInfo) {
        if (!downloadInFlight.compareAndSet(false, true)) return
        executor.execute {
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "puttvision-${info.versionCode}.apk")

                val input = if (!info.githubToken.isNullOrBlank() && info.apkUrl.startsWith("https://api.github.com/")) {
                    openGithubAsset(info.apkUrl, info.githubToken)
                } else {
                    openHttpStream(info.apkUrl)
                }

                input.use { source ->
                    apk.outputStream().use { output -> source.copyTo(output) }
                }

                require(apk.length() > 32 * 1024L) { "다운로드된 APK 파일이 비정상적으로 작습니다." }

                info.sha256?.let { expected ->
                    val actual = sha256(apk)
                    require(actual.equals(expected, ignoreCase = true)) {
                        "APK SHA-256 검증 실패"
                    }
                }

                onUi { install(apk) }
            } catch (t: Throwable) {
                onUi {
                    activity.pvMessageDialog("업데이트 실패", t.message ?: "다운로드 오류").show()
                }
            } finally {
                downloadInFlight.set(false)
            }
        }
    }

    private fun openHttpStream(url: String): InputStream {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 45_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        val code = c.responseCode
        if (code !in 200..299) {
            val body = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            error("APK 다운로드 실패: HTTP $code ${body.take(120)}")
        }
        return disconnectingStream(c)
    }

    private fun githubJson(url: String, token: String): JSONObject {
        val c = githubConnection(url, token, "application/vnd.github+json")
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("GitHub $code: ${text.take(180)}")
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    private fun fetchGithubAssetText(url: String, token: String): String =
        openGithubAsset(url, token).bufferedReader().use { it.readText() }

    private fun openGithubAsset(url: String, token: String): InputStream {
        val first = githubConnection(url, token, "application/octet-stream").apply {
            instanceFollowRedirects = false
        }
        return when (val code = first.responseCode) {
            in 200..299 -> disconnectingStream(first)
            in 300..399 -> {
                val location = first.getHeaderField("Location") ?: error("GitHub asset redirect 없음")
                first.disconnect()
                val redirected = (URL(location).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                }
                if (redirected.responseCode !in 200..299) {
                    val failed = redirected.responseCode
                    redirected.disconnect()
                    error("APK 다운로드 실패: HTTP $failed")
                }
                disconnectingStream(redirected)
            }
            else -> {
                val body = first.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                first.disconnect()
                error("GitHub asset $code: ${body.take(180)}")
            }
        }
    }

    private fun disconnectingStream(connection: HttpURLConnection): InputStream =
        object : FilterInputStream(connection.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun githubConnection(url: String, token: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PuttVision-Screen-Updater")
        }

    private fun install(apk: File) {
        if (!apk.exists()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            prefs.edit().putString(KEY_PENDING_APK, apk.absolutePath).apply()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.pvMessageDialog(
                title = "설치 권한 필요",
                message = "'이 출처 허용'을 켠 뒤 PuttVision으로 돌아오면 설치 화면이 자동으로 이어집니다.",
                positiveLabel = "설정 열기",
                onPositive = { activity.startActivity(intent) },
                negativeLabel = "취소",
                onNegative = { prefs.edit().remove(KEY_PENDING_APK).apply() }
            ).show()
            return
        }
        prefs.edit().remove(KEY_PENDING_APK).apply()
        launchInstaller(apk)
    }

    /** Called from Activity.onResume after the unknown-sources permission screen returns. */
    fun resumePendingInstallIfPossible() {
        val path = prefs.getString(KEY_PENDING_APK, null) ?: return
        val apk = File(path)
        if (!apk.exists()) {
            prefs.edit().remove(KEY_PENDING_APK).apply()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) return

        prefs.edit().remove(KEY_PENDING_APK).apply()
        launchInstaller(apk)
    }

    private fun launchInstaller(apk: File) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { error ->
                onUi { activity.pvMessageDialog("설치 실행 실패", error.message ?: "APK 설치 화면을 열 수 없습니다.").show() }
            }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun onUi(block: () -> Unit) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        activity.runOnUiThread {
            if (!activity.isFinishing && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed)) block()
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
