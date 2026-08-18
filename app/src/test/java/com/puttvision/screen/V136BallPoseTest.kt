package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class V136BallPoseTest {
    @Test
    fun identityPoseCarriesAuthoritativeCenterOfMassHeight() {
        val state = SimState(
            ballCenterZM = -0.061,
            orientationW = 1.0,
            orientationX = 0.0,
            orientationY = 0.0,
            orientationZ = 0.0
        )
        val m = V136BallPose.matrix(state, .12, 1.9, .5)
        assertEquals(1f, m[0], 1e-6f)
        assertEquals(1f, m[5], 1e-6f)
        assertEquals(1f, m[10], 1e-6f)
        assertEquals(.12f, m[12], 1e-6f)
        assertEquals(1.9f, m[13], 1e-6f)
        assertEquals((-0.061 + 0.020).toFloat(), m[14], 1e-6f)
    }

    @Test
    fun quaternionProducesRealRotationNotTranslationAnimation() {
        val half = Math.PI / 4.0
        val state = SimState(
            ballCenterZM = .02135,
            orientationW = cos(half),
            orientationX = sin(half),
            orientationY = 0.0,
            orientationZ = 0.0
        )
        val m = V136BallPose.matrix(state, 0.0, 0.0, .0)
        assertTrue("90deg X rotation should move local Y onto world Z", kotlin.math.abs(m[6] - 1f) < 1e-5f)
        assertTrue("rotation block must remain orthonormal", kotlin.math.abs(m[5]) < 1e-5f && kotlin.math.abs(m[10]) < 1e-5f)
    }

    @Test
    fun malformedQuaternionFallsBackToSafeIdentity() {
        val state = SimState(
            ballCenterZM = .02135,
            orientationW = Double.NaN,
            orientationX = Double.NaN,
            orientationY = Double.NaN,
            orientationZ = Double.NaN
        )
        val m = V136BallPose.matrix(state, 0.0, 0.0, .1)
        assertEquals(1f, m[0], 1e-6f)
        assertEquals(1f, m[5], 1e-6f)
        assertEquals(1f, m[10], 1e-6f)
    }
}
