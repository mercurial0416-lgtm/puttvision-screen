package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwarelessLabSlopeReadRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File("src/main/java/com/puttvision/screen/$path"), File("app/src/main/java/com/puttvision/screen/$path"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate source $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun hardwarelessLabUsesExplicitNonFlatGradeForReadValidation() {
        val activity = source("V144HardwarelessGodotActivity.kt")
        val presets = source("HardwarelessGreenPreset.kt")

        assertTrue("LAB must configure cross slope from its active drill", activity.contains("engine.settings.sideSlopePct = greenPreset.sideSlopePct"))
        assertTrue("LAB must configure longitudinal slope from its active drill", activity.contains("engine.settings.longSlopePct = greenPreset.longSlopePct"))
        assertTrue("LAB must expose left break practice", presets.contains("LEFT_BREAK"))
        assertTrue("LAB must expose right break practice", presets.contains("RIGHT_BREAK"))
        assertTrue("LAB must expose uphill practice", presets.contains("UPHILL"))
        assertTrue("LAB must expose downhill practice", presets.contains("DOWNHILL"))
        assertTrue(activity.contains("gradeLabel()"))
    }

    @Test
    fun hardwarelessLabNoLongerResetsReadGradeToFlat() {
        val activity = source("V144HardwarelessGodotActivity.kt")
        assertFalse(activity.contains("engine.settings.sideSlopePct = 0.0"))
        assertFalse(activity.contains("engine.settings.longSlopePct = 0.0"))
    }
}
