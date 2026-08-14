package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V50ArPathOutlierTest {
    private val base = listOf(
        V38Point(100f, 600f), V38Point(170f, 500f), V38Point(240f, 400f),
        V38Point(310f, 300f), V38Point(380f, 200f)
    )

    private fun shifted(dx: Float, dy: Float) = base.map { V38Point(it.x + dx, it.y + dy) }

    @Test fun oneWildSolverFrameIsHeldInsteadOfBlendedIntoVisiblePath() {
        val s = V38PathStabilizer()
        assertEquals(base, s.update(base, "green"))
        val wild = shifted(240f, -180f)
        val held = s.update(wild, "green")
        assertEquals(base, held)

        val normal = shifted(3f, 2f)
        val resumed = s.update(normal, "green")
        assertTrue(resumed.first().x in 100f..102f)
        assertTrue(resumed.first().y in 600f..601f)
    }

    @Test fun repeatedConsistentLargePathChangeIsEventuallyAdopted() {
        val s = V38PathStabilizer()
        s.update(base, "green")
        val moved = shifted(180f, 120f)
        assertEquals(base, s.update(moved, "green"))
        val adopted = s.update(moved, "green")
        assertTrue(adopted.first().x > 250f)
        assertTrue(adopted.first().y > 690f)
    }

    @Test fun inconsistentLargeJumpsNeverPolluteStablePath() {
        val s = V38PathStabilizer()
        s.update(base, "green")
        assertEquals(base, s.update(shifted(220f, 0f), "green"))
        assertEquals(base, s.update(shifted(-220f, 0f), "green"))
        assertEquals(base, s.update(shifted(0f, 220f), "green"))
    }

    @Test fun newGreenStillSnapsImmediatelyWithoutOutlierHold() {
        val s = V38PathStabilizer()
        s.update(base, "green-a")
        val next = shifted(300f, 150f)
        assertEquals(next, s.update(next, "green-b"))
    }
}
