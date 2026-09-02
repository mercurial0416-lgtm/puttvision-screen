package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanarGradeAddressCameraRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun steadyPlanarGradeStillEarnsGrazingPerspective() {
        val script = asset("relief_depth_finish.gd")
        assertTrue(script.contains("RELIEF_PLANAR_GRADE_START_PCT := 0.55"))
        assertTrue(script.contains("RELIEF_PLANAR_GRADE_FULL_PCT := 2.25"))
        assertTrue(script.contains("func _address_planar_grade_signal"))
        assertTrue(script.contains("Vector2(terrain.y, terrain.z).length()"))
        assertTrue(script.contains("position.y -= RELIEF_PLANAR_CAMERA_LOWER_M * planar_signal"))
        assertTrue(script.contains("RELIEF_PLANAR_FOV_BOOST_DEG * planar_signal"))
        assertTrue(script.contains("plan[\"planar_grade_signal\"] = planar_signal"))
    }

    @Test
    fun planarGradeCameraRemainsPresentationOnlyAndOcclusionGuarded() {
        val script = asset("relief_depth_finish.gd")
        assertTrue(script.contains("clearance_raise <= RELIEF_DEPTH_CLEARANCE_GUARD_M"))
        assertTrue(script.contains("plan[\"planar_grade_signal\"] = 0.0"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
    }

    @Test
    fun forwardMobileRendererRemainsEnabled() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
