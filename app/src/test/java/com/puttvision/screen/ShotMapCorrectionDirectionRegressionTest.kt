package com.puttvision.screen

import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapCorrectionDirectionRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private data class Point(val x: Double, val y: Double)

    private fun mapPoint(lineCm: Double, paceCm: Double): Point {
        var x = lineCm / 30.0
        var y = -paceCm / 70.0
        val length = hypot(x, y)
        if (length > 1.0) {
            x /= length
            y /= length
        }
        return Point(76.0 + x * 42.0, 86.0 + y * 42.0)
    }

    @Test
    fun radialClippingCanReverseLegacyCorrectionComponent() {
        val lineCm = 31.0
        val paceCm = 1000.0
        val targetLineCm = 8.0
        val targetPaceCm = 21.0
        val start = mapPoint(lineCm, paceCm)
        val oldTarget = mapPoint(targetLineCm, targetPaceCm)

        val truthfulScreenX = (targetLineCm - lineCm) / 30.0
        val legacyScreenX = oldTarget.x - start.x
        assertTrue("coach truth must point left", truthfulScreenX < 0.0)
        assertTrue("legacy clipped connector demonstrates the regression by pointing right", legacyScreenX > 0.0)
    }

    @Test
    fun offScaleCorrectionUsesPreClipTruthAndBoundedCue() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("_v197_correction_screen_delta"))
        assertTrue(script.contains("_v197_component_direction_matches"))
        assertTrue(script.contains("inward_chord_px"))
        assertTrue(script.contains("V197_CORRECTION_CUE_MAX_PX"))
        assertTrue(script.contains("_v197_correction_visual_target(line_delta_cm, pace_delta_cm, target_delta, start)"))
        assertFalse(script.contains("var target := _v188_point(target_delta.x, target_delta.y)"))
    }

    @Test
    fun correctionDirectionFixRemainsPresentationOnly() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("Presentation-only SHOT MAP make window"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("shot scoring ="))
    }
}
