package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V175ReplayArcLengthPacingTest {
    private fun asset(path: String): String {
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun replayCameraWalksPhysicalTrailDistanceInsteadOfSampleIndex() {
        val script = asset("v175_cinematic_replay.gd")

        assertTrue(script.contains("func _v175_trail_total_length(points: Array) -> float:"))
        assertTrue(script.contains("var target_distance := clampf(progress, 0.0, 1.0) * total_length"))
        assertTrue(script.contains("var segment_length := a.distance_to(b)"))
        assertTrue(script.contains("var local_t := clampf((target_distance - walked) / segment_length"))
        assertTrue(script.contains("var sample_progress := minf(0.22, sample_m / total_length)"))

        // Regression: sample-index interpolation causes speed surges when native trail spacing varies.
        assertFalse(script.contains("var scaled: float = clamp(progress, 0.0, 1.0) * float(points.size() - 1)"))
        assertFalse(script.contains("var index_a: int = clamp(int(floor(scaled))"))

        // Presentation-only change: never rewrite authoritative Android trail data.
        assertFalse(script.contains("_v171_replay_actual["))
        assertFalse(script.contains("actualTrail] ="))
    }

    @Test
    fun replayHeadingBlendsLocalAndWidePhysicalTangents() {
        val script = asset("v175_cinematic_replay.gd")

        assertTrue(script.contains("const V175_HEADING_SAMPLE_M := 0.18"))
        assertTrue(script.contains("const V175_HEADING_WIDE_SAMPLE_M := 0.42"))
        assertTrue(script.contains("func _v175_heading_vector(points: Array, progress: float, sample_m: float, total_length: float) -> Vector2:"))
        assertTrue(script.contains("var near_heading := _v175_heading_vector(points, progress, V175_HEADING_SAMPLE_M, total_length)"))
        assertTrue(script.contains("var wide_heading := _v175_heading_vector(points, progress, V175_HEADING_WIDE_SAMPLE_M, total_length)"))
        assertTrue(script.contains("var heading := near_heading * 0.68 + wide_heading * 0.32"))

        // Regression: don't collapse replay orientation back to one short tangent that can snap at corners.
        assertFalse(script.contains("var ahead := _v175_trail_point(points, min(1.0, progress + sample_progress))\n    var behind := _v175_trail_point(points, max(0.0, progress - sample_progress))\n    var heading: Vector2 = ahead - behind"))
    }
}
