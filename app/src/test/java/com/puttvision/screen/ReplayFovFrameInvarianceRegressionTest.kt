package com.puttvision.screen

import java.io.File
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFovFrameInvarianceRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun dampingAlpha(rate: Double, delta: Double): Double = 1.0 - exp(-delta * rate)

    @Test
    fun replayLensUsesExponentialDampingInsteadOfFrameScaledLerp() {
        val script = asset("v175_cinematic_replay.gd")
        assertTrue(script.contains("const V175_FOV_RESPONSE := 4.8"))
        assertTrue(script.contains("func _v175_fov_damping_alpha(delta: float) -> float:"))
        assertTrue(script.contains("return 1.0 - exp(-delta * V175_FOV_RESPONSE)"))
        assertTrue(script.contains("_v175_fov_damping_alpha(delta)"))
        assertFalse(script.contains("min(1.0, delta * 4.8)"))
    }

    @Test
    fun replayLensResponseIsInvariantToFrameSlicing() {
        val rate = 4.8
        val oneThirtieth = dampingAlpha(rate, 1.0 / 30.0)
        val oneSixtieth = dampingAlpha(rate, 1.0 / 60.0)
        val oneOneTwentieth = dampingAlpha(rate, 1.0 / 120.0)
        val twoSixtiethFrames = 1.0 - (1.0 - oneSixtieth) * (1.0 - oneSixtieth)
        val fourOneTwentiethFrames = 1.0 - Math.pow(1.0 - oneOneTwentieth, 4.0)

        assertEquals(oneThirtieth, twoSixtiethFrames, 1e-12)
        assertEquals(oneThirtieth, fourOneTwentiethFrames, 1e-12)
    }

    @Test
    fun replayLensRejectsInvalidOrBackwardFrameTime() {
        val script = asset("v175_cinematic_replay.gd")
        assertTrue(script.contains("not is_finite(delta) or delta <= 0.0"))
        assertTrue(script.contains("return 0.0"))
    }
}
