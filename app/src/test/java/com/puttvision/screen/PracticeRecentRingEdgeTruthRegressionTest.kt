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
    fun productionOverrideDelegatesToTheTruthGuardInsteadOfDrawingClippedArcs() {
        val production = asset("practice_ring_edge_truth.gd")

        assertTrue(production.contains("return super._practice_recent_ring_geometry(samples)"))
        assertFalse(production.contains("clampf("))
        assertFalse(production.contains("visible_arc"))
        assertFalse(production.contains("best_len"))
    }

    @Test
    fun envelopeTruthGuardRemainsPresentationOnlyAndBounded() {
        val trend = asset("practice_trend_vector.gd")
        val production = asset("practice_ring_edge_truth.gd")

        assertTrue(trend.contains("const PRACTICE_RECENT_RING_SEGMENTS := 20"))
        assertTrue(trend.contains("PRACTICE_RECENT_GROUP_SIZE := 3"))
        for (source in listOf(trend, production)) {
            assertFalse(source.contains("GreenTerrain.set"))
            assertFalse(source.contains("GreenReadAdvisor.set"))
            assertFalse(source.contains("ballVelocity ="))
            assertFalse(source.contains("readLineDeltaCm ="))
            assertFalse(source.contains("paceDeltaCm ="))
        }
    }
}
