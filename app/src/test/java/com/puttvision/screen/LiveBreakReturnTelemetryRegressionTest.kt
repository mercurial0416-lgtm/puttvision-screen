package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakReturnTelemetryRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun exactRestCanExposeMeaningfulReturnTowardCenter() {
        val layer = asset("replay_timeline_camera_truth.gd")

        assertTrue(layer.contains("const LIVE_RETURN_MIN_CM := 1.0"))
        assertTrue(layer.contains("func _live_peak_finish_readout(peak_signed_cm: float, rest_signed_cm: float) -> String:"))
        assertTrue(layer.contains("· BACK %.1f cm"))
        assertTrue(layer.contains("_live_peak_finish_readout(_live_curve_peak_signed_cm, cross_track_cm)"))
    }

    @Test
    fun incompleteTerminalTelemetryDoesNotPretendToKnowReturn() {
        val layer = asset("replay_timeline_camera_truth.gd")
        val lastObservedBlock = layer.substringAfter("func _finalize_last_observed_live_roll_truth() -> void:")
            .substringBefore("func _finalize_live_roll_truth(s: Dictionary) -> void:")

        assertTrue(lastObservedBlock.contains("_live_peak_readout(_live_curve_peak_signed_cm)"))
        assertFalse(lastObservedBlock.contains("_live_peak_finish_readout"))
    }

    @Test
    fun changeRemainsPresentationOnly() {
        val layer = asset("replay_timeline_camera_truth.gd")
        assertTrue(layer.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(layer.contains("GreenTerrain and GreenReadAdvisor remain authoritative and untouched"))
        assertFalse(layer.contains("GreenTerrain."))
        assertFalse(layer.contains("GreenReadAdvisor."))
    }
}
