package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotMapTargetReticleRegressionTest {
    private fun asset(path: String): String {
        val candidates = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $path from ${File(".").absolutePath}")
    }

    @Test
    fun landingTargetGetsCenteredTvReadableReticleWithoutPolling() {
        val script = asset("shot_map_target_reticle.gd")
        assertTrue(script.contains("find_child(\"ShotMapCorrectionTarget\", true, false)"))
        assertTrue(script.contains("TargetReticleHorizontal"))
        assertTrue(script.contains("TargetReticleVertical"))
        assertTrue(script.contains("target.add_child(horizontal)"))
        assertTrue(script.contains("target.add_child(vertical)"))
        assertFalse(script.contains("func _process("))
        assertFalse(script.contains("Timer.new()"))
    }

    @Test
    fun reticleRemainsPresentationOnlyAndProductionWired() {
        val script = asset("shot_map_target_reticle.gd")
        val scene = asset("v143_tv.tscn")
        assertTrue(scene.contains("res://shot_map_target_reticle.gd"))
        assertTrue(scene.contains("ShotMapTargetReticle"))
        assertFalse(script.contains("GreenTerrain" + ".set"))
        assertFalse(script.contains("GreenReadAdvisor" + ".set"))
        assertFalse(script.contains("ballVelocity"))
        assertFalse(script.contains("readLineDeltaCm ="))
        assertFalse(script.contains("paceDeltaCm ="))
    }
}
