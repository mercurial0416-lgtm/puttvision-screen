package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSummaryHierarchyRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name from ${File(".").absolutePath}")
    }

    @Test
    fun practiceAverageLabelsKeepTvReadableHierarchy() {
        val helper = asset("address_relief_camera.gd")

        assertTrue(helper.contains("const SESSION_SUMMARY_FONT_SIZE := 17"))
        assertTrue(helper.contains("const SESSION_SUMMARY_OUTLINE_SIZE := 2"))
        assertTrue(helper.contains("func _session_apply_summary_hierarchy(label: Label) -> void:"))
        assertTrue(helper.contains("font_outline_color"))
        assertTrue(helper.contains("label.clip_text = true"))
        assertTrue(helper.contains("_session_apply_summary_hierarchy(_v179_line_mean_label)"))
        assertTrue(helper.contains("_session_apply_summary_hierarchy(_v179_pace_mean_label)"))
    }

    @Test
    fun malformedPracticeTelemetryStaysNeutral() {
        val helper = asset("address_relief_camera.gd")
        val lineText = helper.substringAfter("func _session_line_average_text(value_cm: float) -> String:")
            .substringBefore("func _session_pace_average_text")
        val paceText = helper.substringAfter("func _session_pace_average_text(value_cm: float) -> String:")
            .substringBefore("func _session_apply_rep_hierarchy")

        assertTrue(lineText.contains("if not is_finite(value_cm):"))
        assertTrue(lineText.contains("return \"LINE --\""))
        assertTrue(paceText.contains("if not is_finite(value_cm):"))
        assertTrue(paceText.contains("return \"PACE --\""))
        assertFalse(lineText.contains("GreenTerrain("))
        assertFalse(lineText.contains("GreenReadAdvisor("))
        assertFalse(paceText.contains("GreenTerrain("))
        assertFalse(paceText.contains("GreenReadAdvisor("))
    }

    @Test
    fun practiceHierarchyStaysPresentationOnly() {
        val helper = asset("address_relief_camera.gd")
        val hierarchy = helper.substringAfter("func _session_apply_summary_hierarchy(label: Label) -> void:")
            .substringBefore("func _v179_refresh")

        assertFalse(hierarchy.contains("GreenTerrain("))
        assertFalse(hierarchy.contains("GreenReadAdvisor("))
        assertFalse(hierarchy.contains("_v179_samples.append"))
        assertFalse(hierarchy.contains("_v179_mean("))
        assertFalse(hierarchy.contains("_update_camera("))
    }
}
