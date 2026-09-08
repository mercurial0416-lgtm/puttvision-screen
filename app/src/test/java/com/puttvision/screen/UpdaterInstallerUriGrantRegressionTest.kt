package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterInstallerUriGrantRegressionTest {
    private fun updaterSource(): String {
        val relative = "src/main/java/com/puttvision/screen/AppUpdater.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate AppUpdater.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun installerIntentsKeepFileProviderReadGrantAndApkMimeFallback() {
        val source = updaterSource()
        val start = source.indexOf("private fun launchInstaller(apk: File) {")
        val end = source.indexOf("private fun sha256(file: File)", start)
        require(start >= 0 && end > start) { "Unable to locate launchInstaller" }
        val installer = source.substring(start, end)

        assertTrue(
            "installer must expose the cached APK through FileProvider",
            installer.contains("FileProvider.getUriForFile")
        )
        assertTrue(
            "installer must attach ClipData so URI permission propagates on OEM package installers",
            installer.contains("ClipData.newRawUri")
        )
        assertTrue(
            "both primary and fallback installer intents must grant read access to the FileProvider URI",
            installer.split("Intent(Intent.").drop(1).count { block ->
                block.substringBefore("}\n").contains("FLAG_GRANT_READ_URI_PERMISSION")
            } >= 2
        )
        assertTrue(
            "ACTION_VIEW primary installer must declare the APK MIME type",
            installer.contains("setDataAndType(uri, \"application/vnd.android.package-archive\")")
        )

        val primaryLaunch = installer.indexOf("activity.startActivity(viewIntent)")
        val fallbackLaunch = installer.indexOf("activity.startActivity(installIntent)")
        val failureDialog = installer.indexOf("pvMessageDialog(\"설치 실행 실패\"")
        assertTrue("ACTION_VIEW installer launch must remain present", primaryLaunch >= 0)
        assertTrue("deprecated installer action must only run as fallback", fallbackLaunch > primaryLaunch)
        assertTrue("failure UI must only run after both installer attempts", failureDialog > fallbackLaunch)
    }
}
