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
        assertTrue(script.contains("V175_HEADING_SAMPLE_M / total_length"))

        // Regression: sample-index interpolation causes speed surges when native trail spacing varies.
        assertFalse(script.contains("var scaled: float = clamp(progress, 0.0, 1.0) * float(points.size() - 1)"))
        assertFalse(script.contains("var index_a: int = clamp(int(floor(scaled))"))

        // Presentation-only change: never rewrite authoritative Android trail data.
        assertFalse(script.contains("_v171_replay_actual["))
        assertFalse(script.contains("actualTrail] ="))
    }
}
