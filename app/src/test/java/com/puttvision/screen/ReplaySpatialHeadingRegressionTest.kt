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
    fun replayHeadingPreservesPhysicalDistanceSmoothingWithoutRepeatedSanitizing() {
        val script = asset("replay_spatial_pacing.gd")

        assertTrue(script.contains("V175_HEADING_SAMPLE_M / total_length"))
        assertTrue(script.contains("V175_HEADING_WIDE_SAMPLE_M / total_length"))
        assertTrue(script.contains("var heading := near_heading * 0.68 + wide_heading * 0.32"))
        assertTrue(script.contains("_replay_spatial_point_valid(valid_points"))
        assertFalse(script.contains("return super._v175_trail_heading(valid_points, p)"))
    }

    @Test
    fun replayHeadingSanitizesOnceAndKeepsNonzeroFallback() {
        val script = asset("replay_spatial_pacing.gd")

        assertTrue(script.contains("var valid_points := _replay_spatial_valid_points(points)"))
        assertTrue(script.contains("var total_length := _replay_spatial_total_length_valid(valid_points)"))
        assertTrue(script.contains("else Vector2.UP"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
    }
}
