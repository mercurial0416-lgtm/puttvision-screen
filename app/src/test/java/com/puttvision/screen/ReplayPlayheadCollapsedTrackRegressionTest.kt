package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPlayheadCollapsedTrackRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun replayPlayheadRequiresValidTimingAndRoomForItsRotatedFootprint() {
        val script = asset("replay_playhead_polish.gd")

        assertTrue(script.contains("func _replay_playhead_rotated_extent() -> float:"))
        assertTrue(script.contains("func _replay_track_has_playhead_room(track_width: float) -> bool:"))
        assertTrue(script.contains("return track_width >= _replay_playhead_rotated_extent()"))
        assertTrue(script.contains("_replay_playhead.visible = timing_valid and _replay_track_has_playhead_room(track_width)"))
        assertTrue(script.contains("if not _replay_playhead.visible:"))
    }
}
