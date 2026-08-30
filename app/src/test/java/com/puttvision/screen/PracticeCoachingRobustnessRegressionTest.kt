package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeCoachingRobustnessRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) * 0.5
        }
    }

    @Test
    fun singleGrossMishitCannotInvertTypicalNextRepPattern() {
        val clusteredLine = listOf(5.0, 6.0, 4.0, 5.0, -30.0)
        val clusteredPace = listOf(12.0, 14.0, 11.0, 13.0, -70.0)

        // Raw averages are still allowed to show the outlier in the session statistics.
        assertTrue(clusteredLine.average() < 0.0)
        assertTrue(clusteredPace.average() < 0.0)

        // Coaching tracks the typical miss instead of telling the player to correct
        // in the opposite direction because of one obvious mishit.
        assertEquals(5.0, median(clusteredLine), 0.001)
        assertEquals(12.0, median(clusteredPace), 0.001)
    }

    @Test
    fun practiceHudUsesRobustCenterForAdviceButKeepsRawAverageVisible() {
        val script = asset("v179_session_dispersion.gd")

        assertTrue(script.contains("func _v179_median(axis: int) -> float:"))
        assertTrue(script.contains("func _v179_coaching_center(axis: int) -> float:"))
        assertTrue(script.contains("return _v179_median(axis)"))

        val coachingStart = script.indexOf("func _v179_next_rep_text()")
        val coachingEnd = script.indexOf("func _v179_plot_position", coachingStart)
        val coaching = script.substring(coachingStart, coachingEnd)
        assertTrue(coaching.contains("_v179_coaching_center(0)"))
        assertTrue(coaching.contains("_v179_coaching_center(1)"))

        val refreshStart = script.indexOf("func _v179_refresh()")
        val refreshEnd = script.indexOf("func _v179_capture", refreshStart)
        val refresh = script.substring(refreshStart, refreshEnd)
        assertTrue(refresh.contains("_v179_line_mean_label.text = \"%+.0f cm\" % _v179_mean(0)"))
        assertTrue(refresh.contains("_v179_pace_mean_label.text = \"%+.0f cm\" % _v179_mean(1)"))
    }

    @Test
    fun biasVectorUsesSameRobustCoachingCenterAndShowsMagnitude() {
        val script = asset("v195_practice_bias_vector.gd")

        assertTrue(script.contains("func _v195_coaching_bias() -> Vector2:"))
        assertTrue(script.contains("Vector2(_v179_coaching_center(0), _v179_coaching_center(1))"))
        assertTrue(script.contains("line_text = \"R %.0f CM\" % absf(mean.x)"))
        assertTrue(script.contains("line_text = \"L %.0f CM\" % absf(mean.x)"))
        assertTrue(script.contains("pace_text = \"LONG %.0f CM\" % absf(mean.y)"))
        assertTrue(script.contains("pace_text = \"SHORT %.0f CM\" % absf(mean.y)"))

        val refreshStart = script.indexOf("func _v195_refresh_bias()")
        val refreshEnd = script.indexOf("func _v188_refresh", refreshStart)
        val refresh = script.substring(refreshStart, refreshEnd)
        assertTrue(refresh.contains("var bias := _v195_coaching_bias()"))
        assertTrue(!refresh.contains("_v194_mean_sample()"))
    }
}
