package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TruthfulReadApexLabelRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun apexBadgeNamesTheActualRenderedPathLandmarkInsteadOfReusingLaunchOffsetCentimetres() {
        val production = asset("truthful_read_apex_root.gd")
        val apex = asset("commercial_read_apex.gd")

        assertTrue(production.contains("var apex := _read_apex_point(offset_m)"))
        assertTrue(production.contains("var delta_px := apex.x - center_x"))
        assertTrue(production.contains("return \"APEX  RIGHT\" if delta_px > 0.0 else \"APEX  LEFT\""))
        assertFalse(production.contains("offset_m * 100"))
        assertTrue(apex.contains("_read_apex_point(offset_m)"))
    }

    @Test
    fun startGateKeepsTheAuthoritativeLaunchOffsetCentimetresAsASeparateQuantity() {
        val apex = asset("commercial_read_apex.gd")

        assertTrue(apex.contains("func _read_start_gate_descriptor(offset_m: float) -> String:"))
        assertTrue(apex.contains("int(round(absf(offset_m) * 100.0))"))
        assertTrue(apex.contains("START  %s %d cm"))
    }

    @Test
    fun productionRootKeepsExistingTruthGuardChainAndPhysicsIsolation() {
        val tv = asset("v143_tv.tscn")
        val previewScene = asset("v143_preview.tscn")
        val production = asset("truthful_read_apex_root.gd")

        assertTrue(tv.contains("res://truthful_read_apex_root.gd"))
        assertTrue(tv.contains("res://live_origin_truth_guard.gd"))
        assertTrue(previewScene.contains("res://premium_direction_language_preview.gd"))
        assertFalse(previewScene.contains("truthful_read_apex_preview.gd"))
        assertTrue(production.contains("extends \"res://live_origin_truth_guard.gd\""))
        assertFalse(production.contains("GreenTerrain("))
        assertFalse(production.contains("GreenReadAdvisor("))
        assertFalse(production.contains("score ="))
    }
}
