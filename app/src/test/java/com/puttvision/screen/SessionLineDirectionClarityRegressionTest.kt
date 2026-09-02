package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLineDirectionClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun sessionLineUsesTvReadableDirectionWords() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("func _session_line_average_text(value_cm: float) -> String:"))
        assertTrue(script.contains("return \"RIGHT %.0f cm\" % absf(value_cm) if value_cm > 0.0 else \"LEFT %.0f cm\" % absf(value_cm)"))
        assertTrue(script.contains("return \"CENTER 0 cm\""))
        assertFalse(script.contains("return \"R %.0f cm\" % absf(value_cm)"))
        assertFalse(script.contains("else \"L %.0f cm\" % absf(value_cm)"))
    }

    @Test
    fun sessionDirectionPolishStaysPresentationOnly() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("GreenTerrain and GreenReadAdvisor remain"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("bridge."))
    }
}
