package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakApexReadMarkerRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun breakApexUsesExistingRecommendedReadWithoutChangingPhysics() {
        val script = asset("break_apex_read_marker.gd")
        assertTrue(script.contains("root.has_method(\"_v183_path\")"))
        assertTrue(script.contains("root.call(\"_v183_path\", offset_m)"))
        assertTrue(script.contains("root.get(\"_v165_recommended_offset\")"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }

    @Test
    fun breakApexFindsMaximumChordDeviationAndSuppressesStraightReads() {
        val script = asset("break_apex_read_marker.gd")
        assertTrue(script.contains("func _apex_geometry(curve: PackedVector2Array) -> Dictionary:"))
        assertTrue(script.contains("var signed_deviation := chord_dir.cross(curve[i] - start)"))
        assertTrue(script.contains("if abs_deviation > best_abs_deviation:"))
        assertTrue(script.contains("var threshold := maxf(MIN_APEX_DEVIATION_PX, chord_length * MIN_APEX_DEVIATION_RATIO)"))
        assertTrue(script.contains("if best_index < 0 or best_abs_deviation < threshold:"))
        assertTrue(script.contains("return {}"))
    }

    @Test
    fun breakApexBadgeStaysInsideOverviewPanel() {
        val script = asset("break_apex_read_marker.gd")
        assertTrue(script.contains("func _badge_position(apex: Vector2, panel_size: Vector2) -> Vector2:"))
        assertTrue(script.contains("clampf(x_unclamped, PANEL_MARGIN_PX"))
        assertTrue(script.contains("clampf(y_unclamped, PANEL_MARGIN_PX"))
        assertTrue(script.contains("_badge.text = \"BREAK APEX\""))
    }

    @Test
    fun breakApexRunsAtLowFrequencyAndIsWiredToTvAndPreview() {
        val script = asset("break_apex_read_marker.gd")
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.12"))
        assertTrue(script.contains("_timer.wait_time = REFRESH_INTERVAL_S"))
        assertTrue(tv.contains("res://break_apex_read_marker.gd"))
        assertTrue(tv.contains("[node name=\"BreakApexReadMarker\""))
        assertTrue(preview.contains("res://break_apex_read_marker.gd"))
        assertTrue(preview.contains("[node name=\"BreakApexReadMarker\""))
    }
}
