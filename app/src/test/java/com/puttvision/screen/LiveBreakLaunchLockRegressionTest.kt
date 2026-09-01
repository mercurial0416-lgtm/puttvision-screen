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
    fun delayedVelocityFrameWithoutStartCoordinatesPreservesEstablishedOrigin() {
        val script = asset("replay_timeline_camera_truth.gd")
        val guard = "if s.has(\"startX\") and s.has(\"startY\"):\n        _live_curve_origin = Vector2"
        assertTrue(script.contains(guard))
        assertTrue(script.indexOf(guard) < script.indexOf("_live_curve_forward = velocity.normalized()"))
        assertTrue(script.contains("Delayed velocity frames are not guaranteed to repeat startX/startY"))
        assertTrue(script.contains("otherwise defaulting missing values to zero would rotate a correct axis around a fake origin"))
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
        assertTrue(script.contains("elif missing_ball_position:\n            _finalize_last_observed_live_roll_truth()"))
        assertTrue(script.contains("_live_curve_value.text = _live_last_observed_readout(cross_track_cm)"))
        assertTrue(script.contains("return \"LAST OBS CENTER\""))
        assertTrue(script.contains("return \"LAST OBS %s %.1f cm\""))
        assertTrue(script.contains("without pretending the last sample is exact rest"))
        assertFalse(script.contains("_live_curve_value.text = _live_finish_readout(cross_track_cm)\n    if _live_curve_peak_label != null:\n        _live_curve_peak_label.text = _live_peak_readout(_live_curve_peak_signed_cm)\n    if _live_curve_pace_label != null:\n        _live_curve_pace_label.text = _live_summary_pace_readout()\n\nfunc _finalize_last_observed_live_roll_truth"))
    }
}
