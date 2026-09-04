package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapTelemetryTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun shotMapRequiresBothFiniteMeasuredDeltasBeforeShowing() {
        val map = asset("v188_miss_map.gd")
        assertTrue(map.contains("func _v188_snapshot_has_metrics(s: Dictionary) -> bool:"))
        assertTrue(map.contains("s.has(\"readLineDeltaCm\") and s.has(\"paceDeltaCm\")"))
        assertTrue(map.contains("is_finite(line_delta_cm) and is_finite(pace_delta_cm)"))
        assertTrue(map.contains("and metrics_valid"))
    }

    @Test
    fun missingTelemetryCannotBePresentedAsAZeroZeroMake() {
        val map = asset("v188_miss_map.gd")
        val guard = asset("presentation_telemetry_guard.gd")
        assertTrue(guard.contains("safe.erase(\"readLineDeltaCm\")"))
        assertTrue(guard.contains("safe.erase(\"paceDeltaCm\")"))
        assertTrue(map.contains("var truthful_visible := visible and _v188_metrics_are_finite(line_delta_cm, pace_delta_cm)"))
        assertTrue(map.contains("_v188_panel.visible = truthful_visible"))
        assertFalse(map.contains("var show: bool = _v177_panel != null and _v177_panel.visible\n    _v188_refresh("))
    }

    @Test
    fun truthGuardRemainsPresentationOnly() {
        val map = asset("v188_miss_map.gd")
        assertFalse(map.contains("GreenTerrain.set"))
        assertFalse(map.contains("GreenReadAdvisor.set"))
        assertFalse(map.contains("ballVelocity ="))
        assertFalse(map.contains("bridge."))
    }
}
