package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDirectionLanguagePreviewRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun renderedPreviewUsesExplicitTvReadableDirectionWords() {
        val preview = asset("premium_direction_language_preview.gd")
        val scene = asset("v143_preview.tscn")

        assertTrue(preview.contains("RIGHT 12.4 cm"))
        assertTrue(preview.contains("PEAK RIGHT 18.7 cm"))
        assertTrue(preview.contains("BREAK  RIGHT 1.35%"))
        assertTrue(preview.contains("BREAK  LEFT 1.35%"))
        assertFalse(preview.contains("\"R 12.4 cm\""))
        assertFalse(preview.contains("\"PEAK R 18.7 cm\""))
        assertTrue(scene.contains("res://premium_direction_language_preview.gd"))
        assertTrue(preview.contains("PREMIUM_DIRECTION_LANGUAGE_PREVIEW_OK=1"))
    }
}
