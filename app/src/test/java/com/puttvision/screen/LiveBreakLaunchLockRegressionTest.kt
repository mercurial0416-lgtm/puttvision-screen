package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBreakLaunchLockRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun zeroVelocityRunningFrameDoesNotLockFakeUpAxis() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("const LIVE_LAUNCH_LOCK_MIN_SPEED_MPS := 0.03"))
        assertTrue(script.contains("_live_launch_lock_pending = not _live_launch_velocity_is_trustworthy(launch_velocity)"))
        assertTrue(script.contains("_suppress_unlocked_live_break()"))
        assertTrue(script.contains("_live_curve_value.text = \"TRACKING\""))
        assertFalse(script.contains("_live_curve_forward = Vector2.UP"))
    }

    @Test
    fun firstTrustworthyVelocityRelocksLiveTelemetryWithoutChangingPhysics() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("_live_curve_forward = velocity.normalized()"))
        assertTrue(script.contains("_live_curve_launch_speed = velocity.length()"))
        assertTrue(script.contains("_live_trace_push(cross_track_cm, _live_curve_travel_m)"))
        assertTrue(script.contains("_live_launch_lock_pending = false"))
        assertTrue(script.contains("This only repairs HUD orientation"))
        assertFalse(script.contains("GreenTerrain.set"))
        assertFalse(script.contains("GreenReadAdvisor.set"))
    }

    @Test
    fun invalidVelocityCannotBecomeTrustworthyLaunchVector() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("is_finite(velocity.x) and is_finite(velocity.y)"))
        assertTrue(script.contains("velocity.length_squared() >= LIVE_LAUNCH_LOCK_MIN_SPEED_MPS * LIVE_LAUNCH_LOCK_MIN_SPEED_MPS"))
    }

    @Test
    fun rollThatNeverGetsLaunchLockCannotReusePreviousShotAxisAtFinish() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("var launch_lock_was_pending := _live_launch_lock_pending"))
        assertTrue(script.contains("if launch_lock_was_pending:\n            _finalize_unlocked_live_break()"))
        assertTrue(script.contains("_live_curve_value.text = \"REST --\""))
        assertTrue(script.contains("_live_curve_trace.clear_points()"))
        assertTrue(script.contains("Never turn that stale state into a"))
    }

    @Test
    fun terminalFrameWithoutCoordinatesCannotPresentRunningSampleAsFinalRest() {
        val script = asset("replay_timeline_camera_truth.gd")
        assertTrue(script.contains("if not s.has(\"ballX\") or not s.has(\"ballY\"):\n        # A terminal bridge frame"))
        assertTrue(script.contains("_finalize_unlocked_live_break()\n        return"))
        assertTrue(script.contains("misrepresent an in-flight sample as the final rest"))
    }
}
