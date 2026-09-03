package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFadeFrameStallRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun focusFadesBoundSingleFrameHitches() {
        val source = asset("presentation_focus_choreography.gd")

        assertTrue(source.contains("const FOCUS_MAX_FADE_DELTA_S := 0.10"))
        assertTrue(source.contains("func _focus_safe_fade_delta(delta: float) -> float:"))
        assertTrue(source.contains("if not is_finite(delta) or delta <= 0.0:"))
        assertTrue(source.contains("return minf(delta, FOCUS_MAX_FADE_DELTA_S)"))
        assertTrue(source.contains("var safe_delta := _focus_safe_fade_delta(delta)"))
        assertTrue(source.contains("FOCUS_FADE_SPEED * safe_delta"))
        assertFalse(source.contains("FOCUS_FADE_SPEED * delta)"))
    }

    @Test
    fun immediatePhaseApplicationStillBypassesDamping() {
        val source = asset("presentation_focus_choreography.gd")

        assertTrue(source.contains("c.a = target if immediate else move_toward"))
        assertTrue(source.contains("_focus_apply_phase(PHASE_READY, true)"))
    }

    @Test
    fun focusFadeGuardRemainsPresentationOnly() {
        val source = asset("presentation_focus_choreography.gd")

        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("ball.position ="))
        assertFalse(source.contains("s[\"ballSpeed\"] ="))
    }
}
