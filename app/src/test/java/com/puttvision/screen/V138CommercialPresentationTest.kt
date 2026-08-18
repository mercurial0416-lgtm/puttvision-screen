package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class V138CommercialPresentationTest {
    @Test
    fun rimActionUsesLowCupHeroCamera() {
        val state = SimState(
            x = .01,
            y = 4.98,
            running = true,
            cupPhase = V134CupPhase.RIM
        )
        val f = V138CommercialCameraPlanner.target(5.0, 0.0, 0.0, state.x, state.y, state, null)
        assertTrue(f.eyeZ < .40f)
        assertTrue(f.fovDeg <= 30.0f)
        assertTrue(kotlin.math.abs(f.lookY - 5f) < .01f)
    }

    @Test
    fun addressViewKeepsSimulatorPerspective() {
        val f = V138CommercialCameraPlanner.target(5.0, 0.0, 0.0, 0.0, 0.0, null, null)
        assertTrue(f.eyeY < -2.5f)
        assertTrue(f.eyeZ > 1.3f)
        assertTrue(f.fovDeg in 34f..38f)
    }

    @Test
    fun smootherDoesNotSnapToCupCamera() {
        val smoother = V138CameraSmoother()
        val address = V133CameraFrame(0f, -4f, 1.5f, 0f, 4f, .05f, 36f)
        val cup = V133CameraFrame(.54f, 4.18f, .29f, 0f, 5f, -.018f, 29f)
        smoother.reset(address)
        val first = smoother.step(cup, heroCupAction = true)
        assertTrue(first.eyeY > address.eyeY)
        assertTrue(first.eyeY < cup.eyeY)
        assertTrue(first.eyeZ < address.eyeZ)
        assertTrue(first.eyeZ > cup.eyeZ)
    }
}
