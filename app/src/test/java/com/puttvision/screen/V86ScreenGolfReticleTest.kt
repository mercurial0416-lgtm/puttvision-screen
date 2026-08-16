package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V86ScreenGolfReticleTest {
    @Test fun reliableReadProducesAssistedReticle() {
        val p = V86ScreenGolfReticlePlanner.plan(5.0, 4.0, true, false, false, false)
        assertTrue(p.visible)
        assertTrue(p.assisted)
        assertEquals(4.0, p.aimAngleDeg, 1e-9)
        assertTrue(p.aimOffsetM > 0.0)
        assertTrue(p.label.contains("R "))
    }

    @Test fun leftReadKeepsLeftSign() {
        val p = V86ScreenGolfReticlePlanner.plan(4.0, -3.0, true, false, false, false)
        assertTrue(p.visible)
        assertTrue(p.aimOffsetM < 0.0)
        assertTrue(p.label.contains("L "))
    }

    @Test fun hiddenSolutionUsesNeutralCenterWithoutLeakingRead() {
        val p = V86ScreenGolfReticlePlanner.plan(6.0, 8.0, true, true, false, false)
        assertTrue(p.visible)
        assertFalse(p.assisted)
        assertEquals(0.0, p.aimAngleDeg, 0.0)
        assertEquals(0.0, p.aimOffsetM, 0.0)
        assertEquals("TARGET · CENTER", p.label)
    }

    @Test fun unreliableSolverFallsBackToCenter() {
        val p = V86ScreenGolfReticlePlanner.plan(7.0, 5.0, false, false, false, false)
        assertTrue(p.visible)
        assertFalse(p.assisted)
        assertEquals(0.0, p.aimOffsetM, 0.0)
    }

    @Test fun excessiveAngleIsBounded() {
        val p = V86ScreenGolfReticlePlanner.plan(3.0, 89.0, true, false, false, false)
        assertTrue(p.visible)
        assertEquals(18.0, p.aimAngleDeg, 0.0)
        assertTrue(kotlin.math.abs(p.aimOffsetM) <= 3.0 * .42 + 1e-9)
    }

    @Test fun reticleHidesWhileShotRunsOrAfterResult() {
        assertFalse(V86ScreenGolfReticlePlanner.plan(5.0, 2.0, true, false, true, false).visible)
        assertFalse(V86ScreenGolfReticlePlanner.plan(5.0, 2.0, true, false, false, true).visible)
    }

    @Test fun nonFiniteAndOutOfRangeDistanceFailClosed() {
        assertFalse(V86ScreenGolfReticlePlanner.plan(Double.NaN, 0.0, true, false, false, false).visible)
        assertFalse(V86ScreenGolfReticlePlanner.plan(100.0, 0.0, true, false, false, false).visible)
    }

    @Test fun nonFiniteRecommendationNeverPoisonsGeometry() {
        val p = V86ScreenGolfReticlePlanner.plan(5.0, Double.NaN, true, false, false, false)
        assertTrue(p.visible)
        assertFalse(p.assisted)
        assertTrue(p.aimAngleDeg.isFinite())
        assertTrue(p.aimOffsetM.isFinite())
    }
}
