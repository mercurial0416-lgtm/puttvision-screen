package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayCupDistanceClockRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun cupDistanceSamplesTheSameExactReplayClockAsTheTraceCamera() {
        val focus = asset("v180_replay_cup_focus.gd")
        val function = focus.substringAfter("func _v180_distance_to_cup_cm(progress: float) -> float:")
            .substringBefore("\nfunc _v180_composed_look_point")

        assertTrue(function.contains("_v175_trail_point(_v171_replay_actual, clampf(progress, 0.0, 1.0))"))
        assertFalse(function.contains("smoothstep"))

        val replay = asset("v175_cinematic_replay.gd")
        assertTrue(replay.contains("var current := _v175_trail_point(_v171_replay_actual, progress)"))
    }

    @Test
    fun replayTelemetryFixCannotMutateAuthoritativePuttingInputs() {
        val focus = asset("v180_replay_cup_focus.gd")
        assertFalse(focus.contains("GreenTerrain.set"))
        assertFalse(focus.contains("GreenReadAdvisor.set"))
        assertFalse(focus.contains("ballVelocity ="))
        assertFalse(focus.contains("readLineDeltaCm ="))
        assertFalse(focus.contains("paceDeltaCm ="))
    }
}
