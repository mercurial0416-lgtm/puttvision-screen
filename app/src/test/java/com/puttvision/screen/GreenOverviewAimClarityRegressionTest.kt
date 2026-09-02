package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenOverviewAimClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun overviewShowsAdvisorAimAsDirectionAndCentimeters() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("return \"AIM %s %d cm\""))
        assertTrue(source.contains("var direction := \"RIGHT\" if offset_m > 0.0 else \"LEFT\""))
        assertFalse(source.contains("var direction := \"R\" if offset_m > 0.0 else \"L\""))
        assertTrue(source.contains("int(round(absf(offset_m) * 100.0))"))
        assertTrue(source.contains("return \"AIM CENTER\""))
    }

    @Test
    fun overviewAimMarkerUsesSameRecommendationSignAndScaleAsReadPath() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("offset_m / OVERVIEW_AIM_VISUAL_SPAN_M"))
        assertTrue(source.contains("center_x + normalized * OVERVIEW_AIM_VISUAL_SPAN_PX"))
        assertTrue(source.contains("var offset := _v165_recommended_offset"))
        assertTrue(source.contains("_overview_aim_marker.position = _overview_aim_target_position(offset)"))
        assertTrue(source.contains("absf(offset) >= OVERVIEW_AIM_DEADBAND_M"))
    }

    @Test
    fun overviewFooterYieldsSpaceToActionableAimWithoutTouchingPhysics() {
        val source = asset("green_read_direction_truth.gd")
        assertTrue(source.contains("label.text = \"gold read  •  teal fall\""))
        assertTrue(source.contains("GreenOverviewAimReadout"))
        assertTrue(source.contains("GreenOverviewAimTarget"))
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("paceDeltaCm ="))
    }
}
