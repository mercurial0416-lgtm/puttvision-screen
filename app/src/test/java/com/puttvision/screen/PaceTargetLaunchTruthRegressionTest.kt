package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTargetLaunchTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun paceTargetKeepsFirstTrustworthyMeasuredLaunchAfterStop() {
        val script = asset("v181_pace_target.gd")

        assertTrue(script.contains("var _v181_launch_speed := 0.0"))
        assertTrue(script.contains("var _v181_was_running := false"))
        assertTrue(script.contains("if running and not _v181_was_running:"))
        assertTrue(script.contains("if running and _v181_launch_speed <= V181_LAUNCH_LOCK_MIN_MPS"))
        assertTrue(script.contains("_v181_launch_speed = ball_speed"))
        assertTrue(script.contains("_v181_capture_launch(running, sampled_speed)"))
        assertTrue(script.contains("_v181_fill.size.x = V181_BAR_W * clampf(_v181_launch_speed / 5.0"))
        assertFalse(script.contains("_v181_fill.size.x = V181_BAR_W * clampf(ball_speed / 5.0"))
    }

    @Test
    fun paceTargetDoesNotTreatStopPacketZeroAsMeasuredLaunch() {
        val script = asset("v181_pace_target.gd")

        val capture = script.substringAfter("func _v181_capture_launch").substringBefore("func _build_hud")
        assertTrue(capture.contains("running and _v181_launch_speed <= V181_LAUNCH_LOCK_MIN_MPS"))
        assertTrue(capture.contains("is_finite(ball_speed)"))
        assertTrue(capture.contains("ball_speed > V181_LAUNCH_LOCK_MIN_MPS"))
        assertFalse(capture.contains("if not running:\n        _v181_launch_speed = 0.0"))
    }

    @Test
    fun paceTargetAddsImmediateSoftOnPaceFirmCoachingWithoutPhysicsWrites() {
        val script = asset("v181_pace_target.gd")

        assertTrue(script.contains("V181_ON_PACE_LOW_RATIO := 0.92"))
        assertTrue(script.contains("V181_ON_PACE_HIGH_RATIO := 1.08"))
        assertTrue(script.contains("return \"SOFT\""))
        assertTrue(script.contains("return \"FIRM\""))
        assertTrue(script.contains("return \"ON PACE\""))
        assertTrue(script.contains("green = measured launch"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("s[\"ballSpeed\"] ="))
        assertFalse(script.contains("s[\"vx\"] ="))
        assertFalse(script.contains("s[\"vy\"] ="))
    }
}
