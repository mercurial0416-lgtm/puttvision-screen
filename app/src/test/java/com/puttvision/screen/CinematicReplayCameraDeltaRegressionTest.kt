package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicReplayCameraDeltaRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun cinematicReplayCameraUsesOneBoundedDeltaGuardForPositionLookAndFov() {
        val script = asset("v175_cinematic_replay.gd")

        assertTrue(script.contains("const V175_MAX_CAMERA_DELTA_S := 0.10"))
        assertTrue(script.contains("func _v175_camera_damping_alpha(delta: float, response: float) -> float:"))
        assertTrue(script.contains("if not is_finite(delta) or delta <= 0.0 or not is_finite(response) or response <= 0.0:"))
        assertTrue(script.contains("var safe_delta := minf(delta, V175_MAX_CAMERA_DELTA_S)"))
        assertTrue(script.contains("return 1.0 - exp(-safe_delta * response)"))

        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V175_POSITION_RESPONSE)"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V175_LOOK_RESPONSE)"))
        assertTrue(script.contains("_v175_camera_damping_alpha(delta, V175_FOV_RESPONSE)"))

        assertFalse(script.contains("exp(-delta * 5.4)"))
        assertFalse(script.contains("exp(-delta * 6.4)"))
        assertFalse(script.contains("exp(-delta * V175_FOV_RESPONSE)"))
    }

    @Test
    fun replayCameraGuardIsPresentationOnlyAndDoesNotRewriteShotTruth() {
        val script = asset("v175_cinematic_replay.gd")

        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
        assertTrue(script.contains("camera_pos = camera_pos.lerp(desired_pos, pos_alpha)"))
        assertTrue(script.contains("camera_look = camera_look.lerp(desired_look, look_alpha)"))
    }
}
