package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCameraFrameStallRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun stationaryAddressCameraBoundsSingleFrameHitches() {
        val source = asset("address_relief_camera.gd")

        assertTrue(source.contains("const ADDRESS_MAX_CAMERA_DELTA_S := 0.10"))
        assertTrue(source.contains("var safe_delta := minf(delta, ADDRESS_MAX_CAMERA_DELTA_S)"))
        assertTrue(source.contains("return 1.0 - exp(-safe_delta * response_rate)"))
        assertFalse(source.contains("return 1.0 - exp(-delta * response_rate)"))
    }

    @Test
    fun addressCameraStillRejectsInvalidAndBackwardTime() {
        val source = asset("address_relief_camera.gd")

        assertTrue(source.contains("not is_finite(delta) or delta <= 0.0"))
        assertTrue(source.contains("return 0.0"))
    }

    @Test
    fun addressCameraGuardRemainsPresentationOnly() {
        val source = asset("address_relief_camera.gd")

        assertFalse(source.contains("GreenTerrain("))
        assertFalse(source.contains("GreenReadAdvisor("))
        assertFalse(source.contains("s[\"ballSpeed\"] ="))
        assertFalse(source.contains("s[\"vx\"] ="))
        assertFalse(source.contains("s[\"vy\"] ="))
        assertTrue(source.contains("camera_pos = camera_pos.lerp(desired_pos, pos_alpha)"))
        assertTrue(source.contains("camera_look = camera_look.lerp(desired_look, look_alpha)"))
        assertTrue(source.contains("camera.fov = lerpf(camera.fov, desired_fov, fov_alpha)"))
    }
}
