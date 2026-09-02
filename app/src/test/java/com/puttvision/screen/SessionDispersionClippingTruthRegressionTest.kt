package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionClippingTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun clippedSessionSamplesAreExplicitInsteadOfLookingExactAtThePlotEdge() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("func _session_dispersion_is_outside_view(sample: Vector2) -> bool:"))
        assertTrue(script.contains("absf(sample.x) > V179_LINE_SCALE_CM"))
        assertTrue(script.contains("absf(sample.y) > V179_PACE_SCALE_CM"))
        assertTrue(script.contains("OUTSIDE VIEW %d"))
        assertTrue(script.contains("ALL POINTS IN VIEW"))
    }

    @Test
    fun clippedSamplesGainVisualWeightWithoutChangingTheFixedComparisonScale() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("const SESSION_NORMAL_DOT_SIZE := Vector2(10.0, 10.0)"))
        assertTrue(script.contains("const SESSION_CLIPPED_DOT_SIZE := Vector2(14.0, 14.0)"))
        assertTrue(script.contains("dot.size = SESSION_CLIPPED_DOT_SIZE if clipped else SESSION_NORMAL_DOT_SIZE"))
        assertTrue(script.contains("dot.position = _v179_plot_position(_v179_samples[index]) - dot.size * 0.5"))
    }

    @Test
    fun renderPreviewExercisesAnOutOfViewMiss() {
        val script = asset("replay_timeline_camera_truth.gd")
        val preview = script.substringAfter("func _v179_preview_seed() -> void:")
            .substringBefore("\nfunc _apply_snapshot")
        assertTrue(preview.contains("Vector2(38, 14)"))
        assertTrue(preview.contains("_v179_refresh()"))
    }
}
