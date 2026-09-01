package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeBiasReadoutRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun practiceGroupingStatesDirectionalBiasInCentimeters() {
        val script = asset("v194_dispersion_envelope.gd")
        assertTrue(script.contains("func _v194_bias_readout(mean: Vector2) -> String:"))
        assertTrue(script.contains("\"RIGHT\" if mean.x > 0.0 else \"LEFT\""))
        assertTrue(script.contains("\"%s %.0f CM\""))
        assertTrue(script.contains("\"LONG\" if mean.y > 0.0 else \"SHORT\""))
        assertTrue(script.contains("return \"BIAS %s  ·  %s\""))
        assertTrue(script.contains("_v194_bias_label.text = _v194_bias_readout(mean)"))
    }

    @Test
    fun centeredPracticeBiasUsesStableNeutralLanguage() {
        val script = asset("v194_dispersion_envelope.gd")
        assertTrue(script.contains("const V194_BIAS_CENTER_DEADZONE_CM := 1.5"))
        assertTrue(script.contains("\"LINE OK\" if absf(mean.x) < V194_BIAS_CENTER_DEADZONE_CM"))
        assertTrue(script.contains("\"PACE OK\" if absf(mean.y) < V194_BIAS_CENTER_DEADZONE_CM"))
    }

    @Test
    fun biasReadoutDoesNotRegressToUnitlessAbbreviations() {
        val script = asset("v194_dispersion_envelope.gd")
        assertFalse(script.contains("\"R\" if mean.x > 0.0 else \"L\""))
        assertFalse(script.contains("var line_text := \"CTR\""))
    }

    @Test
    fun biasReadoutRemainsPresentationOnly() {
        val script = asset("v194_dispersion_envelope.gd")
        assertTrue(script.contains("Presentation-only session grouping envelope"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }
}
