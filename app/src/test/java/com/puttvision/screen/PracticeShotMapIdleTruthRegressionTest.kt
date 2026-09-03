package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeShotMapIdleTruthRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun hiddenShotMapReturnsVerdictAndEmphasisToNeutralState() {
        val script = asset("v197_shot_map_make_window.gd")
        val hiddenBranch = script.substringAfter("if not visible:").substringBefore("var made :=")

        assertTrue(script.contains("const V197_IDLE_LEGEND := \"GREEN BOX = MAKE\""))
        assertTrue(script.contains("func _v197_set_idle_legend() -> void:"))
        assertTrue(hiddenBranch.contains("_v197_make_fill.color = V197_IDLE_FILL"))
        assertTrue(hiddenBranch.contains("_v197_make_outline.default_color = V197_IDLE_OUTLINE"))
        assertTrue(hiddenBranch.contains("_v197_set_idle_legend()"))
        assertTrue(hiddenBranch.indexOf("_v197_set_idle_legend()") < hiddenBranch.lastIndexOf("return"))
    }

    @Test
    fun idleResetCannotReusePriorSuccessOrCorrectionVerdict() {
        val script = asset("v197_shot_map_make_window.gd")
        val idleHelper = script.substringAfter("func _v197_set_idle_legend() -> void:")
            .substringBefore("func _v197_correction_target")

        assertTrue(idleHelper.contains("_v196_center_legend.text = V197_IDLE_LEGEND"))
        assertTrue(idleHelper.contains("V197_IDLE_LEGEND_COLOR"))
        assertFalse(idleHelper.contains("IN MAKE WINDOW"))
        assertFalse(idleHelper.contains("_v197_correction_text"))
    }

    @Test
    fun idleTruthFixRemainsPresentationOnly() {
        val script = asset("v197_shot_map_make_window.gd")

        assertFalse(script.contains("GreenTerrain("))
        assertFalse(script.contains("GreenReadAdvisor("))
        assertFalse(script.contains("predictedTrail] ="))
        assertFalse(script.contains("actualTrail] ="))
    }
}
