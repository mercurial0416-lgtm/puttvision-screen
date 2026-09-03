package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySpatialHeadingRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun replayHeadingUsesSameSpatialDomainAsReplayPosition() {
        val script = asset("replay_spatial_pacing.gd")

        assertTrue(script.contains("const REPLAY_HEADING_SAMPLE_FRACTION := 0.006"))
        assertTrue(script.contains("var before := _v175_trail_point(valid_points, before_p)"))
        assertTrue(script.contains("var after := _v175_trail_point(valid_points, after_p)"))
        assertTrue(script.contains("return heading.normalized()"))
        assertFalse(script.contains("return super._v175_trail_heading(valid_points, p)"))
    }

    @Test
    fun replayHeadingSanitizesInputsAndKeepsPhysicsUntouched() {
        val script = asset("replay_spatial_pacing.gd")

        assertTrue(script.contains("var valid_points := _replay_spatial_valid_points(points)"))
        assertTrue(script.contains("if not heading.is_finite()"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
    }
}
