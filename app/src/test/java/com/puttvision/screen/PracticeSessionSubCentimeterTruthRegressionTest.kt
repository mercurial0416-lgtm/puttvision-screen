package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSessionSubCentimeterTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun nearPerfectPracticeAveragesDoNotRoundMeasuredMissesToPerfectZero() {
        val script = asset("address_relief_camera.gd")

        assertTrue(script.contains("SESSION_DISPERSION_ZERO_EPSILON_CM := 0.5"))
        assertTrue(script.contains("SESSION_EXACT_ZERO_EPSILON_CM := 0.05"))
        assertTrue(script.contains("return \"%s <1 cm\" % anchor"))
        assertTrue(script.contains("_session_near_zero_text(absf(value_cm), \"CENTER\")"))
        assertTrue(script.contains("_session_near_zero_text(absf(value_cm), \"CUP\")"))
        assertFalse(script.contains("return \"CENTER 0 cm\""))
        assertFalse(script.contains("return \"CUP 0 cm\""))
    }

    @Test
    fun practiceSummaryPrecisionRemainsPresentationOnly() {
        val script = asset("address_relief_camera.gd")
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("ball_velocity ="))
        assertFalse(script.contains("target_distance ="))
    }
}
