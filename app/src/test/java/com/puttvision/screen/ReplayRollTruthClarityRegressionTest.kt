package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRollTruthClarityRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun replayDistanceUsesSanitizedSpatialPolyline() {
        val pacing = asset("replay_spatial_pacing.gd")

        assertTrue(pacing.contains("func _v175_trail_total_length(points: Array) -> float:"))
        assertTrue(pacing.contains("_replay_spatial_valid_points(points)"))
        assertTrue(pacing.contains("func _replay_spatial_total_length_valid(valid_points: Array[Vector2]) -> float:"))
        assertTrue(pacing.contains("if is_finite(segment) and segment > REPLAY_SPATIAL_EPSILON"))
        assertTrue(pacing.contains("var total_length := _replay_spatial_total_length_valid(valid_points)"))
    }

    @Test
    fun replayHudCallsRemainingRollToStop() {
        val layout = asset("replay_roll_distance_layout.gd")

        assertTrue(layout.contains("const PREVIEW_SAMPLE_DISTANCE := \"0.9m TO STOP\""))
        assertTrue(layout.contains("const LEGACY_REMAINING_SUFFIX := \" REST\""))
        assertTrue(layout.contains("const CLEAR_REMAINING_SUFFIX := \" TO STOP\""))
        assertTrue(layout.contains("presented_text = presented_text.replace(LEGACY_REMAINING_SUFFIX, CLEAR_REMAINING_SUFFIX)"))
        assertTrue(layout.contains("stage.text = presented_text"))
        assertFalse(layout.contains("set_process(false)"))
    }

    @Test
    fun changesStayPresentationOnly() {
        val pacing = asset("replay_spatial_pacing.gd")
        val layout = asset("replay_roll_distance_layout.gd")

        assertTrue(pacing.contains("Android V135-V137 / GreenTerrain / GreenReadAdvisor remain"))
        assertTrue(layout.contains("no replay clock, trail point, camera or physics data changes"))
    }
}
