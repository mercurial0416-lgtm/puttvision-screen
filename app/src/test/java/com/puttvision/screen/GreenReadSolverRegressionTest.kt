package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadSolverRegressionTest {
    @Test fun flatGreenAimsNearCenter() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0))
        assertTrue(kotlin.math.abs(read.aimOffsetCm) < 12.0)
        assertTrue(read.recommendedBallSpeedMps in 0.3..5.0)
    }

    @Test fun rightBreakRequiresLeftAim() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0, sideSlopePct = 1.8))
        assertTrue(read.aimOffsetCm < -1.0)
    }

    @Test fun leftBreakRequiresRightAim() {
        val read = GreenReadAdvisor.read(GreenSettings(stimpMeters = 2.8, holeDistanceM = 5.0, sideSlopePct = -1.8))
        assertTrue(read.aimOffsetCm > 1.0)
    }
}
