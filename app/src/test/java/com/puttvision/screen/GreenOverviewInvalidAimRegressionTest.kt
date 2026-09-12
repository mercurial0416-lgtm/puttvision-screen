package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenOverviewInvalidAimRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path")
    }

    @Test
    fun invalidAdvisorOffsetKeepsOverviewNeutralAndMarkerHidden() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("func _telemetry_value_is_valid(value: float) -> bool:\n    return is_finite(value)"))
        assertTrue(source.contains("func _overview_aim_is_valid(offset_m: float) -> bool:\n    return _telemetry_value_is_valid(offset_m)"))
        assertTrue(source.contains("return \"AIM --\""))
        assertTrue(source.contains("active and valid and absf(offset) >= OVERVIEW_AIM_DEADBAND_M"))
    }

    @Test
    fun invalidOffsetNeverReachesClampOrOffMapDirectionMath() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("if not _overview_aim_is_valid(offset_m):"))
        assertTrue(source.contains("return false\n    return absf(offset_m) > OVERVIEW_AIM_VISUAL_SPAN_M"))
        assertTrue(source.contains("return Vector2(center_x, V183_MAP_ORIGIN.y + 18.0)"))
    }

    @Test
    fun invalidLiveTelemetryStaysNeutralInsteadOfInventingDirection() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("func _live_curve_readout(cross_track_cm: float) -> String:"))
        assertTrue(source.contains("if not _telemetry_value_is_valid(cross_track_cm):\n        return \"--\""))
        assertTrue(source.contains("func _live_peak_readout(peak_signed_cm: float) -> String:"))
        assertTrue(source.contains("if not _telemetry_value_is_valid(peak_signed_cm):\n        return \"PEAK --\""))
    }

    @Test
    fun invalidSlopeTelemetryKeepsReadCardNeutral() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("var slope_valid := _telemetry_value_is_valid(side_pct) and _telemetry_value_is_valid(long_pct)"))
        assertTrue(source.contains("if not slope_valid:\n        _v165_aim_label.text = \"%s   |   READ --\" % aim_text"))
        assertTrue(source.contains("_v165_detail_label.text = \"BREAK --   |   LIVE FLOW | CONTOUR | CUP 0.125m\""))
    }
}
