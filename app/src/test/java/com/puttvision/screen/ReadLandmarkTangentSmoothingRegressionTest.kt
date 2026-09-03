package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadLandmarkTangentSmoothingRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun directionalReadLandmarksUseSpatiallySmoothedTangents() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("const READ_TANGENT_SAMPLE_FRACTION := 0.025"))
        assertTrue(source.contains("func _read_smoothed_tangent(curve: PackedVector2Array, fraction: float, fallback: Vector2) -> Vector2:"))
        assertTrue(source.contains("_read_smoothed_tangent(curve, READ_LAUNCH_FRACTION, sample[\"tangent\"])"))
        assertTrue(source.contains("_read_smoothed_tangent(curve, READ_START_GATE_FRACTION, sample[\"tangent\"])"))
        assertTrue(source.contains("_read_smoothed_tangent(curve, p, sample[\"tangent\"])"))
    }

    @Test
    fun tangentSmoothingDoesNotMoveAuthoritativeReadAnchors() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("var tip: Vector2 = sample[\"point\"]"))
        assertTrue(source.contains("var center: Vector2 = sample[\"point\"]"))
        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("s[\"ballSpeed\"] ="))
        assertFalse(source.contains("s[\"vx\"] ="))
        assertFalse(source.contains("s[\"vy\"] ="))
    }

    @Test
    fun malformedOrDegenerateTangentNeighborhoodFallsBackSafely() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("if curve.size() < 3 or not is_finite(fraction):"))
        assertTrue(source.contains("if not is_finite(chord.x) or not is_finite(chord.y) or chord.length_squared() <= READ_SPATIAL_EPSILON:"))
        assertTrue(source.contains("return fallback"))
    }
}
