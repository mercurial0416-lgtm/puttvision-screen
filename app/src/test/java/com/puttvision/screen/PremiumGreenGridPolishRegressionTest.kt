package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumGreenGridPolishRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun premiumGridCreatesMetricVisualHierarchyInsteadOfUniformCheckerboardWeight() {
        val source = asset("premium_green_grid_polish.gd")

        assertTrue(source.contains("1.0, 0.0032"))
        assertTrue(source.contains("5.0, 0.0058"))
        assertTrue(source.contains("float anchor_grid = max(anchor_x, anchor_z);"))
        assertTrue(source.contains("major_grid * 0.082 + anchor_grid * 0.105"))
        assertTrue(source.contains("ALPHA = clamp(alpha, 0.0, 0.42);"))
    }

    @Test
    fun liveSlopeGlyphBecomesARestrainedDownhillStreakWithoutChangingDirectionSource() {
        val source = asset("premium_green_grid_polish.gd")
        val inherited = asset("v168_commercial_grid_read.gd")

        assertTrue(source.contains("bead_q.y *= 0.46;"))
        assertTrue(source.contains("0.052, 0.098 + px * 0.24"))
        assertTrue(inherited.contains("vec2 downhill = slope_pct > 0.0005 ? slope_vec / slope_pct"))
        assertTrue(inherited.contains("dot(grid_pos, downhill)"))
        assertFalse(source.contains("side_slope ="))
        assertFalse(source.contains("long_slope ="))
    }

    @Test
    fun secondaryReliefGridIsQuietEnoughNotToFightTheMetricRead() {
        val source = asset("premium_green_grid_polish.gd")
        val relief = asset("terrain_relief_visibility.gd")

        assertTrue(relief.contains("relief_color = mix(relief_color, flow_color, flow_grid * 0.32)"))
        assertTrue(source.contains("RELIEF_NODE_NAME := \"TerrainReliefVisibility\""))
        assertTrue(source.contains("relief_color, flow_color, flow_grid * 0.08"))
        assertTrue(source.contains("float flow_alpha = flow_grid * 0.035;"))
        assertTrue(source.contains("float ribbon_alpha = elevation_ribbon * active * 0.18;"))
        assertTrue(source.contains("ALPHA = min(0.38, ALPHA + flow_alpha);"))
    }

    @Test
    fun polishIsOneShotPresentationOnlyAndCoveredByTvAndPreview() {
        val source = asset("premium_green_grid_polish.gd")
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")

        assertTrue(source.contains("call_deferred(\"_install_premium_grid\")"))
        assertTrue(source.contains("set_process(false)"))
        assertFalse(source.contains("func _process("))
        assertFalse(source.contains("func _physics_process("))
        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("score ="))
        assertTrue(tv.contains("res://premium_green_grid_polish.gd"))
        assertTrue(preview.contains("res://premium_green_grid_polish.gd"))
    }

    @Test
    fun shaderPatchingFailsClosedIfInheritedGridContractsChange() {
        val source = asset("premium_green_grid_polish.gd")

        assertTrue(source.contains("const POLISH_MARKER := \"// PUTTVISION_PREMIUM_GRID_V1\""))
        assertTrue(source.contains("const RELIEF_POLISH_MARKER := \"// PUTTVISION_PREMIUM_RELIEF_GRID_V1\""))
        assertTrue(source.contains("var complete := code.contains(POLISH_MARKER)"))
        assertTrue(source.contains("var complete := code.contains(RELIEF_POLISH_MARKER)"))
        assertTrue(source.contains("if not complete:"))
        assertTrue(source.contains("if complete:\n        material.shader.code = code"))
    }
}
