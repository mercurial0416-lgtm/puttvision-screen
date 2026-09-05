package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadStartGateClarityRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun startGateCarriesTheAuthoritativeAimInTvReadableLanguage() {
        val script = asset("commercial_read_apex.gd")
        assertTrue(script.contains("func _read_start_gate_descriptor(offset_m: float) -> String:"))
        assertTrue(script.contains("READ_START_GATE_DEADBAND_M := 0.015"))
        assertTrue(script.contains("\"START  %s %d cm\""))
        assertTrue(script.contains("\"RIGHT\" if offset_m > 0.0 else \"LEFT\""))
        assertTrue(script.contains("_read_start_gate_badge.text = _read_start_gate_descriptor(_v165_recommended_offset)"))
    }

    @Test
    fun startGateBadgeHasRoomAndStaysInsideTheOverviewMap() {
        val script = asset("commercial_read_apex.gd")
        assertTrue(script.contains("READ_START_GATE_BADGE_WIDTH := 120.0"))
        assertTrue(script.contains("V183_MAP_SIZE.x - READ_START_GATE_BADGE_WIDTH - 4.0"))
    }

    @Test
    fun previewExercisesTheSameActionableGateContract() {
        val preview = asset("commercial_read_apex_preview.gd")
        assertTrue(preview.contains("START  RIGHT 42 cm"))
        assertTrue(preview.contains("START  LEFT 42 cm"))
        assertTrue(preview.contains("START  CENTER"))
        assertTrue(preview.contains("COMMERCIAL_READ_START_GATE_ACTIONABLE_OK=1"))
        assertTrue(preview.contains("probe._read_start_gate_descriptor(_v165_recommended_offset)"))
    }

    @Test
    fun startGateClarityRemainsPresentationOnly() {
        val script = asset("commercial_read_apex.gd")
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
        assertFalse(script.contains("ballVelocity ="))
        assertFalse(script.contains("recommendedOffsetM ="))
    }
}
