package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFocusPriorityRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun replayOwnsPresentationWhenLiveRunningFlagLingers() {
        val source = asset("presentation_focus_choreography.gd")
        val phase = source.substringAfter("func _focus_phase_for(running: bool, replaying: bool, showing_result: bool) -> String:")
            .substringBefore("func _focus_role_alpha")

        val replayCheck = phase.indexOf("if replaying:")
        val runningCheck = phase.indexOf("if running:")
        assertTrue(replayCheck >= 0)
        assertTrue(runningCheck > replayCheck)
        assertTrue(phase.contains("if replaying:\n        return PHASE_REPLAY"))
        assertTrue(phase.contains("if running:\n        return PHASE_ROLL"))
    }

    @Test
    fun replayPhaseKeepsCinematicHierarchyAndSuppressesReadClutter() {
        val source = asset("presentation_focus_choreography.gd")
        val roles = source.substringAfter("func _focus_role_alpha(phase: String, role: String) -> float:")
            .substringBefore("func _focus_replay_progress")
        val replay = roles.substringAfter("PHASE_REPLAY:").substringBefore("PHASE_RESULT:")

        assertTrue(replay.contains("\"read\": return 0.0"))
        assertTrue(replay.contains("\"practice\": return 0.0"))
        assertTrue(replay.contains("\"replay_timeline\": return 1.0"))
        assertTrue(replay.contains("\"letterbox\": return 0.68"))
    }

    @Test
    fun replayFocusFixStaysPresentationOnly() {
        val source = asset("presentation_focus_choreography.gd")
        val phase = source.substringAfter("func _focus_phase_for(running: bool, replaying: bool, showing_result: bool) -> String:")
            .substringBefore("func _focus_role_alpha")

        // Guard actual authoritative/camera mutations, not harmless prose in comments.
        assertFalse(phase.contains("GreenTerrain("))
        assertFalse(phase.contains("GreenReadAdvisor("))
        assertFalse(phase.contains("score ="))
        assertFalse(phase.contains("ball.position ="))
        assertFalse(phase.contains("camera.position ="))
        assertFalse(phase.contains("camera.fov ="))
        assertFalse(phase.contains("_v171_replay_remaining ="))
    }
}
