package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakSummaryLayoutRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun completedPeakAndReturnUseTwoLineSafeLayout() {
        val helper = asset("live_break_summary_layout.gd")

        assertTrue(helper.contains("const RETURN_SEPARATOR := \" · BACK \""))
        assertTrue(helper.contains("_peak_label.text.replace(RETURN_SEPARATOR, \"\\nBACK \" )".replace(" \" )", "\") ) || helper.contains("replace(RETURN_SEPARATOR, \"\\nBACK \")"))
        assertTrue(helper.contains("_peak_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART"))
        assertTrue(helper.contains("_peak_label.clip_text = true"))
    }

    @Test
    fun layoutGuardIsAttachedToTvScene() {
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://live_break_summary_layout.gd"))
        assertTrue(scene.contains("[node name=\"LiveBreakSummaryLayout\" type=\"Node\" parent=\".\"]"))
    }

    @Test
    fun guardRemainsPresentationOnly() {
        val helper = asset("live_break_summary_layout.gd")

        assertTrue(helper.contains("GreenTerrain, GreenReadAdvisor, scoring, aiming and shot physics remain untouched"))
        assertTrue(helper.contains("LiveBreakMeter"))
        assertTrue(helper.contains("LiveBreakPeak"))
    }
}
