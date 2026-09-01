package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRollMissingTerminalCoordinateRegressionTest {
    private fun asset(): String {
        val candidates = listOf(
            File("src/main/assets/replay_timeline_camera_truth.gd"),
            File("app/src/main/assets/replay_timeline_camera_truth.gd")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate replay timeline truth asset")
    }

    @Test
    fun terminalPacketWithoutCoordinatesKeepsLastMeasuredBreakTruth() {
        val script = asset()
        assertTrue(script.contains("func _finalize_last_observed_live_roll_truth() -> void:"))
        assertTrue(script.contains("var ball_pos := _live_curve_last_ball_pos"))
        assertTrue(script.contains("_live_curve_value.text = _live_last_observed_readout(cross_track_cm)"))
        assertTrue(script.contains("LAST OBS %s %.1f cm"))
        assertTrue(script.contains("elif missing_ball_position:\n            _finalize_last_observed_live_roll_truth()"))
    }

    @Test
    fun terminalHoldNeverMutatesAuthoritativeBridgePayload() {
        val script = asset()
        assertTrue(script.contains("presentation_snapshot = s.duplicate()"))
        assertTrue(script.contains("presentation_snapshot[\"ballX\"] = _live_curve_last_ball_pos.x"))
        assertTrue(script.contains("presentation_snapshot[\"ballY\"] = _live_curve_last_ball_pos.y"))
        assertTrue(script.contains("super._apply_snapshot(presentation_snapshot, immediate, delta)"))
        assertTrue(script.contains("authoritative consumers keep the original payload"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("bridge."))
    }
}
