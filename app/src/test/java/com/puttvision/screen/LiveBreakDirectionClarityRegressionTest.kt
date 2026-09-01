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
    fun liveBreakClarityIsLowFrequencyAndWiredToTvAndPreview() {
        val script = asset("live_break_direction_clarity.gd")
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        assertTrue(script.contains("const REFRESH_INTERVAL_S := 0.10"))
        assertTrue(script.contains("_timer.wait_time = REFRESH_INTERVAL_S"))
        assertTrue(tv.contains("res://live_break_direction_clarity.gd"))
        assertTrue(preview.contains("res://live_break_direction_clarity.gd"))
    }
}
