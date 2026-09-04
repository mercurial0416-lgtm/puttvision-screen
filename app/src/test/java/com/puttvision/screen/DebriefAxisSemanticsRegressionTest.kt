package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefAxisSemanticsRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun debriefBarsExposeDirectionalMeaningAtBothEnds() {
        val helper = asset("debrief_axis_semantics.gd")

        assertTrue(helper.contains("\"LineAxisLeft\", \"LEFT\""))
        assertTrue(helper.contains("\"LineAxisRight\", \"RIGHT\""))
        assertTrue(helper.contains("\"PaceAxisShort\", \"SHORT\""))
        assertTrue(helper.contains("\"PaceAxisLong\", \"LONG\""))
        assertTrue(helper.contains("const LINE_AXIS_Y := 88.0"))
        assertTrue(helper.contains("const PACE_AXIS_Y := 141.0"))
    }

    @Test
    fun helperInstallsOnceAndLeavesForwardMobileFrameBudgetAlone() {
        val helper = asset("debrief_axis_semantics.gd")

        assertTrue(helper.contains("if panel.get_node_or_null(\"LineAxisLeft\") != null:"))
        assertTrue(helper.contains("set_process(false)"))
        assertTrue(helper.contains("root.find_child(\"V177ShotDebrief\", true, false)"))
        assertFalse(helper.contains("queue_redraw"))
        assertFalse(helper.contains("_physics_process"))
    }

    @Test
    fun axisSemanticsArePresentationOnlyAndAttachedToProductionTv() {
        val helper = asset("debrief_axis_semantics.gd")
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://debrief_axis_semantics.gd"))
        assertTrue(scene.contains("DebriefAxisSemantics"))
        assertFalse(helper.contains("GreenTerrain.set"))
        assertFalse(helper.contains("GreenReadAdvisor.set"))
        assertFalse(helper.contains("ballVelocity ="))
        assertFalse(helper.contains("readLineDeltaCm ="))
        assertFalse(helper.contains("paceDeltaCm ="))
    }
}
