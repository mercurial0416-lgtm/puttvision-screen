package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDispersionEdgeTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun clippedMissShapeExplainsWhichPlotBoundaryWasExceeded() {
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(script.contains("func _session_dispersion_clip_axes(sample: Vector2) -> Vector2i:"))
        assertTrue(script.contains("1 if absf(sample.x) > V179_LINE_SCALE_CM else 0"))
        assertTrue(script.contains("1 if absf(sample.y) > V179_PACE_SCALE_CM else 0"))
        assertTrue(script.contains("return Vector2(SESSION_EDGE_BAR_THIN, SESSION_EDGE_BAR_LONG)"))
        assertTrue(script.contains("return Vector2(SESSION_EDGE_BAR_LONG, SESSION_EDGE_BAR_THIN)"))
        assertTrue(script.contains("return SESSION_CLIPPED_DOT_SIZE"))
    }

    @Test
    fun edgeBarsReuseExistingMarkersAndKeepPlotCoordinatesTruthful() {
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(script.contains("super._v179_refresh()"))
        assertTrue(script.contains("dot.size = _session_dispersion_clipped_size(sample)"))
        assertTrue(script.contains("dot.position = _v179_plot_position(sample) - dot.size * 0.5"))
        assertFalse(script.contains("ColorRect.new()"))
        assertFalse(script.contains("_v179_samples.append"))
    }

    @Test
    fun mainSceneUsesTheDirectionalTruthLayerWithoutPhysicsMutation() {
        val scene = asset("v143_tv.tscn")
        val script = asset("session_dispersion_edge_truth.gd")
        assertTrue(scene.contains("res://session_dispersion_edge_truth.gd"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
