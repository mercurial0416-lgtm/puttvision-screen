package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V38ArStabilityTest {
    private val base = listOf(
        V38Point(100f, 700f), V38Point(900f, 700f),
        V38Point(820f, 120f), V38Point(180f, 120f)
    )

    private fun shifted(dx: Float, dy: Float) = base.map { V38Point(it.x + dx, it.y + dy) }

    @Test fun normalPixelJitterLocksWithoutFollowingEveryPixel() {
        val s = V38CornerStabilizer()
        val first = requireNotNull(s.update(base, 1000, 800))
        assertFalse(first.stable)
        val second = requireNotNull(s.update(shifted(1f, -1f), 1000, 800))
        assertTrue(second.stable)
        assertTrue(second.points[0].x in 100f..101f)
        assertTrue(second.stability > .8)
    }

    @Test fun largeCameraMoveNeedsThreeConsistentFramesThenReacquires() {
        val s = V38CornerStabilizer()
        s.update(base, 1000, 800)
        s.update(base, 1000, 800)
        val moved = shifted(70f, 35f)
        assertFalse(requireNotNull(s.update(moved, 1000, 800)).stable)
        assertFalse(requireNotNull(s.update(moved, 1000, 800)).stable)
        val third = requireNotNull(s.update(moved, 1000, 800))
        assertTrue(third.stable)
        assertTrue(third.reacquired)
        assertEquals(170f, third.points[0].x, .5f)
    }

    @Test fun pathSmoothingOnlyAppliesWithinSameGreenConfiguration() {
        val p = V38PathStabilizer()
        val first = listOf(V38Point(10f, 10f), V38Point(20f, 20f))
        val next = listOf(V38Point(20f, 20f), V38Point(30f, 30f))
        assertEquals(first, p.update(first, "green-a"))
        val smooth = p.update(next, "green-a")
        assertTrue(smooth[0].x in 13f..14f)
        val reset = p.update(next, "green-b")
        assertEquals(next, reset)
    }

    @Test fun inconsistentLargeMoveNeverReacquires() {
        val s = V38CornerStabilizer()
        s.update(base, 1000, 800)
        s.update(base, 1000, 800)
        assertFalse(requireNotNull(s.update(shifted(60f, 30f), 1000, 800)).stable)
        assertFalse(requireNotNull(s.update(shifted(-60f, -30f), 1000, 800)).stable)
        assertFalse(requireNotNull(s.update(shifted(80f, -25f), 1000, 800)).stable)
    }
}
