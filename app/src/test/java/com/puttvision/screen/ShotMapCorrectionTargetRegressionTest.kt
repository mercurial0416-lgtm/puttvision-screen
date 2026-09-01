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
    fun actionableInMapMissGetsCorrectionEndpointLandingTarget() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("root.find_child(\"ShotMapCorrectionVector\", true, false)"))
        assertTrue(script.contains("_target_ring.position = _correction_vector.points[_correction_vector.points.size() - 1]"))
        assertTrue(script.contains("_target_ring.visible = true"))
    }

    @Test
    fun successfulAndOffScaleCuesDoNotShowMisleadingTarget() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("if _correction_vector == null or not _correction_vector.visible:"))
        assertTrue(script.contains("if _overflow_tick != null and _overflow_tick.visible:"))
        assertTrue(script.contains("_target_ring.visible = false"))
    }

    @Test
    fun targetCueStaysPresentationOnlyAndMobileBounded() {
        val script = asset("shot_map_correction_target.gd")
        assertTrue(script.contains("const TARGET_SEGMENTS := 14"))
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.10"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
        assertFalse(script.contains("ballVelocity"))
    }

    @Test
    fun productionTvScenePreservesRootAndLoadsHelper() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(scene.contains("res://shot_map_correction_target.gd"))
        assertTrue(scene.contains("ShotMapCorrectionTargetHelper"))
    }
}
