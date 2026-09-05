package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRecentCenterReticleRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun productionPresentationChainIncludesRecentCenterReticle() {
        val root = asset("live_origin_truth_guard.gd")
        assertTrue(root.startsWith("extends \"res://practice_recent_center_reticle.gd\""))
    }

    @Test
    fun reticleUsesOnlyRecentThreeShotCentroid() {
        val source = asset("practice_recent_center_reticle.gd")
        assertTrue(source.contains("const PRACTICE_RECENT_CENTER_GROUP_SIZE := 3"))
        assertTrue(source.contains("var start := samples.size() - count"))
        assertTrue(source.contains("for index in range(start, samples.size()):"))
        assertTrue(source.contains("var centroid := total / float(count)"))
    }

    @Test
    fun targetToCentroidBiasVectorMakesRecentDriftReadable() {
        val source = asset("practice_recent_center_reticle.gd")
        assertTrue(source.contains("var target_center := _v179_plot_position(Vector2.ZERO)"))
        assertTrue(source.contains("\"bias\": PackedVector2Array([target_center, center])"))
        assertTrue(source.contains("\"PracticeRecentCenterBias\""))
        assertTrue(source.contains("_v179_plot.add_child(_practice_recent_center_bias)"))
        assertTrue(source.contains("_practice_recent_center_bias.points = geometry[\"bias\"]"))
    }

    @Test
    fun reticleIsScopedToActiveProspectivePracticeFocus() {
        val source = asset("practice_recent_center_reticle.gd")
        val streak = asset("v191_practice_streak.gd")
        assertTrue(source.contains("func _practice_recent_center_focus_samples() -> Array[Vector2]:"))
        assertTrue(source.contains("clampi(_v191_focus_start_index, 0, _v179_samples.size())"))
        assertTrue(source.contains("for index in range(first_eligible, _v179_samples.size()):"))
        assertTrue(source.contains("_practice_recent_center_geometry(_practice_recent_center_focus_samples())"))
        assertTrue(streak.contains("_v191_focus_start_index = sample_count"))
        assertTrue(streak.contains("_v191_focus_start_index -= 1"))
    }

    @Test
    fun focusSwitchNeedsThreeComparableRepsBeforeCenterReturns() {
        val source = asset("practice_recent_center_reticle.gd")
        assertTrue(source.contains("if samples.size() < PRACTICE_RECENT_CENTER_MIN_SAMPLES:"))
        assertTrue(source.contains("return {\"visible\": false}"))
        assertTrue(source.contains("const PRACTICE_RECENT_CENTER_MIN_SAMPLES := 3"))
        assertFalse(source.contains("clampi(first_eligible -"))
    }

    @Test
    fun malformedAndOffMapCentersFailClosedInsteadOfClamping() {
        val source = asset("practice_recent_center_reticle.gd")
        assertTrue(source.contains("not is_finite(sample.x) or not is_finite(sample.y)"))
        assertTrue(source.contains("absf(centroid.x) > V179_LINE_SCALE_CM"))
        assertTrue(source.contains("absf(centroid.y) > V179_PACE_SCALE_CM"))
        assertTrue(source.contains("return {\"visible\": false, \"clipped\": true}"))
    }

    @Test
    fun markerRefreshesWithExistingDispersionRefreshWithoutProcessLoop() {
        val source = asset("practice_recent_center_reticle.gd")
        assertTrue(source.contains("func _v179_refresh() -> void:"))
        assertTrue(source.contains("super._v179_refresh()"))
        assertTrue(source.contains("_practice_recent_center_refresh()"))
        assertFalse(source.contains("func _process("))
        assertFalse(source.contains("find_child("))
    }

    @Test
    fun reticleRemainsPresentationOnly() {
        val source = asset("practice_recent_center_reticle.gd")
        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("readLineDeltaCm ="))
        assertFalse(source.contains("paceDeltaCm ="))
        assertFalse(source.contains("_apply_snapshot("))
    }
}
