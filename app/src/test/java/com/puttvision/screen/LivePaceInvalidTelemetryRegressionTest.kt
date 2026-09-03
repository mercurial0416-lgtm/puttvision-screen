package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePaceInvalidTelemetryRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun invalidLivePaceTelemetryClearsSurgeLatchBeforeNeutralFallback() {
        val script = asset("live_pace_surge.gd")
        val invalidGuard = script.indexOf("if not is_finite(current_speed) or not is_finite(launch_speed) or launch_speed <= 0.001:")
        val reset = script.indexOf("_live_pace_surging = false", invalidGuard)
        val fallback = script.indexOf("return \"PACE --\"", invalidGuard)

        assertTrue(invalidGuard >= 0)
        assertTrue(reset > invalidGuard)
        assertTrue(fallback > reset)
    }

    @Test
    fun livePaceRepairRemainsPresentationOnly() {
        val script = asset("live_pace_surge.gd")
        assertTrue(script.contains("extends \"res://terrain_relief_visibility.gd\""))
        assertTrue(script.contains("Authoritative ball velocity still comes from the"))
        assertTrue(!script.contains("GreenTerrain.set"))
        assertTrue(!script.contains("GreenReadAdvisor.set"))
        assertTrue(!script.contains("ballVelocity ="))
    }
}
