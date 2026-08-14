package com.puttvision.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    private val fallbackManifestUrl: String = "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/update.json"
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val tokenStore = SecureTokenStore(activity)
    private val prefs = activity.getSharedPreferences("puttvision_updater", Context.MODE_PRIVATE)
    private val checkInFlight = AtomicBoolean(false)
    private val downloadInFlight = AtomicBoolean(false)

    private companion object {
        const val KEY_PENDING_APK = "pending_apk_path"
        const val KEY_PENDING_SHA = "pending_apk_sha"
        const val KEY_PENDING_VERSION = "pending_apk_version"
    }

    fun check(silent: Boolean = true) {
        if (!checkInFlight.compareAndSet(false, true)) return
        executor.execute {
            try {
                val info = fetchInfo()
                val currentCode = currentInstalledVersionCode().toInt()
                val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val currentName = packageInfo.versionName ?: currentCode.toString()

                if (info.versionCode > currentCode) {
                    onUi { showUpdateDialog(info) }
                } else if (!silent) {
                    onUi { activity.pvMessageDialog("PuttVision", "최신 버전입니다. (v$currentName)").show() }
                }
            } catch (t: Throwable) {
                if (!silent) {
                    onUi { activity.pvMessageDialog("업데이트 확인 실패", t.message ?: "네트워크 오류").show() }
                }
            } finally {
                checkInFlight.set(false)
            }
        }
    }

    private fun fetchInfo(): UpdateInfo {
        val publicInfo = runCatching { fetchFallbackManifest() }.getOrNull()
        val token = if (BuildConfig.DEVELOPER_BUILD) tokenStore.loadToken() else null
        val privateInfo = if (BuildConfig.DEVELOPER_BUILD && !token.isNullOrBlank()) {
            runCatching { fetchGitHubRelease(token) }.getOrNull()
        } else null
        return listOfNotNull(publicInfo, privateInfo)
            .maxByOrNull { it.versionCode }
            ?: error("업데이트 채널에 연결할 수 없습니다")
    }

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
        val info = UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkApiUrl ?: error("Release에 puttvision.apk가 없습니다."),
            sha256 = shaApiUrl?.let { fetchGithubAssetText(it, token).trim().substringBefore(' ') },
            githubToken = token
        )
        val check = V49UpdatePolicy.validateInfo(info, publicChannel = false)
        require(check.valid) { check.reason ?: "GitHub 업데이트 정보 오류" }
        return info
    }

    private fun fetchFallbackManifest(): UpdateInfo {
        val source = V49UpdatePolicy.validateManifestUrl(fallbackManifestUrl)
        require(source.valid) { source.reason ?: "업데이트 manifest URL 오류" }
        val c = (URL(fallbackManifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val lengthCheck = V49UpdatePolicy.validateContentLength(c.contentLengthLong)
            if (c.contentLengthLong > V49UpdatePolicy.MAX_MANIFEST_BYTES) {
                error("업데이트 manifest가 비정상적으로 큽니다")
            }
            if (!lengthCheck.valid && c.contentLengthLong == 0L) error(lengthCheck.reason ?: "manifest 길이 오류")
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.let { V49UpdatePolicy.limited(it, V49UpdatePolicy.MAX_MANIFEST_BYTES) }
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("업데이트 서버 HTTP $code: ${text.take(180)}")
            val j = JSONObject(text)
            val info = UpdateInfo(
                j.getInt("versionCode"),
                j.getString("versionName").removePrefix("PuttVision ").removePrefix("v"),
                j.getString("apkUrl"),
                j.optString("sha256").takeIf { it.isNotBlank() }
            )
            val check = V49UpdatePolicy.validateInfo(info, publicChannel = true)
            require(check.valid) { check.reason ?: "공개 업데이트 정보 오류" }
            return info
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
            var failedApk: File? = null
            try {
                val validation = V49UpdatePolicy.validateInfo(info, publicChannel = info.githubToken.isNullOrBlank())
                require(validation.valid) { validation.reason ?: "업데이트 정보 검증 실패" }
                require(V49UpdatePolicy.isUpgrade(currentInstalledVersionCode(), info.versionCode.toLong())) {
                    "현재 버전보다 새 버전이 아닙니다"
                }

                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                V49UpdatePolicy.cleanCache(dir)
                val targetApk = File(dir, "puttvision-${info.versionCode}.apk")
                failedApk = targetApk

                val input = if (!info.githubToken.isNullOrBlank() && info.apkUrl.startsWith("https://api.github.com/")) {
                    openGithubAsset(info.apkUrl, info.githubToken)
                } else {
                    openHttpStream(info.apkUrl)
                }

                input.use { source -> targetApk.outputStream().use { output -> source.copyTo(output) } }
                require(targetApk.length() in (32 * 1024L + 1)..V49UpdatePolicy.MAX_APK_BYTES) {
                    "다운로드된 APK 파일 크기가 비정상적입니다."
                }

                info.sha256?.let { expected ->
                    val actual = sha256(targetApk)
                    require(actual.equals(expected, ignoreCase = true)) { "APK SHA-256 검증 실패" }
                }

                val candidateCode = verifyApkIdentity(targetApk)
                require(V49UpdatePolicy.isUpgrade(currentInstalledVersionCode(), candidateCode)) {
                    "다운로드 APK가 현재 버전보다 새 버전이 아닙니다"
                }

                failedApk = null
                onUi { install(targetApk, info) }
            } catch (t: Throwable) {
                runCatching { failedApk?.takeIf { it.exists() }?.delete() }
                onUi { activity.pvMessageDialog("업데이트 실패", t.message ?: "다운로드 오류").show() }
            } finally {
                downloadInFlight.set(false)
            }
        }
    }

    private fun openHttpStream(url: String): InputStream {
        val sourceCheck = V49UpdatePolicy.validateInfo(UpdateInfo(1, "download", url, null, "private"), publicChannel = false)
        require(sourceCheck.valid) { sourceCheck.reason ?: "APK URL 오류" }
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
        val length = V49UpdatePolicy.validateContentLength(c.contentLengthLong)
        if (!length.valid) {
            c.disconnect()
            error(length.reason ?: "APK 길이 오류")
        }
        return V49UpdatePolicy.limited(disconnectingStream(c))
    }

    private fun githubJson(url: String, token: String): JSONObject {
        val c = githubConnection(url, token, "application/vnd.github+json")
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.let { V49UpdatePolicy.limited(it, V49UpdatePolicy.MAX_MANIFEST_BYTES) }
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
        require(V49UpdatePolicy.validateManifestUrl(url).valid) { "GitHub asset URL은 HTTPS여야 합니다" }
        val first = githubConnection(url, token, "application/octet-stream").apply { instanceFollowRedirects = false }
        return when (val code = first.responseCode) {
            in 200..299 -> {
                val length = V49UpdatePolicy.validateContentLength(first.contentLengthLong)
                if (!length.valid) {
                    first.disconnect()
                    error(length.reason ?: "GitHub asset 길이 오류")
                }
                V49UpdatePolicy.limited(disconnectingStream(first))
            }
            in 300..399 -> {
                val location = first.getHeaderField("Location") ?: error("GitHub asset redirect 없음")
                first.disconnect()
                val redirectCheck = V49UpdatePolicy.validateManifestUrl(location)
                require(redirectCheck.valid) { redirectCheck.reason ?: "GitHub redirect URL 오류" }
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
                val length = V49UpdatePolicy.validateContentLength(redirected.contentLengthLong)
                if (!length.valid) {
                    redirected.disconnect()
                    error(length.reason ?: "APK 길이 오류")
                }
                V49UpdatePolicy.limited(disconnectingStream(redirected))
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
                try { super.close() } finally { connection.disconnect() }
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

    private fun verifyApkIdentity(apk: File): Long {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }

        val installed = pm.getPackageInfo(activity.packageName, flags)
        @Suppress("DEPRECATION")
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("다운로드 APK 패키지 정보를 읽을 수 없습니다")
        require(candidate.packageName == activity.packageName) { "업데이트 APK 패키지명이 PuttVision과 다릅니다" }

        fun signerDigests(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = info.signingInfo ?: return emptySet()
                val current = signing.apkContentsSigners?.toList().orEmpty()
                val history = if (signing.hasMultipleSigners()) emptyList() else signing.signingCertificateHistory?.toList().orEmpty()
                (current + history).distinctBy { it.toCharsString() }
            } else {
                @Suppress("DEPRECATION") info.signatures?.toList().orEmpty()
            }
            return signatures.mapTo(linkedSetOf()) { signature ->
                val md = MessageDigest.getInstance("SHA-256")
                md.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }
        }

        val installedSigners = signerDigests(installed)
        val candidateSigners = signerDigests(candidate)
        require(installedSigners.isNotEmpty() && candidateSigners.isNotEmpty()) { "APK 서명 정보를 읽을 수 없습니다" }
        require(installedSigners.any { it in candidateSigners }) { "업데이트 APK 서명이 현재 PuttVision과 다릅니다" }
        return if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else {
            @Suppress("DEPRECATION") candidate.versionCode.toLong()
        }
    }

    private fun install(apk: File, info: UpdateInfo) {
        if (!apk.exists()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            prefs.edit()
                .putString(KEY_PENDING_APK, apk.absolutePath)
                .putString(KEY_PENDING_SHA, info.sha256.orEmpty())
                .putInt(KEY_PENDING_VERSION, info.versionCode)
                .apply()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.pvMessageDialog(
                title = "설치 권한 필요",
                message = "'이 출처 허용'을 켠 뒤 PuttVision으로 돌아오면 APK를 다시 검증한 후 설치 화면을 이어갑니다.",
                positiveLabel = "설정 열기",
                onPositive = { activity.startActivity(intent) },
                negativeLabel = "취소",
                onNegative = { clearPendingInstall() }
            ).show()
            return
        }
        clearPendingInstall()
        launchInstaller(apk)
    }

    /** Called from Activity.onResume after the unknown-sources permission screen returns. */
    fun resumePendingInstallIfPossible() {
        val path = prefs.getString(KEY_PENDING_APK, null) ?: return
        if (!V49UpdatePolicy.pendingPathAllowed(activity.cacheDir, path)) {
            clearPendingInstall()
            return
        }
        val apk = File(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) return

        val expectedSha = prefs.getString(KEY_PENDING_SHA, "").orEmpty()
        val expectedVersion = prefs.getInt(KEY_PENDING_VERSION, -1)
        val ok = runCatching {
            require(apk.length() in (32 * 1024L + 1)..V49UpdatePolicy.MAX_APK_BYTES) { "대기 중 APK 크기 오류" }
            if (expectedSha.isNotBlank()) {
                require(V49UpdatePolicy.isSha256(expectedSha)) { "대기 중 SHA 형식 오류" }
                require(sha256(apk).equals(expectedSha, ignoreCase = true)) { "대기 중 APK SHA 검증 실패" }
            }
            val candidate = verifyApkIdentity(apk)
            require(expectedVersion <= 0 || candidate == expectedVersion.toLong()) { "대기 중 APK 버전이 바뀌었습니다" }
            require(V49UpdatePolicy.isUpgrade(currentInstalledVersionCode(), candidate)) { "이미 설치된 버전보다 새 APK가 아닙니다" }
        }.isSuccess
        if (!ok) {
            runCatching { apk.delete() }
            clearPendingInstall()
            return
        }
        clearPendingInstall()
        launchInstaller(apk)
    }

    private fun clearPendingInstall() {
        prefs.edit().remove(KEY_PENDING_APK).remove(KEY_PENDING_SHA).remove(KEY_PENDING_VERSION).apply()
    }

    private fun currentInstalledVersionCode(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }

    private fun launchInstaller(apk: File) {
        if (activity.isFinishing || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) return
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { error -> onUi { activity.pvMessageDialog("설치 실행 실패", error.message ?: "APK 설치 화면을 열 수 없습니다.").show() } }
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

    fun close() { executor.shutdownNow() }
}
