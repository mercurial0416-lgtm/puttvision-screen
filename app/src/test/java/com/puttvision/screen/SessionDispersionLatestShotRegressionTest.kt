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
        val production = asset("replay_timeline_camera_truth.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(production.contains("var visible_count := mini(_v179_samples.size(), _v179_points.size())"))
        assertTrue(production.contains("var latest_index := visible_count - 1"))
        assertTrue(production.contains("index == latest_index and latest_index >= 0"))
        assertFalse(production.contains("index == V179_HISTORY - 1"))
    }

    @Test
    fun latestShotPolishStaysPresentationOnlyAndBounded() {
        val production = asset("replay_timeline_camera_truth.gd")

        assertTrue(production.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(production.contains("for index in range(_v179_points.size())"))
        assertFalse(production.contains("GreenTerrain.set"))
        assertFalse(production.contains("GreenReadAdvisor.set"))
        assertFalse(production.contains("ballVelocity ="))
        assertFalse(production.contains("readLineDeltaCm ="))
        assertFalse(production.contains("paceDeltaCm ="))
    }
}
