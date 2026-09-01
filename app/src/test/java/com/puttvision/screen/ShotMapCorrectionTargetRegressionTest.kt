package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapCorrectionTargetRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun actionableInMapMissGetsTruthfulLandingTarget() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("_shot_map_correction_target.position = _v188_point(target_delta.x, target_delta.y)"))
        assertTrue(script.contains("_shot_map_correction_target.visible = true"))
        assertTrue(script.contains("var target_delta := _v197_correction_target(line_delta_cm, pace_delta_cm)"))
    }

    @Test
    fun madeAndOffScaleShotsDoNotShowMisleadingTarget() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("if not visible or _v197_inside_make_window(line_delta_cm, pace_delta_cm):"))
        assertTrue(script.contains("if _v188_normalized_miss(line_delta_cm, pace_delta_cm).length() > 1.0:"))
        assertTrue(script.contains("_shot_map_correction_target.visible = false"))
    }

    @Test
    fun targetCueStaysPresentationOnlyAndMobileBounded() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("const SHOT_MAP_TARGET_SEGMENTS := 14"))
        assertFalse(script.contains("_process("))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
        assertFalse(script.contains("ballVelocity"))
    }

    @Test
    fun productionTvSceneLoadsCorrectionTargetLayer() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://shot_map_correction_target.gd"))
    }
}
