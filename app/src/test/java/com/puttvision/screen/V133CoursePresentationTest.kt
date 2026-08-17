package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V133CoursePresentationTest {
    @Test
    fun idleFiveMeterCameraIsRaisedAndPulledBack() {
        val frame = V133CameraPlanner.plan(
            distanceMRaw = 5.0,
            startXRaw = 0.0,
            startYRaw = 0.0,
            ballXRaw = 0.0,
            ballYRaw = 0.0,
            running = false,
            result = null
        )
        assertTrue(frame.eyeZ >= 1.6f)
        assertTrue(frame.eyeY <= -3.8f)
        assertTrue(frame.fovDeg <= 39f)
        assertTrue(frame.lookY >= 3.4f)
    }

    @Test
    fun renderedBallUsesPhysicalGolfBallRadius() {
        assertEquals(0.02135f, V133CourseSpec.BALL_RADIUS_M, 0.00001f)
    }
}
