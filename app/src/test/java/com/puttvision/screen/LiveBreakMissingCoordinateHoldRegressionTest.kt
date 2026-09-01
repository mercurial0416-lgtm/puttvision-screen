package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakMissingCoordinateHoldRegressionTest {
    private fun asset(): String {
        val candidates = listOf(
            File("src/main/assets/live_break_coordinate_hold.gd"),
            File("app/src/main/assets/live_break_coordinate_hold.gd")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate LIVE BREAK coordinate hold asset")
    }

    @Test
    fun missingMidRollCoordinateHoldsLastRealPosition() {
        val script = asset()
        assertTrue(script.contains("missing_ball_position and had_real_ball_position"))
        assertTrue(script.contains("presentation_snapshot = s.duplicate()"))
        assertTrue(script.contains("presentation_snapshot[\"ballX\"] = _live_curve_last_ball_pos.x"))
        assertTrue(script.contains("presentation_snapshot[\"ballY\"] = _live_curve_last_ball_pos.y"))
        assertTrue(script.contains("super._apply_snapshot(presentation_snapshot, immediate, delta)"))
    }

    @Test
    fun missingLaunchCoordinateCannotSeedSyntheticTelemetry() {
        val script = asset()
        val superCall = "super._apply_snapshot(presentation_snapshot, immediate, delta)"
        val neutralizeCall = "if missing_ball_position and not had_real_ball_position:\n        _live_break_neutralize_missing_position()"
        assertTrue(script.contains(neutralizeCall))
        assertTrue(script.indexOf(superCall) < script.indexOf(neutralizeCall))
        assertTrue(script.contains("_live_curve_travel_m = 0.0"))
        assertTrue(script.contains("_live_curve_has_ball_pos = false"))
        assertTrue(script.contains("_live_curve_value.text = \"TRACKING\""))
        assertTrue(script.contains("_live_curve_peak_label.text = \"PEAK --\""))
        assertTrue(script.contains("_live_curve_pace_label.text = \"PACE --\""))
    }
}
