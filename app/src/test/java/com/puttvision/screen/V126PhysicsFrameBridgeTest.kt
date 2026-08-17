package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class V126PhysicsFrameBridgeTest {
    @Test
    fun snapshotIsDetachedFromMutablePhysicsState() {
        val source = SimState(
            x = .12,
            y = .34,
            vx = .4,
            vy = 1.1,
            running = true,
            trail = mutableListOf(.12 to .34)
        )
        val frame = requireNotNull(V126PhysicsFrameBridge.snapshot(source))

        assertNotSame(source, frame)
        assertNotSame(source.trail, frame.trail)

        source.x = 2.0
        source.y = 3.0
        source.trail += 2.0 to 3.0

        assertEquals(.12, frame.x, 1e-9)
        assertEquals(.34, frame.y, 1e-9)
        assertEquals(1, frame.trail.size)
    }

    @Test
    fun successivePhysicsStepsPublishVisibleForwardMotion() {
        val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0)
        val metrics = ShotMetrics(
            ballSpeedMps = 1.25,
            launchAngleDeg = 0.0,
            headSpeedMps = null,
            faceAngleDeg = null,
            pathAngleDeg = null,
            faceToPathDeg = null,
            smash = null,
            impactOffsetMm = null,
            measuredAtNs = 1L
        )
        val physics = GreenPhysics()
        val authoritative = physics.launch(metrics, settings)
        val first = requireNotNull(V126PhysicsFrameBridge.snapshot(authoritative))

        physics.step(authoritative, settings, .016)
        val second = requireNotNull(V126PhysicsFrameBridge.snapshot(authoritative))
        physics.step(authoritative, settings, .016)
        val third = requireNotNull(V126PhysicsFrameBridge.snapshot(authoritative))

        assertTrue("first rendered physics frame should move forward", second.y > first.y)
        assertTrue("successive rendered physics frame should keep moving", third.y > second.y)
        assertTrue("rolling friction should reduce forward velocity", third.vy < first.vy)
        assertEquals("old frame must stay immutable after later physics steps", 0.0, first.y, 1e-9)
    }
}
