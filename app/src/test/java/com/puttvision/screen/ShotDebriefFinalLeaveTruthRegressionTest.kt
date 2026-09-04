package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDebriefFinalLeaveTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun malformedFinalLeaveCannotCoerceToAFalseZeroDistance() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("func _v177_leave_text(value: Variant) -> String:"))
        assertTrue(source.contains("value_type != TYPE_INT and value_type != TYPE_FLOAT"))
        assertTrue(source.contains("if not is_finite(leave_m):"))
        assertTrue(source.contains("return \"--\""))
        assertTrue(source.contains("_v177_leave_value.text = _v177_leave_text(s.get(\"distanceToCup\", null))"))
        assertFalse(source.contains("var leave_m: float = max(0.0, float(s.get(\"distanceToCup\", 0.0)))"))
    }

    @Test
    fun validFinalLeaveKeepsExistingCommercialPrecision() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("return \"%.2f m\" % max(0.0, leave_m)"))
    }

    @Test
    fun finalLeaveGuardRemainsPresentationOnly() {
        val source = asset("v177_shot_debrief.gd")

        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("targetDistance ="))
    }
}
