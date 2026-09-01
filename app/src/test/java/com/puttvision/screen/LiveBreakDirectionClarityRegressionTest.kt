package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakDirectionClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun replayTruthExpandsTerseDirectionTokensForTvReadability() {
        val script = asset("replay_direction_clarity.gd")
        assertTrue(script.contains("extends \"res://replay_timeline_camera_truth.gd\""))
        assertTrue(script.contains("REST RIGHT"))
        assertTrue(script.contains("REST LEFT"))
        assertTrue(script.contains("LAST OBS RIGHT"))
        assertTrue(script.contains("LAST OBS LEFT"))
        assertTrue(script.contains("PEAK RIGHT"))
        assertTrue(script.contains("PEAK LEFT"))
        assertTrue(script.contains("return \"RIGHT \" + text.substr(2)"))
        assertTrue(script.contains("return \"LEFT \" + text.substr(2)"))
    }

    @Test
    fun replayTruthPreservesParentValuesAndChangesSemanticsOnly() {
        val script = asset("replay_direction_clarity.gd")
        assertTrue(script.contains("super._live_curve_readout(cross_track_cm)"))
        assertTrue(script.contains("super._live_peak_readout(peak_signed_cm)"))
        assertTrue(script.contains("super._live_finish_readout(cross_track_cm)"))
        assertTrue(script.contains("super._live_last_observed_readout(cross_track_cm)"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("V135RigidBallPhysics"))
        assertFalse(script.contains("V137RollingResistance"))
    }

    @Test
    fun tvUsesDirectClarityTruthAndPreviewMirrorsIt() {
        val tv = asset("v143_tv.tscn")
        val preview = asset("v143_preview.tscn")
        val previewScript = asset("replay_direction_clarity_preview.gd")
        assertTrue(tv.contains("res://replay_direction_clarity.gd"))
        assertFalse(tv.contains("live_break_direction_clarity.gd"))
        assertTrue(preview.contains("res://replay_direction_clarity_preview.gd"))
        assertTrue(previewScript.contains("extends \"res://session_dispersion_readability_preview.gd\""))
        assertTrue(previewScript.contains("super._live_curve_readout(cross_track_cm)"))
        assertTrue(previewScript.contains("super._live_peak_readout(peak_signed_cm)"))
    }
}
