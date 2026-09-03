package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOriginTruthGuardRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun malformedLaunchOriginUsesFirstFiniteMeasuredBallForPresentation() {
        val script = asset("live_origin_truth_guard.gd")
        assertTrue(script.contains("_live_origin_pending = not _live_pair_is_finite(s, \"startX\", \"startY\")"))
        assertTrue(script.contains("_live_pair_is_finite(s, \"ballX\", \"ballY\")"))
        assertTrue(script.contains("_live_curve_origin = ball_pos"))
        assertTrue(script.contains("presentation_snapshot[\"startX\"] = ball_pos.x"))
        assertTrue(script.contains("presentation_snapshot[\"startY\"] = ball_pos.y"))
    }

    @Test
    fun invalidOriginNeverFallsBackToWorldOriginBeforeRealBallSample() {
        val script = asset("live_origin_truth_guard.gd")
        assertTrue(script.contains("if running and _live_origin_pending and _live_pair_is_finite"))
        assertFalse(script.contains("_live_curve_origin = Vector2.ZERO"))
        assertFalse(script.contains("presentation_snapshot[\"startX\"] = 0"))
        assertFalse(script.contains("presentation_snapshot[\"startY\"] = 0"))
    }

    @Test
    fun productionSceneUsesGuardAndPhysicsRemainOutsidePresentationLayer() {
        val scene = asset("v143_tv.tscn")
        val script = asset("live_origin_truth_guard.gd")
        assertTrue(scene.contains("res://live_origin_truth_guard.gd"))
        assertTrue(script.contains("extends \"res://presentation_telemetry_guard.gd\""))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("V135RigidBallPhysics("))
        assertFalse(script.contains("V137RollingResistance("))
    }
}
