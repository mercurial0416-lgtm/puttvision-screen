package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class V89ScreenGolfVisualPhysicsTest {
    @Test
    fun stationaryBallHasNoMotionBlur() {
        val p = V89VisualPhysicsPlanner.plan(0.0, 0.0)
        assertEquals(0, p.blurSamples)
        assertEquals(0f, p.blurStrength, 1e-6f)
        assertEquals(0f, p.spinDegrees, 1e-6f)
    }

    @Test
    fun fasterBallGetsStrongerVisualMotionCues() {
        val slow = V89VisualPhysicsPlanner.plan(.25, .10)
        val fast = V89VisualPhysicsPlanner.plan(1.8, .10)
        assertTrue(fast.blurStrength > slow.blurStrength)
        assertTrue(fast.blurSamples > slow.blurSamples)
        assertTrue(fast.shadowStretch > slow.shadowStretch)
        assertTrue(fast.highlightStrength > slow.highlightStrength)
    }

    @Test
    fun ballRotationComesFromTravelDistanceNotFrameCount() {
        val circumference = 2.0 * PI * V89VisualPhysicsPlanner.BALL_RADIUS_M
        val quarter = V89VisualPhysicsPlanner.plan(.7, circumference * .25)
        val half = V89VisualPhysicsPlanner.plan(.7, circumference * .50)
        assertEquals(90f, quarter.spinDegrees, .01f)
        assertEquals(180f, half.spinDegrees, .01f)
    }

    @Test
    fun invalidVisualInputsFailSafeInsteadOfCreatingNanEffects() {
        val p = V89VisualPhysicsPlanner.plan(Double.NaN, Double.POSITIVE_INFINITY)
        assertEquals(0.0, p.speedMps, 0.0)
        assertEquals(0f, p.spinDegrees, 0f)
        assertEquals(0, p.blurSamples)
        assertTrue(p.shadowStretch.isFinite())
        assertTrue(p.highlightStrength.isFinite())
    }
}
