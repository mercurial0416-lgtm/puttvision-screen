package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V139FriendsReferenceTest {
    @Test
    fun addressViewIsCenteredAndLessCinematicThanV138() {
        val frame = V139FriendsCameraPlanner.target(
            distanceMRaw = 5.0,
            startXRaw = 0.0,
            startYRaw = 0.0,
            ballXRaw = 0.0,
            ballYRaw = 0.0,
            state = null,
            result = null
        )
        assertEquals(0f, frame.eyeX, 0.0001f)
        assertEquals(0f, frame.lookX, 0.0001f)
        assertEquals(V139FriendsReference.ADDRESS_FOV_DEG, frame.fovDeg, 0.0001f)
        assertTrue(frame.eyeZ in 1.0f..1.2f)
        assertTrue(frame.lookY > 3.0f)
    }

    @Test
    fun cupHeroKeepsTargetCenteredAndLow() {
        val state = SimState(
            x = 0.01,
            y = 4.95,
            vx = 0.0,
            vy = 0.05,
            running = true,
            cupPhase = V134CupPhase.RIM
        )
        val frame = V139FriendsCameraPlanner.target(5.0, 0.0, 0.0, state.x, state.y, state, null)
        assertEquals(0f, frame.lookX, 0.0001f)
        assertEquals(5f, frame.lookY, 0.0001f)
        assertTrue(frame.eyeZ < 0.5f)
        assertTrue(frame.fovDeg <= 34.5f)
    }

    @Test
    fun smootherMovesWithoutSnapping() {
        val smoother = V139FriendsCameraSmoother()
        val a = V133CameraFrame(0f, -2f, 1f, 0f, 3f, .1f, 41f)
        val b = V133CameraFrame(.5f, 3.8f, .4f, 0f, 5f, 0f, 34f)
        smoother.reset(a)
        val next = smoother.step(b, cupAction = false)
        assertTrue(next.eyeY > a.eyeY)
        assertTrue(next.eyeY < b.eyeY)
        assertTrue(next.fovDeg < a.fovDeg)
        assertTrue(next.fovDeg > b.fovDeg)
    }
}
