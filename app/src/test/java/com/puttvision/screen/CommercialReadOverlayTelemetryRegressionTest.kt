package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialReadOverlayTelemetryRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun malformedAdvisorTelemetryHidesCommercialGeometry() {
        val source = asset("commercial_read_apex.gd")

        assertTrue(source.contains("func _read_overlay_telemetry_valid(offset_m: float) -> bool:"))
        assertTrue(source.contains("return is_finite(offset_m)"))
        assertTrue(source.contains("var visible := _v183_panel.visible and valid"))
        assertTrue(source.contains("_read_corridor_fill.polygon = PackedVector2Array()"))
        assertTrue(source.contains("_read_start_gate.points = PackedVector2Array()"))
        assertTrue(source.contains("_read_apex_ring.points = PackedVector2Array()"))
    }

    @Test
    fun invalidOffsetNeverEntersCommercialPathGeometry() {
        val source = asset("commercial_read_apex.gd")
        val apexPoint = source.substringAfter("func _read_apex_point(offset_m: float) -> Vector2:")
            .substringBefore("func _read_corridor_edges")
        val startGate = source.substringAfter("func _read_start_gate_geometry(offset_m: float) -> Dictionary:")
            .substringBefore("func _build_hud")

        assertTrue(apexPoint.indexOf("if not _read_overlay_telemetry_valid(offset_m):") < apexPoint.indexOf("_v183_path(offset_m)"))
        assertTrue(startGate.indexOf("if not _read_overlay_telemetry_valid(offset_m):") < startGate.indexOf("_v183_path(offset_m)"))
        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
    }
}
