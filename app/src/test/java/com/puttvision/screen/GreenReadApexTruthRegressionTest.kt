package com.puttvision.screen

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadApexTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    private data class P(val x: Double, val y: Double)

    private fun baselineDeviation(point: P, start: P, finish: P): Double {
        val bx = finish.x - start.x
        val by = finish.y - start.y
        val length = kotlin.math.sqrt(bx * bx + by * by)
        if (length <= 0.0001) return 0.0
        return abs(bx * (point.y - start.y) - by * (point.x - start.x)) / length
    }

    @Test
    fun asymmetricLateBreakUsesMaximumDepartureInsteadOfMidpoint() {
        val curve = listOf(
            P(0.0, 0.0),
            P(10.0, 0.6),
            P(20.0, 2.2),
            P(30.0, 8.4),
            P(40.0, 13.0),
            P(50.0, 0.0)
        )
        val start = curve.first()
        val finish = curve.last()
        val apexIndex = (1 until curve.lastIndex).maxBy { baselineDeviation(curve[it], start, finish) }

        assertEquals(4, apexIndex)
        assertTrue(baselineDeviation(curve[apexIndex], start, finish) > baselineDeviation(curve[2], start, finish))
    }

    @Test
    fun productionScriptScansInteriorVerticesForStrongestBreak() {
        val script = asset("read_landmark_spatial_spacing.gd")
        assertTrue(script.contains("func _read_baseline_deviation"))
        assertTrue(script.contains("for index in range(1, curve.size() - 1):"))
        assertTrue(script.contains("if is_finite(deviation) and deviation > best_deviation:"))
        assertTrue(script.contains("best_point = curve[index]"))
        assertFalse(script.contains("return _read_path_sample(curve, 0.5)[\"point\"] as Vector2\n\nfunc _read_launch_geometry"))
    }

    @Test
    fun nearStraightAndDegenerateReadsKeepStableMidpointFallback() {
        val script = asset("read_landmark_spatial_spacing.gd")
        assertTrue(script.contains("const READ_APEX_MIN_DEVIATION_PX := 0.5"))
        assertTrue(script.contains("if start.distance_to(finish) <= READ_SPATIAL_EPSILON:"))
        assertTrue(script.contains("if best_deviation < READ_APEX_MIN_DEVIATION_PX:"))
        assertTrue(script.contains("return _read_path_sample(curve, 0.5)[\"point\"] as Vector2"))
    }

    @Test
    fun apexCorrectionRemainsPresentationOnly() {
        val script = asset("read_landmark_spatial_spacing.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
