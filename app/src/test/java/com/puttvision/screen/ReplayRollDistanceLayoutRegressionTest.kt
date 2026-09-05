package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRollDistanceLayoutRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun replayDistanceGuardCachesSceneLookupsInsteadOfWalkingTreeEveryFrame() {
        val helper = asset("replay_roll_distance_layout.gd")

        assertTrue(helper.contains("var _cached_stage: Label = null"))
        assertTrue(helper.contains("var _cached_track: Control = null"))
        assertTrue(helper.contains("if not is_instance_valid(_cached_stage) or not is_instance_valid(_cached_track):"))
        assertTrue(helper.contains("func _bind_nodes(root: Node) -> void:"))
        assertTrue(helper.contains("if source_text == _last_presented_text:"))
    }

    @Test
    fun replayDistanceWordingStaysClearAndPresentationOnly() {
        val helper = asset("replay_roll_distance_layout.gd")

        assertTrue(helper.contains("const CLEAR_REMAINING_SUFFIX := \" TO STOP\""))
        assertTrue(helper.contains("runtime distance and replay time always come from existing"))
        assertTrue(helper.contains("no replay clock, trail point, camera or physics data changes"))
    }

    @Test
    fun replayClockAddsGlanceableTimeToStopWithoutMutatingPlayback() {
        val helper = asset("replay_roll_distance_layout.gd")

        assertTrue(helper.contains("func _replay_clock_readout(root: Node) -> String:"))
        assertTrue(helper.contains("root.get(\"_v171_replay_remaining\")"))
        assertTrue(helper.contains("if value_type != TYPE_INT and value_type != TYPE_FLOAT:"))
        assertTrue(helper.contains("if not is_finite(remaining) or remaining <= 0.0:"))
        assertTrue(helper.contains("return \"%.1fs\" % remaining"))
        assertTrue(helper.contains("func _inject_replay_clock(source_text: String, clock_text: String) -> String:"))
        assertFalse(helper.contains("_v171_replay_remaining ="))
        assertFalse(helper.contains("_v171_replay_duration ="))
        assertFalse(helper.contains("GreenTerrain.set"))
        assertFalse(helper.contains("GreenReadAdvisor.set"))
    }

    @Test
    fun replayClockKeepsDistanceAsTerminalCueAndExercisesPreview() {
        val helper = asset("replay_roll_distance_layout.gd")

        assertTrue(helper.contains("const PREVIEW_SAMPLE_TIME := \"1.2s\""))
        assertTrue(helper.contains("var distance_separator := source_text.rfind(STATUS_SEPARATOR)"))
        assertTrue(helper.contains("var clock_text := PREVIEW_SAMPLE_TIME if previewing else _replay_clock_readout(root)"))
        assertTrue(helper.contains("presented_text = _inject_replay_clock(presented_text, clock_text)"))
    }

    @Test
    fun replayDistanceGuardRemainsAttachedToTvScene() {
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://replay_roll_distance_layout.gd"))
        assertTrue(scene.contains("ReplayRollDistanceLayout"))
    }
}
