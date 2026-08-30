package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainReliefPresentationRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun terrainReliefUsesBoundedPresentationOnlyGeometryExaggeration() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("RELIEF_VISUAL_SCALE := 4.6"))
        assertTrue(script.contains("RELIEF_EXTRA_CAP_M := 0.72"))
        assertTrue(script.contains("terrain_height * (4.6 - 1.0)"))
        assertTrue(script.contains("VERTEX.y = terrain_height + relief_delta + 0.0030"))
        assertFalse(script.contains("RELIEF_VISUAL_SCALE := 3.2"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("_v166_samples["))
        assertFalse(script.contains("shadow_enabled = true"))
    }

    @Test
    fun reliefSurfaceDepthWritesInsteadOfGhostingOverFlatBase() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("render_mode unshaded, cull_disabled;"))
        assertTrue(script.contains("surface now depth-writes"))
        assertFalse(script.contains("blend_mix"))
        assertFalse(script.contains("depth_draw_never"))
        assertFalse(script.contains("ALPHA ="))
    }

    @Test
    fun subtleGradeReliefBudgetIsMateriallyStrongerButStillCapped() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("previous 3.2x translucent pass still read too flat"))
        assertTrue(script.contains("-0.72,\n        0.72"))
        assertFalse(script.contains("RELIEF_EXTRA_CAP_M := 1."))
    }

    @Test
    fun reliefAddsOnlyVisualDeltaToInheritedBallAndCupPose() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("ball.position.y += ball_delta"))
        assertTrue(script.contains("target_root.position.y += cup_delta"))
        assertTrue(script.contains("aim_line.position.y = _terrain_relief_visual_height"))
        assertTrue(script.contains("super._apply_snapshot(s, immediate, delta)\n    _terrain_relief_sync_anchors(s)"))

        assertFalse(script.contains("ball.position.y = float(s.get(\"ballZ\""))
        assertFalse(script.contains("target_root.position.y = float(s.get(\"cupZ\""))
        assertFalse(script.contains("s[\"ballZ\"] ="))
        assertFalse(script.contains("s[\"cupZ\"] ="))
    }

    @Test
    fun allContactShadowsFollowPresentationRelief() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("_v155_ball_shadow.position.y += ball_delta"))
        assertTrue(script.contains("_v162_ball_shadow.position.y += ball_delta"))
        assertTrue(script.contains("_v173_ball_shadow.position.y += ball_delta"))
    }

    @Test
    fun terrainReliefAddsSparsePhysicalElevationRibbons() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("RELIEF_MINOR_CONTOUR_M := 0.05"))
        assertTrue(script.contains("RELIEF_MAJOR_CONTOUR_M := 0.10"))
        assertTrue(script.contains("terrain_height / 0.05"))
        assertTrue(script.contains("terrain_height / 0.10"))
        assertTrue(script.contains("float elevation_ribbon = max(minor_ribbon * 0.42, major_ribbon)"))
        assertTrue(script.contains("float ribbon_strength = elevation_ribbon * active * 0.18"))
        assertTrue(script.contains("relief_color = mix(relief_color, ribbon_color, ribbon_strength)"))
        assertFalse(script.contains("VERTEX.y += elevation_ribbon"))
    }

    @Test
    fun terrainReliefAvoidsDarkPaintedIslandRegression() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("mix(0.94, 1.06"))
        assertTrue(script.contains("vec3(0.014, 0.005, -0.012)"))
        assertTrue(script.contains("vec3 low_green = vec3(0.120, 0.300, 0.100)"))
        assertTrue(script.contains("vec3 high_green = vec3(0.180, 0.380, 0.140)"))
        assertFalse(script.contains("mix(0.84, 1.16"))
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
