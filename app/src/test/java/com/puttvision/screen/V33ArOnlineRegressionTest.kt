package com.puttvision.screen

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V33ArOnlineRegressionTest {
    @Test
    fun homographyInverseRoundTripKeepsMatCoordinates() {
        val image = listOf(
            PointF(120f, 820f),
            PointF(920f, 780f),
            PointF(760f, 180f),
            PointF(260f, 210f)
        )
        val real = listOf(
            PointF(-22.5f, 0f),
            PointF(22.5f, 0f),
            PointF(22.5f, 100f),
            PointF(-22.5f, 100f)
        )
        val h = Homography.fromFourPoints(image, real)!!
        val targets = listOf(
            PointF(0f, 0f),
            PointF(4.5f, 25f),
            PointF(-8f, 57f),
            PointF(0f, 95f)
        )
        targets.forEach { world ->
            val camera = h.inverseMap(world)!!
            val roundTrip = h.map(camera)
            assertEquals(world.x.toDouble(), roundTrip.x.toDouble(), 0.03)
            assertEquals(world.y.toDouble(), roundTrip.y.toDouble(), 0.03)
        }
    }

    @Test
    fun onlineCourseSeedsAreStablePerMatchAndHole() {
        val baseA = V33OnlineCourseSeed.fromMatchId("11111111-2222-3333-4444-555555555555")
        val baseB = V33OnlineCourseSeed.fromMatchId("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        assertEquals(baseA, V33OnlineCourseSeed.fromMatchId("11111111-2222-3333-4444-555555555555"))
        assertNotEquals(baseA, baseB)
        val hole1 = V33OnlineCourseSeed.forHole(baseA, 1)
        assertEquals(hole1, V33OnlineCourseSeed.forHole(baseA, 1))
        assertNotEquals(hole1, V33OnlineCourseSeed.forHole(baseA, 2))
        assertTrue(hole1 != 0 || V33OnlineCourseSeed.forHole(baseA, 2) != 0)
    }
}
