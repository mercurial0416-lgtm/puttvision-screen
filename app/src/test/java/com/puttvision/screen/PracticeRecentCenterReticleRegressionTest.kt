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
