package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayCupCameraSideRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun cupFocusChoosesTheOppositeSideOfMeaningfulFinalMisses() {
        val focus = asset("v180_replay_cup_focus.gd")
        assertTrue(focus.contains("const V180_CAMERA_SIDE_DEADBAND_M := 0.04"))
        assertTrue(focus.contains("func _v180_cup_camera_side_sign(final_point: Vector2, cup_point: Vector2, final_heading: Vector2) -> float:"))
        assertTrue(focus.contains("var lateral_m := (final_point - cup_point).dot(side)"))
        assertTrue(focus.contains("return -1.0 if lateral_m > 0.0 else 1.0"))
        assertTrue(focus.contains("var side_sign := _v180_cup_camera_side_sign(_v180_final_point(), cup2, final_heading)"))
        assertTrue(focus.contains("var approach_side := side * 0.82 * side_sign"))
    }

    @Test
    fun nearCenterFinishesKeepStableDefaultCameraSide() {
        val focus = asset("v180_replay_cup_focus.gd")
        val chooser = focus.substringAfter("func _v180_cup_camera_side_sign")
            .substringBefore("\nfunc _v180_finish_verdict")
        assertTrue(chooser.contains("absf(lateral_m) <= V180_CAMERA_SIDE_DEADBAND_M"))
        assertTrue(chooser.contains("return 1.0"))
    }

    @Test
    fun cameraVisibilityFixCannotMutateAuthoritativePuttingInputs() {
        val focus = asset("v180_replay_cup_focus.gd")
        assertFalse(focus.contains("GreenTerrain.set"))
        assertFalse(focus.contains("GreenReadAdvisor.set"))
        assertFalse(focus.contains("ballVelocity ="))
        assertFalse(focus.contains("readLineDeltaCm ="))
        assertFalse(focus.contains("paceDeltaCm ="))
    }
}
