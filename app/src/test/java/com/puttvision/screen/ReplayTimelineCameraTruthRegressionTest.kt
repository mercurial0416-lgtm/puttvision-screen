package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTimelineCameraTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun replayTimelineUsesCameraOwnedCupFocusThresholds() {
        val live = asset("replay_timeline_camera_truth.gd")
        assertTrue(live.contains("if p < V180_FOCUS_START:"))
        assertTrue(live.contains("if p < V180_FOCUS_FULL:"))
        assertTrue(live.contains("track_width * V180_FOCUS_START"))
        assertTrue(live.contains("track_width * V180_FOCUS_FULL"))
        assertFalse(live.contains("0.72"))
        assertFalse(live.contains("0.90"))
    }

    @Test
    fun liveTvSceneRoutesThroughTimelineTruthWithoutTouchingPhysics() {
        val scene = asset("v143_tv.tscn")
        val live = asset("replay_timeline_camera_truth.gd")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(live.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(live.contains("Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative"))
        assertFalse(live.contains("GreenTerrain.set"))
        assertFalse(live.contains("GreenReadAdvisor.set"))
        assertFalse(live.contains("bridge."))
    }
}
