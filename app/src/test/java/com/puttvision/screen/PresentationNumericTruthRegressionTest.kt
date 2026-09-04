package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationNumericTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun presentationGuardRejectsNonNumericTelemetryBeforeFloatCoercion() {
        val guard = asset("presentation_telemetry_guard.gd")
        assertTrue(guard.contains("func _presentation_is_finite_number(value: Variant) -> bool:"))
        assertTrue(guard.contains("value_type == TYPE_INT or value_type == TYPE_FLOAT"))
        assertTrue(guard.contains("const PRESENTATION_PRACTICE_METRIC_KEYS := [\"readLineDeltaCm\", \"paceDeltaCm\"]"))
        assertTrue(guard.contains("for key in PRESENTATION_PRACTICE_METRIC_KEYS:"))
        assertTrue(guard.contains("not _presentation_is_finite_number(s.get(key))"))
        assertFalse(guard.contains("not is_finite(float(s.get(key, 0.0)))"))
    }

    @Test
    fun shotMapRequiresNumericMeasuredDeltasNotJustCoercibleValues() {
        val map = asset("v188_miss_map.gd")
        assertTrue(map.contains("func _v188_metric_is_finite_number(value: Variant) -> bool:"))
        assertTrue(map.contains("value_type == TYPE_INT or value_type == TYPE_FLOAT"))
        assertTrue(map.contains("_v188_metric_is_finite_number(\n        s.get(\"readLineDeltaCm\")"))
        assertTrue(map.contains("_v188_metric_is_finite_number(s.get(\"paceDeltaCm\"))"))
    }

    @Test
    fun numericTruthHardeningRemainsPresentationOnly() {
        val guard = asset("presentation_telemetry_guard.gd")
        val map = asset("v188_miss_map.gd")
        for (source in listOf(guard, map)) {
            assertFalse(source.contains("GreenTerrain.set"))
            assertFalse(source.contains("GreenReadAdvisor.set"))
            assertFalse(source.contains("ballVelocity ="))
        }
    }
}
