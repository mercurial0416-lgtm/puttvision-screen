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
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String
)

class AppUpdater(
    private val activity: Activity,
    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL
) {
    private val executor = Executors.newSingleThreadExecutor()

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
                            .setMessage("최신 버전입니다.")
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
        requireHttps(manifestUrl)
        val c = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("Cache-Control", "no-cache")
        }
        require(c.responseCode == HttpURLConnection.HTTP_OK) { "update.json HTTP ${c.responseCode}" }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        val j = JSONObject(text)
        val apkUrl = j.getString("apkUrl")
        val digest = j.getString("sha256").lowercase()
        requireHttps(apkUrl)
        require(digest.matches(Regex("[0-9a-f]{64}"))) { "잘못된 SHA-256" }
        return UpdateInfo(
            j.getInt("versionCode"),
            j.getString("versionName"),
            apkUrl,
            digest
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
                val c = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 30000
                }
                require(c.responseCode == HttpURLConnection.HTTP_OK) { "APK HTTP ${c.responseCode}" }
                val length = c.contentLengthLong
                require(length in 1..MAX_APK_BYTES) { "잘못된 APK 크기" }
                c.inputStream.use { input ->
                    apk.outputStream().use { output ->
                        val copied = input.copyTo(output)
                        require(copied == length) { "APK 다운로드가 완료되지 않았습니다" }
                    }
                }

                val actual = sha256(apk)
                require(actual == info.sha256) {
                    apk.delete()
                    "APK SHA-256 검증 실패"
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

    private fun requireHttps(value: String) {
        require(Uri.parse(value).scheme.equals("https", ignoreCase = true)) {
            "HTTPS 업데이트 주소만 허용됩니다"
        }
    }

    companion object {
        private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    }
}
