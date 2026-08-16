package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class V89ScreenGolfVisualPhysicsTest {
    @Test
    fun stationaryBallHasNoMotionBlur() {
        val plan = V89VisualPhysicsPlanner.plan(0.0, 0.0)
        assertEquals(0, plan.blurSamples)
        assertEquals(0f, plan.blurStrength, 1e-6f)
        assertEquals(0f, plan.spinDegrees, 1e-6f)
        assertEquals(0f, plan.focusStrength, 1e-6f)
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
        assertEquals(90f, V89VisualPhysicsPlanner.plan(.7, circumference * .25).spinDegrees, .01f)
        assertEquals(180f, V89VisualPhysicsPlanner.plan(.7, circumference * .50).spinDegrees, .01f)
    }

    @Test
    fun visualPhysicsRemainsBoundedAtExtremeSpeed() {
        val plan = V89VisualPhysicsPlanner.plan(99.0, 1000.0)
        assertTrue(plan.blurStrength in 0f..1f)
        assertTrue(plan.focusStrength in 0f..0.62f)
        assertTrue(plan.cometLengthM in .025..0.24)
        assertTrue(plan.dimpleAlpha in 42..110)
        assertTrue(plan.spinDegrees >= 0f && plan.spinDegrees < 360f)
    }

    @Test
    fun invalidVisualInputsFailSafeInsteadOfCreatingNanEffects() {
        val plan = V89VisualPhysicsPlanner.plan(Double.NaN, Double.POSITIVE_INFINITY)
        assertEquals(0.0, plan.speedMps, 0.0)
        assertEquals(0f, plan.spinDegrees, 0f)
        assertEquals(0, plan.blurSamples)
        assertTrue(plan.shadowStretch.isFinite())
        assertTrue(plan.highlightStrength.isFinite())
        assertTrue(plan.focusStrength.isFinite())
        assertTrue(plan.cometLengthM.isFinite())
        assertTrue(plan.dimpleAlpha in 42..110)
    }
}
