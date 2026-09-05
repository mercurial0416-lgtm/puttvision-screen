package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionReadTelemetryTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun precisionReadRejectsMalformedSlopeTelemetryBeforeCasting() {
        val script = asset("v176_precision_read_window.gd")
        assertTrue(script.contains("func _v176_metric_is_valid(value: Variant) -> bool:"))
        assertTrue(script.contains("value_type != TYPE_INT and value_type != TYPE_FLOAT"))
        assertTrue(script.contains("return is_finite(float(value))"))
        assertTrue(script.contains("var side_value: Variant = s.get(\"sideSlope\", null)"))
        assertTrue(script.contains("var long_value: Variant = s.get(\"longSlope\", null)"))
        assertFalse(script.contains("float(s.get(\"sideSlope\", 0.0))"))
        assertFalse(script.contains("float(s.get(\"longSlope\", 0.0))"))
    }

    @Test
    fun invalidSlopeTelemetryCannotMasqueradeAsStraightAndLevel() {
        val script = asset("v176_precision_read_window.gd")
        assertTrue(script.contains("func _v176_update_unavailable(offset_m: float) -> void:"))
        assertTrue(script.contains("_v176_break_value.text = \"--\""))
        assertTrue(script.contains("_v176_grade_value.text = \"--\""))
        assertTrue(script.contains("_v176_source_value.text = \"SLOPE DATA UNAVAILABLE\""))
        assertTrue(script.contains("_v176_break_arrow.visible = false"))
        assertTrue(script.contains("_v176_update_unavailable(_v165_recommended_offset)"))
    }

    @Test
    fun invalidSlopeFallbackKeepsAuthoritativeAimButDoesNotMutatePhysics() {
        val script = asset("v176_precision_read_window.gd")
        assertTrue(script.contains("_v176_update_aim_curve(offset_m)"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
