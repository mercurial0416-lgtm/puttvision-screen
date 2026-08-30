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
    fun terrainReliefUsesBoundedPresentationOnlyGeometryExaggeration() {
        val script = asset("terrain_relief_visibility.gd")

        assertTrue(script.contains("RELIEF_VISUAL_SCALE := 3.2"))
        assertTrue(script.contains("RELIEF_EXTRA_CAP_M := 0.55"))
        assertTrue(script.contains("_terrain_relief_visual_height"))
        assertTrue(script.contains("terrain_height * (3.2 - 1.0)"))
        assertTrue(script.contains("VERTEX.y = terrain_height + relief_delta + 0.0030"))
        assertTrue(script.contains("ALPHA = 0.055 + active * (0.205 + 0.055 * abs(height_bias))"))

        // The relief layer is presentation-only. Physics, GreenTerrain, GreenReadAdvisor and
        // scoring must not be mutated or replaced from this script.
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("_v166_samples["))
        assertFalse(script.contains("shadow_enabled = true"))
    }

    @Test
    fun ballCupAimAndPhysicsTrailsStayGroundedOnVisualRelief() {
        val script = asset("terrain_relief_visibility.gd")

        assertTrue(script.contains("func _terrain_relief_visual_offset(terrain_height_m: float) -> float:"))
        assertTrue(script.contains("func _terrain_relief_sync_anchors(s: Dictionary) -> void:"))
        assertTrue(script.contains("ball.position.y = float(s.get(\"ballZ\", BALL_RADIUS)) + ball_delta"))
        assertTrue(script.contains("target_root.position.y = float(s.get(\"cupZ\", last_cup_z)) + cup_delta"))
        assertTrue(script.contains("aim_line.position.y = _terrain_relief_visual_height"))
        assertTrue(script.contains("func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(a.x, a.y).x) + 0.0075"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(b.x, b.y).x) + 0.0075"))
        assertTrue(script.contains("super._apply_snapshot(s, immediate, delta)\n    _terrain_relief_sync_anchors(s)"))

        // Visual grounding must not overwrite authoritative snapshot coordinates or trail XY.
        assertFalse(script.contains("s[\"ballZ\"] ="))
        assertFalse(script.contains("s[\"cupZ\"] ="))
        assertFalse(script.contains("points[i] ="))
    }

    @Test
    fun terrainReliefRetainsContinuousDirectionalReadabilityWithoutDarkMaskRegression() {
        val script = asset("terrain_relief_visibility.gd")

        assertTrue(script.contains("primary_hillshade"))
        assertTrue(script.contains("cross_hillshade"))
        assertTrue(script.contains("cross_tint"))
        assertTrue(script.contains("mix(0.84, 1.16"))
        assertTrue(script.contains("vec3(0.040, 0.012, -0.035)"))
        assertFalse(script.contains("mix(0.72, 1.28"))
        assertFalse(script.contains("ALPHA = 0.10 + active * (0.38"))
        assertFalse(script.contains("abs(facing) > 0.06"))
        assertFalse(script.contains("hillshade_sign"))
        assertFalse(script.contains("DirectionalLight3D.new()"))
        assertFalse(script.contains("contour_wave"))
    }

    @Test
    fun forwardMobileRendererRemainsAuthoritativeForTvAssets() {
        val project = asset("project.godot")
        assertTrue(project.contains("renderer/rendering_method=\"mobile\""))
        assertTrue(project.contains("renderer/rendering_method.mobile=\"mobile\""))
    }
}
