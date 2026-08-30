package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V177ShotDebriefPresentationTest {
    private fun asset(path: String): String {
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun shotDebriefUsesCenteredSignedErrorBars() {
        val script = asset("v177_shot_debrief.gd")

        assertTrue(script.contains("const V177_BAR_HALF_PX := V177_BAR_MAX_PX * 0.5"))
        assertTrue(script.contains("func _v177_bar_geometry(delta_cm: float, full_scale_cm: float) -> Vector2:"))
        assertTrue(script.contains("var start_x := center_x if delta_cm >= 0.0 else center_x - width"))
        assertTrue(script.contains("_v177_line_bar.position.x = line_bar.x"))
        assertTrue(script.contains("_v177_pace_bar.position.x = pace_bar.x"))
        assertTrue(script.contains("_v177_add_zero_marker(_v177_panel, 82.0)"))
        assertTrue(script.contains("_v177_add_zero_marker(_v177_panel, 135.0)"))

        // Direction is presentation-only; authoritative read/pace values remain untouched.
        assertFalse(script.contains("line_delta = abs"))
        assertFalse(script.contains("pace_delta = abs"))
        assertFalse(script.contains("readLineDeltaCm] ="))
        assertFalse(script.contains("paceDeltaCm] ="))
    }
}
