package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V125ScreenGolfHudSafetyTest {
    @Test fun targetAndSurfaceInputsAreBounded() {
        assertEquals(.20, V125ScreenGolfHudSafety.targetM(Double.NaN), 0.0)
        assertEquals(.20, V125ScreenGolfHudSafety.targetM(-9.0), 0.0)
        assertEquals(33.0, V125ScreenGolfHudSafety.targetM(10_000.0), 0.0)
        assertEquals(20.0, V125ScreenGolfHudSafety.stimpM(99.0), 0.0)
        assertEquals(-20.0, V125ScreenGolfHudSafety.slopePct(-99.0), 0.0)
    }

    @Test fun normalizedMapCoordinatesFailSafe() {
        assertEquals(0.0, V125ScreenGolfHudSafety.normalizedProgress(Double.NaN, 3.0), 0.0)
        assertEquals(1.0, V125ScreenGolfHudSafety.normalizedProgress(99.0, 3.0), 0.0)
        assertEquals(-1.0, V125ScreenGolfHudSafety.normalizedLateral(-99.0), 0.0)
        assertEquals(0.0, V125ScreenGolfHudSafety.normalizedLateral(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test fun liveMetricsCannotExplodeTheHud() {
        assertEquals(0.0, V125ScreenGolfHudSafety.liveRemainingM(Double.NaN, 1.0, 3.0), 0.0)
        assertEquals(50.0, V125ScreenGolfHudSafety.liveRemainingM(500.0, 500.0, 3.0), 0.0)
        assertEquals(0.0, V125ScreenGolfHudSafety.ballSpeedMps(Double.NaN, 1.0), 0.0)
        assertEquals(8.0, V125ScreenGolfHudSafety.ballSpeedMps(99.0, 99.0), 0.0)
        assertEquals(0.0, V125ScreenGolfHudSafety.resultDistanceCm(-1.0), 0.0)
        assertEquals(5000.0, V125ScreenGolfHudSafety.resultDistanceCm(99.0), 0.0)
    }

    @Test fun malformedTrailPointsAreDiscardedAndBudgeted() {
        val points = buildList {
            add(0.0 to 0.0)
            add(Double.NaN to 1.0)
            for (i in 1..100) add(i / 10.0 to i / 4.0)
            add(0.0 to Double.POSITIVE_INFINITY)
        }
        val sampled = V125ScreenGolfHudSafety.trailSamples(points)
        assertTrue(sampled.size <= V125ScreenGolfHudSafety.MAX_TRAIL_POINTS)
        assertEquals(0.0 to 0.0, sampled.first())
        assertEquals(10.0 to 25.0, sampled.last())
        assertTrue(sampled.all { it.first.isFinite() && it.second.isFinite() })
    }

    @Test fun shortValidTrailKeepsOriginalOrdering() {
        val points = listOf(0.0 to 0.0, .2 to 1.0, .3 to 2.0)
        assertEquals(points, V125ScreenGolfHudSafety.trailSamples(points))
    }

    @Test fun qualitySnapshotSamplingIsBoundedAndClockSafe() {
        assertTrue(V125ScreenGolfHudSafety.qualityCacheRefreshDue(1000L, 0L))
        assertFalse(V125ScreenGolfHudSafety.qualityCacheRefreshDue(1500L, 1000L))
        assertTrue(V125ScreenGolfHudSafety.qualityCacheRefreshDue(1900L, 1000L))
        assertTrue(V125ScreenGolfHudSafety.qualityCacheRefreshDue(900L, 1000L))
    }
}
