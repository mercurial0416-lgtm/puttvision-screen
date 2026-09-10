package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentMainReleaseContractTest {
    private fun workflowSource(): String {
        val candidates = listOf(
            File(".github/workflows/current-main-release.yml"),
            File("../.github/workflows/current-main-release.yml"),
            File("../../.github/workflows/current-main-release.yml")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate current-main-release.yml from ${File(".").absolutePath}")
    }

    @Test
    fun releaseTriggerRemainsExplicitAndIssueScoped() {
        val workflow = workflowSource()

        assertTrue(
            "Current-main release must stay scoped to issue #138",
            workflow.contains("github.event.issue.number == 138")
        )
        assertTrue(
            "Current-main release must require the exact explicit command",
            workflow.contains("github.event.comment.body == '/release-current-main'")
        )
    }

    @Test
    fun publishRemainsBoundToTheVerifiedMainSha() {
        val workflow = workflowSource()

        assertTrue(
            "Release must re-read live main immediately before publishing",
            workflow.contains("LIVE_MAIN_SHA=$(gh api \"repos/$GITHUB_REPOSITORY/commits/main\" --jq .sha)")
        )
        assertTrue(
            "Release must abort if main moved after the verified checkout",
            workflow.contains("test \"$LIVE_MAIN_SHA\" = \"$PV_SOURCE_SHA\"")
        )
        assertTrue(
            "Published GitHub Release must target the verified source SHA",
            workflow.contains("test \"$(echo \"$LATEST\" | jq -r .target_commitish)\" = \"$PV_SOURCE_SHA\"")
        )
    }

    @Test
    fun updaterContractVerifiesMetadataAndDownloadedApkIntegrity() {
        val workflow = workflowSource()

        assertTrue(
            "Updater manifest verification must bypass caches",
            workflow.contains("Cache-Control: no-cache")
        )
        assertTrue(
            "Updater manifest must match release version, hash and URL",
            workflow.contains(".versionCode == $vc and .versionName == $vn and .sha256 == $sha and .apkUrl == $url")
        )
        assertTrue(
            "Updater verification must download the APK referenced by the live manifest",
            workflow.contains("curl --fail-with-body -sSL --retry 3 --retry-all-errors \"$LIVE_APK_URL\" -o \"$UPDATER_APK\"")
        )
        assertTrue(
            "Downloaded updater APK hash must be verified",
            workflow.contains("sha256sum \"$UPDATER_APK\"")
        )
        assertTrue(
            "Downloaded updater APK size must be verified",
            workflow.contains("stat -c%s \"$UPDATER_APK\"")
        )
    }

    @Test
    fun finalContractRequiresBothApksAndDetectableVersionAdvance() {
        val workflow = workflowSource()

        assertTrue(
            "Final release contract must require both consumer and developer APK assets",
            workflow.contains("index(\"puttvision-consumer.apk\") != null") &&
                workflow.contains("index(\"puttvision.apk\") != null")
        )
        assertTrue(
            "Final updater contract must prove a previously installed version sees an update",
            workflow.contains("PREVIOUS_VERSION_CODE=$((PV_VERSION_CODE - 1))") &&
                workflow.contains(".versionCode > $installed")
        )
    }
}
