package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainSlopeFlowGridRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun slopeFlowGridAnimatesDownhillFromEncodedAuthoritativeGrade() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("vec2 downhill = slope_pct > 0.001 ? local_slope / slope_pct"))
        assertTrue(script.contains("float flow_active = smoothstep(0.16, 0.72, slope_pct)"))
        assertTrue(script.contains("green_m + downhill * (TIME * flow_speed)"))
        assertTrue(script.contains("dot(green_m, downhill)"))
        assertTrue(script.contains("float flow_grid = moving_grid * flow_active"))
    }

    @Test
    fun levelGreenDoesNotFakeDirectionalMotion() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("float flow_speed = mix(0.0, 0.46, flow_active)"))
        assertTrue(script.contains("moving_grid * flow_active"))
        assertFalse(script.contains("flow_active = 1.0"))
    }

    @Test
    fun slopeFlowStaysShaderOnlyForForwardMobile() {
        val script = asset("terrain_relief_visibility.gd")
        val project = asset("project.godot")
        assertTrue(script.contains("TIME * flow_speed"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("func _physics_process("))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
