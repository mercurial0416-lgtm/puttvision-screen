package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapOverflowRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun shotMapProjectsLargeMissesRadiallyInsteadOfSquareClampingThem() {
        val script = asset("v188_miss_map.gd")
        assertTrue(script.contains("V188_LINE_WINDOW_CM := 30.0"))
        assertTrue(script.contains("V188_PACE_WINDOW_CM := 70.0"))
        assertTrue(script.contains("if normalized.length() > 1.0"))
        assertTrue(script.contains("normalized = normalized.normalized()"))
        assertFalse(script.contains("clampf(line_delta_cm / 30.0, -1.0, 1.0)"))
        assertFalse(script.contains("clampf(pace_delta_cm / 70.0, -1.0, 1.0)"))
    }

    @Test
    fun offScaleMissesExposeDirectionAndMagnitudeAtTheMapEdge() {
        val script = asset("v188_miss_map.gd")
        assertTrue(script.contains("ShotMissOverflowTick"))
        assertTrue(script.contains("ShotMapOverflowReadout"))
        assertTrue(script.contains("OUTSIDE  %.1fx"))
        assertTrue(script.contains("_v188_arrow_marker(7.0)"))
        assertTrue(script.contains("_v188_dot.rotation = direction.angle()"))
        assertTrue(script.contains("tail_length := clampf"))
    }

    @Test
    fun shotMapEnhancementRemainsPresentationOnly() {
        val script = asset("v188_miss_map.gd")
        assertTrue(script.contains("Presentation-only post-shot miss map"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("shot scoring ="))
    }
}
