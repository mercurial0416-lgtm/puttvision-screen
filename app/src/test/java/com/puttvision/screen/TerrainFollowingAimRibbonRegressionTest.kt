package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainFollowingAimRibbonRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun aimGuideUsesSampledReliefInsteadOfSingleMidpointBox() {
        val base = asset("v143_tv.gd")
        val finish = asset("relief_depth_finish.gd")

        assertTrue(base.contains("func _update_aim_line(distance_m: float)"))
        assertTrue(base.contains("var mesh := BoxMesh.new()"))
        assertTrue(finish.contains("func _terrain_following_aim_mesh(distance_m: float)"))
        assertTrue(finish.contains("_v166_sample(-half_width, forward_m).x"))
        assertTrue(finish.contains("_v166_sample(half_width, forward_m).x"))
        assertTrue(finish.contains("_terrain_relief_visual_height(left_surface_m)"))
        assertTrue(finish.contains("_terrain_relief_visual_height(right_surface_m)"))
        assertTrue(finish.contains("Vector3(-half_width, left_height_m, -forward_m)"))
        assertTrue(finish.contains("Vector3(half_width, right_height_m, -forward_m)"))
        assertTrue(finish.contains("aim_line.mesh = _terrain_following_aim_mesh(distance_m)"))
        assertTrue(finish.contains("aim_line.position = Vector3.ZERO"))
        assertFalse(finish.contains("var surface_m := _v166_sample(0.0, forward_m).x"))
    }

    @Test
    fun terrainRefreshRebuildsGuideAndKeepsWorkBounded() {
        val finish = asset("relief_depth_finish.gd")

        assertTrue(finish.contains("const RELIEF_AIM_MAX_SEGMENTS := 96"))
        assertTrue(finish.contains("clampi(int(ceil(span_m / RELIEF_AIM_SEGMENT_M)), 2, RELIEF_AIM_MAX_SEGMENTS)"))
        assertTrue(finish.contains("func _terrain_relief_rebuild() -> void:"))
        assertTrue(finish.contains("super._terrain_relief_rebuild()"))
        assertTrue(finish.contains("_update_aim_line(target_distance)"))
        assertFalse(finish.contains("GreenTerrain("))
        assertFalse(finish.contains("GreenReadAdvisor("))
    }
}
