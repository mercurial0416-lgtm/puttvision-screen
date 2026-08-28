package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class GreenPhysicsRegressionTest {
    private fun roll(settings: GreenSettings, speed: Double = 1.0, launchDeg: Double = 0.0): SimResult {
        val physics = GreenPhysics()
        val metrics = ShotMetrics(
            ballSpeedMps = speed,
            launchAngleDeg = launchDeg,
            headSpeedMps = null,
            faceAngleDeg = null,
            pathAngleDeg = null,
            faceToPathDeg = null,
            smash = null,
            impactOffsetMm = null,
            measuredAtNs = 1L
        )
        val state = physics.launch(metrics, settings)
        repeat(6000) {
            val result = physics.step(state, settings, 0.01)
            if (result != null) return result
        }
        error("simulation did not finish")
    }

    @Test
    fun flatGreenRollDistanceStaysInPhysicalRange() {
        val result = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0))
        assertTrue("flat roll should move forward", result.finishY > 0.60)
        assertTrue("flat roll should not travel implausibly far", result.finishY < 0.90)
        assertTrue("flat roll should remain near center", kotlin.math.abs(result.finishX) < 0.02)
    }

    @Test
    fun positiveSideSlopeBreaksRight() {
        val flat = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0), speed = 1.25)
        val right = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, sideSlopePct = 1.0), speed = 1.25)
        assertTrue("right-lower slope should produce positive X break", right.finishX > flat.finishX + 0.02)
    }

    @Test
    fun sideSlopeDirectionAndMagnitudeStayAuthoritative() {
        val flat = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0), speed = 1.35)
        val left = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, sideSlopePct = -2.0), speed = 1.35)
        val right = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, sideSlopePct = 2.0), speed = 1.35)
        assertTrue("left-lower slope must bend left", left.finishX < flat.finishX - 0.04)
        assertTrue("right-lower slope must bend right", right.finishX > flat.finishX + 0.04)
        assertTrue("opposite slopes should separate the finish by a visible amount", right.finishX - left.finishX > 0.10)
    }

    @Test
    fun longitudinalSlopeChangesRollDistance() {
        val uphill = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, longSlopePct = -2.0), speed = 1.35)
        val flat = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0), speed = 1.35)
        val downhill = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, longSlopePct = 2.0), speed = 1.35)
        assertTrue("uphill must stop shorter than flat", uphill.finishY < flat.finishY - 0.04)
        assertTrue("downhill must roll farther than flat", downhill.finishY > flat.finishY + 0.04)
    }

    @Test
    fun allPracticeTerrainProfilesRemainFinite() {
        for (profile in 0..23) {
            val result = roll(
                GreenSettings(stimpMeters = 3.0, holeDistanceM = 6.0, terrainProfileId = profile),
                speed = 1.4
            )
            assertTrue("profile $profile X finite", result.finishX.isFinite())
            assertTrue("profile $profile Y finite", result.finishY.isFinite())
            assertTrue("profile $profile bounded X", kotlin.math.abs(result.finishX) < 8.1)
        }
    }
}
