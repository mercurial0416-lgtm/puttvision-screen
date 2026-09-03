package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadFinitePathRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun malformedPathPointsCannotBecomeLandmarkAnchorsOrApexBaseline() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("func _read_point_is_finite(point: Vector2) -> bool:"))
        assertTrue(source.contains("func _read_first_finite_point(curve: PackedVector2Array"))
        assertTrue(source.contains("func _read_last_finite_point(curve: PackedVector2Array"))
        assertTrue(source.contains("if not _read_point_is_finite(a) or not _read_point_is_finite(b):"))
        assertTrue(source.contains("var start := _read_first_finite_point(curve)"))
        assertTrue(source.contains("var finish := _read_last_finite_point(curve, start)"))
        assertTrue(source.contains("if not _read_point_is_finite(point) or not _read_point_is_finite(start) or not _read_point_is_finite(finish):"))
    }

    @Test
    fun degenerateMalformedPathFallsBackToFinitePresentationCoordinates() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("only_point if _read_point_is_finite(only_point) else Vector2.ZERO"))
        assertTrue(source.contains("return {\"point\": first_finite, \"tangent\": Vector2.UP}"))
        assertTrue(source.contains("return {\"point\": fallback_point, \"tangent\": fallback_tangent}"))
    }

    @Test
    fun finitePathGuardRemainsPresentationOnly() {
        val source = asset("read_landmark_spatial_spacing.gd")

        assertTrue(source.contains("extends \"res://practice_ring_boundary_finish.gd\""))
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("predictedTrail] ="))
        assertFalse(source.contains("actualTrail] ="))
    }
}
