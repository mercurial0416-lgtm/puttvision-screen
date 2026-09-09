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
    fun publishedReleaseIsCleanedIfFailureHappensBeforeUpdaterPublish() {
        val workflow = workflow()
        val publish = workflow.indexOf("echo \"PV_RELEASE_PUBLISHED=true\" >> \"$GITHUB_ENV\"")
        val cleanup = workflow.indexOf("Remove unpublished stale release on pre-updater failure")
        val updater = workflow.indexOf("Publish and verify public updater manifest")

        assertTrue("release publication marker must exist", publish >= 0)
        assertTrue("cleanup step must exist after GitHub release publication", cleanup > publish)
        assertTrue("cleanup must run before updater publication", updater > cleanup)
        assertTrue(workflow.contains("if: ${{ failure() && env.PV_RELEASE_PUBLISHED == 'true' }}"))
        assertTrue(workflow.contains("gh release delete \"$PV_TAG\" --cleanup-tag --yes"))
    }
}
