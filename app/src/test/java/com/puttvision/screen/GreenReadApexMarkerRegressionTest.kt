package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadApexMarkerRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun apexUsesExistingAuthoritativeRecommendationPathOnly() {
        val script = asset("green_read_apex_marker.gd")
        assertTrue(script.contains("root.has_method(\"_v183_path\")"))
        assertTrue(script.contains("root.call(\"_v183_path\", offset_m)"))
        assertTrue(script.contains("root.get(\"_v165_recommended_offset\")"))
        assertTrue(script.contains("_distance_to_baseline(curve[i], start, finish)"))
    }

    @Test
    fun apexFindsMaximumBreakAndSuppressesStraightReads() {
        val script = asset("green_read_apex_marker.gd")
        assertTrue(script.contains("const MIN_BREAK_PX := 6.0"))
        assertTrue(script.contains("const SEARCH_START_FRACTION := 0.14"))
        assertTrue(script.contains("const SEARCH_END_FRACTION := 0.84"))
        assertTrue(script.contains("if break_px > best_break_px:"))
        assertTrue(script.contains("if best_index < 0 or best_break_px < MIN_BREAK_PX:"))
        assertTrue(script.contains("_set_visible(false)"))
    }

    @Test
    fun apexBadgeIsEdgeSafeAndMobileBounded() {
        val script = asset("green_read_apex_marker.gd")
        assertTrue(script.contains("const MARKER_SEGMENTS := 16"))
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.12"))
        assertTrue(script.contains("clampf(badge_x_unclamped"))
        assertTrue(script.contains("clampf(center.y - 20.0"))
        assertFalse(script.contains("func _process("))
    }

    @Test
    fun apexCannotMutateAuthoritativePhysicsOrReadState() {
        val script = asset("green_read_apex_marker.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
    }

    @Test
    fun tvAndPreviewBothLoadApexCue() {
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(tv.contains("res://green_read_apex_marker.gd"))
        assertTrue(tv.contains("GreenReadApexMarker"))
        assertTrue(preview.contains("res://green_read_apex_marker.gd"))
        assertTrue(preview.contains("GreenReadApexMarker"))
    }
}
