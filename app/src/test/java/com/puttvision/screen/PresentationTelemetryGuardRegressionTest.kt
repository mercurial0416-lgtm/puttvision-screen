package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationTelemetryGuardRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun tvRootRoutesSnapshotsThroughPresentationGuard() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://presentation_telemetry_guard.gd"))
    }

    @Test
    fun malformedReadOrPaceTelemetryIsRemovedWithoutMutatingSourceSnapshot() {
        val production = asset("presentation_telemetry_guard.gd")
        assertTrue(production.contains("func _presentation_is_finite_number(value: Variant) -> bool:"))
        assertTrue(production.contains("const PRESENTATION_PRACTICE_METRIC_KEYS := [\"readLineDeltaCm\", \"paceDeltaCm\"]"))
        assertTrue(production.contains("for key in PRESENTATION_PRACTICE_METRIC_KEYS:"))
        assertTrue(production.contains("if s.has(key) and not _presentation_is_finite_number(s.get(key)):"))
        assertTrue(!production.contains("if not s.has(\"readLineDeltaCm\") or not s.has(\"paceDeltaCm\"):\n        return true"))
        assertTrue(production.contains("safe = s.duplicate(false)"))
        assertTrue(production.contains("safe.erase(\"readLineDeltaCm\")"))
        assertTrue(production.contains("safe.erase(\"paceDeltaCm\")"))
        assertTrue(!production.contains("s.erase(\"readLineDeltaCm\")"))
        assertTrue(!production.contains("s.erase(\"paceDeltaCm\")"))
        assertTrue(production.contains("super._apply_snapshot(_presentation_safe_snapshot(s), immediate, delta)"))
    }

    @Test
    fun guardStaysOutsideAuthoritativePhysics() {
        val production = asset("presentation_telemetry_guard.gd")
        val clarity = asset("replay_stop_distance_clarity.gd")
        assertTrue(production.contains("extends \"res://replay_stop_distance_clarity.gd\""))
        assertTrue(clarity.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(!production.contains("GreenTerrain.set"))
        assertTrue(!production.contains("GreenReadAdvisor.set"))
        assertTrue(!production.contains("ballVelocity ="))
        assertTrue(!clarity.contains("GreenTerrain.set"))
        assertTrue(!clarity.contains("GreenReadAdvisor.set"))
        assertTrue(!clarity.contains("ballVelocity ="))
    }
}
