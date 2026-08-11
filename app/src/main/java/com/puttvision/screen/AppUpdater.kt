package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

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

    fun check(silent: Boolean = true) {
        executor.execute {
            try {
                val info = fetchInfo()
                val current = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).let {
                        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt()
                        else @Suppress("DEPRECATION") it.versionCode
                    }

                if (info.versionCode > current) {
                    activity.runOnUiThread { showUpdateDialog(info) }
                } else if (!silent) {
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle("PuttVision")
                            .setMessage("최신 버전입니다. (v$current)")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }
            } catch (t: Throwable) {
                if (!silent) {
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle("업데이트 확인 실패")
                            .setMessage(t.message ?: "네트워크 오류")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun fetchInfo(): UpdateInfo {
        val token = tokenStore.loadToken()
        if (!token.isNullOrBlank()) {
            try {
                return fetchGitHubRelease(token)
            } catch (_: Throwable) {
                // Keep the v0.5 public-manifest path as a bootstrap fallback.
            }
        }
        return fetchFallbackManifest()
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
        val versionName = release.optString("name").ifBlank { tag }

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
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(text)
        return UpdateInfo(
            j.getInt("versionCode"),
            j.getString("versionName"),
            j.getString("apkUrl"),
            j.optString("sha256").takeIf { it.isNotBlank() }
        )
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle("PuttVision ${info.versionName} 업데이트")
            .setMessage("새 버전이 있습니다. 지금 업데이트할까요?")
            .setNegativeButton("나중에", null)
            .setPositiveButton("업데이트") { _, _ -> downloadAndInstall(info) }
            .show()
    }

    private fun downloadAndInstall(info: UpdateInfo) {
        executor.execute {
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "puttvision-${info.versionCode}.apk")

                val input = if (!info.githubToken.isNullOrBlank() && info.apkUrl.startsWith("https://api.github.com/")) {
                    openGithubAsset(info.apkUrl, info.githubToken)
                } else {
                    val c = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 45_000
                    }
                    c.inputStream
                }

                input.use { source ->
                    apk.outputStream().use { output -> source.copyTo(output) }
                }

                info.sha256?.let { expected ->
                    val actual = sha256(apk)
                    require(actual.equals(expected, ignoreCase = true)) {
                        "APK SHA-256 검증 실패"
                    }
                }

                activity.runOnUiThread { install(apk) }
            } catch (t: Throwable) {
                activity.runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("업데이트 실패")
                        .setMessage(t.message ?: "다운로드 오류")
                        .setPositiveButton("확인", null)
                        .show()
                }
            }
        }
    }

    private fun githubJson(url: String, token: String): JSONObject {
        val c = githubConnection(url, token, "application/vnd.github+json")
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("GitHub $code: ${text.take(180)}")
        return JSONObject(text)
    }

    private fun fetchGithubAssetText(url: String, token: String): String =
        openGithubAsset(url, token).bufferedReader().use { it.readText() }

    private fun openGithubAsset(url: String, token: String): InputStream {
        val first = githubConnection(url, token, "application/octet-stream").apply {
            instanceFollowRedirects = false
        }
        return when (val code = first.responseCode) {
            in 200..299 -> first.inputStream
            in 300..399 -> {
                val location = first.getHeaderField("Location") ?: error("GitHub asset redirect 없음")
                val redirected = (URL(location).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                }
                if (redirected.responseCode !in 200..299) {
                    error("APK 다운로드 실패: HTTP ${redirected.responseCode}")
                }
                redirected.inputStream
            }
            else -> {
                val body = first.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("GitHub asset $code: ${body.take(180)}")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            AlertDialog.Builder(activity)
                .setTitle("설치 권한 필요")
                .setMessage("PuttVision 업데이트를 위해 '이 출처 허용'을 켠 뒤 앱으로 돌아와 업데이트를 다시 눌러주세요.")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
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
}
