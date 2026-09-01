package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReliefMaterialTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun sampledGreenDoesNotReceiveLegacyPlanarSlopeTwice() {
        val guard = asset("green_relief_material_truth.gd")
        val base = asset("v143_tv.gd")
        val relief = asset("terrain_relief_visibility.gd")

        assertTrue(base.contains("VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope"))
        assertTrue(relief.contains("green.mesh = _terrain_relief_surface_mesh("))
        assertTrue(relief.contains("var visible_height := _terrain_relief_geometry_height(terrain.x)"))
        assertTrue(guard.contains("mat_green.set_shader_parameter(\"side_slope\", 0.0)"))
        assertTrue(guard.contains("mat_green.set_shader_parameter(\"long_slope\", 0.0)"))
    }

    @Test
    fun guardRunsAfterInheritedSnapshotAndLeavesSurroundingSurfacesAlone() {
        val guard = asset("green_relief_material_truth.gd")
        val superIndex = guard.indexOf("super._apply_snapshot(s, immediate, delta)")
        val lockIndex = guard.indexOf("_green_relief_lock_sampled_mesh_material()", superIndex)

        assertTrue(superIndex >= 0)
        assertTrue(lockIndex > superIndex)
        assertFalse(guard.contains("mat_fringe.set_shader_parameter"))
        assertFalse(guard.contains("mat_rough.set_shader_parameter"))
        assertFalse(guard.contains("GreenTerrain("))
        assertFalse(guard.contains("GreenReadAdvisor("))
    }

    @Test
    fun tvSceneUsesMaterialTruthAsFinalPresentationLayer() {
        val scene = asset("v143_tv.tscn")
        val guard = asset("green_relief_material_truth.gd")

        assertTrue(scene.contains("res://green_relief_material_truth.gd"))
        assertTrue(guard.contains("extends \"res://replay_timeline_camera_truth.gd\""))
    }
}
