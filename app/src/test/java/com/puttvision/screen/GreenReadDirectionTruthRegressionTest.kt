package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadDirectionTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun aimDirectionFollowsRecommendedOffsetSignAndUsesTvReadableWords() {
        val script = asset("green_read_direction_truth.gd")
        assertTrue(script.contains("var aim_dir := \"RIGHT\" if _v165_recommended_offset > 0.0 else \"LEFT\""))
        assertTrue(script.contains("var direction := \"RIGHT\" if offset_m > 0.0 else \"LEFT\""))
        assertFalse(script.contains("var aim_dir := \"R\" if _v165_recommended_offset > 0.0 else \"L\""))
        assertFalse(script.contains("var aim_dir := \"R\" if side_pct > 0.0 else \"L\""))
    }

    @Test
    fun breakDirectionMatchesGreenOverviewSlopeSemantics() {
        val script = asset("green_read_direction_truth.gd")
        assertTrue(script.contains("break_dir = \"BREAK RIGHT\" if side_pct > 0.0 else \"BREAK LEFT\""))
        assertFalse(script.contains("break_dir = \"BREAK R\" if side_pct > 0.0 else \"BREAK L\""))
        val overview = asset("v183_green_overview.gd")
        assertTrue(overview.contains("positive means the right side is lower"))
        assertTrue(overview.contains("therefore the ball's gravity break is right"))
    }

    @Test
    fun correctionStaysPresentationOnlyAndIsInLiveSceneInheritance() {
        val script = asset("green_read_direction_truth.gd")
        val scene = asset("v143_tv.tscn")
        val live = asset("replay_timeline_camera_truth.gd")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(live.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(script.contains("GreenTerrain and GreenReadAdvisor remain authoritative"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("bridge."))
    }
}
