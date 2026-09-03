package com.puttvision.screen

import java.io.File
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
        assertTrue(helper.contains("runtime distance always comes from the recorded actual trail"))
        assertTrue(helper.contains("no replay clock, trail point, camera or physics data changes"))
    }

    @Test
    fun replayDistanceGuardRemainsAttachedToTvScene() {
        val scene = asset("v143_tv.tscn")

        assertTrue(scene.contains("res://replay_roll_distance_layout.gd"))
        assertTrue(scene.contains("ReplayRollDistanceLayout"))
    }
}
