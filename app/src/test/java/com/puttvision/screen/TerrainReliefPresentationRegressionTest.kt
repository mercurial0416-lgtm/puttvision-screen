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
        assertTrue(script.contains("RELIEF_VISUAL_SCALE := 7.2"))
        assertTrue(script.contains("RELIEF_EXTRA_CAP_M := 0.96"))
        assertTrue(script.contains("terrain_height_m * (RELIEF_VISUAL_SCALE - 1.0)"))
        assertTrue(script.contains("VERTEX.y = terrain_height + relief_delta + 0.0030"))
        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("_v166_samples["))
        assertFalse(script.contains("shadow_enabled = true"))
    }

    @Test
    fun opaqueGreenMeshUsesTheSamePresentationReliefAsReadCues() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("func _terrain_relief_surface_mesh("))
        assertTrue(script.contains("var visible_height := _terrain_relief_geometry_height(terrain.x)"))
        assertTrue(script.contains("vertices.append(Vector3(x, visible_height, local_z))"))
        assertTrue(script.contains("green.mesh = _terrain_relief_surface_mesh("))
        assertTrue(script.contains("_v164_grid.mesh = _terrain_relief_surface_mesh("))
        assertTrue(script.contains("terrain.y * 0.01 * RELIEF_VISUAL_SCALE"))
        assertTrue(script.contains("-terrain.z * 0.01 * RELIEF_VISUAL_SCALE"))
        assertFalse(script.contains("green.visible = false"))
    }

    @Test
    fun translucentReliefMakesSubtleGradeShadingReadableWithoutHeavyEffects() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;"))
        assertTrue(script.contains("smoothstep(0.10, 0.70, slope_pct)"))
        assertTrue(script.contains("mix(0.89, 1.11"))
        assertTrue(script.contains("float base_alpha = 0.022"))
        assertTrue(script.contains("float ribbon_alpha = elevation_ribbon * active * 0.28"))
        assertTrue(script.contains("ALPHA = min(0.40, base_alpha + ribbon_alpha)"))
        assertFalse(script.contains("depth_test_disabled"))
        assertFalse(script.contains("DirectionalLight3D.new()"))
    }

    @Test
    fun subtleGradeReliefBudgetIsMateriallyStrongerButStillCapped() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("RELIEF_VISUAL_SCALE := 7.2"))
        assertTrue(script.contains("RELIEF_EXTRA_CAP_M := 0.96"))
        assertTrue(script.contains("-RELIEF_EXTRA_CAP_M,\n        RELIEF_EXTRA_CAP_M"))
        assertFalse(script.contains("RELIEF_EXTRA_CAP_M := 1."))
    }

    @Test
    fun greenBladesFollowOpaquePresentationSurface() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("func _terrain_relief_ground_green_blades()"))
        assertTrue(script.contains("_v163_green_blades as MultiMeshInstance3D"))
        assertTrue(script.contains("transform.origin.y = _terrain_relief_geometry_height"))
        assertTrue(script.contains("_terrain_relief_ground_green_blades()"))
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
    fun authoritativePhysicsTrailsStayVisibleOnPresentationRelief() {
        val script = asset("terrain_relief_visibility.gd")
        val source = asset("v166_true_physics_green_read.gd")

        assertTrue(script.contains("RELIEF_TRAIL_CLEARANCE_M := 0.0075"))
        assertTrue(script.contains("func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(a_left_x, a_left_y).x) + RELIEF_TRAIL_CLEARANCE_M"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(a_right_x, a_right_y).x) + RELIEF_TRAIL_CLEARANCE_M"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(b_left_x, b_left_y).x) + RELIEF_TRAIL_CLEARANCE_M"))
        assertTrue(script.contains("_terrain_relief_visual_height(_v166_sample(b_right_x, b_right_y).x) + RELIEF_TRAIL_CLEARANCE_M"))

        assertTrue(source.contains("var ah: float = _v166_sample(a.x, a.y).x + 0.0075"))
        assertFalse(script.contains("a.x +="))
        assertFalse(script.contains("a.y +="))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
    }

    @Test
    fun terrainReliefUsesStrongButSparsePhysicalElevationRibbons() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("RELIEF_MINOR_CONTOUR_M := 0.05"))
        assertTrue(script.contains("RELIEF_MAJOR_CONTOUR_M := 0.10"))
        assertTrue(script.contains("terrain_height / 0.05"))
        assertTrue(script.contains("terrain_height / 0.10"))
        assertTrue(script.contains("float elevation_ribbon = max(minor_ribbon * 0.50, major_ribbon)"))
        assertTrue(script.contains("float ribbon_strength = elevation_ribbon * active * 0.42"))
        assertTrue(script.contains("relief_color = mix(relief_color, ribbon_color, ribbon_strength)"))
        assertFalse(script.contains("VERTEX.y += elevation_ribbon"))
    }

    @Test
    fun terrainReliefAvoidsPaintedIslandRegression() {
        val script = asset("terrain_relief_visibility.gd")
        assertTrue(script.contains("vec3 low_green = vec3(0.108, 0.276, 0.090)"))
        assertTrue(script.contains("vec3 high_green = vec3(0.196, 0.405, 0.151)"))
        assertFalse(script.contains("mix(0.80, 1.20"))
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
