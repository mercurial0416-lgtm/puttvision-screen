package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySpatialSampleSafetyRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun replayTrailFiltersInvalidSamplesBeforeCameraInterpolation() {
        val production = asset("replay_spatial_pacing.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(production.contains("func _replay_spatial_valid_points(points: Array) -> Array[Vector2]:"))
        assertTrue(production.contains("typeof(value) != TYPE_VECTOR2"))
        assertTrue(production.contains("point.is_finite()"))
        assertTrue(production.contains("var valid_points := _replay_spatial_valid_points(points)"))
        assertTrue(production.contains("if valid_points.is_empty():\n        return Vector2.ZERO"))
    }

    @Test
    fun replaySampleSafetyRemainsPresentationOnly() {
        val production = asset("replay_spatial_pacing.gd")

        assertTrue(production.contains("extends \"res://practice_ring_edge_truth.gd\""))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
    }
}
