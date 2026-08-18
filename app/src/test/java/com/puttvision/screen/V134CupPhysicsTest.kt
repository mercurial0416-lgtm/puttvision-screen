package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V134CupPhysicsTest {
    private val settings = GreenSettings(stimpMeters = 2.8, holeDistanceM = 1.0)

    @Test
    fun centeredPuttDropsOverTimeInsteadOfTeleporting() {
        val physics = GreenPhysics()
        val state = SimState(x = 0.0, y = 0.93, vx = 0.0, vy = 0.45, running = true)
        var sawDrop = false
        var lowestOffset = 0.0
        var result: SimResult? = null

        repeat(180) {
            result = physics.step(state, settings, 0.01)
            sawDrop = sawDrop || state.cupPhase == V134CupPhase.DROP || state.v135Airborne
            lowestOffset = minOf(lowestOffset, state.cupVerticalOffsetM)
            if (result != null) return@repeat
        }

        assertTrue("centered capture must enter a visible gravity-drop state", sawDrop)
        assertTrue("captured putt must eventually hole", result?.holed == true)
        assertEquals(V134CupPhase.SETTLED, state.cupPhase)
        assertTrue("ball center must visibly finish below the green", state.cupVerticalOffsetM < -0.09)
        assertTrue("ball must pass through multiple physical frames before settling", state.elapsed > 0.12)
        assertTrue("drop path must reach deep into the regulation cup", lowestOffset < -0.06)
        assertTrue("settlement must include a real cup-bottom contact", state.cupBottomContacts >= 1)
    }

    @Test
    fun slowEdgePuttRidesRimBeforeFalling() {
        val physics = GreenPhysics()
        // A small but non-zero impact parameter exercises true rim contact without relying on the
        // old V134 magnetic edge-capture zone. Larger offsets are legitimately allowed to lip out.
        val state = SimState(x = 0.008, y = 0.93, vx = 0.0, vy = 0.45, running = true)
        var sawRim = false
        var sawDrop = false
        var result: SimResult? = null

        repeat(260) {
            result = physics.step(state, settings, 0.01)
            sawRim = sawRim || state.cupPhase == V134CupPhase.RIM || state.cupContacts > 0
            sawDrop = sawDrop || state.cupPhase == V134CupPhase.DROP || state.v135Airborne
            if (result != null) return@repeat
        }

        assertTrue("edge capture must physically contact the lip/wall", sawRim)
        assertTrue("edge capture should include unsupported/free-fall motion", sawDrop)
        assertTrue("properly paced capturable edge putt should finish holed", result?.holed == true)
        assertTrue("rim contact must be recorded", state.cupContacts >= 1)
        assertTrue("captured edge putt must physically reach the cup bottom", state.cupBottomContacts >= 1)
    }

    @Test
    fun fastCenterPuttCanBridgeCupWithoutMagicCapture() {
        val physics = GreenPhysics()
        val state = SimState(x = 0.0, y = 0.90, vx = 0.0, vy = 2.0, running = true)
        var holed = false
        var becameUnsupported = false

        repeat(35) {
            val result = physics.step(state, settings, 0.01)
            holed = holed || result?.holed == true || state.holed
            becameUnsupported = becameUnsupported || state.v135Airborne || state.cupVerticalOffsetM < -0.001
        }

        assertFalse("high-speed center pass must not be magnetically captured", holed)
        assertTrue("real bridge must include a brief gravity-driven unsupported interval", becameUnsupported)
        assertTrue("high-speed pass should travel beyond the cup", state.y > 1.05)
        assertTrue("bridge/contact outcome should be explicitly recorded", state.bridgeCount > 0 || state.cupContacts > 0)
    }

    @Test
    fun physicsSnapshotCarriesVerticalCupStateAtomically() {
        val source = SimState(
            x = 0.031,
            y = 1.0,
            vx = 0.1,
            vy = 0.2,
            running = true,
            cupPhase = V134CupPhase.RIM,
            cupPhaseElapsedSec = 0.22,
            cupVerticalOffsetM = -0.004,
            cupRimAngleRad = 1.4,
            cupRimRadiusM = 0.052,
            cupRimAngularVelocityRadS = 4.2,
            cupRimWillDrop = true
        )
        val copy = V126PhysicsFrameBridge.snapshot(source)!!

        assertEquals(source.cupPhase, copy.cupPhase)
        assertEquals(source.cupVerticalOffsetM, copy.cupVerticalOffsetM, 1e-9)
        assertEquals(source.cupRimAngleRad, copy.cupRimAngleRad, 1e-9)
        assertEquals(source.cupRimRadiusM, copy.cupRimRadiusM, 1e-9)
        assertEquals(source.cupRimAngularVelocityRadS, copy.cupRimAngularVelocityRadS, 1e-9)
        assertTrue(copy.cupRimWillDrop)
    }
}
