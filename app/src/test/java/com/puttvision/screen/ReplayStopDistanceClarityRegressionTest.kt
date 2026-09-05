package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayStopDistanceClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun replayDistanceReadsAsRemainingDistanceNotRestPosition() {
        val clarity = asset("replay_stop_distance_clarity.gd")
        assertTrue(clarity.contains("cm TO STOP"))
        assertTrue(clarity.contains("m TO STOP"))
        assertFalse(clarity.contains("cm REST"))
        assertFalse(clarity.contains("m REST"))
    }

    @Test
    fun productionPresentationRoutesThroughClarityAndOverlayWithoutTouchingPhysics() {
        val guard = asset("presentation_telemetry_guard.gd")
        val clarity = asset("replay_stop_distance_clarity.gd")
        val overlayBridge = asset("replay_production_overlay_bridge.gd")
        assertTrue(guard.startsWith("extends \"res://replay_stop_distance_clarity.gd\""))
        assertTrue(clarity.contains("extends \"res://replay_production_overlay_bridge.gd\""))
        assertTrue(overlayBridge.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertFalse(clarity.contains("GreenTerrain.set"))
        assertFalse(clarity.contains("GreenReadAdvisor.set"))
        assertFalse(clarity.contains("bridge."))
        assertFalse(overlayBridge.contains("GreenTerrain.set"))
        assertFalse(overlayBridge.contains("GreenReadAdvisor.set"))
        assertFalse(overlayBridge.contains("ballVelocity ="))
    }
}
