package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V142PlayableRenderTest {
    @Test
    fun physicsStateAlwaysBeatsPreSimulationPresentationHandoff() {
        val p = V142RenderContract.authoritativeBallPosition(
            stateX = 0.37,
            stateY = 2.41,
            startX = 0.0,
            startY = 0.0
        )
        assertEquals(0.37, p.first, 1e-12)
        assertEquals(2.41, p.second, 1e-12)
    }

    @Test
    fun missingStateFallsBackToConfiguredStart() {
        val p = V142RenderContract.authoritativeBallPosition(null, null, -0.2, 0.4)
        assertEquals(-0.2, p.first, 1e-12)
        assertEquals(0.4, p.second, 1e-12)
    }

    @Test
    fun targetPinRemainsVisibleWhenPhysicalFlagstickIsOut() {
        assertTrue(V142RenderContract.showTargetPin())
    }
}
