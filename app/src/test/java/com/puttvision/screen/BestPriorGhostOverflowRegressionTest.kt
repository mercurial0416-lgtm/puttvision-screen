package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BestPriorGhostOverflowRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun offScaleBestPriorUsesDirectionalEdgeCueAndMagnitude() {
        val script = asset("v193_best_rep_ghost.gd")
        assertTrue(script.contains("_v188_normalized_miss(sample.x, sample.y)"))
        assertTrue(script.contains("var offscale := extent > 1.0"))
        assertTrue(script.contains("_v193_edge_chevron(6.8)"))
        assertTrue(script.contains("_v193_ghost.rotation = normalized.angle()"))
        assertTrue(script.contains("BEST PRIOR · OUT %.1fx"))
    }

    @Test
    fun returningOnScaleRestoresDiamondInsteadOfLeakingOverflowStyle() {
        val script = asset("v193_best_rep_ghost.gd")
        assertTrue(script.contains("_v193_ghost.points = _v193_diamond(6.4)"))
        assertTrue(script.contains("_v193_ghost.rotation = 0.0"))
        assertTrue(script.contains("_v193_ghost.width = 1.8"))
        assertTrue(script.contains("_v193_ghost.default_color = V193_GHOST_COLOR"))
        assertTrue(script.contains("_v193_ghost_label.text = \"◇ BEST PRIOR\""))
    }

    @Test
    fun bestPriorOverflowCueRemainsPresentationOnly() {
        val script = asset("v193_best_rep_ghost.gd")
        assertTrue(script.contains("Presentation-only best-prior-rep ghost"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("shot scoring ="))
    }
}
