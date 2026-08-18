package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V135RigidBallPhysicsTest {
    private fun metrics(speed: Double, launchDeg: Double = 0.0) = ShotMetrics(
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

    @Test
    fun launchStartsWithPhysicalSkidAndConvergesTowardPureRoll() {
        val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 10.0)
        val physics = GreenPhysics()
        val state = physics.launch(metrics(1.25), settings)
        val initialSlip = state.v135SlipSpeedMps
        assertTrue("putter launch should begin with measurable skid", initialSlip > 0.10)

        repeat(55) {
            if (state.running) physics.step(state, settings, 0.01, cupEnabled = false)
        }
        assertTrue("skid should decay into rolling contact", state.v135SlipSpeedMps < initialSlip * 0.20)
        assertTrue("ball must carry angular velocity", abs(state.omegaXRadS) + abs(state.omegaYRadS) > 1.0)
        assertTrue("orientation must advance with roll", abs(state.orientationW - 1.0) > 1e-4)
    }

    @Test
    fun slowCenteredPuttFallsUnderGravityAndSettlesInRegulationDepthCup() {
        val settings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 0.48)
        val physics = GreenPhysics()
        val state = physics.launch(metrics(0.95), settings)
        var lowestOffset = 0.0
        var result: SimResult? = null
        repeat(3000) {
            result = physics.step(state, settings, 0.005)
            lowestOffset = minOf(lowestOffset, state.cupVerticalOffsetM)
            if (result != null) return@repeat
        }
        assertTrue("centered dying-speed putt should hole", result?.holed == true)
        assertTrue("ball center must visibly fall well below the green", lowestOffset < -0.06)
        assertTrue("settled ball must physically touch cup bottom", state.cupBottomContacts > 0)
        assertTrue("cup state should end settled", state.cupPhase == V134CupPhase.SETTLED)
    }

    @Test
    fun fastCenteredPuttCanBridgeInsteadOfMagneticCapture() {
        val settings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 0.40)
        val physics = GreenPhysics()
        val state = physics.launch(metrics(2.25), settings)
        var result: SimResult? = null
        repeat(2500) {
            result = physics.step(state, settings, 0.005)
            if (result != null) return@repeat
        }
        assertFalse("fast center pass must not be guaranteed to hole", result?.holed == true)
        assertTrue("fast pass should either bridge or make a physical cup contact", state.bridgeCount > 0 || state.cupContacts > 0)
    }

    @Test
    fun sideSlopeUsesActualTerrainNormalAndBreaksBall() {
        val flatSettings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 10.0)
        val slopeSettings = GreenSettings(stimpMeters = 3.0, holeDistanceM = 10.0, sideSlopePct = 1.5)
        val physics = GreenPhysics()
        val flat = physics.launch(metrics(1.35), flatSettings)
        val slope = physics.launch(metrics(1.35), slopeSettings)

        repeat(1000) {
            if (flat.running) physics.step(flat, flatSettings, 0.005, cupEnabled = false)
            if (slope.running) physics.step(slope, slopeSettings, 0.005, cupEnabled = false)
            if (!flat.running && !slope.running) return@repeat
        }
        assertTrue("right-low surface must break toward +X", slope.x > flat.x + 0.025)
        assertTrue("surface normal should reflect the slope", abs(slope.surfaceNormalX) > 0.005)
    }

    @Test
    fun solverIsStableAcrossDisplayStepSizes() {
        val settings = GreenSettings(stimpMeters = 2.9, holeDistanceM = 8.0, sideSlopePct = 0.7, longSlopePct = -0.4)
        val physics = GreenPhysics()
        val a = physics.launch(metrics(1.45, 1.2), settings)
        val b = physics.launch(metrics(1.45, 1.2), settings)

        repeat(4000) {
            if (a.running) physics.step(a, settings, 0.005, cupEnabled = false)
            if (b.running) physics.step(b, settings, 0.010, cupEnabled = false)
            if (!a.running && !b.running) return@repeat
        }
        assertTrue("fixed microsteps should keep X nearly frame-rate invariant", abs(a.x - b.x) < 0.025)
        assertTrue("fixed microsteps should keep Y nearly frame-rate invariant", abs(a.y - b.y) < 0.035)
    }
}
