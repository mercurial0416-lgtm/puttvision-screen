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
    fun transitionCueOnlyOverridesTimelineDuringRealReplay() {
        val cue = asset("replay_transition_cues.gd")

        assertTrue(cue.contains("func _replay_transition_is_active(remaining: float, actual_sample_count: int) -> bool:"))
        assertTrue(cue.contains("is_finite(remaining) and remaining > 0.0 and actual_sample_count >= 2"))
        assertTrue(cue.contains("if not _replay_transition_is_active(_v171_replay_remaining, _v171_replay_actual.size()):"))
        assertTrue(cue.contains("return\n    var progress := _focus_replay_progress"))
    }

    @Test
    fun completedReplayDoesNotInstallSyntheticIdleCopy() {
        val cue = asset("replay_transition_cues.gd")

        assertFalse(cue.contains("_focus_replay_stage_label.text = \"CUP · 0.0s\""))
        assertTrue(cue.contains("The parent timeline owns the idle/completed label"))
        assertTrue(cue.contains("leaves replay timing,"))
        assertTrue(cue.contains("GreenTerrain and GreenReadAdvisor untouched"))
    }
}
