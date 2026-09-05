package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayProductionOverlayRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun productionReplayChainAdvancesPremiumOverlayAfterCameraTruthPass() {
        val bridge = asset("replay_production_overlay_bridge.gd")
        val nextLayer = asset("replay_stop_distance_clarity.gd")

        assertTrue(nextLayer.startsWith("extends \"res://replay_production_overlay_bridge.gd\""))
        assertTrue(bridge.startsWith("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(bridge.contains("func _focus_update_replay_timeline() -> void:\n    super._focus_update_replay_timeline()"))
        assertTrue(bridge.contains("var timing_valid := _replay_raw_timing_valid(_v171_replay_remaining, _v171_replay_duration)"))
        assertTrue(bridge.contains("var chapters := _production_replay_chapters(track_width)"))
        assertTrue(bridge.contains("V180_FOCUS_START"))
        assertTrue(bridge.contains("V180_FOCUS_FULL"))
        assertTrue(bridge.contains("_production_replay_apply_emphasis(progress, timing_valid)"))
        assertTrue(bridge.contains("_production_replay_update_progress(progress, chapters, timing_valid)"))
        assertTrue(bridge.contains("_replay_playhead.visible = timing_valid and _replay_track_has_playhead_room(track_width)"))
    }

    @Test
    fun replayTimelineMarksProductionCameraHandoffsWithoutInventingTiming() {
        val bridge = asset("replay_production_overlay_bridge.gd")

        assertTrue(bridge.contains("ReplayRollBlendHandoff"))
        assertTrue(bridge.contains("ReplayBlendCupHandoff"))
        assertTrue(bridge.contains("func _production_replay_update_handoff_markers(track_width: float, chapters: Dictionary, timing_valid: bool) -> void:"))
        assertTrue(bridge.contains("var roll_blend_x := clampf(roll.x + roll.y, 0.0, track_width)"))
        assertTrue(bridge.contains("var blend_cup_x := clampf(cup.x, 0.0, track_width)"))
        assertTrue(bridge.contains("_production_replay_update_handoff_markers(track_width, chapters, timing_valid)"))
        assertFalse(bridge.contains("GreenTerrain.set"))
        assertFalse(bridge.contains("GreenReadAdvisor.set"))
        assertFalse(bridge.contains("ballVelocity ="))
    }

    @Test
    fun replayHandoffMarkersFailClosedOnInvalidTimingNarrowTracksAndDegenerateChapters() {
        val bridge = asset("replay_production_overlay_bridge.gd")

        assertTrue(bridge.contains("const REPLAY_HANDOFF_MARKER_MIN_TRACK_WIDTH := 132.0"))
        assertTrue(bridge.contains("var chapter_geometry_valid := ("))
        assertTrue(bridge.contains("and roll.y > 0.0"))
        assertTrue(bridge.contains("and blend.y > 0.0"))
        assertTrue(bridge.contains("and cup.y > 0.0"))
        assertTrue(bridge.contains("timing_valid\n        and is_finite(track_width)"))
        assertTrue(bridge.contains("and track_width >= REPLAY_HANDOFF_MARKER_MIN_TRACK_WIDTH"))
        assertTrue(bridge.contains("and chapter_geometry_valid"))
        assertTrue(bridge.contains("_replay_roll_blend_handoff.visible = visible"))
        assertTrue(bridge.contains("_replay_blend_cup_handoff.visible = visible"))
        assertTrue(bridge.contains("if not visible:\n        return"))
    }
}
