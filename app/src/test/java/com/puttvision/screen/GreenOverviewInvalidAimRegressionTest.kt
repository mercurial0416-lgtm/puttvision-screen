package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenOverviewInvalidAimRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path")
    }

    @Test
    fun invalidAdvisorOffsetKeepsOverviewNeutralAndMarkerHidden() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("func _overview_aim_is_valid(offset_m: float) -> bool:"))
        assertTrue(source.contains("return is_finite(offset_m)"))
        assertTrue(source.contains("return \"AIM --\""))
        assertTrue(source.contains("active and valid and absf(offset) >= OVERVIEW_AIM_DEADBAND_M"))
    }

    @Test
    fun invalidOffsetNeverReachesClampOrOffMapDirectionMath() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("if not _overview_aim_is_valid(offset_m):"))
        assertTrue(source.contains("return _overview_aim_is_valid(offset_m) and absf(offset_m) > OVERVIEW_AIM_VISUAL_SPAN_M"))
    }
}
