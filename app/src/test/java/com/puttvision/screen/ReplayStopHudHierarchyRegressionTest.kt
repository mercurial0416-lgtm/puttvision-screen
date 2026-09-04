package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayStopHudHierarchyRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun replayStopDistanceUsesStableBroadcastHierarchy() {
        val helper = asset("replay_roll_distance_layout.gd")

        assertTrue(helper.contains("const STATUS_FONT_SIZE := 17"))
        assertTrue(helper.contains("stage.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT"))
        assertTrue(helper.contains("stage.vertical_alignment = VERTICAL_ALIGNMENT_CENTER"))
        assertTrue(helper.contains("stage.clip_text = true"))
        assertTrue(helper.contains("font_outline_color"))
        assertTrue(helper.contains("_apply_stage_hierarchy(stage)"))
    }

    @Test
    fun hierarchyIsBindTimePresentationOnlyWork() {
        val helper = asset("replay_roll_distance_layout.gd")
        val processBody = helper.substringAfter("func _process(_delta: float) -> void:")
            .substringBefore("func _bind_nodes")
        val hierarchy = helper.substringAfter("func _apply_stage_hierarchy(stage: Label) -> void:")
            .substringBefore("func _present_stage_text")

        assertTrue(processBody.contains("if _layout_done:\n        return"))
        assertFalse(hierarchy.contains("GreenTerrain("))
        assertFalse(hierarchy.contains("GreenReadAdvisor("))
        assertFalse(hierarchy.contains("actualTrail"))
        assertFalse(hierarchy.contains("predictedTrail"))
    }
}
