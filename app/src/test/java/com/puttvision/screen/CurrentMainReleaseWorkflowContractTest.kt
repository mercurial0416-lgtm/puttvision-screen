package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentMainReleaseWorkflowContractTest {
    private fun workflowSource(): String {
        val candidates = listOf(
            File(".github/workflows/current-main-release.yml"),
            File("../.github/workflows/current-main-release.yml")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate current-main-release.yml from ${File(".").absolutePath}")
    }

    @Test
    fun releasePinsMainBeforePublishingReleaseAndUpdater() {
        val source = workflowSource()
        val pinMain = source.indexOf("- name: Pin current main source")
        val publishRelease = source.indexOf("- name: Publish semantic GitHub release")
        val publishUpdater = source.indexOf("- name: Publish and verify public updater manifest")
        val finalContract = source.indexOf("- name: Verify latest release and updater contract")

        assertTrue("Release must pin the main SHA before publishing anything", pinMain >= 0)
        assertTrue("GitHub Release must be published only after main is pinned", publishRelease > pinMain)
        assertTrue("Updater manifest must be published only after the GitHub Release", publishUpdater > publishRelease)
        assertTrue("Final release/updater contract must run after updater publication", finalContract > publishUpdater)
        assertTrue(source.contains("- name: Ensure pinned source is still current main"))
        assertTrue(source.contains("- name: Ensure updater still targets current main"))
    }

    @Test
    fun releaseKeepsSignedDualApkAndPublicUpdaterIntegrityChecks() {
        val source = workflowSource()

        assertTrue("Consumer APK must stay in the production release", source.contains("puttvision-consumer.apk"))
        assertTrue("Developer APK must stay in the production release", source.contains("puttvision.apk"))
        assertTrue("Production signing must remain APK Signature Scheme v3", source.contains("--v3-signing-enabled true"))
        assertTrue("Published APKs must be downloaded and integrity-checked", source.contains("- name: Verify published APK integrity"))
        assertTrue("Public updater manifest must be checked without trusting cache", source.contains("Cache-Control: no-cache"))
        assertTrue("Updater contract must validate versionCode", source.contains(".versionCode =="))
        assertTrue("Updater contract must validate versionName", source.contains(".versionName =="))
        assertTrue("Updater contract must validate sha256", source.contains(".sha256 =="))
        assertTrue("Updater contract must validate apkUrl", source.contains(".apkUrl =="))
    }
}
