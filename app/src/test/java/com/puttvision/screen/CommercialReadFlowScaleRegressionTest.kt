package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialReadFlowScaleRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun liveBreakTraceKeepsShotPeakOutsideRollingWindow() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("display_peak_cm: float = 0.0"))
        assertTrue(script.contains("var peak := maxf(5.0, display_peak_cm)"))
        assertTrue(script.contains("_live_trace_points_with_distance(_live_curve_history, _live_curve_distance_history, _live_curve_peak_cm)"))
        assertTrue(script.contains("if absf(cross_track_cm) > _live_curve_peak_cm:"))
        assertTrue(script.contains("_live_curve_peak_cm = 0.0"))
    }

    @Test
    fun rollingWindowAndAuthoritativePhysicsBoundaryRemainUnchanged() {
        val script = asset("commercial_read_flow.gd")
        assertTrue(script.contains("LIVE_TRACE_MAX_POINTS := 28"))
        assertTrue(script.contains("while _live_curve_history.size() > LIVE_TRACE_MAX_POINTS:"))
        assertTrue(script.contains("Nothing feeds back into GreenTerrain, GreenReadAdvisor, scoring, aim, or physics."))
    }
}
