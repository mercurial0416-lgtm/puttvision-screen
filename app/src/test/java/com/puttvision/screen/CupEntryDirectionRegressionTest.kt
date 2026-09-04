package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CupEntryDirectionRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun cupEntryBadgePreservesLeftRightApproachDirection() {
        val script = asset("cup_entry_read_gate.gd")

        assertTrue(script.contains("func _entry_signed_angle_degrees("))
        assertTrue(script.contains("var cross_value := baseline.x * tangent.y - baseline.y * tangent.x"))
        assertTrue(script.contains("rad_to_deg(atan2(cross_value, dot_value))"))
        assertTrue(script.contains("var direction := \"RIGHT\" if signed_angle_deg > 0.0 else \"LEFT\""))
        assertTrue(script.contains("CUP ENTRY  %s %.0f°"))
        assertFalse(script.contains("return \"CUP ENTRY  %.0f°\" % angle_deg"))
    }

    @Test
    fun widerDirectionalBadgeStillClampsInsideOverviewPanel() {
        val script = asset("cup_entry_read_gate.gd")

        assertTrue(script.contains("const ENTRY_BADGE_WIDTH_PX := 156.0"))
        assertTrue(script.contains("const ENTRY_BADGE_HEIGHT_PX := 20.0"))
        assertTrue(script.contains("_badge.size = Vector2(ENTRY_BADGE_WIDTH_PX, ENTRY_BADGE_HEIGHT_PX)"))
        assertTrue(script.contains("var badge_width := ENTRY_BADGE_WIDTH_PX"))
        assertTrue(script.contains("panel_size.x - badge_width - 4.0"))
        assertTrue(script.contains("panel_size.y - ENTRY_BADGE_HEIGHT_PX - 4.0"))
    }

    @Test
    fun cupEntryDirectionRemainsPresentationOnly() {
        val script = asset("cup_entry_read_gate.gd")

        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("_v165_recommended_offset ="))
    }
}
