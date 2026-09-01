package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSlopePathRibbonRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun readAndReplayRibbonGroundsAllFourShoulderVertices() {
        val relief = asset("terrain_relief_visibility.gd")
        val ribbon = relief.substringAfter("func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:")
            .substringBefore("\nfunc _apply_snapshot")

        assertTrue(ribbon.contains("var a_left_x := a.x + perp.x"))
        assertTrue(ribbon.contains("var a_left_y := a.y - perp.y"))
        assertTrue(ribbon.contains("var a_right_x := a.x - perp.x"))
        assertTrue(ribbon.contains("var a_right_y := a.y + perp.y"))
        assertTrue(ribbon.contains("_v166_sample(a_left_x, a_left_y).x"))
        assertTrue(ribbon.contains("_v166_sample(a_right_x, a_right_y).x"))
        assertTrue(ribbon.contains("_v166_sample(b_left_x, b_left_y).x"))
        assertTrue(ribbon.contains("_v166_sample(b_right_x, b_right_y).x"))
        assertTrue(ribbon.contains("Vector3(a_left_x, a_left_h, -a_left_y)"))
        assertTrue(ribbon.contains("Vector3(a_right_x, a_right_h, -a_right_y)"))
        assertFalse(ribbon.contains("var ah: float = _terrain_relief_visual_height(_v166_sample(a.x, a.y).x)"))
        assertFalse(ribbon.contains("var bh: float = _terrain_relief_visual_height(_v166_sample(b.x, b.y).x)"))
    }

    @Test
    fun presentationFixDoesNotMutateAuthoritativeGreenInputs() {
        val relief = asset("terrain_relief_visibility.gd")
        val ribbon = relief.substringAfter("func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:")
            .substringBefore("\nfunc _apply_snapshot")

        assertFalse(ribbon.contains("GreenTerrain("))
        assertFalse(ribbon.contains("GreenReadAdvisor("))
        assertFalse(ribbon.contains("points["))
        assertTrue(ribbon.contains("RELIEF_TRAIL_CLEARANCE_M"))
    }
}
