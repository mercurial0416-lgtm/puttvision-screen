package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePressureLadderStateRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun completionHighlightHasExplicitNonCompleteReset() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("_v191_base_target_color(axis)"))
        assertTrue(source.contains("if _v190_target_zone != null:"))
        assertTrue(source.contains("if complete else _v191_base_target_color(axis)"))
        assertTrue(source.contains("Color(0.96, 0.86, 0.49, 0.18) if complete"))
    }

    @Test
    fun normalPaletteStillMatchesTargetWindowSemantics() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("axis == \"BOTH\""))
        assertTrue(source.contains("Color(0.96, 0.86, 0.49, 0.13)"))
        assertTrue(source.contains("Color(0.46, 0.84, 0.71, 0.11)"))
    }

    @Test
    fun resetCoachingExplainsMinimumCorrectionBackIntoWindow() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("absf(sample.x) - V190_LINE_TOLERANCE_CM"))
        assertTrue(source.contains("absf(sample.y) - V190_PACE_TOLERANCE_CM"))
        assertTrue(source.contains("\"LEFT\" if sample.x > 0.0 else \"RIGHT\""))
        assertTrue(source.contains("\"SHORTEN\" if sample.y > 0.0 else \"ADD\""))
        assertTrue(source.contains("maxi(1, int(ceil(line_excess)))"))
        assertTrue(source.contains("maxi(1, int(ceil(pace_excess)))"))
        assertTrue(source.contains("PRESSURE LADDER  ·  RESET  ·  %s"))
    }

    @Test
    fun resetCoachingSupportsCombinedLineAndPaceMisses() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("if axis == \"LINE\" or axis == \"BOTH\":"))
        assertTrue(source.contains("if axis == \"PACE\" or axis == \"BOTH\":"))
        assertTrue(source.contains("return \" · \".join(corrections)"))
    }

    @Test
    fun productionDrillOverridePreservesActionableResetCoaching() {
        val source = asset("v192_drill_progression.gd")
        assertTrue(source.contains("var correction := _v191_reset_coaching(axis)"))
        assertTrue(source.contains("PRESSURE LADDER  ·  RESET  ·  %s  ·  -0.5 m EASIER"))
        assertTrue(source.contains("PRESSURE LADDER  ·  RESET  ·  %s  ·  0/3"))
        assertFalse(source.contains("return \"START STREAK  ·  -0.5 m EASIER\""))
        assertFalse(source.contains("return \"START STREAK  ·  BUILD  ·  0/3\""))
    }

    @Test
    fun pressureLadderRemainsPresentationOnly() {
        val source = asset("v191_practice_streak.gd") + asset("v192_drill_progression.gd")
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("readLineDeltaCm ="))
        assertFalse(source.contains("paceDeltaCm ="))
    }
}
