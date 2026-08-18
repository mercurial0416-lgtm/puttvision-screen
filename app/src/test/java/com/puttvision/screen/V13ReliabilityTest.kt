package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V13ReliabilityTest {
    private fun shot(speed: Double, angle: Double = 0.0) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = angle,
        headSpeedMps = 0.9,
        faceAngleDeg = 0.0,
        pathAngleDeg = 0.0,
        faceToPathDeg = 0.0,
        smash = speed / 0.9,
        impactOffsetMm = 0.0,
        measuredAtNs = 0L,
        confidence = .95
    )

    private fun simulate(settings: GreenSettings, speed: Double, angle: Double = 0.0): SimResult {
        val physics = GreenPhysics()
        val state = physics.launch(shot(speed, angle), settings)
        repeat(1800) {
            physics.step(state, settings, .016)?.let { return it }
        }
        error("simulation did not terminate")
    }

    @Test fun highQuality240FpsProducesTighterUncertainty() {
        val good = MeasurementUncertaintyEstimator.forHfr(
            fps = 240,
            ballDetectionRatio = .96,
            headDetectionRatio = .92,
            matDecelAvailable = true,
            ballSpeedMps = 1.5,
            headSpeedMps = 1.0,
            impactOffsetMm = 1.0
        )
        val weak = MeasurementUncertaintyEstimator.forHfr(
            fps = 120,
            ballDetectionRatio = .62,
            headDetectionRatio = .52,
            matDecelAvailable = false,
            ballSpeedMps = 1.5,
            headSpeedMps = 1.0,
            impactOffsetMm = 1.0
        )
        assertTrue(good.ballSpeedMps < weak.ballSpeedMps)
        assertTrue(good.launchDeg < weak.launchDeg)
        assertTrue((good.headSpeedMps ?: 99.0) < (weak.headSpeedMps ?: 0.0))
        assertTrue((good.faceDeg ?: 99.0) < (weak.faceDeg ?: 0.0))
    }

    @Test fun centeredPaceCanBeCapturedByCup() {
        val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 1.0)
        val result = simulate(settings, speed = 1.17)
        assertTrue("expected centered putt to hole; miss=${result.distanceToCupM}", result.holed)
        assertFalse(result.lipOut)
    }

    @Test fun excessiveCenterSpeedBridgesCupInsteadOfAutoHoling() {
        val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 1.0)
        // V135 models launch skid before pure roll. 2.20 m/s at one metre leaves the ball above the
        // published 1.626 m/s maximum physical rim-capture speed when it actually reaches the cup.
        val result = simulate(settings, speed = 2.20)
        assertFalse(result.holed)
        assertTrue("fast cup crossing should be marked lip-out", result.lipOut)
        assertTrue(result.cupContacts >= 1)
    }

    @Test fun effectiveHeightGradientMatchesEffectiveSlope() {
        val settings = GreenSettings(
            stimpMeters = 3.0,
            holeDistanceM = 7.0,
            sideSlopePct = .72,
            longSlopePct = -.48,
            terrainProfileId = 18
        )
        val x = .31
        val y = 3.8
        val e = .002
        val dx = (
            GreenTerrain.effectiveHeightAt(settings, x + e, y) -
                GreenTerrain.effectiveHeightAt(settings, x - e, y)
            ) / (2.0 * e)
        val dy = (
            GreenTerrain.effectiveHeightAt(settings, x, y + e) -
                GreenTerrain.effectiveHeightAt(settings, x, y - e)
            ) / (2.0 * e)
        val expected = GreenTerrain.effectiveSlopeAt(settings, x, y)
        assertTrue(abs(-100.0 * dx - expected.sidePct) < .08)
        assertTrue(abs(-100.0 * dy - expected.longPct) < .08)
    }

    @Test fun allTwentyFourGreensTerminateWithFinitePhysics() {
        for (profile in 0..23) {
            val settings = GreenSettings(
                stimpMeters = 2.8,
                holeDistanceM = 5.0,
                sideSlopePct = 0.0,
                longSlopePct = 0.0,
                terrainProfileId = profile
            )
            val result = simulate(settings, speed = 1.65)
            assertTrue("profile $profile distance", result.distanceToCupM.isFinite())
            assertTrue("profile $profile elapsed", result.elapsedSec.isFinite())
            assertTrue("profile $profile elapsed range", result.elapsedSec in 0.0..20.1)
        }
    }
}
