package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V113BroadcastTrailPlannerTest {
    @Test fun longTrailIsEvenlyBoundedAndPreservesBothEnds() {
        val raw = (0 until 1000).map { i -> (i / 1000.0) to (i / 40.0) }
        val sampled = V113BroadcastTrailPlanner.sample(raw, holeDistanceM = 25.0, cap = 120)

        assertEquals(120, sampled.size)
        assertEquals(raw.first(), sampled.first())
        assertEquals(raw.last(), sampled.last())
        assertTrue(sampled.zipWithNext().all { (a, b) -> b.second >= a.second })
    }

    @Test fun malformedWorldCoordinatesAreRejectedBeforeProjection() {
        val sampled = V113BroadcastTrailPlanner.sample(
            listOf(
                0.0 to 0.0,
                Double.NaN to 1.0,
                999.0 to 2.0,
                0.0 to Double.POSITIVE_INFINITY,
                0.2 to 4.0
            ),
            holeDistanceM = 4.0,
            cap = 20
        )

        assertEquals(listOf(0.0 to 0.0, 0.2 to 4.0), sampled)
        assertTrue(sampled.all { (x, y) -> x.isFinite() && y.isFinite() })
    }

    @Test fun nearDuplicateAdjacentPointsAreCollapsed() {
        val sampled = V113BroadcastTrailPlanner.sample(
            listOf(
                0.0 to 0.0,
                .0001 to .0001,
                .0002 to .0002,
                .01 to .01,
                .02 to .02
            ),
            holeDistanceM = 3.0,
            cap = 20
        )

        assertEquals(3, sampled.size)
        assertEquals(0.0 to 0.0, sampled.first())
        assertEquals(.02 to .02, sampled.last())
    }

    @Test fun invalidHoleDistanceOrTinyCapFailsClosed() {
        val raw = listOf(0.0 to 0.0, 0.0 to 1.0)
        assertTrue(V113BroadcastTrailPlanner.sample(raw, Double.NaN, 20).isEmpty())
        assertTrue(V113BroadcastTrailPlanner.sample(raw, 0.0, 20).isEmpty())
        assertTrue(V113BroadcastTrailPlanner.sample(raw, 3.0, 1).isEmpty())
    }

    @Test fun legitimateShortTrailIsNotResampled() {
        val raw = listOf(0.0 to 0.0, .05 to 1.0, .12 to 2.0, .18 to 3.0)
        val sampled = V113BroadcastTrailPlanner.sample(raw, holeDistanceM = 3.0, cap = 20)
        assertEquals(raw, sampled)
    }

    @Test fun samplingNeverExceedsBudget() {
        val raw = (0 until 600).map { i -> .2 to (i / 30.0) }
        val sampled = V113BroadcastTrailPlanner.sample(raw, holeDistanceM = 20.0, cap = 180)
        assertTrue(sampled.size <= 180)
        assertFalse(sampled.isEmpty())
    }
}
