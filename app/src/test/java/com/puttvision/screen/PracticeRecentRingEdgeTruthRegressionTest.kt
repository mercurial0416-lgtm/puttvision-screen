package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRecentRingEdgeTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun consistencyEnvelopeNeverShrinksToFakeATighterGroupAtPlotEdges() {
        val trend = asset("practice_trend_vector.gd")

        assertTrue(trend.contains("var raw_radius := max_distance + PRACTICE_RECENT_RING_PADDING"))
        assertTrue(trend.contains("if raw_radius > PRACTICE_RECENT_RING_MAX_RADIUS + PRACTICE_RECENT_RING_FIT_EPSILON:"))
        assertTrue(trend.contains("if edge_radius + PRACTICE_RECENT_RING_FIT_EPSILON < desired_radius:"))
        assertTrue(trend.contains("return {\"visible\": false, \"clipped\": true}"))
        assertTrue(trend.contains("var radius := desired_radius"))
        assertFalse(trend.contains("var radius := minf(desired_radius, maxf(0.0, edge_radius))"))
    }

    @Test
    fun envelopeTruthGuardRemainsPresentationOnlyAndBounded() {
        val trend = asset("practice_trend_vector.gd")

        assertTrue(trend.contains("const PRACTICE_RECENT_RING_SEGMENTS := 20"))
        assertTrue(trend.contains("PRACTICE_RECENT_GROUP_SIZE := 3"))
        assertFalse(trend.contains("GreenTerrain.set"))
        assertFalse(trend.contains("GreenReadAdvisor.set"))
        assertFalse(trend.contains("ballVelocity ="))
        assertFalse(trend.contains("readLineDeltaCm ="))
        assertFalse(trend.contains("paceDeltaCm ="))
    }
}
