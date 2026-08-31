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
    fun addressCameraFocusesRealMacroReliefInsteadOfOnlyFixedMidpoint() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("ADDRESS_RELIEF_SAMPLES := 5"))
        assertTrue(script.contains("terrain_h - chord_h"))
        assertTrue(script.contains("best_fraction"))
        assertTrue(script.contains("ADDRESS_RELIEF_FOCUS_BLEND"))
        assertTrue(script.contains("look_fraction\": look_fraction"))
    }

    @Test
    fun addressCameraUsesBoundedCrossSlopeParallaxWithoutOrbitingPhysics() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("ADDRESS_CAMERA_SIDE_ADAPT := 0.18"))
        assertTrue(script.contains("cross_component := downhill.dot(right)"))
        assertTrue(script.contains("smoothstep(0.35, 2.20"))
        assertTrue(script.contains("-signf(cross_component) * ADDRESS_CAMERA_SIDE_ADAPT"))
        assertFalse(script.contains("TIME"))
        assertFalse(script.contains("rotate_y("))
    }

    @Test
    fun addressCameraTracksVisibleReliefAndProtectsCupSightline() {
        val camera = asset("address_relief_camera.gd")
        val relief = asset("terrain_relief_visibility.gd")

        assertTrue(camera.contains("ADDRESS_RELIEF_VISUAL_SCALE := 4.6"))
        assertTrue(camera.contains("ADDRESS_RELIEF_EXTRA_CAP_M := 0.72"))
        assertTrue(relief.contains("RELIEF_VISUAL_SCALE := 4.6"))
        assertTrue(relief.contains("RELIEF_EXTRA_CAP_M := 0.72"))
        assertTrue(camera.contains("ADDRESS_CLEARANCE_SAMPLES := 9"))
        assertTrue(camera.contains("ADDRESS_SIGHTLINE_CLEARANCE_M := 0.055"))
        assertTrue(camera.contains("ADDRESS_MAX_CLEARANCE_RAISE_M := 0.26"))
        assertTrue(camera.contains("visible_h + ADDRESS_SIGHTLINE_CLEARANCE_M - sight_y"))
        assertTrue(camera.contains("required_raise = intrusion / eye_weight"))
        assertTrue(camera.contains("camera_visible_y + clearance_raise"))
        assertTrue(camera.contains("\"clearance_raise\": clearance_raise"))

        // Presentation-only: no camera clearance value can leak into authoritative systems.
        assertFalse(camera.contains("GreenTerrain" + ".set"))
        assertFalse(camera.contains("GreenReadAdvisor" + ".set"))
    }

    @Test
    fun tvSceneKeepsReliefAwareCameraInProductionInheritanceChain() {
        val scene = asset("v143_tv.tscn")
        val topLayer = asset("green_read_direction_truth.gd")
        assertTrue(scene.contains("res://green_read_direction_truth.gd"))
        assertTrue(topLayer.contains("extends \"res://address_relief_camera.gd\""))
    }

    @Test
    fun forwardMobileRendererRemainsEnabled() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
