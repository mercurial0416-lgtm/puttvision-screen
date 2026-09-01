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
        val layer = asset("replay_timeline_camera_truth.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(layer.contains("REST %s %.1f cm"))
        assertTrue(layer.contains("LAST OBS %s %.1f cm"))
        assertTrue(layer.contains("\"RIGHT\" if cross_track_cm > 0.0 else \"LEFT\""))
        assertFalse(layer.contains("\"R\" if cross_track_cm > 0.0 else \"L\""))
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
    }

    @Test
    fun changeRemainsPresentationOnly() {
        val layer = asset("replay_timeline_camera_truth.gd")
        assertTrue(layer.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(layer.contains("GreenTerrain and GreenReadAdvisor remain authoritative and untouched"))
        assertFalse(layer.contains("GreenTerrain."))
        assertFalse(layer.contains("GreenReadAdvisor."))
    }
}
