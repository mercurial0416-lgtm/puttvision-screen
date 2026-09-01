package com.puttvision.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumDirectionLanguagePreviewRegressionTest {
    private fun asset(name: String): String = File("src/main/assets/$name").readText()

    @Test
    fun renderedPreviewUsesExplicitTvReadableDirectionWords() {
        val preview = asset("premium_direction_language_preview.gd")
        val scene = asset("v143_preview.tscn")

        assertTrue(preview.contains("RIGHT 12.4 cm"))
        assertTrue(preview.contains("PEAK RIGHT 18.7 cm"))
        assertFalse(preview.contains("\"R 12.4 cm\""))
        assertFalse(preview.contains("\"PEAK R 18.7 cm\""))
        assertTrue(scene.contains("res://premium_direction_language_preview.gd"))
        assertTrue(preview.contains("PREMIUM_DIRECTION_LANGUAGE_PREVIEW_OK=1"))
    }
}
