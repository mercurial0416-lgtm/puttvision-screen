package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDebriefOutcomeHeaderRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun debriefHeaderSurfacesHoledAndLipOutOutcomesBeforeMetricParsing() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("func _v177_outcome_title(holed: bool, lip_out: bool) -> String:"))
        assertTrue(source.contains("return \"SHOT DEBRIEF  •  HOLED\""))
        assertTrue(source.contains("return \"SHOT DEBRIEF  •  LIP OUT\""))
        assertTrue(source.contains("_v177_title_label.text = _v177_outcome_title(holed, lip_out)"))
    }

    @Test
    fun ordinaryShotsKeepTheCompactDefaultHeader() {
        val source = asset("v177_shot_debrief.gd")

        assertTrue(source.contains("return \"SHOT DEBRIEF\""))
        assertTrue(source.contains("Vector2(300, 30), \"SHOT DEBRIEF\", 15"))
    }

    @Test
    fun outcomeHeaderIsPresentationOnlyAndDoesNotMutateAuthoritativePhysics() {
        val source = asset("v177_shot_debrief.gd")

        assertFalse(source.contains("GreenTerrain.set"))
        assertFalse(source.contains("GreenReadAdvisor.set"))
        assertFalse(source.contains("ballVelocity ="))
        assertFalse(source.contains("targetDistance ="))
    }
}
