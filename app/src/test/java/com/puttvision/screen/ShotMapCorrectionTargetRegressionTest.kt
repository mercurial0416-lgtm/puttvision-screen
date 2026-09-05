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
    fun actionableInMapMissGetsEventDrivenCorrectionLandingTarget() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("ShotMapCorrectionTarget"))
        assertTrue(script.contains("_v197_correction_target_marker.position = target"))
        assertTrue(script.contains("_v197_correction_target_marker.visible = correction_visible and not offscale"))
        assertTrue(script.contains("var correction_visible := start.distance_to(target) >= 2.0"))
    }

    @Test
    fun successfulAndOffScaleCuesDoNotShowMisleadingTarget() {
        val script = asset("v197_shot_map_make_window.gd")
        assertTrue(script.contains("func _v197_hide_correction() -> void:"))
        assertTrue(script.contains("_v197_correction_target_marker.visible = false"))
        assertTrue(script.contains("var offscale := _v188_normalized_miss(line_delta_cm, pace_delta_cm).length() > 1.0"))
    }

    @Test
    fun targetCueStaysPresentationOnlyWithoutPolling() {
        val script = asset("v197_shot_map_make_window.gd")
        assertFalse(script.contains("Timer.new()"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
        assertFalse(script.contains("ballVelocity"))
    }

    @Test
    fun productionTvSceneNoLongerLoadsPollingHelper() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertFalse(scene.contains("res://shot_map_correction_target.gd"))
        assertFalse(scene.contains("ShotMapCorrectionTargetHelper"))
    }
}
