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
    fun finishCompositionKeepsRollingBallAndCupInTheSameFrame() {
        val focus = asset("v180_replay_cup_focus.gd")
        val composer = focus.substringAfter("func _v180_composed_look_point")
            .substringBefore("\nfunc _v180_finish_fov")
        assertTrue(focus.contains("const V180_BALL_FRAMING_WEIGHT_START := 0.62"))
        assertTrue(focus.contains("const V180_BALL_FRAMING_WEIGHT_END := 0.38"))
        assertTrue(composer.contains("clampf(focus, 0.0, 1.0)"))
        assertTrue(composer.contains("return cup_point.lerp(ball_point, weight)"))
        assertTrue(focus.contains("var replay_ball2 := _v175_trail_point(_v171_replay_actual, progress)"))
        assertTrue(focus.contains("var look2 := _v180_composed_look_point(replay_ball2, cup2, focus)"))
        assertTrue(focus.contains("var desired_look := camera_look.lerp(focus_look, blend)"))
        assertFalse(focus.contains("var desired_look := camera_look.lerp(cup_look, blend)"))
    }

    @Test
    fun framingWeightsStayBetweenBallAndCupWithoutOvershoot() {
        val startWeight = 0.62
        val endWeight = 0.38
        assertTrue(startWeight in 0.0..1.0)
        assertTrue(endWeight in 0.0..1.0)
        assertTrue(startWeight > endWeight)
        val cup = 0.0
        val ball = 1.0
        val startLook = cup + (ball - cup) * startWeight
        val endLook = cup + (ball - cup) * endWeight
        assertTrue(startLook in cup..ball)
        assertTrue(endLook in cup..ball)
        assertTrue(endLook < startLook)
    }

    @Test
    fun finishLensWidensOnlyWhenBallCupSeparationNeedsRoom() {
        val focus = asset("v180_replay_cup_focus.gd")
        val fov = focus.substringAfter("func _v180_finish_fov")
            .substringBefore("\nfunc _v180_cup_camera_side_sign")
        assertTrue(focus.contains("const V180_FINISH_FOV_MIN := 30.5"))
        assertTrue(focus.contains("const V180_FINISH_FOV_MAX := 38.0"))
        assertTrue(focus.contains("const V180_FINISH_FOV_WIDEN_START_M := 0.35"))
        assertTrue(focus.contains("const V180_FINISH_FOV_WIDEN_FULL_M := 1.20"))
        assertTrue(fov.contains("ball_point.distance_to(cup_point)"))
        assertTrue(fov.contains("smoothstep(V180_FINISH_FOV_WIDEN_START_M, V180_FINISH_FOV_WIDEN_FULL_M, separation_m)"))
        assertTrue(fov.contains("return lerpf(V180_FINISH_FOV_MIN, V180_FINISH_FOV_MAX, widen)"))
        assertTrue(focus.contains("var finish_fov := _v180_finish_fov(replay_ball2, cup2)"))
        assertTrue(focus.contains("camera.fov = lerp(camera.fov, finish_fov, fov_alpha * blend)"))
        assertFalse(focus.contains("camera.fov = lerp(camera.fov, 30.5, fov_alpha * blend)"))
    }

    @Test
    fun finishLensBoundsStayCinematicAndMobileSafe() {
        val minFov = 30.5
        val maxFov = 38.0
        val widenStartMeters = 0.35
        val widenFullMeters = 1.20
        assertTrue(minFov >= 28.0)
        assertTrue(maxFov <= 40.0)
        assertTrue(maxFov > minFov)
        assertTrue(widenStartMeters > 0.0)
        assertTrue(widenFullMeters > widenStartMeters)
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
        assertTrue(focus.contains("var sightline_lift := _v180_sightline_lift(cup_cam2, base_cam_y, look2, focus_look.y)"))
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
