package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationSpatialTelemetryGuardRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun malformedSpatialTelemetryCannotReachPresentationTransforms() {
        val production = asset("presentation_telemetry_guard.gd")
        assertTrue(production.contains("PRESENTATION_SPATIAL_KEYS := [\"ballX\", \"ballY\", \"holeDistance\"]"))
        assertTrue(production.contains("if is_finite(value):"))
        assertTrue(production.contains("_presentation_last_spatial[key] = value"))
        assertTrue(production.contains("safe[key] = float(_presentation_last_spatial[key])"))
        assertTrue(production.contains("safe.erase(key)"))
    }

    @Test
    fun spatialRepairStaysPresentationOnly() {
        val production = asset("presentation_telemetry_guard.gd")
        assertTrue(production.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(production.contains("super._apply_snapshot(_presentation_safe_snapshot(s), immediate, delta)"))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
    }
}
