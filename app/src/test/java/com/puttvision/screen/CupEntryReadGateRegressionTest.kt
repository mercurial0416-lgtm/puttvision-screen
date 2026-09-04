package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CupEntryReadGateRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun cupEntryGateUsesExistingRecommendedReadPath() {
        val script = asset("cup_entry_read_gate.gd")
        assertTrue(script.contains("const ENTRY_FRACTION := 0.88"))
        assertTrue(script.contains("root.has_method(\"_v183_path\")"))
        assertTrue(script.contains("root.call(\"_v183_path\", offset_m)"))
        assertTrue(script.contains("root.get(\"_v165_recommended_offset\")"))
        assertTrue(script.contains("_gate.points = PackedVector2Array([geometry[\"left\"], geometry[\"right\"]])"))
    }

    @Test
    fun cupEntryGateIsPerpendicularToLocalPathAndEdgeSafe() {
        val script = asset("cup_entry_read_gate.gd")
        assertTrue(script.contains("var tangent := (curve[index + 1] - curve[index - 1]).normalized()"))
        assertTrue(script.contains("var normal := Vector2(-tangent.y, tangent.x)"))
        assertTrue(script.contains("center + normal * ENTRY_HALF_WIDTH_PX"))
        assertTrue(script.contains("center - normal * ENTRY_HALF_WIDTH_PX"))
        assertTrue(script.contains("const ENTRY_BADGE_WIDTH_PX := 156.0"))
        assertTrue(script.contains("const ENTRY_BADGE_HEIGHT_PX := 20.0"))
        assertTrue(script.contains("var badge_width := ENTRY_BADGE_WIDTH_PX"))
        assertTrue(script.contains("center.x - badge_width - 10.0"))
        assertTrue(script.contains("panel_size.x - badge_width - 4.0"))
        assertTrue(script.contains("center.y - ENTRY_BADGE_HEIGHT_PX * 0.5"))
        assertTrue(script.contains("panel_size.y - ENTRY_BADGE_HEIGHT_PX - 4.0"))
    }

    @Test
    fun cupEntryBadgeQuantifiesSignedFinalApproachAngleAgainstBallToCupBaseline() {
        val script = asset("cup_entry_read_gate.gd")
        assertTrue(script.contains("func _entry_signed_angle_degrees(curve: PackedVector2Array, geometry: Dictionary) -> float:"))
        assertTrue(script.contains("func _entry_angle_degrees(curve: PackedVector2Array, geometry: Dictionary) -> float:"))
        assertTrue(script.contains("var baseline := (curve[curve.size() - 1] - curve[0]).normalized()"))
        assertTrue(script.contains("var dot_value := clampf(baseline.dot(tangent), -1.0, 1.0)"))
        assertTrue(script.contains("var cross_value := baseline.x * tangent.y - baseline.y * tangent.x"))
        assertTrue(script.contains("return rad_to_deg(atan2(cross_value, dot_value))"))
        assertTrue(script.contains("return absf(_entry_signed_angle_degrees(curve, geometry))"))
        assertTrue(script.contains("return \"CUP ENTRY  STRAIGHT\""))
        assertTrue(script.contains("var direction := \"RIGHT\" if signed_angle_deg > 0.0 else \"LEFT\""))
        assertTrue(script.contains("return \"CUP ENTRY  %s %.0f°\" % [direction, absf(signed_angle_deg)]"))
        assertTrue(script.contains("_badge.text = _entry_badge_text(curve, geometry)"))
        assertFalse(script.contains("return rad_to_deg(acos(dot_value))"))
        assertFalse(script.contains("curve[curve.size() - 1] - center"))
    }

    @Test
    fun cueIsBoundedPresentationOnlyForMobile() {
        val script = asset("cup_entry_read_gate.gd")
        assertTrue(script.contains("const ENTRY_RING_SEGMENTS := 14"))
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.12"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
    }

    @Test
    fun tvAndPreviewBothLoadCupEntryCue() {
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(tv.contains("res://cup_entry_read_gate.gd"))
        assertTrue(tv.contains("CupEntryReadGate"))
        assertTrue(preview.contains("res://cup_entry_read_gate.gd"))
        assertTrue(preview.contains("CupEntryReadGate"))
    }
}
