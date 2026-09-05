package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDebriefNextRepRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun debriefRejectsMalformedReadMetricsBeforeCastingThem() {
        val script = asset("v177_shot_debrief.gd")
        assertTrue(script.contains("func _v177_metric_is_valid(value: Variant) -> bool:"))
        assertTrue(script.contains("value_type != TYPE_INT and value_type != TYPE_FLOAT"))
        assertTrue(script.contains("return is_finite(float(value))"))
        assertTrue(script.contains("var has_read_metrics: bool = _v177_read_metrics_valid(s)"))
        assertFalse(script.contains("var has_read_metrics: bool = s.has(\"readLineDeltaCm\") and s.has(\"paceDeltaCm\")"))
    }

    @Test
    fun nextRepInvertsLineErrorIntoAnActionableStartCorrection() {
        val script = asset("v177_shot_debrief.gd")
        assertTrue(script.contains("func _v177_next_rep(line_delta_cm: float, pace_delta_cm: float, holed: bool, lip_out: bool) -> String:"))
        assertTrue(script.contains("(\"LEFT\" if line_delta_cm > 0.0 else \"RIGHT\")"))
        assertTrue(script.contains("line_cue = \"START %d cm %s\""))
        assertTrue(script.contains("abs(line_delta_cm) >= 1.5"))
    }

    @Test
    fun nextRepTurnsPaceMissIntoSimpleRepeatableLanguage() {
        val script = asset("v177_shot_debrief.gd")
        assertTrue(script.contains("pace_delta_cm >= 8.0"))
        assertTrue(script.contains("pace_cue = \"PACE SOFTER\""))
        assertTrue(script.contains("pace_delta_cm <= -8.0"))
        assertTrue(script.contains("pace_cue = \"PACE FIRMER\""))
        assertTrue(script.contains("return \"HOLD LINE  •  HOLD PACE\""))
        assertTrue(script.contains("\"NEXT REP\""))
    }

    @Test
    fun nextRepRemainsPresentationOnly() {
        val script = asset("v177_shot_debrief.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
