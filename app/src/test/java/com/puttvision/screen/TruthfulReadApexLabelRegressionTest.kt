package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TruthfulReadApexLabelRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun apexBadgeNamesTheRenderedPathLandmarkInsteadOfReusingLaunchOffsetCentimetres() {
        val guard = asset("truthful_read_apex_label.gd")
        val apex = asset("commercial_read_apex.gd")

        assertTrue(guard.contains("var apex_x := _ring.position.x"))
        assertTrue(guard.contains("return \"APEX  RIGHT\" if delta > 0.0 else \"APEX  LEFT\""))
        assertTrue(guard.contains("APEX_CENTER_DEADBAND_PX"))
        assertFalse(guard.contains("offset_m * 100"))
        assertTrue(apex.contains("_read_apex_point(offset_m)"))
    }

    @Test
    fun startGateKeepsTheAuthoritativeLaunchOffsetCentimetresAsASeparateQuantity() {
        val apex = asset("commercial_read_apex.gd")

        assertTrue(apex.contains("func _read_start_gate_descriptor(offset_m: float) -> String:"))
        assertTrue(apex.contains("int(round(absf(offset_m) * 100.0))"))
        assertTrue(apex.contains("START  %s %d cm"))
    }

    @Test
    fun guardIsLightweightPresentationOnlyAndWiredToTvAndPreview() {
        val guard = asset("truthful_read_apex_label.gd")
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")

        assertTrue(guard.contains("process_priority = 180"))
        assertTrue(guard.contains("find_child(\"GreenReadOverview\", true, false)"))
        assertFalse(guard.contains("GreenTerrain("))
        assertFalse(guard.contains("GreenReadAdvisor("))
        assertFalse(guard.contains("score ="))
        assertTrue(tv.contains("res://truthful_read_apex_label.gd"))
        assertTrue(preview.contains("res://truthful_read_apex_label.gd"))
    }
}
