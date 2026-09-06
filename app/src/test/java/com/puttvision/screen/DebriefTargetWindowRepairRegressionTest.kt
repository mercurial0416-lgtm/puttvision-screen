package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefTargetWindowRepairRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun partialInstallRepairsBothDebriefWindowsInsteadOfTreatingOneNodeAsComplete() {
        val source = asset("debrief_target_window.gd")
        val install = source.substringAfter("func _install_target_windows(panel: Control) -> void:")
            .substringBefore("func _process")

        assertTrue(install.contains("_ensure_target_band(panel, \"LineGoodWindow\""))
        assertTrue(install.contains("_ensure_target_band(panel, \"PaceGoodWindow\""))
        assertFalse(install.contains("if panel.get_node_or_null(\"LineGoodWindow\") != null:"))
    }

    @Test
    fun targetBandInstallerIsIdempotentAndAddsAnIdealCenterCue() {
        val source = asset("debrief_target_window.gd")
        val helper = source.substringAfter("func _add_rect_if_missing")
            .substringBefore("func _install_target_windows")

        assertTrue(helper.contains("if panel.get_node_or_null(name_value) != null:"))
        assertTrue(helper.contains("\"%sIdeal\" % name_value"))
        assertTrue(source.contains("const IDEAL_COLOR :="))
        assertTrue(source.contains("Vector2(BAR_CENTER_X - 0.5, y - 5.0)"))
    }

    @Test
    fun repairRemainsPresentationOnly() {
        val source = asset("debrief_target_window.gd")
        val install = source.substringAfter("func _install_target_windows(panel: Control) -> void:")
            .substringBefore("func _process")

        assertFalse(install.contains("GreenTerrain"))
        assertFalse(install.contains("GreenReadAdvisor"))
        assertFalse(install.contains("score"))
        assertFalse(install.contains("telemetry"))
    }
}
