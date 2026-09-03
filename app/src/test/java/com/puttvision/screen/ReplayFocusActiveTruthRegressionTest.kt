package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFocusActiveTruthRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun cinematicFocusRequiresFiniteClockDurationAndRenderableTrail() {
        val cue = asset("replay_transition_cues.gd")

        assertTrue(cue.contains("func _replay_focus_is_active(remaining: float, duration: float, actual_sample_count: int) -> bool:"))
        assertTrue(cue.contains("is_finite(remaining)"))
        assertTrue(cue.contains("and remaining > 0.0"))
        assertTrue(cue.contains("and is_finite(duration)"))
        assertTrue(cue.contains("and duration > REPLAY_TRANSITION_LABEL_MIN_DURATION"))
        assertTrue(cue.contains("and actual_sample_count >= 2"))
    }

    @Test
    fun focusPhaseUsesValidatedReplayStateInsteadOfRawRemainingClock() {
        val cue = asset("replay_transition_cues.gd")

        assertTrue(cue.contains("func _focus_current_phase() -> String:"))
        assertTrue(cue.contains("var replaying := _replay_focus_is_active("))
        assertTrue(cue.contains("_v171_replay_duration,"))
        assertTrue(cue.contains("_v171_replay_actual.size()"))
        assertTrue(cue.contains("return _focus_phase_for(_focus_running, replaying, showing_result)"))
    }
}
