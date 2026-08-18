package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V137RollingResistanceTest {
    @Test
    fun resistanceChangesByAboutTenPercentAcrossAReferencePutt() {
        val slow = V137RollingResistance.decelerationFactor(0.0)
        val fast = V137RollingResistance.decelerationFactor(V137RollingResistance.STIMP_LAUNCH_MPS)
        val mid = V137RollingResistance.decelerationFactor(V137RollingResistance.STIMP_LAUNCH_MPS * 0.5)
        assertTrue(fast > mid)
        assertTrue(mid > slow)
        val halfRangeRelativeToMid = (fast - slow) / (2.0 * mid)
        assertTrue("target variation should remain close to the measured ~10% scale", abs(halfRangeRelativeToMid - 0.10) < 0.012)
    }

    @Test
    fun analyticalNormalizationPreservesStimpStoppingDistance() {
        val stimp = 3.0
        val v0 = V137RollingResistance.STIMP_LAUNCH_MPS
        val legacyA = v0 * v0 / (2.0 * stimp)
        val steps = 200_000
        val dv = v0 / steps
        var distance = 0.0
        for (i in 0 until steps) {
            val v = (i + 0.5) * dv
            val a = legacyA * V137RollingResistance.decelerationFactor(v)
            distance += v / a * dv
        }
        assertTrue("variable friction must not redefine selected Stimp", abs(distance - stimp) < 0.0002)
    }

    @Test
    fun pureRollingSixDofBallStillStopsNearSelectedStimp() {
        val stimp = 2.8
        val settings = GreenSettings(
            stimpMeters = stimp,
            holeDistanceM = 20.0,
            flagstickIn = false,
            grainStrength01 = 0.0,
            moisture01 = 0.5,
            firmness01 = 0.5,
            trueness01 = 1.0
        )
        val state = SimState(
            x = 0.0,
            y = 0.0,
            vx = 0.0,
            vy = V137RollingResistance.STIMP_LAUNCH_MPS,
            running = true,
            trail = mutableListOf(0.0 to 0.0)
        )
        V135RigidBallPhysics.initialize(state, settings)
        // Remove the launch-skid transient: a Stimpmeter calibration ball is evaluated in its
        // rolling phase here so this regression isolates rolling-resistance normalization.
        state.omegaXRadS = -state.vy / V135RigidBallPhysics.BALL_RADIUS_M
        state.omegaYRadS = 0.0
        state.omegaZRadS = 0.0
        state.v135SlipSpeedMps = 0.0

        val physics = GreenPhysics()
        repeat(10_000) {
            if (!state.running) return@repeat
            physics.step(state, settings, 0.005, cupEnabled = false)
        }
        assertTrue("reference roll must terminate", !state.running)
        assertTrue("six-DOF integration should retain Stimp within 5 cm", abs(state.y - stimp) < 0.05)
    }

    @Test
    fun highSpeedRollingFeelsMoreResistiveThanDyingRoll() {
        val settings = GreenSettings(stimpMeters = 3.0)
        val fastState = SimState(vy = 1.8, running = true, ballCenterZM = V135RigidBallPhysics.BALL_RADIUS_M, v135Initialized = true)
        val slowState = SimState(vy = 0.25, running = true, ballCenterZM = V135RigidBallPhysics.BALL_RADIUS_M, v135Initialized = true)
        val fastEffective = V136PhysicalRealism.effectiveSettings(settings, fastState).stimpMeters
        val slowEffective = V136PhysicalRealism.effectiveSettings(settings, slowState).stimpMeters
        assertTrue("higher rolling resistance is represented by lower instantaneous effective Stimp", fastEffective < slowEffective)
    }
}
