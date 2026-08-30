package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapAxisSemanticsRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun shotMapLabelsAllFourErrorDirections() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("\"LONG\", \"ShotMapAxisLong\""))
        assertTrue(script.contains("\"SHORT\", \"ShotMapAxisShort\""))
        assertTrue(script.contains("\"LEFT\", \"ShotMapAxisLeft\""))
        assertTrue(script.contains("\"RIGHT\", \"ShotMapAxisRight\""))
    }

    @Test
    fun paceAxisMatchesExistingMapSignConvention() {
        val map = asset("v188_miss_map.gd")
        val labels = asset("v197_shot_map_make_window.gd")
        assertTrue(map.contains("Vector2(line_delta_cm / V188_LINE_WINDOW_CM, -pace_delta_cm / V188_PACE_WINDOW_CM)"))
        assertTrue(labels.contains("ShotMapAxisLong\", Vector2(58, 47)"))
        assertTrue(labels.contains("ShotMapAxisShort\", Vector2(55, 116)"))
    }

    @Test
    fun resultIndicatorsRenderAboveStaticAxisLabels() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("const V197_RESULT_Z_INDEX := 2"))
        assertTrue(script.contains("_v188_vector.z_index = V197_RESULT_Z_INDEX"))
        assertTrue(script.contains("_v188_overflow_tick.z_index = V197_RESULT_Z_INDEX"))
        assertTrue(script.contains("_v188_dot.z_index = V197_RESULT_Z_INDEX"))
        assertTrue(script.contains("_v188_overflow_label.z_index = V197_RESULT_Z_INDEX"))
        assertTrue(script.contains("_v197_promote_shot_indicators()"))
    }

    @Test
    fun presentationLabelsDoNotMutateAuthoritativeSystems() {
        val script = asset("v197_shot_map_make_window.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
    }
}
