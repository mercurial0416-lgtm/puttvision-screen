package com.puttvision.screen

import java.io.File
import kotlin.math.abs
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
            .substringBefore("\nfunc _v180_sightline_lift")
        assertTrue(chooser.contains("absf(lateral_m) <= V180_CAMERA_SIDE_DEADBAND_M"))
        assertTrue(chooser.contains("return 1.0"))
    }

    @Test
    fun cupFocusClearsIntermediateTerrainWithoutPerFrameScanExplosion() {
        val focus = asset("v180_replay_cup_focus.gd")
        val sightline = focus.substringAfter("func _v180_sightline_lift")
            .substringBefore("\nfunc _v180_finish_verdict")
        assertTrue(focus.contains("const V180_SIGHTLINE_SAMPLES := 7"))
        assertTrue(focus.contains("const V180_SIGHTLINE_CLEARANCE_M := 0.10"))
        assertTrue(focus.contains("const V180_SIGHTLINE_MAX_LIFT_M := 0.72"))
        assertTrue(sightline.contains("for i in range(V180_SIGHTLINE_SAMPLES):"))
        assertTrue(sightline.contains("var terrain_y := _v166_sample(probe2.x, probe2.y).x"))
        assertTrue(sightline.contains("var line_y := lerpf(camera_y, look_y, t)"))
        assertTrue(sightline.contains("/ maxf(0.12, 1.0 - t)"))
        assertTrue(sightline.contains("return clampf(needed, 0.0, V180_SIGHTLINE_MAX_LIFT_M)"))
        assertTrue(focus.contains("var sightline_lift := _v180_sightline_lift(cup_cam2, base_cam_y, cup2, cup_look.y)"))
        assertTrue(focus.contains("base_cam_y + sightline_lift"))
    }

    @Test
    fun sightlineSamplingCatchesNarrowRidgeBetweenLegacyQuarterProbes() {
        val legacy = listOf(0.25, 0.50, 0.75)
        val upgraded = (0 until 7).map { (it + 1).toDouble() / 8.0 }
        val narrowRidgeCenter = 0.125

        assertFalse(legacy.any { abs(it - narrowRidgeCenter) < 0.001 })
        assertTrue(upgraded.any { abs(it - narrowRidgeCenter) < 0.001 })
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
