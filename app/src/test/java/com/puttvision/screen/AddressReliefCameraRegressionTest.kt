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
    fun shortPuttsProgressivelyFrameCupWithoutChangingReadTruth() {
        val script = asset("address_relief_camera.gd")
        assertTrue(script.contains("ADDRESS_CUP_FRAME_START_M := 2.40"))
        assertTrue(script.contains("ADDRESS_CUP_FRAME_FULL_M := 0.90"))
        assertTrue(script.contains("ADDRESS_CUP_LOOK_FRACTION := 0.82"))
        assertTrue(script.contains("func _address_short_putt_cup_signal(distance_m: float) -> float:"))
        assertTrue(script.contains("1.0 - smoothstep(ADDRESS_CUP_FRAME_FULL_M, ADDRESS_CUP_FRAME_START_M, distance_m)"))
        assertTrue(script.contains("cup_frame_signal * ADDRESS_CUP_FOCUS_BLEND"))
        assertTrue(script.contains("\"cup_frame_signal\": cup_frame_signal"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
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

        assertTrue(camera.contains("ADDRESS_RELIEF_VISUAL_SCALE := 7.2"))
        assertTrue(camera.contains("ADDRESS_RELIEF_EXTRA_CAP_M := 0.96"))
        assertTrue(relief.contains("RELIEF_VISUAL_SCALE := 7.2"))
        assertTrue(relief.contains("RELIEF_EXTRA_CAP_M := 0.96"))
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
    fun meaningfulReliefGetsBoundedGrazingPerspectiveWhileFlatsStayNeutral() {
        val camera = asset("address_relief_camera.gd")
        assertTrue(camera.contains("ADDRESS_RELIEF_SIGNAL_START_M := 0.018"))
        assertTrue(camera.contains("ADDRESS_RELIEF_SIGNAL_FULL_M := 0.090"))
        assertTrue(camera.contains("ADDRESS_RELIEF_GRAZE_DROP_M := 0.085"))
        assertTrue(camera.contains("ADDRESS_RELIEF_FOV_BOOST_DEG := 3.0"))
        assertTrue(camera.contains("smoothstep(ADDRESS_RELIEF_SIGNAL_START_M, ADDRESS_RELIEF_SIGNAL_FULL_M, best_relief)"))
        assertTrue(camera.contains("graze_drop := ADDRESS_RELIEF_GRAZE_DROP_M * relief_signal"))
        assertTrue(camera.contains("ADDRESS_CAMERA_HEIGHT - graze_drop"))
        assertTrue(camera.contains("ADDRESS_RELIEF_FOV_BOOST_DEG * relief_signal"))
        assertTrue(camera.contains("\"relief_signal\": relief_signal"))
        assertTrue(camera.contains("\"graze_drop\": graze_drop"))
        assertTrue(camera.contains("return {\"fraction\": ADDRESS_LOOK_FRACTION, \"signal\": 0.0}"))
        assertFalse(camera.contains("GreenTerrain" + ".set"))
        assertFalse(camera.contains("GreenReadAdvisor" + ".set"))
    }

    @Test
    fun tvSceneKeepsReliefAwareCameraInProductionInheritanceChain() {
        val scene = asset("v143_tv.tscn")
        val liveLayer = asset("replay_timeline_camera_truth.gd")
        val greenRead = asset("green_read_direction_truth.gd")
        val depthFinish = asset("relief_depth_finish.gd")
        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(liveLayer.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(greenRead.contains("extends \"res://relief_depth_finish.gd\""))
        assertTrue(depthFinish.contains("extends \"res://address_relief_camera.gd\""))
    }

    @Test
    fun forwardMobileRendererRemainsEnabled() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
