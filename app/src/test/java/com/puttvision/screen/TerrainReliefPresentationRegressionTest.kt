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
    fun terrainReliefUsesTvReadableOmnidirectionalHillshadeWithoutGeometryExaggeration() {
        val script = asset("terrain_relief_visibility.gd")

        assertTrue(script.contains("cross_facing"))
        assertTrue(script.contains("hillshade_axis = max(abs(facing), abs(cross_facing) * 0.34)"))
        assertTrue(script.contains("signed_hillshade"))
        assertTrue(script.contains("hillshade_exposure"))
        assertTrue(script.contains("mix(0.68, 1.32"))
        assertTrue(script.contains("ALPHA = active * (0.235 + 0.115 * abs(height_bias))"))
        assertTrue(script.contains("VERTEX.y += 0.0016"))

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
