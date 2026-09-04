package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CupEntryReadTruthRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun malformedAdvisorTelemetryNeverBecomesStraightCupEntry() {
        val source = asset("cup_entry_read_gate.gd")
        val refresh = source.substringAfter("func _refresh() -> void:")

        assertTrue(source.contains("func _entry_curve_is_valid(curve: PackedVector2Array) -> bool:"))
        assertTrue(source.contains("if not is_finite(point.x) or not is_finite(point.y):"))
        assertTrue(refresh.contains("if offset_variant == null:"))
        assertTrue(refresh.contains("if not is_finite(offset_m):"))
        assertTrue(refresh.indexOf("if not is_finite(offset_m):") < refresh.indexOf("root.call(\"_v183_path\", offset_m)"))
        assertTrue(source.contains("return \"CUP ENTRY  --\""))
        assertFalse(refresh.contains("else 0.0"))
    }

    @Test
    fun hiddenCupEntryClearsStalePresentationGeometry() {
        val source = asset("cup_entry_read_gate.gd")
        val hide = source.substringAfter("func _entry_hide() -> void:")
            .substringBefore("func _refresh")

        assertTrue(hide.contains("_gate.points = PackedVector2Array()"))
        assertTrue(hide.contains("_badge.text = \"CUP ENTRY  --\""))
        assertTrue(hide.contains("_center_ring.visible = false"))
    }

    @Test
    fun cupEntryBadgeKeepsAcrossRoomHierarchyWithoutTouchingPhysics() {
        val source = asset("cup_entry_read_gate.gd")

        assertTrue(source.contains("const ENTRY_BADGE_FONT_SIZE := 11"))
        assertTrue(source.contains("const ENTRY_BADGE_OUTLINE_SIZE := 2"))
        assertTrue(source.contains("font_outline_color"))
        assertTrue(source.contains("_badge.clip_text = true"))
        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
    }
}
