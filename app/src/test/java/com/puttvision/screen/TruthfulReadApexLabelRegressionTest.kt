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
        val preview = asset("truthful_read_apex_preview.gd")
        val apex = asset("commercial_read_apex.gd")

        for (source in listOf(production, preview)) {
            assertTrue(source.contains("var apex := _read_apex_point(offset_m)"))
            assertTrue(source.contains("var delta_px := apex.x - center_x"))
            assertTrue(source.contains("return \"APEX  RIGHT\" if delta_px > 0.0 else \"APEX  LEFT\""))
            assertFalse(source.contains("offset_m * 100"))
        }
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
    fun productionAndPreviewRootsExerciseTheSameTruthfulDescriptor() {
        val tv = asset("v143_tv.tscn")
        val previewScene = asset("v143_preview.tscn")
        val production = asset("truthful_read_apex_root.gd")
        val preview = asset("truthful_read_apex_preview.gd")

        assertTrue(tv.contains("res://truthful_read_apex_root.gd"))
        assertTrue(tv.contains("res://live_origin_truth_guard.gd"))
        assertTrue(previewScene.contains("res://truthful_read_apex_preview.gd"))
        assertTrue(production.contains("extends \"res://live_origin_truth_guard.gd\""))
        assertTrue(preview.contains("extends \"res://premium_direction_language_preview.gd\""))
        assertFalse(production.contains("GreenTerrain("))
        assertFalse(production.contains("GreenReadAdvisor("))
        assertFalse(production.contains("score ="))
    }
}
