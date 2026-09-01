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
    fun productionTimelineShowsPhysicalDistanceToRest() {
        val timeline = asset("replay_timeline_camera_truth.gd")
        assertTrue(timeline.contains("_focus_replay_stage_label.text = \"%s  ·  %s\""))
        assertTrue(timeline.contains("_focus_replay_roll_distance(progress)"))
        assertTrue(timeline.contains("REST"))
    }

    @Test
    fun distanceUsesRecordedActualTrailAndExistingReplayClock() {
        val timeline = asset("replay_timeline_camera_truth.gd")
        assertTrue(timeline.contains("_v171_replay_actual.size() >= 2"))
        assertTrue(timeline.contains("_v175_trail_total_length(_v171_replay_actual)"))
        assertTrue(timeline.contains("_focus_replay_progress(_v171_replay_remaining, _v171_replay_duration)"))
        assertTrue(timeline.contains("_focus_replay_roll_total_m * (1.0 - clampf(progress"))
    }

    @Test
    fun trailLengthIsCachedOnceWhenReplayActivates() {
        val timeline = asset("replay_timeline_camera_truth.gd")
        assertTrue(timeline.contains("if replay_active and not _focus_replay_roll_was_active:"))
        assertTrue(timeline.contains("_focus_replay_roll_total_m = _v175_trail_total_length(_v171_replay_actual)"))
        assertTrue(timeline.contains("_focus_replay_roll_was_active = replay_active"))
    }

    @Test
    fun statusLaneIsWidenedAndPreviewExercisesRestDistance() {
        val layout = asset("replay_roll_distance_layout.gd")
        val tvScene = asset("v143_tv.tscn")
        val previewScene = asset("v143_preview.tscn")
        assertTrue(layout.contains("const STATUS_WIDTH := 248.0"))
        assertTrue(layout.contains("PREVIEW_SAMPLE_REST := \"0.9m REST\""))
        assertTrue(layout.contains("set_process(false)"))
        assertTrue(tvScene.contains("res://replay_roll_distance_layout.gd"))
        assertTrue(previewScene.contains("res://replay_roll_distance_layout.gd"))
    }

    @Test
    fun timelineCannotMutateAuthoritativePuttingSystems() {
        val timeline = asset("replay_timeline_camera_truth.gd")
        val layout = asset("replay_roll_distance_layout.gd")
        assertFalse(timeline.contains("GreenTerrain.set"))
        assertFalse(timeline.contains("GreenReadAdvisor.set"))
        assertFalse(timeline.contains("ballVelocity ="))
        assertFalse(timeline.contains("readLineDeltaCm ="))
        assertFalse(timeline.contains("paceDeltaCm ="))
        assertFalse(layout.contains("GreenTerrain"))
        assertFalse(layout.contains("GreenReadAdvisor"))
    }
}
