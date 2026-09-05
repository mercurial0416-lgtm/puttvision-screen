package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayChapterEmphasisRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun replayTimelineEmphasizesOnlyTheCurrentCameraChapter() {
        val script = asset("replay_playhead_polish.gd")

        assertTrue(script.contains("func _replay_active_chapter(progress: float) -> int:"))
        assertTrue(script.contains("if not is_finite(progress):\n        return -1"))
        assertTrue(script.contains("if safe_progress < REPLAY_CUP_CHAPTER_START:"))
        assertTrue(script.contains("if safe_progress < REPLAY_CUP_CHAPTER_FULL:"))
        assertTrue(script.contains("func _replay_apply_chapter_emphasis(progress: float) -> void:"))
        assertTrue(script.contains("var is_active := index == active"))
        assertTrue(script.contains("REPLAY_CHAPTER_ACTIVE_FONT_SIZE if is_active else REPLAY_CHAPTER_FONT_SIZE"))
        assertTrue(script.contains("active_colors[index] if is_active else inactive_colors[index]"))
        assertTrue(script.contains("_replay_apply_chapter_emphasis(progress)"))
    }

    @Test
    fun replayTimelineShowsTruthfulWithinChapterProgressWithoutTouchingTiming() {
        val script = asset("replay_playhead_polish.gd")

        assertTrue(script.contains("ReplayChapterProgress"))
        assertTrue(script.contains("func _replay_chapter_local_progress(progress: float) -> float:"))
        assertTrue(script.contains("if not is_finite(progress):\n        return -1.0"))
        assertTrue(script.contains("chapter_start = REPLAY_CUP_CHAPTER_START"))
        assertTrue(script.contains("chapter_end = REPLAY_CUP_CHAPTER_FULL"))
        assertTrue(script.contains("chapter_start = REPLAY_CUP_CHAPTER_FULL"))
        assertTrue(script.contains("chapter_end = 1.0"))
        assertTrue(script.contains("return clampf((safe_progress - chapter_start) / chapter_span, 0.0, 1.0)"))
        assertTrue(script.contains("segment.y >= REPLAY_CHAPTER_PROGRESS_MIN_WIDTH"))
        assertTrue(script.contains("segment.y * local_progress"))
        assertTrue(script.contains("_replay_update_chapter_progress(progress, chapters)"))
        assertTrue(script.contains("Presentation-only replay timeline polish"))
    }
}
