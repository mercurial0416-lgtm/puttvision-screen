package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePaceSurgeHysteresisRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun surgePhaseUsesSeparateEnterAndExitThresholds() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("const LIVE_PACE_SURGE_ENTER_RATIO := 1.07"))
        assertTrue(script.contains("const LIVE_PACE_SURGE_EXIT_RATIO := 1.03"))
        assertTrue(script.contains("var _live_pace_surging := false"))
        assertTrue(script.contains("elif ratio > LIVE_PACE_SURGE_ENTER_RATIO:"))
        assertTrue(script.contains("if ratio < LIVE_PACE_SURGE_EXIT_RATIO:"))
        assertFalse(script.contains("LIVE_PACE_SURGE_THRESHOLD"))
    }

    @Test
    fun numericPaceRemainsSampleAccurateWhilePhaseIsStabilized() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("clampf(current_speed / launch_speed, 0.0, LIVE_PACE_MAX_DISPLAY_RATIO)"))
        assertTrue(script.contains("_live_pace_phase(ratio)"))
        assertTrue(script.contains("int(round(ratio * 100.0))"))
    }

    @Test
    fun surgeLatchResetsBetweenShotsAndRemainsPresentationOnly() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("if not bool(s.get(\"running\", false)):"))
        assertTrue(script.contains("_live_pace_surging = false"))
        assertTrue(script.contains("Authoritative ball velocity still comes from the"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
    }
}
