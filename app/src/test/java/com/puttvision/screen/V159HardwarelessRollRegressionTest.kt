package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V159HardwarelessRollRegressionTest {
    @Test
    fun centerScenarioIsAcceptedAndPublishesVisibleTravel() {
        val engine = GameEngine()
        engine.settings.holeDistanceM = 5.0
        engine.settings.stimpMeters = 2.8
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = 8
        engine.resetSimulation()

        val start = V26BallStartRuntime.current(engine.settings)
        val distance = hypot(start.first, engine.settings.holeDistanceM - start.second).coerceAtLeast(0.25)
        val stimpLaunch = 1.95072
        val rollingDecel = stimpLaunch * stimpLaunch / (2.0 * engine.settings.stimpMeters)
        val cupSpeed = V27CupPaceRuntime.targetCupSpeedMps.coerceIn(0.25, 0.85)
        val pureRollLaunch = sqrt(cupSpeed * cupSpeed + 2.0 * rollingDecel * distance)
        val ballSpeed = (pureRollLaunch / 0.92).coerceIn(0.15, 4.75)
        val headSpeed = max(0.20, ballSpeed * 0.66)

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
        assertNotNull("CENTER synthetic shot must create a physics state", launched)
        assertTrue("CENTER synthetic shot must be running", launched!!.running)
        val y0 = launched.y
        val q0 = listOf(launched.orientationW, launched.orientationX, launched.orientationY, launched.orientationZ)

        repeat(24) { engine.step(1.0 / 60.0) }
        val moved = engine.state
        assertNotNull(moved)
        assertTrue("ball must translate by a TV-visible amount", moved!!.y > y0 + 0.20)
        val q1 = listOf(moved.orientationW, moved.orientationX, moved.orientationY, moved.orientationZ)
        assertTrue("ball quaternion must change while rolling", q0.zip(q1).any { (a, b) -> kotlin.math.abs(a - b) > 1e-4 })
        assertTrue("simulation clock must advance", moved.elapsed > 0.25)
    }
}
