package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterPendingInstallRevalidationRegressionTest {
    private fun updaterSource(): String {
        val relative = "src/main/java/com/puttvision/screen/AppUpdater.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate AppUpdater.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun pendingInstallIsFullyRevalidatedBeforeInstallerLaunch() {
        val source = updaterSource()
        val start = source.indexOf("fun resumePendingInstallIfPossible() {")
        val end = source.indexOf("private fun clearPendingInstall()", start)
        require(start >= 0 && end > start) { "Unable to locate pending-install resume flow" }
        val resume = source.substring(start, end)

        val pathGuard = resume.indexOf("V49UpdatePolicy.pendingPathAllowed")
        val sizeGuard = resume.indexOf("V49UpdatePolicy.validateDownloadedApkSize")
        val shaGuard = resume.indexOf("sha256(apk).equals(expectedSha, ignoreCase = true)")
        val identityGuard = resume.indexOf("verifyApkIdentity(apk)")
        val versionGuard = resume.indexOf("V49UpdatePolicy.validateArtifactVersion")
        val launch = resume.lastIndexOf("launchInstaller(apk)")

        assertTrue("pending path must be confined to the updater cache before the APK is trusted", pathGuard >= 0)
        assertTrue("APK size must be revalidated after returning from settings", sizeGuard > pathGuard)
        assertTrue("stored SHA-256 must be revalidated before package identity", shaGuard > sizeGuard)
        assertTrue("package/signing identity must be revalidated after SHA-256", identityGuard > shaGuard)
        assertTrue("candidate version must be checked after package identity", versionGuard > identityGuard)
        assertTrue("installer must launch only after every pending APK revalidation gate", launch > versionGuard)

        val clearBeforeLaunch = resume.lastIndexOf("clearPendingInstall()", launch)
        assertTrue("validated pending state must be cleared immediately before installer handoff", clearBeforeLaunch > versionGuard)
    }
}
