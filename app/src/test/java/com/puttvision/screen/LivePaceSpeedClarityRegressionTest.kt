package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePaceSpeedClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun livePaceKeepsAuthoritativeSpeedVisibleAlongsideRelativePhase() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("func _live_pace_speed_text(current_speed: float) -> String:"))
        assertTrue(script.contains("return \"%.2f%s m/s\" % [speed, suffix]"))
        assertTrue(script.contains("return \"PACE %s · %s · %s\" % [pct_text, _live_pace_speed_text(current_speed), _live_pace_phase(raw_ratio)]"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
    }

    @Test
    fun malformedOrExtremeSpeedCannotPolluteHud() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("if not is_finite(current_speed):"))
        assertTrue(script.contains("LIVE_PACE_MAX_DISPLAY_SPEED_MPS := 9.99"))
        assertTrue(script.contains("clampf(current_speed, 0.0, LIVE_PACE_MAX_DISPLAY_SPEED_MPS)"))
        assertTrue(script.contains("var suffix := \"+\" if current_speed > LIVE_PACE_MAX_DISPLAY_SPEED_MPS else \"\""))
        assertTrue(script.contains("launch_speed <= 0.001"))
    }
}
