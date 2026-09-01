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
        val truth = asset("green_read_direction_truth.gd")
        val base = asset("v143_tv.gd")
        val relief = asset("terrain_relief_visibility.gd")

        assertTrue(base.contains("VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope"))
        assertTrue(relief.contains("green.mesh = _terrain_relief_surface_mesh("))
        assertTrue(relief.contains("var visible_height := _terrain_relief_geometry_height(terrain.x)"))
        assertTrue(truth.contains("mat_green.set_shader_parameter(\"side_slope\", 0.0)"))
        assertTrue(truth.contains("mat_green.set_shader_parameter(\"long_slope\", 0.0)"))
    }

    @Test
    fun materialTruthRunsAfterInheritedSnapshotAndLeavesSurroundingSurfacesAlone() {
        val truth = asset("green_read_direction_truth.gd")
        val superIndex = truth.indexOf("super._apply_snapshot(s, immediate, delta)")
        val lockIndex = truth.indexOf("_lock_sampled_green_material_to_relief_mesh()", superIndex)

        assertTrue(superIndex >= 0)
        assertTrue(lockIndex > superIndex)
        assertFalse(truth.contains("mat_fringe.set_shader_parameter"))
        assertFalse(truth.contains("mat_rough.set_shader_parameter"))
        assertFalse(truth.contains("GreenTerrain("))
        assertFalse(truth.contains("GreenReadAdvisor("))
    }

    @Test
    fun establishedTvPresentationEntryPointIsPreserved() {
        val scene = asset("v143_tv.tscn")
        val replay = asset("replay_timeline_camera_truth.gd")
        val truth = asset("green_read_direction_truth.gd")

        assertTrue(scene.contains("res://replay_timeline_camera_truth.gd"))
        assertTrue(replay.contains("extends \"res://green_read_direction_truth.gd\""))
        assertTrue(truth.contains("extends \"res://relief_depth_finish.gd\""))
    }
}