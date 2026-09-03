package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeFocusWindowRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun focusSwitchStartsProspectiveScoringWindow() {
        val streak = asset("v191_practice_streak.gd")
        assertTrue(streak.contains("var _v191_focus_axis := \"BUILDING\""))
        assertTrue(streak.contains("var _v191_focus_start_index := 0"))
        assertTrue(streak.contains("func _v191_sync_focus_window(axis: String) -> void:"))
        assertTrue(streak.contains("if axis != _v191_focus_axis:"))
        assertTrue(streak.contains("_v191_focus_start_index = sample_count"))
        assertTrue(streak.contains("_v191_sync_focus_window(axis)"))
    }

    @Test
    fun streakAndFailureCountersIgnorePreFocusSamples() {
        val streak = asset("v191_practice_streak.gd")
        val drill = asset("v192_drill_progression.gd")
        val boundedLoop = "for index in range(_v179_samples.size() - 1, _v191_focus_start_index - 1, -1):"
        assertTrue(streak.contains(boundedLoop))
        assertTrue(drill.contains(boundedLoop))
        assertTrue(streak.contains("not _v191_has_focus_samples()"))
        assertTrue(drill.contains("not _v191_has_focus_samples()"))
        assertFalse(drill.contains("for index in range(_v179_samples.size() - 1, -1, -1):"))
    }

    @Test
    fun resetCoachingWaitsForARepPlayedUnderCurrentFocus() {
        val streak = asset("v191_practice_streak.gd")
        assertTrue(streak.contains("return _v179_samples.size() > _v191_focus_start_index"))
        assertTrue(streak.contains("if axis == \"BUILDING\" or not _v191_has_focus_samples():\n        return \"START STREAK\""))
        assertTrue(streak.contains("_v191_streak == 0 and _v191_has_focus_samples()"))
    }

    @Test
    fun focusWindowChangeRemainsPresentationOnly() {
        val combined = asset("v191_practice_streak.gd") + asset("v192_drill_progression.gd")
        assertFalse(combined.contains("GreenTerrain("))
        assertFalse(combined.contains("GreenReadAdvisor("))
        assertFalse(combined.contains("ball.position ="))
        assertFalse(combined.contains("s[\"ballSpeed\"] ="))
    }
}
