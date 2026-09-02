package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakDirectionClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun liveBreakUsesTvReadableDirectionWordsForCurrentAndPeak() {
        val script = asset("green_read_direction_truth.gd")
        assertTrue(script.contains("func _live_curve_readout(cross_track_cm: float) -> String:"))
        assertTrue(script.contains("\"RIGHT\" if cross_track_cm > 0.0 else \"LEFT\""))
        assertTrue(script.contains("func _live_peak_readout(peak_signed_cm: float) -> String:"))
        assertTrue(script.contains("\"RIGHT\" if peak_signed_cm > 0.0 else \"LEFT\""))
        assertFalse(script.contains("return \"%s %.1f cm\" % [\"R\" if cross_track_cm"))
        assertFalse(script.contains("return \"PEAK %s %.1f cm\" % [\"R\" if peak_signed_cm"))
    }

    @Test
    fun directionCorrectionStaysPresentationOnly() {
        val script = asset("green_read_direction_truth.gd")
        assertTrue(script.contains("GreenTerrain and GreenReadAdvisor remain authoritative"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("bridge."))
    }
}
