package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakDirectionClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun liveBreakExpandsTerseDirectionTokensForTvReadability() {
        val script = asset("live_break_direction_clarity.gd")
        assertTrue(script.contains("REST RIGHT"))
        assertTrue(script.contains("REST LEFT"))
        assertTrue(script.contains("LAST OBS RIGHT"))
        assertTrue(script.contains("LAST OBS LEFT"))
        assertTrue(script.contains("PEAK RIGHT"))
        assertTrue(script.contains("PEAK LEFT"))
        assertTrue(script.contains("return \"RIGHT \" + text.substr(2)"))
        assertTrue(script.contains("return \"LEFT \" + text.substr(2)"))
    }

    @Test
    fun liveBreakClarityTouchesPresentationLabelsOnly() {
        val script = asset("live_break_direction_clarity.gd")
        assertTrue(script.contains("_rewrite_label(root, \"_live_curve_value\")"))
        assertTrue(script.contains("_rewrite_label(root, \"_live_curve_peak_label\")"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }

    @Test
    fun liveBreakClarityRunsAfterRootHudAtBoundedFrequency() {
        val script = asset("live_break_direction_clarity.gd")
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.05"))
        assertTrue(script.contains("var _elapsed_s := REFRESH_INTERVAL_S"))
        assertTrue(script.contains("func _process(delta: float) -> void:"))
        assertTrue(script.contains("if _elapsed_s < REFRESH_INTERVAL_S:"))
        assertTrue(script.contains("_refresh()"))
        assertFalse(script.contains("Timer.new()"))
    }

    @Test
    fun liveBreakClarityIsWiredToTvAndRenderedPreview() {
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(tv.contains("res://live_break_direction_clarity.gd"))
        assertTrue(tv.contains("[node name=\"LiveBreakDirectionClarity\""))
        assertTrue(preview.contains("res://live_break_direction_clarity.gd"))
        assertTrue(preview.contains("[node name=\"LiveBreakDirectionClarity\""))
    }
}
