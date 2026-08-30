package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainReliefPresentationRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun terrainReliefUsesContinuousDualAxisHillshadeWithoutGeometryExaggeration() {
        val script = asset("terrain_relief_visibility.gd")

        assertTrue(script.contains("primary_hillshade"))
        assertTrue(script.contains("cross_hillshade"))
        assertTrue(script.contains("cross_tint"))
        assertTrue(script.contains("mix(0.72, 1.28"))
        assertTrue(script.contains("vec3(0.090, 0.020, -0.080) * cross_hillshade"))
        assertTrue(script.contains("ALPHA = active * (0.235 + 0.115 * abs(height_bias))"))
        assertTrue(script.contains("VERTEX.y += 0.0016"))

        // Regression: directional readability must not depend on a hard sign-switch threshold.
        assertFalse(script.contains("abs(facing) > 0.06"))
        assertFalse(script.contains("hillshade_sign"))
        assertFalse(script.contains("TerrainReliefGrazingLight"))
        assertFalse(script.contains("DirectionalLight3D.new()"))
        assertFalse(script.contains("contour_wave"))
        assertFalse(script.contains("VERTEX.y *="))
        assertFalse(script.contains("shadow_enabled = true"))
    }

    @Test
    fun forwardMobileRendererRemainsAuthoritativeForTvAssets() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
