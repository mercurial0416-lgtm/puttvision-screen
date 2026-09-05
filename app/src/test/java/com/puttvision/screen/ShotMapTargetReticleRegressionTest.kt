package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapTargetReticleRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun landingTargetGetsCenteredTvReadableFocusTreatmentWithoutPolling() {
        val script = asset("shot_map_target_reticle.gd")
        assertTrue(script.contains("find_child(\"ShotMapCorrectionTarget\", true, false)"))
        assertTrue(script.contains("TargetFocusDisc"))
        assertTrue(script.contains("Polygon2D.new()"))
        assertTrue(script.contains("FOCUS_DISC_SEGMENTS := 16"))
        assertTrue(script.contains("target.add_child(focus_disc)"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("Timer.new()"))
        assertFalse(script.contains("Tween"))
    }

    @Test
    fun reticleKeepsARealCenterGapInsteadOfConnectingAcrossIt() {
        val script = asset("shot_map_target_reticle.gd")
        assertTrue(script.contains("TargetReticleLeft"))
        assertTrue(script.contains("TargetReticleRight"))
        assertTrue(script.contains("TargetReticleTop"))
        assertTrue(script.contains("TargetReticleBottom"))
        assertTrue(script.contains("func _add_reticle_segment("))
        assertTrue(script.contains("PackedVector2Array([start, finish])"))
        assertFalse(script.contains("Vector2(-RETICLE_GAP_PX, 0.0),\n        Vector2(RETICLE_GAP_PX, 0.0)"))
        assertFalse(script.contains("Vector2(0.0, -RETICLE_GAP_PX),\n        Vector2(0.0, RETICLE_GAP_PX)"))
    }

    @Test
    fun focusTreatmentRemainsPresentationOnlyAndProductionWired() {
        val script = asset("shot_map_target_reticle.gd")
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://shot_map_target_reticle.gd"))
        assertTrue(scene.contains("ShotMapTargetReticle"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("ballVelocity"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
    }
}
