package com.puttvision.screen

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V27PaceSolverTest {
    @Test fun paceIsPartOfGreenReadKey() {
        val s = GreenSettings(holeDistanceM = 4.0, stimpMeters = 2.8, sideSlopePct = 1.4, longSlopePct = .5)
        assertNotEquals(GreenReadAdvisor.key(s, .25), GreenReadAdvisor.key(s, .85))
    }

    @Test fun firmerCupPaceRequiresMoreLaunchSpeed() {
        val s = GreenSettings(holeDistanceM = 3.0, stimpMeters = 2.8, sideSlopePct = 1.2, longSlopePct = .3, terrainProfileId = -1)
        val soft = GreenReadAdvisor.read(s, .25)
        val firm = GreenReadAdvisor.read(s, .85)
        assertTrue("firm pace should need more launch speed", firm.recommendedBallSpeedMps > soft.recommendedBallSpeedMps)
    }
}
