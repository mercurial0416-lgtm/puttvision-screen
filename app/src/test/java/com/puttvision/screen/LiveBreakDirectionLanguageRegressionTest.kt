package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakDirectionLanguageRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun productionLiveRollUsesTvReadableDirectionWords() {
        val layer = asset("live_break_direction_language.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(layer.contains("REST %s %.1f cm"))
        assertTrue(layer.contains("LAST OBS %s %.1f cm"))
        assertTrue(layer.contains("\"RIGHT\" if cross_track_cm > 0.0 else \"LEFT\""))
        assertFalse(layer.contains("\"R\" if cross_track_cm > 0.0 else \"L\""))
        assertTrue(scene.contains("res://live_break_direction_language.gd"))
    }

    @Test
    fun changeRemainsPresentationOnly() {
        val layer = asset("live_break_direction_language.gd")
        assertTrue(layer.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertFalse(layer.contains("GreenTerrain."))
        assertFalse(layer.contains("GreenReadAdvisor."))
        assertFalse(layer.contains("velocity ="))
    }
}
