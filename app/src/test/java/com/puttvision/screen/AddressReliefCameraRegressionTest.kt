package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressReliefCameraRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun stationaryAddressCameraUsesLowTerrainGroundedPerspective() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("ADDRESS_CAMERA_HEIGHT := 0.30"))
        assertTrue(script.contains("ADDRESS_CAMERA_TRAIL := 1.08"))
        assertTrue(script.contains("_v166_sample(camera_xz.x, -camera_xz.y).x"))
        assertTrue(script.contains("_v166_sample(look_xz.x, -look_xz.y).x"))
        assertTrue(script.contains("running or phase != \"NONE\""))
        assertTrue(script.contains("_v171_replay_remaining > 0.0"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
    }

    @Test
    fun tvSceneUsesReliefAwareCameraTopLayer() {
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://address_relief_camera.gd"))
    }

    @Test
    fun forwardMobileRendererRemainsEnabled() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
