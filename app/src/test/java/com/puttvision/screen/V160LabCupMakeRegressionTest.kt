package com.puttvision.screen

import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V160LabCupMakeRegressionTest {
    @Test
    fun testMakeStartsNearCupAndHolesThroughAuthoritativePhysics() {
        val engine = GameEngine()
        engine.settings.holeDistanceM = 5.0
        engine.settings.stimpMeters = 2.8
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = 8
        engine.resetSimulation()

        val testDistance = 0.22
        val desiredCupSpeed = 0.28
        val stimpLaunch = 1.95072
        val rollingDecel = stimpLaunch * stimpLaunch / (2.0 * engine.settings.stimpMeters)
        val pureRollLaunch = sqrt(desiredCupSpeed * desiredCupSpeed + 2.0 * rollingDecel * testDistance)
        val ballSpeed = (pureRollLaunch / 0.92).coerceIn(0.15, 4.75)
        val headSpeed = max(0.20, ballSpeed * 0.66)

        engine.setNextLabShotStart(0.0, engine.settings.holeDistanceM - testDistance)
        engine.launch(
            ShotMetrics(
                ballSpeedMps = ballSpeed,
                launchAngleDeg = 0.0,
                headSpeedMps = headSpeed,
                faceAngleDeg = 0.0,
                pathAngleDeg = 0.0,
                faceToPathDeg = 0.0,
                smash = ballSpeed / headSpeed,
                impactOffsetMm = 0.0,
                measuredAtNs = 1L,
                confidence = .98,
                uncertainty = MeasurementUncertaintyEstimator.synthetic()
            )
        )

        val launched = engine.state
        assertNotNull("TEST MAKE must create a physics state", launched)
        assertTrue("TEST MAKE must begin as a rolling shot", launched!!.running)
        assertEquals(5.0 - testDistance, engine.virtualStartAtShot.second, 1e-9)

        var result: SimResult? = null
        repeat(600) {
            if (result == null) result = engine.step(1.0 / 120.0)
        }

        assertNotNull("TEST MAKE must finish", result)
        assertTrue("TEST MAKE must be captured by the real V135 cup solver", result!!.holed)
        assertTrue("holed shot must record at least one physical cup contact", result!!.cupContacts > 0)
    }
}
