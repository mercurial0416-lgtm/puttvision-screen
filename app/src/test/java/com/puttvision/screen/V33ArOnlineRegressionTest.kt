package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V33ArOnlineRegressionTest {
    @Test
    fun onlineCourseSeedsAreStablePerMatchAndHole() {
        val matchA = "11111111-2222-3333-4444-555555555555"
        val matchB = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val baseA = V33OnlineCourseSeed.fromMatchId(matchA)
        val baseB = V33OnlineCourseSeed.fromMatchId(matchB)
        assertEquals(baseA, V33OnlineCourseSeed.fromMatchId(matchA))
        assertNotEquals(baseA, baseB)

        val hole1 = V33OnlineCourseSeed.forHole(baseA, 1)
        val hole2 = V33OnlineCourseSeed.forHole(baseA, 2)
        assertEquals(hole1, V33OnlineCourseSeed.forHole(baseA, 1))
        assertNotEquals(hole1, hole2)
        assertTrue(hole1 != 0 || hole2 != 0)
    }

    @Test
    fun invalidHoleNumbersNormalizeToFirstHole() {
        val seed = V33OnlineCourseSeed.fromMatchId("11111111-2222-3333-4444-555555555555")
        assertEquals(V33OnlineCourseSeed.forHole(seed, 1), V33OnlineCourseSeed.forHole(seed, 0))
        assertEquals(V33OnlineCourseSeed.forHole(seed, 1), V33OnlineCourseSeed.forHole(seed, -5))
    }
}
