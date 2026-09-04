package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTransitionActiveGuardRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun transitionCueOnlyOverridesTimelineDuringRenderableReplay() {
        val cue = asset("replay_transition_cues.gd")
        val update = cue.substringAfter("func _focus_update_replay_timeline() -> void:")

        assertTrue(cue.contains("func _replay_focus_is_active(remaining: float, duration: float, actual_sample_count: int) -> bool:"))
        assertTrue(cue.contains("and duration > REPLAY_TRANSITION_LABEL_MIN_DURATION"))
        assertTrue(cue.contains("and actual_sample_count >= 2"))
        assertTrue(update.contains("if not _replay_focus_is_active("))
        assertTrue(update.indexOf("if not _replay_focus_is_active(") < update.indexOf("_focus_replay_progress("))
        assertTrue(update.contains("_focus_replay_stage_label.text = _replay_transition_readout(cue, parent_readout)"))
    }

    @Test
    fun completedReplayKeepsParentIdleCopyAndDistanceTruth() {
        val cue = asset("replay_transition_cues.gd")
        val update = cue.substringAfter("func _focus_update_replay_timeline() -> void:")

        assertFalse(cue.contains("_focus_replay_stage_label.text = \"CUP · 0.0s\""))
        assertTrue(update.contains("super._focus_update_replay_timeline()"))
        assertTrue(update.contains("var parent_readout := _focus_replay_stage_label.text"))
        assertTrue(update.contains("if not _replay_focus_is_active("))
        assertTrue(update.contains("):\n        return"))
        assertTrue(cue.contains("leaves ownership of distance calculation"))
        assertFalse(update.contains("GreenTerrain("))
        assertFalse(update.contains("GreenReadAdvisor("))
    }
}
