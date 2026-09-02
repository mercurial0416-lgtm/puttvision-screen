package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApexDirectionLanguageRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun apexUsesFullDirectionWordsForTvReadability() {
        val script = asset("commercial_read_apex.gd")
        assertTrue(script.contains("\"RIGHT\" if offset_m > 0.0 else \"LEFT\""))
        assertFalse(script.contains("\"R\" if offset_m > 0.0 else \"L\""))
    }

    @Test
    fun apexLanguageRemainsPresentationOnly() {
        val script = asset("commercial_read_apex.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
