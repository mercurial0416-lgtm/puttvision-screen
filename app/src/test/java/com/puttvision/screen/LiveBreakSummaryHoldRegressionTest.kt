package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakSummaryHoldRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun completedRollKeepsTelemetryVisibleForAReviewWindow() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("const LIVE_SUMMARY_HOLD_SECONDS := 2.4"))
        assertTrue(script.contains("elif _live_curve_was_running:"))
        assertTrue(script.contains("_show_live_curve_summary()"))
        assertTrue(script.contains("_live_curve_summary_timer.start(LIVE_SUMMARY_HOLD_SECONDS)"))
        assertTrue(script.contains("_live_curve_panel.visible = true"))
    }

    @Test
    fun summaryExplainsThatTheRollFinishedAndRetainsTravelDistance() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("_live_curve_title.text = \"ROLL SUMMARY\""))
        assertTrue(script.contains("return \"ROLL COMPLETE · %.2f m\" % _live_curve_travel_m"))
        assertTrue(script.contains("_live_curve_title.text = \"LIVE BREAK\""))
    }

    @Test
    fun newRollCancelsTheOldSummaryWithoutTouchingAuthoritativePhysics() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("_live_curve_summary_timer.stop()"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
    }
}
