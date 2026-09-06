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
        assertTrue(source.contains("PRESSURE LADDER  ·  %s  ·  RESET  ·  %s"))
    }

    @Test
    fun resetCoachingSupportsCombinedLineAndPaceMisses() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("if axis == \"LINE\" or axis == \"BOTH\":"))
        assertTrue(source.contains("if axis == \"PACE\" or axis == \"BOTH\":"))
        assertTrue(source.contains("return \" · \".join(corrections)"))
    }

    @Test
    fun resetCoachingOwnsFullFooterWidthWithoutMeterCollision() {
        val source = asset("v191_practice_streak.gd")
        assertTrue(source.contains("const V191_COPY_COMPACT_WIDTH := 300.0"))
        assertTrue(source.contains("const V191_COPY_RESET_WIDTH := 524.0"))
        assertTrue(source.contains("_v191_streak_label.size.x = V191_COPY_RESET_WIDTH if reset_focus else V191_COPY_COMPACT_WIDTH"))
        assertTrue(source.contains("segment.visible = not reset_focus"))
        assertTrue(source.contains("axis != \"BUILDING\" and _v191_streak == 0 and _v191_has_focus_samples()"))
        assertFalse(source.contains("axis != \"BUILDING\" and _v191_streak == 0 and not _v179_samples.is_empty()"))
    }

    @Test
    fun productionDrillOverridePreservesActionableResetCoaching() {
        val source = asset("v192_drill_progression.gd")
        assertTrue(source.contains("var correction := _v191_reset_coaching(axis)"))
        assertTrue(source.contains("PRESSURE LADDER  ·  %s  ·  RESET  ·  %s  ·  %d/%d  ·  -0.5 m EASIER"))
        assertTrue(source.contains("PRESSURE LADDER  ·  %s  ·  RECOVER  ·  %s  ·  %d/%d TO RESET"))
        assertFalse(source.contains("return \"START STREAK  ·  -0.5 m EASIER\""))
        assertFalse(source.contains("return \"START STREAK  ·  BUILD  ·  0/3\""))
    }

    @Test
    fun freshObjectiveStartsCleanInsteadOfPretendingThePlayerReset() {
        val source = asset("v192_drill_progression.gd")
        assertTrue(source.contains("if not _v191_has_focus_samples():"))
        assertTrue(source.contains("PRESSURE LADDER  ·  %s  ·  0/%d  ·  START"))
        val freshStart = source.indexOf("if not _v191_has_focus_samples():")
        val resetCopy = source.indexOf("var correction := _v191_reset_coaching(axis)")
        assertTrue(freshStart in 0 until resetCopy)
    }

    @Test
    fun failureProgressUsesTheRealTrailingMissCountInsteadOfStickingAtZero() {
        val source = asset("v192_drill_progression.gd")
        assertTrue(source.contains("var failures := mini(_v192_trailing_failures(axis), V192_RESET_FAILURES)"))
        assertTrue(source.contains("[axis, correction, failures, V192_RESET_FAILURES]"))
        assertFalse(source.contains("RESET  ·  %s  ·  0/3"))
    }

    @Test
    fun recoverableMissesDoNotPretendTheResetGateAlreadyFired() {
        val source = asset("v192_drill_progression.gd")
        val threshold = source.indexOf("if failures >= V192_RESET_FAILURES:")
        val reset = source.indexOf("RESET  ·  %s  ·  %d/%d  ·  -0.5 m EASIER")
        val recover = source.indexOf("RECOVER  ·  %s  ·  %d/%d TO RESET")
        assertTrue(threshold >= 0)
        assertTrue(reset > threshold)
        assertTrue(recover > reset)
        assertFalse(source.contains("RESET  ·  %s  ·  %d/%d TO EASIER"))
    }

    @Test
    fun recoveryWarningHasDistinctVisualHierarchyWithoutMasqueradingAsReset() {
        val source = asset("v192_drill_progression.gd")
        assertTrue(source.contains("const V192_RECOVER_COLOR := Color(\"#e9bf72\")"))
        assertTrue(source.contains("const V192_RESET_COLOR := Color(\"#f0a56d\")"))
        assertTrue(source.contains("var recovering := _v191_streak == 0 and failures > 0 and failures < V192_RESET_FAILURES"))
        assertTrue(source.contains("elif recovering:"))
        assertTrue(source.contains("_v191_streak_label.modulate = V192_RECOVER_COLOR"))
        assertTrue(source.contains("_v191_streak_label.modulate = V192_RESET_COLOR"))
        assertFalse(source.contains("_v190_target_zone.color = V192_RECOVER_COLOR"))
    }

    @Test
    fun successCopyCarriesObjectiveAndExplicitProgress() {
        val base = asset("v191_practice_streak.gd")
        val production = asset("v192_drill_progression.gd")
        assertTrue(base.contains("PRESSURE LADDER  ·  %s  ·  %s  ·  ONE MORE"))
        assertTrue(base.contains("PRESSURE LADDER  ·  %s  ·  %s  ·  HOLD IT"))
        assertTrue(production.contains("PRESSURE LADDER  ·  %s  ·  %s  ·  READY  ·  +0.5 m NEXT"))
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
