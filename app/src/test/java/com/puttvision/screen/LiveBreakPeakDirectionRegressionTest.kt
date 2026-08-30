package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakPeakDirectionRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun liveBreakPeakPreservesTheSideOfTheLargestExcursion() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("var _live_curve_peak_signed_cm := 0.0"))
        assertTrue(script.contains("if absf(cross_track_cm) > _live_curve_peak_cm:"))
        assertTrue(script.contains("_live_curve_peak_signed_cm = cross_track_cm"))
        assertTrue(script.contains("_live_peak_readout(_live_curve_peak_signed_cm)"))
        assertTrue(script.contains("PEAK %s %.1f cm"))
    }

    @Test
    fun signedPeakReadoutKeepsCenterLeftAndRightSemantics() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("return \"PEAK CENTER\""))
        assertTrue(script.contains("\"R\" if peak_signed_cm > 0.0 else \"L\""))
        assertFalse(script.contains("_live_curve_peak_label.text = \"PEAK %.1f cm\""))
    }

    @Test
    fun liveBreakPeakChangeRemainsPresentationOnly() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("Nothing feeds back into GreenTerrain, GreenReadAdvisor, scoring, aim, or physics."))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
    }
}
