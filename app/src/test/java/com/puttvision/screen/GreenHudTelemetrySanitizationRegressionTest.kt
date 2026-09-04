package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenHudTelemetrySanitizationRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path")
    }

    @Test
    fun malformedSlopeTelemetryNeverBecomesDirectionalRead() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("func _telemetry_value_is_valid(value: float) -> bool:"))
        assertTrue(source.contains("return is_finite(value)"))
        assertTrue(source.contains("return \"BREAK  --\""))
        assertTrue(source.contains("_v165_aim_label.text = \"%s   |   READ --\" % aim_text"))
        assertTrue(source.contains("_v165_detail_label.text = \"BREAK --   |   LIVE FLOW | CONTOUR | CUP 0.125m\""))
    }

    @Test
    fun malformedLiveCurveTelemetryStaysNeutral() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("if not _telemetry_value_is_valid(cross_track_cm):\n        return \"--\""))
        assertTrue(source.contains("if not _telemetry_value_is_valid(peak_signed_cm):\n        return \"PEAK --\""))
    }
}
