package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeBiasConfidenceRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun biasReadDistinguishesEarlyFromStableSampleConfidence() {
        val script = asset("v195_practice_bias_vector.gd")
        assertTrue(script.contains("const V195_MIN_SAMPLES := 3"))
        assertTrue(script.contains("const V195_STABLE_SAMPLES := 5"))
        assertTrue(script.contains("return \"STABLE\" if sample_count >= V195_STABLE_SAMPLES else \"EARLY\""))
        assertTrue(script.contains("\"%s BIAS · %s\" % [_v195_confidence_text(sample_count), _v195_bias_text(bias)]"))
    }

    @Test
    fun confidenceCueStaysPresentationOnly() {
        val script = asset("v195_practice_bias_vector.gd")
        assertTrue(script.contains("Presentation-only session bias vector"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }
}
