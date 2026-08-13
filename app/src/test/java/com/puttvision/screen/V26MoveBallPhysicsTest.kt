package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class V26MoveBallPhysicsTest {
    @Test fun physicsLaunchUsesVirtualStartCoordinates() {
        val metrics = ShotMetrics(1.0, 0.0, null, null, null, null, null, null, 1L)
        val state = GreenPhysics().launch(metrics, GreenSettings(), startX = .35, startY = 1.20)
        assertEquals(.35, state.x, 1e-9)
        assertEquals(1.20, state.y, 1e-9)
        assertEquals(.35, state.trail.first().first, 1e-9)
        assertEquals(1.20, state.trail.first().second, 1e-9)
    }
}
