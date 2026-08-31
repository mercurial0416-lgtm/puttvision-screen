package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTrendStableWindowRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun stableTrendWindowIsReachableWithinBoundedSessionHistory() {
        val dispersion = asset("v179_session_dispersion.gd")
        val trend = asset("practice_trend_vector.gd")

        assertTrue(dispersion.contains("const V179_HISTORY := 5"))
        assertTrue(trend.contains("const PRACTICE_TREND_STABLE_GROUP_SIZE := 3"))
        assertTrue(trend.contains("const PRACTICE_TREND_STABLE_MIN_SAMPLES := 5"))
        assertTrue(trend.contains("samples.size() >= PRACTICE_TREND_STABLE_MIN_SAMPLES"))
        assertFalse(trend.contains("const PRACTICE_TREND_STABLE_MIN_SAMPLES := 6"))
    }

    @Test
    fun stabilizationRemainsPresentationOnlyAndForwardMobileBounded() {
        val trend = asset("practice_trend_vector.gd")

        assertTrue(trend.contains("PRACTICE_TREND_GROUP_SIZE := 2"))
        assertTrue(trend.contains("PRACTICE_TREND_STABLE_GROUP_SIZE := 3"))
        assertFalse(trend.contains("GreenTerrain.set"))
        assertFalse(trend.contains("GreenReadAdvisor.set"))
        assertFalse(trend.contains("ballVelocity ="))
        assertFalse(trend.contains("readLineDeltaCm ="))
        assertFalse(trend.contains("paceDeltaCm ="))
    }
}
