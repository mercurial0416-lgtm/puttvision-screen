package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePaceOverrangeTruthRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun overrangePaceIsMarkedAsCappedInsteadOfFakeExact() {
        val layer = asset("live_pace_surge.gd")
        assertTrue(layer.contains("var raw_ratio := maxf(0.0, current_speed / launch_speed)"))
        assertTrue(layer.contains("if raw_ratio > LIVE_PACE_MAX_DISPLAY_RATIO:"))
        assertTrue(layer.contains("pct_text += \"+\""))
        assertTrue(layer.contains("_live_pace_phase(raw_ratio)"))
    }

    @Test
    fun renderedPreviewExercisesOverrangeState() {
        val preview = asset("live_pace_surge_preview.gd")
        assertTrue(preview.contains("PACE 199%+ · 5.00 m/s · SURGING"))
        assertTrue(preview.contains("_live_pace_readout(5.0, 1.0) != \"PACE 199%+ · 5.00 m/s · SURGING\""))
        assertTrue(preview.contains("_live_pace_speed_text(99.0) != \"9.99+ m/s\""))
        assertFalse(preview.contains("_live_pace_readout(5.0, 1.0) != \"PACE 199% · SURGING\""))
    }

    @Test
    fun changeStaysPresentationOnly() {
        val layer = asset("live_pace_surge.gd")
        assertTrue(layer.contains("Authoritative ball velocity still comes from the"))
        assertFalse(layer.contains("GreenTerrain.set"))
        assertFalse(layer.contains("GreenReadAdvisor.set"))
        assertFalse(layer.contains("ballVelocity ="))
    }
}
