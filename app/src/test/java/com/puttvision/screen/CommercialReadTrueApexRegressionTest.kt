package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialReadTrueApexRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun apexMarkerUsesMaximumCurveDeviationInsteadOfFixedMidpoint() {
        val script = asset("commercial_read_apex.gd")

        assertTrue(script.contains("var chord_start: Vector2 = curve[0]"))
        assertTrue(script.contains("var chord_end: Vector2 = curve[curve.size() - 1]"))
        assertTrue(script.contains("absf(chord.cross(relative)) / chord_length"))
        assertTrue(script.contains("if distance > best_distance:"))
        assertTrue(script.contains("return curve[best_index]"))
        assertFalse(script.contains("if curve.is_empty():\n        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5\n    return curve[int(curve.size() / 2)]"))
    }

    @Test
    fun apexSelectionRemainsPresentationOnly() {
        val script = asset("commercial_read_apex.gd")

        assertTrue(script.contains("existing recommended read path"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("_v165_recommended_offset ="))
    }
}
