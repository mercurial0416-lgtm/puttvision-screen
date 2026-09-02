package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CupFocusCameraDeltaRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun cupFocusReusesBoundedCinematicDeltaGuardForEntireCameraRig() {
        val script = asset("v180_replay_cup_focus.gd")
        val parent = asset("v175_cinematic_replay.gd")

        assertTrue(parent.contains("const V175_MAX_CAMERA_DELTA_S := 0.10"))
        assertTrue(parent.contains("var safe_delta := minf(delta, V175_MAX_CAMERA_DELTA_S)"))

        assertTrue(script.contains("const V180_POSITION_RESPONSE := 7.2"))
        assertTrue(script.contains("const V180_LOOK_RESPONSE := 7.2"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V180_POSITION_RESPONSE)"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V180_LOOK_RESPONSE)"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V180_FOV_RESPONSE)"))

        assertFalse(script.contains("1.0 - exp(-delta * 7.2)"))
        assertFalse(script.contains("func _v180_damping_alpha("))
    }

    @Test
    fun cupFocusDeltaGuardStaysPresentationOnly() {
        val script = asset("v180_replay_cup_focus.gd")

        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
        assertTrue(script.contains("camera_pos = camera_pos.lerp(desired_pos, pos_alpha)"))
        assertTrue(script.contains("camera_look = camera_look.lerp(desired_look, look_alpha)"))
    }
}
