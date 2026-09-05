package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefTargetWindowRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun targetBandsMirrorEstablishedGoodWindowThresholds() {
        val source = asset("debrief_target_window.gd")

        assertTrue(source.contains("const LINE_GOOD_HALF_PX := BAR_HALF_PX * 1.5 / 30.0"))
        assertTrue(source.contains("const PACE_GOOD_HALF_PX := BAR_HALF_PX * 8.0 / 70.0"))
        assertTrue(source.contains("_add_target_band(panel, \"LineGoodWindow\", LINE_TRACK_Y, LINE_GOOD_HALF_PX)"))
        assertTrue(source.contains("_add_target_band(panel, \"PaceGoodWindow\", PACE_TRACK_Y, PACE_GOOD_HALF_PX)"))
    }

    @Test
    fun targetBandsAreOneShotPresentationOnly() {
        val source = asset("debrief_target_window.gd")

        assertTrue(source.contains("set_process(false)"))
        assertTrue(source.contains("Control.MOUSE_FILTER_IGNORE"))
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("targetDistance ="))
    }

    @Test
    fun productionTvSceneInstallsTargetWindowHelper() {
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://debrief_target_window.gd"))
        assertTrue(scene.contains("[node name=\"DebriefTargetWindow\" type=\"Node\" parent=\".\"]"))
    }
}
