package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakCoordinateRelockRegressionTest {
    private fun asset(): String {
        val candidates = listOf(
            File("src/main/assets/replay_timeline_camera_truth.gd"),
            File("app/src/main/assets/replay_timeline_camera_truth.gd")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate replay timeline asset")
    }

    @Test
    fun relockWithoutBallCoordinatesWaitsForRealPosition() {
        val script = asset()
        val guard = "if not s.has(\"ballX\") or not s.has(\"ballY\"):\n        if _live_curve_value != null:"
        val ballSample = "var ball_pos := Vector2(float(s.get(\"ballX\", 0.0)), float(s.get(\"ballY\", 0.0)))"
        assertTrue(script.contains(guard))
        assertTrue(script.indexOf(guard) < script.indexOf(ballSample))
        assertTrue(script.contains("_live_curve_value.text = \"TRACKING\""))
        assertTrue(script.contains("_live_curve_peak_label.text = \"PEAK --\""))
        assertTrue(script.contains("_live_curve_pace_label.text = \"PACE --\""))
    }
}
