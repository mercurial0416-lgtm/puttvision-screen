package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenReadTrajectoryRegressionTest {
    @Test
    fun solverReturnsPhysicalTrajectoryAndLaunch() {
        val settings = GreenSettings(
            stimpMeters = 2.8,
            holeDistanceM = 5.0,
            sideSlopePct = 0.8,
            longSlopePct = -0.3,
            terrainProfileId = 12
        )
        val read = GreenReadAdvisor.read(settings)
        assertTrue(read.recommendedBallSpeedMps > 0.1)
        assertTrue(read.recommendedLaunchAngleDeg in -35.0..35.0)
        assertTrue(read.predictedTrail.size >= 2)
        assertTrue(read.predictedTrail.first().second <= 0.05)
        assertTrue(read.solverMissCm.isFinite())
    }
}
