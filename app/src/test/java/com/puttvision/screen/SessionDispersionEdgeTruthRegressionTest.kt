package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionEdgeTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private fun constant(source: String, name: String): Double {
        val match = Regex("const\\s+${Regex.escape(name)}\\s*:=\\s*([0-9.]+)").find(source)
            ?: error("Missing constant $name")
        return match.groupValues[1].toDouble()
    }

    @Test
    fun clippedMissShapeExplainsWhichActualPlotBoundaryWasExceeded() {
        val base = asset("v179_session_dispersion.gd")
        val script = asset("session_dispersion_edge_truth.gd")

        assertEquals(constant(base, "V179_LINE_SCALE_CM"), constant(script, "SESSION_VIEW_LINE_SCALE_CM"), 0.0001)
        assertEquals(constant(base, "V179_PACE_SCALE_CM"), constant(script, "SESSION_VIEW_PACE_SCALE_CM"), 0.0001)
        assertTrue(script.contains("func _session_dispersion_clip_axes(sample: Vector2) -> Vector2i:"))
        assertTrue(script.contains("absf(sample.x) > SESSION_VIEW_LINE_SCALE_CM"))
        assertTrue(script.contains("absf(sample.y) > SESSION_VIEW_PACE_SCALE_CM"))
        assertTrue(script.contains("return Vector2(SESSION_EDGE_BAR_THIN, SESSION_EDGE_BAR_LONG)"))
        assertTrue(script.contains("return Vector2(SESSION_EDGE_BAR_LONG, SESSION_EDGE_BAR_THIN)"))
        assertTrue(script.contains("return SESSION_CORNER_SIZE"))
        assertFalse(script.contains("const SESSION_LINE_SCALE_CM := 5.0"))
        assertFalse(script.contains("const SESSION_PACE_SCALE_CM := 15.0"))
    }

    @Test
    fun makeWindowRemainsIndependentFromViewportClipping() {
        val base = asset("v179_session_dispersion.gd")
        val script = asset("session_dispersion_edge_truth.gd")

        assertEquals(5.0, constant(base, "V179_MAKE_LINE_CM"), 0.0001)
        assertEquals(15.0, constant(base, "V179_MAKE_PACE_CM"), 0.0001)
        assertTrue(constant(script, "SESSION_VIEW_LINE_SCALE_CM") > constant(base, "V179_MAKE_LINE_CM"))
        assertTrue(constant(script, "SESSION_VIEW_PACE_SCALE_CM") > constant(base, "V179_MAKE_PACE_CM"))
    }

    @Test
    fun helperReusesExistingMarkersAndOnlyRunsWhenSamplesChange() {
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(script.contains("if samples == _last_samples:"))
        assertTrue(script.contains("_last_samples = samples.duplicate()"))
        assertTrue(script.contains("dot.size = _session_dispersion_clipped_size(sample)"))
        assertTrue(script.contains("root.call(\"_v179_plot_position\", sample) - dot.size * 0.5"))
        assertFalse(script.contains("ColorRect.new()"))
        assertFalse(script.contains("_v179_samples.append"))
    }

    @Test
    fun unchangedPracticeHistoryDoesNotAllocateAStringEveryFrame() {
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(script.contains("var _last_samples: Array = []"))
        assertTrue(script.contains("if samples == _last_samples:"))
        assertTrue(script.contains("_last_samples = samples.duplicate()"))
        assertFalse(script.contains("str(samples)"))
        assertFalse(script.contains("var signature :="))
    }

    @Test
    fun mainScenePreservesEstablishedRootAndAddsPresentationOnlyHelper() {
        val scene = asset("v143_tv.tscn")
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(scene.contains("res://session_dispersion_edge_truth.gd"))
        assertTrue(scene.contains("[node name=\"SessionDispersionEdgeTruth\" type=\"Node\" parent=\".\"]"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
