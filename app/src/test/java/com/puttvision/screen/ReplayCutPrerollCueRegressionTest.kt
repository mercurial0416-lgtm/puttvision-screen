package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayCutPrerollCueRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun nextCameraCutGetsACompactVisualPrerollInsteadOfEtaTextOnly() {
        val source = asset("replay_cut_preroll_cue.gd")

        assertTrue(source.contains("const PREROLL_FRACTION := 0.105"))
        assertTrue(source.contains("NEXT · BLEND"))
        assertTrue(source.contains("NEXT · CUP"))
        assertTrue(source.contains("ReplayNextCutCue"))
        assertTrue(source.contains("ReplayNextCutLabel"))
        assertTrue(source.contains("var intensity := clampf(1.0 - distance / PREROLL_FRACTION"))
    }

    @Test
    fun cueUsesTheExistingReplayClockAndChapterThresholdsWithoutMutatingCameraTiming() {
        val source = asset("replay_cut_preroll_cue.gd")

        assertTrue(source.contains("_root.get(\"_v171_replay_remaining\")"))
        assertTrue(source.contains("_root.get(\"_v171_replay_duration\")"))
        assertTrue(source.contains("const CHAPTER_BLEND := 0.72"))
        assertTrue(source.contains("const CHAPTER_CUP := 0.90"))
        assertFalse(source.contains("_v171_replay_remaining ="))
        assertFalse(source.contains("_v171_replay_duration ="))
        assertFalse(source.contains("Camera3D.new()"))
        assertFalse(source.contains("current ="))
    }

    @Test
    fun invalidOrInactiveReplayFailsClosedAndNarrowTracksHideCopy() {
        val source = asset("replay_cut_preroll_cue.gd")

        assertTrue(source.contains("if not is_finite(remaining) or not is_finite(duration) or remaining <= 0.0 or duration <= 0.05:"))
        assertTrue(source.contains("_hide_preroll()"))
        assertTrue(source.contains("_label.visible = _track.size.x >= 180.0"))
        assertTrue(source.contains("if distance > PREROLL_FRACTION:"))
    }

    @Test
    fun prerollIsPresentationOnlyAndWiredIntoTvAndPreview() {
        val source = asset("replay_cut_preroll_cue.gd")
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")

        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("score ="))
        assertFalse(source.contains("ball.position"))
        assertTrue(tv.contains("res://replay_cut_preroll_cue.gd"))
        assertTrue(preview.contains("res://replay_cut_preroll_cue.gd"))
    }
}
