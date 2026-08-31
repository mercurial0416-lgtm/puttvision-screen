package com.puttvision.screen

import java.io.File
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCameraFrameInvarianceRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun dampingAlpha(rate: Double, delta: Double): Double = 1.0 - exp(-delta * rate)

    @Test
    fun addressCameraUsesExponentialDampingForPositionLookAndFov() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("func _address_damping_alpha(response_rate: float, delta: float) -> float:"))
        assertTrue(script.contains("return 1.0 - exp(-delta * response_rate)"))
        assertTrue(script.contains("_address_damping_alpha(5.8, delta)"))
        assertTrue(script.contains("_address_damping_alpha(6.6, delta)"))
        assertTrue(script.contains("_address_damping_alpha(ADDRESS_FOV_RESPONSE, delta)"))
        assertFalse(script.contains("minf(1.0, delta * 5.0)"))
    }

    @Test
    fun exponentialLensResponseIsInvariantToFrameSlicing() {
        val rate = 5.0
        val oneSixtieth = dampingAlpha(rate, 1.0 / 60.0)
        val oneOneTwentieth = dampingAlpha(rate, 1.0 / 120.0)
        val twoSmallFramesCombined = 1.0 - (1.0 - oneOneTwentieth) * (1.0 - oneOneTwentieth)

        assertEquals(oneSixtieth, twoSmallFramesCombined, 1e-12)
    }

    @Test
    fun dampingGuardRejectsInvalidOrBackwardTime() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("not is_finite(response_rate) or response_rate <= 0.0"))
        assertTrue(script.contains("not is_finite(delta) or delta <= 0.0"))
        assertTrue(script.contains("return 0.0"))
    }
}
