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
    fun replayCupLensUsesExponentialDampingInsteadOfFrameScaledLerp() {
        val script = asset("v180_replay_cup_focus.gd")
        assertTrue(script.contains("const V180_FOV_RESPONSE := 5.5"))
        assertTrue(script.contains("func _v180_damping_alpha(response_rate: float, delta: float) -> float:"))
        assertTrue(script.contains("return 1.0 - exp(-delta * response_rate)"))
        assertTrue(script.contains("_v180_damping_alpha(V180_FOV_RESPONSE, delta)"))
        assertTrue(script.contains("camera.fov = lerp(camera.fov, 30.5, fov_alpha * blend)"))
        assertFalse(script.contains("min(1.0, delta * 5.5)"))
    }

    @Test
    fun replayLensResponseIsInvariantToFrameSlicing() {
        val rate = 5.5
        val oneSixtieth = dampingAlpha(rate, 1.0 / 60.0)
        val oneOneTwentieth = dampingAlpha(rate, 1.0 / 120.0)
        val twoSmallFramesCombined = 1.0 - (1.0 - oneOneTwentieth) * (1.0 - oneOneTwentieth)

        assertEquals(oneSixtieth, twoSmallFramesCombined, 1e-12)
    }

    @Test
    fun replayLensDampingRejectsInvalidOrBackwardTime() {
        val script = asset("v180_replay_cup_focus.gd")
        assertTrue(script.contains("not is_finite(response_rate) or response_rate <= 0.0"))
        assertTrue(script.contains("not is_finite(delta) or delta <= 0.0"))
        assertTrue(script.contains("return 0.0"))
    }
}
