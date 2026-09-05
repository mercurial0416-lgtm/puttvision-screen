package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDebriefSeverityColorRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun debriefBarsEscalateFromGoodToWarningToReset() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("const V177_GOOD_COLOR := Color(\"#76d7b6\")"))
        assertTrue(source.contains("const V177_WARN_COLOR := Color(\"#d6b85c\")"))
        assertTrue(source.contains("const V177_RESET_COLOR := Color(\"#ef7d6f\")"))
        assertTrue(source.contains("func _v177_severity_color(delta_cm: float, good_cm: float, reset_cm: float) -> Color:"))
        assertTrue(source.contains("_v177_line_bar.color = _v177_severity_color(line_delta, 1.5, 9.0)"))
        assertTrue(source.contains("_v177_pace_bar.color = _v177_severity_color(pace_delta, 8.0, 22.0)"))
    }

    @Test
    fun severityThresholdsStayAlignedWithCoachingSemantics() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("if abs(delta_cm) < 1.5:"))
        assertTrue(source.contains("if abs(delta_cm) < 8.0:"))
        assertTrue(source.contains("var line_bad: bool = abs(line_delta_cm) >= 9.0"))
        assertTrue(source.contains("var pace_bad: bool = abs(pace_delta_cm) >= 22.0"))
    }

    @Test
    fun severityPresentationDoesNotMutateAuthoritativePhysics() {
        val source = asset("v177_shot_debrief.gd")

        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("targetDistance ="))
    }
}
