package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentMainReleaseCleanupRegressionTest {
    private fun workflow(): String {
        val candidates = listOf(
            File(".github/workflows/current-main-release.yml"),
            File("../.github/workflows/current-main-release.yml")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate current-main-release.yml from ${File(".").absolutePath}")
    }

    @Test
    fun partialReleaseCreationIsCleanedIfFailureHappensBeforeUpdaterPublish() {
        val workflow = workflow()
        val attempted = workflow.indexOf("echo \"PV_RELEASE_ATTEMPTED=true\" >> \"\$GITHUB_ENV\"")
        val create = workflow.indexOf("gh release create \"\$PV_TAG\"")
        val published = workflow.indexOf("echo \"PV_RELEASE_PUBLISHED=true\" >> \"\$GITHUB_ENV\"")
        val cleanup = workflow.indexOf("Remove unpublished stale release on pre-updater failure")
        val updater = workflow.indexOf("Publish and verify public updater manifest")

        assertTrue("release attempt marker must exist before gh release create", attempted >= 0 && create > attempted)
        assertTrue("published marker must remain after gh release create", published > create)
        assertTrue("cleanup step must exist after GitHub release creation attempt", cleanup > published)
        assertTrue("cleanup must run before updater publication", updater > cleanup)
        assertTrue(workflow.contains("if: \${{ failure() && env.PV_RELEASE_ATTEMPTED == 'true' }}"))
        assertTrue("release lookup must fail closed", workflow.contains("if ! gh api --paginate") && workflow.contains("leaving release/tag untouched"))
        assertTrue("release lookup must persist a successful API response before selection", workflow.contains("RELEASES_JSON=\"\$RUNNER_TEMP/release-cleanup-releases.json\"") && workflow.contains("RELEASE_INFO=\$(jq -r"))
        assertTrue("cleanup must select the allocated release tag", workflow.contains("--arg tag \"\$PV_TAG\"") && workflow.contains(".tag_name == \$tag"))
        assertTrue("cleanup must only delete artifacts owned by the pinned source", workflow.contains("RELEASE_TARGET") && workflow.contains("PV_SOURCE_SHA"))
        assertTrue("cleanup must remove a matching release by id", workflow.contains("gh api --method DELETE \"repos/\$GITHUB_REPOSITORY/releases/\$RELEASE_ID\""))
        assertTrue("cleanup must remove an owned tag left behind by partial creation", workflow.contains("gh api --method DELETE \"repos/\$GITHUB_REPOSITORY/git/refs/tags/\$PV_TAG\""))
    }
}
