package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadSemanticParityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun greenReadAndOverviewShareOneStraightBreakDeadband() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("const GREEN_READ_BREAK_DEADBAND_PCT := 0.05"))
        assertTrue(source.contains("if abs(side_pct) < GREEN_READ_BREAK_DEADBAND_PCT:"))
        assertTrue(source.contains("if side_abs >= GREEN_READ_BREAK_DEADBAND_PCT:"))
        assertFalse(source.contains("if side_abs >= 0.03:"))
        assertFalse(source.contains("if abs(side_pct) < 0.05:"))
    }

    @Test
    fun greenReadAndOverviewReuseTheSameAdvisorAimFormatter() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("var aim_text := _overview_aim_text(_v165_recommended_offset)"))
        assertTrue(source.contains("_overview_aim_panel_text(offset)"))
        assertTrue(source.contains("return \"AIM %s %d cm\""))
        assertFalse(source.contains("AIM %s %.2f m"))
    }

    @Test
    fun overviewMarksAimBeyondItsVisualSpanInsteadOfPretendingEdgeIsExact() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("func _overview_aim_is_off_map(offset_m: float) -> bool:"))
        assertTrue(source.contains("return absf(offset_m) > OVERVIEW_AIM_VISUAL_SPAN_M"))
        assertTrue(source.contains("text += \" · OFF MAP\""))
        assertTrue(source.contains("_overview_aim_marker.position = _overview_aim_target_position(offset)"))
        assertTrue(source.contains("_overview_aim_marker.rotation = (PI * 0.5 if offset > 0.0 else -PI * 0.5) if off_map else 0.0"))
    }

    @Test
    fun semanticParityLayerStaysPresentationOnly() {
        val source = asset("green_read_direction_truth.gd")
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("paceDeltaCm ="))
    }
}
