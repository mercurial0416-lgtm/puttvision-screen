package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakLateLaunchAcquireRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun liveBreakCanAcquireLaunchVectorAfterRunningAlreadyStarted() {
        val script = asset("commercial_read_flow.gd")
        val apply = script.substringAfter("func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:")
        val shotStart = apply.substringAfter("if running and not _live_curve_was_running:").substringBefore("\n    if running:")
        val liveRoll = apply.substringAfter("\n    if running:").substringBefore("\n    elif _live_curve_was_running:")

        assertTrue(shotStart.contains("_live_curve_has_launch_vector = false"))
        assertTrue(shotStart.contains("_live_curve_launch_speed = 0.0"))
        assertFalse(shotStart.contains("_live_curve_forward = velocity.normalized()"))
        assertTrue(liveRoll.contains("if not _live_try_acquire_launch_vector(velocity):"))
        assertTrue(liveRoll.indexOf("_live_trace_accumulate_travel(ball_pos)") < liveRoll.indexOf("_live_try_acquire_launch_vector(velocity)"))
    }

    @Test
    fun zeroVelocityCannotPoisonBreakDirectionOrTrace() {
        val script = asset("commercial_read_flow.gd")
        val helper = script.substringAfter("func _live_try_acquire_launch_vector(velocity: Vector2) -> bool:")
            .substringBefore("\n# Make the authoritative roll response obvious")
        val waitingBranch = script.substringAfter("if not _live_try_acquire_launch_vector(velocity):")
            .substringBefore("var launch_right :=")

        assertTrue(helper.contains("speed < LIVE_LAUNCH_MIN_SPEED_MPS"))
        assertTrue(helper.contains("return false"))
        assertTrue(helper.indexOf("return false") < helper.indexOf("_live_curve_forward = velocity / speed"))
        assertFalse(waitingBranch.contains("_live_trace_push("))
        assertTrue(waitingBranch.contains("_live_curve_pace_label.text = \"PACE --\""))
    }

    @Test
    fun launchAcquisitionRemainsPresentationOnly() {
        val script = asset("commercial_read_flow.gd")

        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
    }
}
