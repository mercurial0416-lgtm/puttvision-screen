package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class V136PhysicalRealismTest {
    private fun rollingState(vx: Double, vy: Double) = SimState(
        x = 0.0,
        y = 0.0,
        vx = vx,
        vy = vy,
        running = true,
        ballCenterZM = V135RigidBallPhysics.BALL_RADIUS_M,
        v135Initialized = true
    )

    @Test
    fun neutralEnvironmentalConditionsAddNoBiasBeyondV137SpeedResistance() {
        val settings = GreenSettings(
            stimpMeters = 3.1,
            flagstickIn = false,
            grainStrength01 = 0.0,
            moisture01 = 0.5,
            firmness01 = 0.5,
            trueness01 = 1.0
        )
        val state = rollingState(0.0, 1.0)
        val effective = V136PhysicalRealism.effectiveSettings(settings, state)
        val expected = V137RollingResistance.effectiveStimp(3.1, 1.0)
        assertTrue(abs(effective.stimpMeters - expected) < 1e-12)
    }

    @Test
    fun grainIsDirectionallyAnisotropic() {
        val settings = GreenSettings(
            stimpMeters = 3.0,
            flagstickIn = false,
            grainDirectionDeg = 90.0,
            grainStrength01 = 1.0
        )
        val withGrain = V136PhysicalRealism.effectiveSettings(settings, rollingState(0.0, 1.0))
        val againstGrain = V136PhysicalRealism.effectiveSettings(settings, rollingState(0.0, -1.0))
        assertTrue("with-grain roll should be effectively faster", withGrain.stimpMeters > settings.stimpMeters)
        assertTrue("against-grain roll should be effectively slower", againstGrain.stimpMeters < settings.stimpMeters)
        assertTrue(withGrain.stimpMeters - againstGrain.stimpMeters > 0.45)
    }

    @Test
    fun wetterSurfaceReducesEffectiveRollDistance() {
        val base = GreenSettings(stimpMeters = 3.0, flagstickIn = false, moisture01 = 0.5)
        val wet = base.copy(moisture01 = 1.0)
        val state = rollingState(0.0, 1.0)
        val baseEffective = V136PhysicalRealism.effectiveSettings(base, state)
        val wetEffective = V136PhysicalRealism.effectiveSettings(wet, state)
        assertTrue(wetEffective.stimpMeters < baseEffective.stimpMeters)
    }

    @Test
    fun sweptFlagstickCollisionCannotTunnelAtHighSpeed() {
        val settings = GreenSettings(holeDistanceM = 1.0, flagstickIn = true)
        val surface = GreenTerrain.effectiveHeightAt(settings, 0.0, 1.0)
        val state = SimState(
            x = 0.0,
            y = 1.08,
            vx = 0.0,
            vy = 3.0,
            running = true,
            ballCenterZM = surface + V135RigidBallPhysics.BALL_RADIUS_M,
            v135Initialized = true
        )
        val hit = V136PhysicalRealism.resolveFlagstickSweep(
            state = state,
            settings = settings,
            fromX = 0.0,
            fromY = 0.92,
            fromZ = surface + V135RigidBallPhysics.BALL_RADIUS_M,
            toX = 0.0,
            toY = 1.08,
            toZ = surface + V135RigidBallPhysics.BALL_RADIUS_M
        )
        assertTrue("segment CCD must detect the thin pole", hit)
        assertTrue(state.flagstickContacts == 1)
        assertTrue(state.cupContacts >= 1)
        assertTrue("high-speed pole impact must materially reduce/reverse forward motion", state.vy < 1.0)
    }

    @Test
    fun stickOutLeavesIdenticalSweepUntouched() {
        val settings = GreenSettings(holeDistanceM = 1.0, flagstickIn = false)
        val surface = GreenTerrain.effectiveHeightAt(settings, 0.0, 1.0)
        val state = SimState(
            x = 0.0,
            y = 1.08,
            vx = 0.0,
            vy = 3.0,
            running = true,
            ballCenterZM = surface + V135RigidBallPhysics.BALL_RADIUS_M,
            v135Initialized = true
        )
        val hit = V136PhysicalRealism.resolveFlagstickSweep(
            state, settings, 0.0, 0.92, surface + V135RigidBallPhysics.BALL_RADIUS_M
        )
        assertFalse(hit)
        assertTrue(state.flagstickContacts == 0)
    }
}
