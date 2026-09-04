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
    fun malformedBallTelemetryKeepsMissingPositionProvenance() {
        val production = asset("presentation_telemetry_guard.gd")
        assertTrue(production.contains("PRESENTATION_BALL_KEYS := [\"ballX\", \"ballY\"]"))
        assertTrue(production.contains("var malformed_ball_pair := false"))
        assertTrue(production.contains("safe.erase(\"ballX\")"))
        assertTrue(production.contains("safe.erase(\"ballY\")"))
        assertTrue(!production.contains("safe[\"ballX\"] ="))
        assertTrue(!production.contains("safe[\"ballY\"] ="))
    }

    @Test
    fun malformedNonBallSpatialTelemetryStillHoldsLastFinitePresentationValue() {
        val production = asset("presentation_telemetry_guard.gd")
        assertTrue(production.contains("PRESENTATION_SPATIAL_KEYS := [\"ballX\", \"ballY\", \"holeDistance\"]"))
        assertTrue(production.contains("if key in PRESENTATION_BALL_KEYS:"))
        assertTrue(production.contains("_presentation_last_spatial[key] = value"))
        assertTrue(production.contains("safe[key] = float(_presentation_last_spatial[key])"))
    }

    @Test
    fun replayTruthOwnsTheVisualHoldForMissingBallCoordinates() {
        val production = asset("presentation_telemetry_guard.gd")
        val clarity = asset("replay_stop_distance_clarity.gd")
        val replayTruth = asset("replay_timeline_camera_truth.gd")
        assertTrue(production.contains("extends \"res://replay_stop_distance_clarity.gd\""))
        assertTrue(clarity.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(replayTruth.contains("if missing_ball_position and had_real_ball_position:"))
        assertTrue(replayTruth.contains("presentation_snapshot[\"ballX\"] = _live_curve_last_ball_pos.x"))
        assertTrue(replayTruth.contains("elif missing_ball_position:"))
        assertTrue(replayTruth.contains("_finalize_last_observed_live_roll_truth()"))
    }

    @Test
    fun spatialRepairStaysPresentationOnly() {
        val production = asset("presentation_telemetry_guard.gd")
        val clarity = asset("replay_stop_distance_clarity.gd")
        assertTrue(production.contains("super._apply_snapshot(_presentation_safe_snapshot(s), immediate, delta)"))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
        assertTrue(!clarity.contains("GreenTerrain.set"))
        assertTrue(!clarity.contains("GreenReadAdvisor.set"))
        assertTrue(!clarity.contains("ballVelocity ="))
    }
}
