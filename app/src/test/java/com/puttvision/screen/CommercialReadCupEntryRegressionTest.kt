package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialReadCupEntryRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun cupEntryConsumesOnlyExistingRecommendedDisplayPath() {
        val source = asset("commercial_read_apex.gd")
        val geometry = source.substringAfter("func _read_cup_entry_geometry(offset_m: float) -> Dictionary:")
            .substringBefore("func _build_hud")

        assertTrue(geometry.contains("if not _read_overlay_telemetry_valid(offset_m):"))
        assertTrue(geometry.contains("var curve := _v183_path(offset_m)"))
        assertTrue(geometry.contains("for i in range(curve.size() - 2, -1, -1):"))
        assertTrue(geometry.contains("var delta := cup - prior"))
        assertTrue(geometry.contains("if delta.length_squared() >= minimum_segment_squared:"))
        assertTrue(geometry.contains("incoming = delta.normalized()"))
        assertTrue(geometry.contains("return {\"valid\": false}"))
        assertFalse(geometry.contains("GreenTerrain("))
        assertFalse(geometry.contains("GreenReadAdvisor("))
    }

    @Test
    fun duplicateCupSamplesCannotEraseOtherwiseValidArrivalDirection() {
        val source = asset("commercial_read_apex.gd")
        val geometry = source.substringAfter("func _read_cup_entry_geometry(offset_m: float) -> Dictionary:")
            .substringBefore("func _build_hud")

        assertTrue(source.contains("const READ_CUP_ENTRY_MIN_SEGMENT_PX := 1.0"))
        assertTrue(geometry.contains("var incoming := Vector2.ZERO"))
        assertTrue(geometry.contains("var minimum_segment_squared := READ_CUP_ENTRY_MIN_SEGMENT_PX * READ_CUP_ENTRY_MIN_SEGMENT_PX"))
        assertFalse(geometry.contains("cup - curve[curve.size() - 2]"))
    }

    @Test
    fun malformedTailSampleFailsClosedInsteadOfDrawingBelievableCue() {
        val source = asset("commercial_read_apex.gd")
        val geometry = source.substringAfter("func _read_cup_entry_geometry(offset_m: float) -> Dictionary:")
            .substringBefore("func _build_hud")

        assertTrue(geometry.contains("if not is_finite(cup.x) or not is_finite(cup.y):"))
        assertTrue(geometry.contains("if not is_finite(prior.x) or not is_finite(prior.y):"))
    }

    @Test
    fun invalidOrHiddenReadClearsCupEntryGeometry() {
        val source = asset("commercial_read_apex.gd")
        val refresh = source.substringAfter("func _refresh_read_cup_entry() -> void:")
            .substringBefore("func _v183_update")

        assertTrue(refresh.contains("var visible := _v183_panel.visible and bool(geometry.get(\"valid\", false))"))
        assertTrue(refresh.contains("_read_cup_entry.points = PackedVector2Array()"))
        assertTrue(refresh.contains("_read_cup_entry_wings.points = PackedVector2Array()"))
        assertTrue(source.contains("_refresh_read_cup_entry()"))
    }
}
