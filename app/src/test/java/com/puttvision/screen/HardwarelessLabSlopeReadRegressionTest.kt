package com.puttvision.screen

import java.io.File
import kotlin.math.hypot
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
        val side = Regex("LAB_SIDE_SLOPE_PCT = (-?[0-9.]+)").find(activity)!!.groupValues[1].toDouble()
        val long = Regex("LAB_LONG_SLOPE_PCT = (-?[0-9.]+)").find(activity)!!.groupValues[1].toDouble()
        val magnitude = hypot(side, long)

        // terrain_relief_visibility.gd begins flow activation at 0.16% and is effectively clear by 0.72%.
        assertTrue("LAB grade must visibly exercise slope flow", magnitude >= 0.72)
        assertTrue("LAB grade should stay in a believable practice range", magnitude <= 3.0)
        assertTrue(activity.contains("engine.settings.sideSlopePct = LAB_SIDE_SLOPE_PCT"))
        assertTrue(activity.contains("engine.settings.longSlopePct = LAB_LONG_SLOPE_PCT"))
        assertTrue(activity.contains("gradeLabel()"))
    }

    @Test
    fun hardwarelessLabNoLongerResetsReadGradeToFlat() {
        val activity = source("V144HardwarelessGodotActivity.kt")
        assertFalse(activity.contains("engine.settings.sideSlopePct = 0.0"))
        assertFalse(activity.contains("engine.settings.longSlopePct = 0.0"))
    }
}