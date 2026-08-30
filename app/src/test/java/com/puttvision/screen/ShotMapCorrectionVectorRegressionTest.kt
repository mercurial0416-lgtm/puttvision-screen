package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapCorrectionVectorRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun missProjectsToBufferedSuccessWindowAndDrawsActionableGuide() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("const V197_CORRECTION_MARGIN_CM := 1.0"))
        assertTrue(script.contains("func _v197_correction_target(line_delta_cm: float, pace_delta_cm: float) -> Vector2:"))
        assertTrue(script.contains("-V197_LINE_WINDOW_CM + V197_CORRECTION_MARGIN_CM"))
        assertTrue(script.contains("V197_PACE_WINDOW_CM - V197_CORRECTION_MARGIN_CM"))
        assertTrue(script.contains("_v197_correction_line.points = PackedVector2Array([start, target])"))
        assertTrue(script.contains("_v197_correction_tip.points = _v197_correction_arrow(start, target)"))
    }

    @Test
    fun correctiveCopyCoversBothAxesAndMadeShotsHideGuide() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("\"L %.0f\" % absf(correction.x)"))
        assertTrue(script.contains("\"R %.0f\" % absf(correction.x)"))
        assertTrue(script.contains("\"SHORT %.0f\" % absf(correction.y)"))
        assertTrue(script.contains("\"LONG %.0f\" % absf(correction.y)"))
        assertTrue(script.contains("_v196_center_legend.text = \"IN MAKE WINDOW\""))
        assertTrue(script.contains("_v197_correction_line.visible = false"))
        assertTrue(script.contains("_v197_correction_tip.visible = false"))
    }

    @Test
    fun correctionLayerCannotMutateAuthoritativePuttingInputs() {
        val script = asset("v197_shot_map_make_window.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
        assertFalse(script.contains("ballVelocity"))
    }
}
