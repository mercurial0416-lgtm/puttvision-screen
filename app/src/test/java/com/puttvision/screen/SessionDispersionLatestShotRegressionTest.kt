package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionLatestShotRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun newestVisiblePracticeRepOwnsTheHighlightBeforeHistoryIsFull() {
        val polish = asset("session_dispersion_latest_shot.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://session_dispersion_latest_shot.gd"))
        assertTrue(polish.contains("var visible_count := mini(_v179_samples.size(), _v179_points.size())"))
        assertTrue(polish.contains("var latest_index := visible_count - 1"))
        assertTrue(polish.contains("index == latest_index and latest_index >= 0"))
        assertFalse(polish.contains("index == V179_HISTORY - 1"))
    }

    @Test
    fun latestShotPolishStaysPresentationOnlyAndBounded() {
        val polish = asset("session_dispersion_latest_shot.gd")

        assertTrue(polish.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(polish.contains("for index in range(_v179_points.size())"))
        assertFalse(polish.contains("GreenTerrain.set"))
        assertFalse(polish.contains("GreenReadAdvisor.set"))
        assertFalse(polish.contains("ballVelocity ="))
        assertFalse(polish.contains("readLineDeltaCm ="))
        assertFalse(polish.contains("paceDeltaCm ="))
    }
}
