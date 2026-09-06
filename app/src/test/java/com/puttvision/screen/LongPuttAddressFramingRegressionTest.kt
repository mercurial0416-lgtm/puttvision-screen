package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPuttAddressFramingRegressionTest {
    private fun asset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate asset $name")
    }

    @Test
    fun longPuttsOpenTheStationaryAddressCompositionProgressively() {
        val source = asset("long_putt_address_framing.gd")
        assertTrue(source.contains("LONG_FRAME_START_M := 6.0"))
        assertTrue(source.contains("LONG_FRAME_FULL_M := 14.0"))
        assertTrue(source.contains("smoothstep(LONG_FRAME_START_M, LONG_FRAME_FULL_M, distance_m)"))
        assertTrue(source.contains("LONG_FRAME_TRAIL_EXTRA_M * signal"))
        assertTrue(source.contains("LONG_FRAME_HEIGHT_EXTRA_M * signal"))
        assertTrue(source.contains("LONG_FRAME_FOV_EXTRA_DEG * signal"))
    }

    @Test
    fun shortAndInvalidDistancesPreserveTheExistingAddressPlan() {
        val source = asset("long_putt_address_framing.gd")
        assertTrue(source.contains("if not is_finite(distance_m) or distance_m <= LONG_FRAME_START_M:"))
        assertTrue(source.contains("if signal <= 0.0:\n        return plan"))
        assertTrue(source.contains("var plan := super._address_relief_camera_plan(ball_world, distance_to_cup)"))
    }

    @Test
    fun longerLookRemainsBoundedAndNeverTurnsIntoAimOrPhysicsState() {
        val source = asset("long_putt_address_framing.gd")
        assertTrue(source.contains("current_fraction + LONG_FRAME_LOOK_EXTRA * signal"))
        assertTrue(source.contains("current_fraction, 0.72"))
        assertTrue(source.contains("plan[\"look_fraction\"] = desired_fraction"))
        assertFalse(source.contains("GreenTerrain" + ".set"))
        assertFalse(source.contains("GreenReadAdvisor" + ".set"))
        assertFalse(source.contains("recommendedAim"))
        assertFalse(source.contains("score ="))
        assertFalse(source.contains("func _process("))
        assertFalse(source.contains("func _physics_process("))
    }

    @Test
    fun productionReliefStackInheritsTheLongPuttFramingLayer() {
        val relief = asset("relief_depth_finish.gd")
        val layer = asset("long_putt_address_framing.gd")
        assertTrue(relief.contains("extends \"res://long_putt_address_framing.gd\""))
        assertTrue(layer.contains("extends \"res://address_relief_camera.gd\""))
    }
}
