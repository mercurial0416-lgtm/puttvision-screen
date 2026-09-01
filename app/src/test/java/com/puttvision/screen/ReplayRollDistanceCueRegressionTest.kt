package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRollDistanceCueRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun tvSceneWiresPhysicalReplayDistanceCue() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://replay_roll_distance_cue.gd"))
        assertTrue(scene.contains("ReplayRollDistanceCue"))
    }

    @Test
    fun cueUsesRecordedActualTrailAndExactReplayClock() {
        val cue = asset("replay_roll_distance_cue.gd")
        assertTrue(cue.contains("_root.get(\"_v171_replay_actual\")"))
        assertTrue(cue.contains("_root.call(\"_v175_replay_progress\")"))
        assertTrue(cue.contains("_trail_length_m * (1.0 - clamped)"))
        assertTrue(cue.contains("TO REST"))
    }

    @Test
    fun trailLengthIsCachedOnlyWhenReplayActivates() {
        val cue = asset("replay_roll_distance_cue.gd")
        assertTrue(cue.contains("if active and not _was_active:"))
        assertTrue(cue.contains("_trail_length_m = _trail_total_length(points)"))
        assertFalse(cue.substringAfter("func _format_remaining").substringBefore("func _process").contains("_trail_total_length("))
    }

    @Test
    fun cueCannotMutateAuthoritativePuttingSystems() {
        val cue = asset("replay_roll_distance_cue.gd")
        assertFalse(cue.contains("GreenTerrain.set"))
        assertFalse(cue.contains("GreenReadAdvisor.set"))
        assertFalse(cue.contains("ballVelocity ="))
        assertFalse(cue.contains("readLineDeltaCm ="))
        assertFalse(cue.contains("paceDeltaCm ="))
    }
}
