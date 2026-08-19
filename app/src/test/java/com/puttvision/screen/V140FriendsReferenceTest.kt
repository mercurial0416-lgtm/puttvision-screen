package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V140FriendsReferenceTest {
    @Test
    fun addressViewIsLowCenteredAndReferenceFov() {
        val frame = V140FriendsCameraPlanner.target(
            distanceMRaw = 5.0,
            startXRaw = 0.0,
            startYRaw = 0.0,
            ballXRaw = 0.0,
            ballYRaw = 0.0,
            state = null,
            result = null
        )
        assertTrue(abs(frame.eyeX) < 1e-6)
        assertTrue(frame.eyeZ in .70f.. .80f)
        assertEquals(V140FriendsReference.ADDRESS_FOV_DEG, frame.fovDeg, .001f)
        assertTrue(frame.lookY > 3.5f)
    }

    @Test
    fun liveCameraRemainsTargetLockedRatherThanWideBroadcastOrbit() {
        val state = SimState(x = .12, y = 2.3, vx = 0.0, vy = .8, running = true)
        val frame = V140FriendsCameraPlanner.target(5.0, 0.0, 0.0, .12, 2.3, state, null)
        assertTrue(abs(frame.eyeX) < .08f)
        assertTrue(frame.fovDeg in 35f..39f)
        assertTrue(frame.lookY > state.y)
    }

    @Test
    fun rimAndDropUseLowFrontCupView() {
        val state = SimState(x = .01, y = 4.99, running = true)
        state.cupPhase = V134CupPhase.RIM
        val frame = V140FriendsCameraPlanner.target(5.0, 0.0, 0.0, .01, 4.99, state, null)
        assertTrue(frame.eyeZ < .40f)
        assertTrue(frame.fovDeg < 33f)
        assertTrue(abs(frame.lookX) < .01f)
    }
}
