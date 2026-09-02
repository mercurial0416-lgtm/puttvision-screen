package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliefDepthFinishRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun strongerReliefDepthFinishIsWiredIntoProductionGreenReadChain() {
        val layer = asset("relief_depth_finish.gd")
        val child = asset("green_read_direction_truth.gd")
        assertTrue(layer.contains("extends \"res://address_relief_camera.gd\""))
        assertTrue(child.contains("extends \"res://relief_depth_finish.gd\""))
    }

    @Test
    fun hillshadeContoursAndContinuousFormGainTvContrastWithoutExtraGeometry() {
        val layer = asset("relief_depth_finish.gd")
        val base = asset("terrain_relief_visibility.gd")
        assertTrue(layer.contains("mix(0.86, 1.14"))
        assertTrue(layer.contains("elevation_ribbon * active * 0.48"))
        assertTrue(layer.contains("0.026 + active * (0.108 + 0.020 * abs(height_bias))"))
        assertTrue(base.contains("ALPHA = min(0.40, base_alpha + ribbon_alpha)"))
        assertTrue(layer.contains("float form_light = clamp(primary_hillshade * 0.78 + cross_hillshade * 0.22"))
        assertTrue(layer.contains("float form_lobe = mix(1.0, mix(0.82, 1.18"))
        assertTrue(layer.contains("relief_color *= form_lobe;"))
        assertTrue(layer.contains("super._terrain_relief_material()"))
        assertFalse(layer.contains("DirectionalLight3D.new()"))
        assertFalse(layer.contains("MeshInstance3D.new()"))
        assertFalse(layer.contains("MultiMesh.new()"))
    }

    @Test
    fun grazingCameraBiasIsSmallAndDisabledWhenSightlineGuardIsBusy() {
        val layer = asset("relief_depth_finish.gd")
        assertTrue(layer.contains("RELIEF_DEPTH_CAMERA_LOWER_M := 0.035"))
        assertTrue(layer.contains("RELIEF_DEPTH_CLEARANCE_GUARD_M := 0.08"))
        assertTrue(layer.contains("clearance_raise <= RELIEF_DEPTH_CLEARANCE_GUARD_M"))
        assertTrue(layer.contains("position.y -= RELIEF_DEPTH_CAMERA_LOWER_M"))
    }

    @Test
    fun finishCannotMutateAuthoritativePhysicsOrReadSystems() {
        val layer = asset("relief_depth_finish.gd")
        assertFalse(layer.contains("GreenTerrain" + ".set"))
        assertFalse(layer.contains("GreenReadAdvisor" + ".set"))
        assertFalse(layer.contains("ballX] ="))
        assertFalse(layer.contains("ballY] ="))
        assertFalse(layer.contains("predictedTrail] ="))
    }
}
