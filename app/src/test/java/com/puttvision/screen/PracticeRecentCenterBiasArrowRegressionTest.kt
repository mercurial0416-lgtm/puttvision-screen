package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRecentCenterBiasArrowRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun recentCentroidAddsDirectionalChevronWithoutChangingAuthoritativeSystems() {
        val source = asset("practice_recent_center_reticle.gd")
        val geometry = source.substringAfter("func _practice_recent_center_geometry(samples: Array[Vector2]) -> Dictionary:")
            .substringBefore("func _practice_recent_center_focus_samples")

        assertTrue(source.contains("PracticeRecentCenterArrow"))
        assertTrue(geometry.contains("var bias_delta := center - target_center"))
        assertTrue(geometry.contains("var direction := bias_delta.normalized()"))
        assertTrue(geometry.contains("\"arrow\": arrow_points"))
        assertFalse(geometry.contains("GreenTerrain"))
        assertFalse(geometry.contains("GreenReadAdvisor"))
        assertFalse(geometry.contains("score"))
    }

    @Test
    fun centeredRecentGroupDoesNotManufactureAnArrowDirection() {
        val source = asset("practice_recent_center_reticle.gd")
        val geometry = source.substringAfter("func _practice_recent_center_geometry(samples: Array[Vector2]) -> Dictionary:")
            .substringBefore("func _practice_recent_center_focus_samples")

        assertTrue(source.contains("const PRACTICE_RECENT_CENTER_ARROW_MIN_LENGTH_PX := 10.0"))
        assertTrue(geometry.contains("var arrow_points := PackedVector2Array()"))
        assertTrue(geometry.contains("if bias_delta.length() >= PRACTICE_RECENT_CENTER_ARROW_MIN_LENGTH_PX:"))
        assertTrue(geometry.indexOf("if bias_delta.length() >= PRACTICE_RECENT_CENTER_ARROW_MIN_LENGTH_PX:") < geometry.indexOf("bias_delta.normalized()"))
    }

    @Test
    fun hiddenOrShortBiasClearsArrowGeometryInsteadOfLeavingAStaleChevron() {
        val source = asset("practice_recent_center_reticle.gd")
        val refresh = source.substringAfter("func _practice_recent_center_refresh() -> void:")
            .substringBefore("func _v179_refresh")

        assertTrue(refresh.contains("var arrow_visible := marker_visible"))
        assertTrue(refresh.contains("_practice_recent_center_arrow.visible = arrow_visible"))
        assertTrue(refresh.contains("_practice_recent_center_arrow.points = PackedVector2Array()"))
    }
}
