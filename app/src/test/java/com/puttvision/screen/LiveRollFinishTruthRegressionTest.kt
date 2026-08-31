package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRollFinishTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun stoppedSnapshotFinalizesRestPositionPeakAndTravel() {
        val live = asset("live_roll_finish_truth.gd")
        assertTrue(live.contains("var was_running := _live_curve_was_running"))
        assertTrue(live.contains("if was_running and not running:"))
        assertTrue(live.contains("_finalize_live_roll_truth(s)"))
        assertTrue(live.contains("_live_trace_accumulate_travel(ball_pos)"))
        assertTrue(live.contains("_live_curve_peak_signed_cm = cross_track_cm"))
        assertTrue(live.contains("_live_curve_pace_label.text = _live_summary_pace_readout()"))
        assertTrue(live.contains("REST %s %.1f cm"))
    }

    @Test
    fun tvRoutesThroughTerminalTelemetryWithoutMutatingPhysics() {
        val scene = asset("v143_tv.tscn")
        val live = asset("live_roll_finish_truth.gd")
        assertTrue(scene.contains("res://live_roll_finish_truth.gd"))
        assertTrue(live.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(live.contains("Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative"))
        assertFalse(live.contains("GreenTerrain.set"))
        assertFalse(live.contains("GreenReadAdvisor.set"))
        assertFalse(live.contains("bridge."))
    }
}
