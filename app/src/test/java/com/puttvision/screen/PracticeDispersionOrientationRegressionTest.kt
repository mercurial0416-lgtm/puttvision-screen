package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeDispersionOrientationRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun groupingEnvelopePreservesCorrelatedMissDirection() {
        val script = asset("v194_dispersion_envelope.gd")
        assertTrue(script.contains("func _v194_covariance_pixels(mean: Vector2) -> Vector3:"))
        assertTrue(script.contains("func _v194_principal_axes(covariance: Vector3) -> Dictionary:"))
        assertTrue(script.contains("0.5 * atan2(2.0 * xy, xx - yy)"))
        assertTrue(script.contains("func _v194_oriented_ellipse(major: float, minor: float, angle: float)"))
        assertTrue(script.contains("func _v194_fit_envelope_to_ring(center: Vector2, points: PackedVector2Array)"))
    }

    @Test
    fun groupingOrientationRemainsPresentationOnly() {
        val script = asset("v194_dispersion_envelope.gd")
        assertTrue(script.contains("Presentation-only session grouping envelope"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }
}
