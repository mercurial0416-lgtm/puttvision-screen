package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GreenPhysicsRegressionTest {
    private fun metrics(speed: Double, launchDeg: Double) = ShotMetrics(
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

    private fun roll(
        settings: GreenSettings,
        speed: Double = 1.0,
        launchDeg: Double = 0.0,
        dt: Double = 0.01
    ): SimResult {
        val physics = GreenPhysics()
        val state = physics.launch(metrics(speed, launchDeg), settings)
        repeat(24_000) {
            val result = physics.step(state, settings, dt)
            if (result != null) return result
        }
        error("simulation did not finish")
    }

    @Test
    fun flatGreenRollDistanceStaysInPhysicalRange() {
        val result = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0))
        assertTrue("flat roll should move forward", result.finishY > 0.60)
        assertTrue("flat roll should not travel implausibly far", result.finishY < 0.90)
        assertTrue("flat roll should remain near center", abs(result.finishX) < 0.02)
    }

    @Test
    fun positiveSideSlopeBreaksRight() {
        val flat = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0), speed = 1.25)
        val right = roll(GreenSettings(stimpMeters = 2.8, holeDistanceM = 12.0, sideSlopePct = 1.0), speed = 1.25)
        assertTrue("right-lower slope should produce positive X break", right.finishX > flat.finishX + 0.02)
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
            assertTrue("profile $profile bounded X", abs(result.finishX) < 8.1)
        }
    }

    @Test
    fun commonRenderCadencesProduceNearlyIdenticalTrajectory() {
        val settings = GreenSettings(
            stimpMeters = 3.15,
            holeDistanceM = 12.0,
            sideSlopePct = 1.35,
            longSlopePct = -0.45
        )
        val at30 = roll(settings.copy(), speed = 1.55, launchDeg = -1.2, dt = 1.0 / 30.0)
        val at60 = roll(settings.copy(), speed = 1.55, launchDeg = -1.2, dt = 1.0 / 60.0)
        val at120 = roll(settings.copy(), speed = 1.55, launchDeg = -1.2, dt = 1.0 / 120.0)
        val at240 = roll(settings.copy(), speed = 1.55, launchDeg = -1.2, dt = 1.0 / 240.0)

        listOf(at30, at60, at120).forEach { candidate ->
            assertTrue("finish X should not depend on render FPS", abs(candidate.finishX - at240.finishX) < 0.003)
            assertTrue("finish Y should not depend on render FPS", abs(candidate.finishY - at240.finishY) < 0.003)
            assertTrue("elapsed should not depend on render FPS", abs(candidate.elapsedSec - at240.elapsedSec) < 0.01)
            assertTrue("cup outcome should not depend on render FPS", candidate.holed == at240.holed)
        }
    }

    @Test
    fun delayedThirtyThreeMsFramePreservesElapsedTimeInsteadOfClippingToTwentyFiveMs() {
        val physics = GreenPhysics()
        val settings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 12.0)
        val state = physics.launch(metrics(1.6, 0.0), settings)

        val result = physics.step(state, settings, 0.033, cupEnabled = false)
        assertTrue("one normal delayed frame should not finish the shot", result == null)
        assertTrue("33ms frame time must be preserved", abs(state.elapsed - 0.033) < 1e-9)
    }

    @Test
    fun oneLargeFrameMatchesEquivalent240HzSteps() {
        val physicsA = GreenPhysics()
        val physicsB = GreenPhysics()
        val settingsA = GreenSettings(stimpMeters = 3.3, holeDistanceM = 10.0, sideSlopePct = 1.1)
        val settingsB = settingsA.copy()
        val stateA = physicsA.launch(metrics(1.7, 1.0), settingsA)
        val stateB = physicsB.launch(metrics(1.7, 1.0), settingsB)

        physicsA.step(stateA, settingsA, 1.0 / 30.0, cupEnabled = false)
        repeat(8) { physicsB.step(stateB, settingsB, 1.0 / 240.0, cupEnabled = false) }

        assertTrue("substepped X should match explicit 240Hz", abs(stateA.x - stateB.x) < 1e-9)
        assertTrue("substepped Y should match explicit 240Hz", abs(stateA.y - stateB.y) < 1e-9)
        assertTrue("substepped VX should match explicit 240Hz", abs(stateA.vx - stateB.vx) < 1e-9)
        assertTrue("substepped VY should match explicit 240Hz", abs(stateA.vy - stateB.vy) < 1e-9)
        assertTrue("substepped elapsed should match explicit 240Hz", abs(stateA.elapsed - stateB.elapsed) < 1e-9)
    }

    @Test
    fun invalidFrameDeltaDoesNotPoisonSimulationState() {
        val physics = GreenPhysics()
        val settings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 12.0)
        val state = physics.launch(metrics(1.5, 0.0), settings)
        val x = state.x
        val y = state.y
        val elapsed = state.elapsed

        assertTrue(physics.step(state, settings, Double.NaN, cupEnabled = false) == null)
        assertTrue(physics.step(state, settings, -0.01, cupEnabled = false) == null)
        assertTrue("invalid dt must not move X", state.x == x)
        assertTrue("invalid dt must not move Y", state.y == y)
        assertTrue("invalid dt must not advance time", state.elapsed == elapsed)
        assertTrue("invalid dt must not stop a valid shot", state.running)
    }
}
