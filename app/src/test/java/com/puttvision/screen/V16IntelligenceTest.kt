package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V16IntelligenceTest {

    @Test fun metricConfidenceSeparatesHardAndSoftMeasurements() {
        val m = metrics(confidence = .82, face = .2, path = .1, impact = null)
        val q = V16MetricConfidenceEstimator.estimate(m)
        assertTrue(q.ballSpeed > q.impact)
        assertEquals(0.0, q.impact, 0.0001)
        assertTrue(q.face > .55)
    }

    @Test fun personalCoachFindsLongDistanceWeakness() {
        val short = (0 until 8).map { i -> record(2.0, .12, 88, i.toLong()) }
        val long = (0 until 8).map { i -> record(5.0, .70, 70, (100 + i).toLong()) }
        val snap = V16PersonalCoach.build(short + long)
        assertNotNull(snap)
        assertTrue(snap!!.topInsights.any { it.headline.contains("4m 이상") })
    }

    @Test fun trainingPlanAlwaysCreatesFullRoutine() {
        val plan = V16TrainingPlanner.build(null)
        assertEquals(4, plan.blocks.size)
        assertTrue(plan.blocks.sumOf { it.shots } >= 30)
    }

    @Test fun deviceCalibratorUsesReferenceSamplesWithoutExtremeScale() {
        val samples = (0 until 10).map { i ->
            V16DeviceCalibrationSample(
                measuredSpeedMps = 1.30 + i * .005,
                referenceSpeedMps = 1.36 + i * .005,
                measuredLaunchDeg = .35 + (i % 2) * .05,
                referenceLaunchDeg = 0.0
            )
        }
        val fit = V16DeviceCalibrator.fit("test", samples)
        assertNotNull(fit)
        assertTrue(fit!!.speedScale in 1.0..1.10)
        assertTrue(fit.launchBiasDeg in .30..45)
    }

    private fun metrics(
        confidence: Double = .8,
        face: Double? = .1,
        path: Double? = .1,
        impact: Double? = 1.0
    ) = ShotMetrics(
        ballSpeedMps = 1.4,
        launchAngleDeg = .15,
        headSpeedMps = 1.0,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = 1.4,
        impactOffsetMm = impact,
        measuredAtNs = 1L,
        rawBallSpeedMps = 1.4,
        confidence = confidence
    )

    private fun record(distance: Double, miss: Double, score: Int, ts: Long) = ShotRecord(
        metrics = metrics(),
        result = SimResult(false, miss, distance, miss, 1.0),
        strokeScore = StrokeScore(score, score, score, score, score, score, score),
        mode = PracticeMode.PRACTICE,
        targetDistanceM = distance,
        timestampMs = ts
    )
}
