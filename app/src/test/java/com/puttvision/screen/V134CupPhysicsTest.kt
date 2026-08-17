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
        var result: SimResult? = null

        repeat(180) {
            result = physics.step(state, settings, 0.01)
            sawDrop = sawDrop || state.cupPhase == V134CupPhase.DROP
            if (result != null) return@repeat
        }

        assertTrue("centered capture must enter a visible DROP phase", sawDrop)
        assertTrue("captured putt must eventually hole", result?.holed == true)
        assertEquals(V134CupPhase.SETTLED, state.cupPhase)
        assertTrue("ball center must visibly finish below the green", state.cupVerticalOffsetM < -0.09)
        assertTrue("drop must take multiple rendered frames", state.elapsed > 0.30)
    }

    @Test
    fun slowEdgePuttRidesRimBeforeFalling() {
        val physics = GreenPhysics()
        val state = SimState(x = 0.044, y = 0.93, vx = 0.0, vy = 0.45, running = true)
        var sawRim = false
        var sawDrop = false
        var rimFrames = 0
        var result: SimResult? = null

        repeat(220) {
            result = physics.step(state, settings, 0.01)
            if (state.cupPhase == V134CupPhase.RIM) {
                sawRim = true
                rimFrames++
            }
            sawDrop = sawDrop || state.cupPhase == V134CupPhase.DROP
            if (result != null) return@repeat
        }

        assertTrue("edge capture must visibly ride the lip", sawRim)
        assertTrue("rim interaction should survive more than a few physics ticks", rimFrames >= 12)
        assertTrue("edge capture should transition from rim to drop", sawDrop)
        assertTrue("properly paced edge putt should finish holed", result?.holed == true)
        assertTrue("rim contact must be recorded", state.cupContacts >= 1)
    }

    @Test
    fun fastCenterPuttCanBridgeCupWithoutMagicCapture() {
        val physics = GreenPhysics()
        val state = SimState(x = 0.0, y = 0.90, vx = 0.0, vy = 2.0, running = true)
        var holed = false
        var enteredDrop = false

        repeat(35) {
            val result = physics.step(state, settings, 0.01)
            holed = holed || result?.holed == true || state.holed
            enteredDrop = enteredDrop || state.cupPhase == V134CupPhase.DROP
        }

        assertFalse("high-speed center pass must not be magnetically captured", holed)
        assertFalse("high-speed center pass must not enter drop animation", enteredDrop)
        assertTrue("high-speed pass should travel beyond the cup", state.y > 1.05)
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
