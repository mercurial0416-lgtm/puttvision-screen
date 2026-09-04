package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOriginNumericTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun liveOriginRejectsNonNumericCoordinatesBeforeFloatCoercion() {
        val source = asset("live_origin_truth_guard.gd")

        assertTrue(source.contains("_presentation_is_finite_number(s.get(x_key))"))
        assertTrue(source.contains("_presentation_is_finite_number(s.get(y_key))"))
        assertFalse(source.contains("is_finite(float(s.get(x_key, 0.0)))"))
        assertFalse(source.contains("is_finite(float(s.get(y_key, 0.0)))"))
    }

    @Test
    fun malformedStartAndBallPairsStillUseTruthfulTrackingFallback() {
        val source = asset("live_origin_truth_guard.gd")

        assertTrue(source.contains("_live_origin_pending = not _live_pair_is_finite(s, \"startX\", \"startY\")"))
        assertTrue(source.contains("if running and _live_origin_pending and _live_pair_is_finite(s, \"ballX\", \"ballY\")"))
        assertTrue(source.contains("presentation_snapshot[\"startX\"] = ball_pos.x"))
        assertTrue(source.contains("presentation_snapshot[\"startY\"] = ball_pos.y"))
    }

    @Test
    fun liveOriginGuardRemainsPresentationOnly() {
        val source = asset("live_origin_truth_guard.gd")

        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("readLineDeltaCm ="))
        assertFalse(source.contains("paceDeltaCm ="))
    }
}
