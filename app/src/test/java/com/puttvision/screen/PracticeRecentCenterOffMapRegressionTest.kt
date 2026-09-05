package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRecentCenterOffMapRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun validOffMapCentroidKeepsTruthfulReadoutWithoutClampedMarker() {
        val source = asset("practice_recent_center_reticle.gd")
        val geometry = source.substringAfter("func _practice_recent_center_geometry(samples: Array[Vector2]) -> Dictionary:")
            .substringBefore("func _practice_recent_center_focus_samples")
        val refresh = source.substringAfter("func _practice_recent_center_refresh() -> void:")
            .substringBefore("func _v179_refresh")

        assertTrue(geometry.contains("return {\"visible\": false, \"clipped\": true, \"sample\": centroid}"))
        assertTrue(refresh.contains("var marker_visible := bool(geometry.get(\"visible\", false))"))
        assertTrue(refresh.contains("var readout_visible := sample_variant is Vector2"))
        assertTrue(refresh.contains("readout += \" · OFF MAP\""))
        assertTrue(refresh.indexOf("if not marker_visible:") < refresh.indexOf("_practice_recent_center_bias.points = geometry[\"bias\"]"))
    }

    @Test
    fun malformedCentroidStillFailsClosed() {
        val source = asset("practice_recent_center_reticle.gd")
        val geometry = source.substringAfter("func _practice_recent_center_geometry(samples: Array[Vector2]) -> Dictionary:")
            .substringBefore("func _practice_recent_center_focus_samples")

        assertTrue(geometry.contains("if not is_finite(sample.x) or not is_finite(sample.y):"))
        assertTrue(geometry.contains("return {\"visible\": false}"))
        assertFalse(geometry.contains("clampf(centroid"))
        assertFalse(geometry.contains("clamp(centroid"))
    }
}
