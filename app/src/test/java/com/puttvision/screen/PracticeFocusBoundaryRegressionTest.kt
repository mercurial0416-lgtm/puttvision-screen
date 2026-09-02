package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeFocusBoundaryRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun focusSwitchStartsAProspectivePracticeWindow() {
        val streak = asset("v191_practice_streak.gd")

        assertTrue(streak.contains("var _v191_focus_axis := \"BUILDING\""))
        assertTrue(streak.contains("var _v191_focus_start_index := 0"))
        assertTrue(streak.contains("func _v191_sync_focus_window(axis: String) -> void:"))
        assertTrue(streak.contains("if axis != _v191_focus_axis:"))
        assertTrue(streak.contains("_v191_focus_start_index = sample_count"))
        assertTrue(streak.contains("_v191_sync_focus_window(axis)\n    _v191_streak = _v191_trailing_streak(axis)"))
    }

    @Test
    fun streakAndResetCoachingIgnorePreFocusSamples() {
        val streak = asset("v191_practice_streak.gd")

        assertTrue(streak.contains("func _v191_has_focus_samples() -> bool:"))
        assertTrue(streak.contains("return _v179_samples.size() > _v191_focus_start_index"))
        assertTrue(streak.contains("for index in range(_v179_samples.size() - 1, _v191_focus_start_index - 1, -1):"))
        assertTrue(streak.contains("if axis == \"BUILDING\" or not _v191_has_focus_samples():\n        return \"START STREAK\""))
        assertFalse(streak.contains("for index in range(_v179_samples.size() - 1, -1, -1):"))
    }

    @Test
    fun adaptiveFailureCountUsesTheSameFocusBoundary() {
        val drill = asset("v192_drill_progression.gd")

        assertTrue(drill.contains("if axis == \"BUILDING\" or not _v191_has_focus_samples():"))
        assertTrue(drill.contains("for index in range(_v179_samples.size() - 1, _v191_focus_start_index - 1, -1):"))
        assertFalse(drill.contains("for index in range(_v179_samples.size() - 1, -1, -1):"))
    }

    @Test
    fun changeRemainsPresentationOnly() {
        val streak = asset("v191_practice_streak.gd")
        val drill = asset("v192_drill_progression.gd")
        val combined = streak + drill

        assertFalse(combined.contains("GreenTerrain("))
        assertFalse(combined.contains("GreenReadAdvisor("))
        assertFalse(combined.contains("ball.position"))
        assertFalse(combined.contains("target_distance ="))
        assertFalse(combined.contains("shot capture" + " ="))
    }
}
