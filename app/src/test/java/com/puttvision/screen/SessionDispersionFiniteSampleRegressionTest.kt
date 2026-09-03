package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionFiniteSampleRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun malformedBridgeTelemetryCannotPoisonDispersionHistory() {
        val production = asset("v179_session_dispersion.gd")

        assertTrue(production.contains("func _v179_push_sample(line_cm: float, pace_cm: float) -> bool:"))
        assertTrue(production.contains("if not is_finite(line_cm) or not is_finite(pace_cm):\n        return false"))
        assertTrue(production.contains("_v179_samples.append(Vector2(line_cm, pace_cm))"))
        assertTrue(production.contains("return true"))
    }

    @Test
    fun rejectedSnapshotDoesNotConsumeCompletionIdentity() {
        val production = asset("v179_session_dispersion.gd")
        val captureStart = production.indexOf("func _v179_capture(s: Dictionary) -> void:")
        val captureEnd = production.indexOf("func _v179_preview_seed() -> void:")
        assertTrue(captureStart >= 0 && captureEnd > captureStart)
        val capture = production.substring(captureStart, captureEnd)

        val push = capture.indexOf("var accepted := _v179_push_sample")
        val reject = capture.indexOf("if not accepted:")
        val serial = capture.indexOf("_v179_last_completion_serial = _v178_completed_shot_serial", reject)
        assertTrue(push >= 0)
        assertTrue(reject > push)
        assertTrue(serial > reject)
    }

    @Test
    fun guardRemainsPresentationOnly() {
        val production = asset("v179_session_dispersion.gd")

        assertTrue(production.contains("extends \"res://v178_session_form_layout.gd\""))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
    }
}
