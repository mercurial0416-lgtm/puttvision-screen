package com.puttvision.screen

import java.io.File
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayCupFovFrameInvarianceRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun dampingAlpha(rate: Double, delta: Double): Double = 1.0 - exp(-delta * rate)

    @Test
    fun replayCupLensUsesSharedBoundedExponentialDamping() {
        val script = asset("v180_replay_cup_focus.gd")
        val parent = asset("v175_cinematic_replay.gd")
        assertTrue(script.contains("const V180_FOV_RESPONSE := 5.5"))
        assertTrue(parent.contains("func _v175_camera_damping_alpha(delta: float, response: float) -> float:"))
        assertTrue(parent.contains("var safe_delta := minf(delta, V175_MAX_CAMERA_DELTA_S)"))
        assertTrue(parent.contains("return 1.0 - exp(-safe_delta * response)"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V180_FOV_RESPONSE)"))
        assertTrue(script.contains("camera.fov = lerp(camera.fov, finish_fov, fov_alpha * blend)"))
        assertFalse(script.contains("min(1.0, delta * 5.5)"))
        assertFalse(script.contains("func _v180_damping_alpha("))
    }

    @Test
    fun replayLensResponseIsInvariantToFrameSlicingAtNormalFrameTimes() {
        val rate = 5.5
        val oneSixtieth = dampingAlpha(rate, 1.0 / 60.0)
        val oneOneTwentieth = dampingAlpha(rate, 1.0 / 120.0)
        val twoSmallFramesCombined = 1.0 - (1.0 - oneOneTwentieth) * (1.0 - oneOneTwentieth)

        assertEquals(oneSixtieth, twoSmallFramesCombined, 1e-12)
    }

    @Test
    fun replayLensDampingRejectsInvalidOrBackwardTimeThroughSharedGuard() {
        val script = asset("v180_replay_cup_focus.gd")
        val parent = asset("v175_cinematic_replay.gd")
        assertTrue(parent.contains("if not is_finite(delta) or delta <= 0.0 or not is_finite(response) or response <= 0.0:"))
        assertTrue(parent.contains("return 0.0"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V180_FOV_RESPONSE)"))
    }
}
