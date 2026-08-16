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
        assertEquals(0f, p.focusStrength, 1e-6f)
    }

    @Test
    fun fasterBallGetsStrongerVisualMotionCues() {
        val slow = V89VisualPhysicsPlanner.plan(.25, .10)
        val fast = V89VisualPhysicsPlanner.plan(1.8, .10)
        assertTrue(fast.blurStrength > slow.blurStrength)
        assertTrue(fast.blurSamples > slow.blurSamples)
        assertTrue(fast.shadowStretch > slow.shadowStretch)
        assertTrue(fast.highlightStrength > slow.highlightStrength)
        assertTrue(fast.focusStrength > slow.focusStrength)
        assertTrue(fast.cometLengthM > slow.cometLengthM)
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
    fun visualPhysicsRemainsBoundedAtExtremeSpeed() {
        val p = V89VisualPhysicsPlanner.plan(99.0, 1000.0)
        assertTrue(p.blurStrength in 0f..1f)
        assertTrue(p.focusStrength in 0f..0.62f)
        assertTrue(p.cometLengthM in .025..0.24)
        assertTrue(p.dimpleAlpha in 42..110)
        assertTrue(p.spinDegrees >= 0f && p.spinDegrees < 360f)
    }

    @Test
    fun invalidVisualInputsFailSafeInsteadOfCreatingNanEffects() {
        val p = V89VisualPhysicsPlanner.plan(Double.NaN, Double.POSITIVE_INFINITY)
        assertEquals(0.0, p.speedMps, 0.0)
        assertEquals(0f, p.spinDegrees, 0f)
        assertEquals(0, p.blurSamples)
        assertTrue(p.shadowStretch.isFinite())
        assertTrue(p.highlightStrength.isFinite())
        assertTrue(p.focusStrength.isFinite())
        assertTrue(p.cometLengthM.isFinite())
        assertTrue(p.dimpleAlpha in 42..110)
    }
}
