package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastReplayStatePriorityRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun broadcastStatePillPrefersReplayWhenRunningFlagLingers() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")
        val state = update.substringAfter("var replaying := _v171_replay_remaining > 0.0")
            .substringBefore("_v174_result_panel.visible")

        val replayCheck = state.indexOf("if replaying:")
        val runningCheck = state.indexOf("elif running:")
        assertTrue(replayCheck >= 0)
        assertTrue(runningCheck > replayCheck)
        assertTrue(state.contains("_v174_state_label.text = \"SHOT REPLAY\""))
        assertTrue(state.contains("_v174_state_label.text = \"BALL ROLLING\""))
    }

    @Test
    fun replayStateUsesExistingPresentationClockWithoutMutatingTiming() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")

        assertTrue(update.contains("var replaying := _v171_replay_remaining > 0.0"))
        assertFalse(update.contains("_v171_replay_remaining ="))
        assertFalse(update.contains("_v171_replay_duration ="))
    }

    @Test
    fun broadcastReplayPriorityFixStaysPresentationOnly() {
        val source = asset("v174_broadcast_hud.gd")
        val update = source.substringAfter("func _update_hud")
        val state = update.substringAfter("var replaying := _v171_replay_remaining > 0.0")
            .substringBefore("_v174_result_panel.visible")

        assertFalse(state.contains("GreenTerrain("))
        assertFalse(state.contains("GreenReadAdvisor("))
        assertFalse(state.contains("score ="))
        assertFalse(state.contains("ball.position ="))
        assertFalse(state.contains("camera.position ="))
        assertFalse(state.contains("camera.fov ="))
    }
}
