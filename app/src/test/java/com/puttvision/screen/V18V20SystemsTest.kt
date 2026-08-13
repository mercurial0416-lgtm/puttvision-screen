package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V18V20SystemsTest {

    @Test fun strokeStudioBuildsIdealCurrentAndGhost() {
        val current = metrics(path = .55, face = .25, back = 16.0)
        val history = listOf(
            record(metrics(path = .20, face = .10), 94, 1000L),
            record(metrics(path = 1.10, face = .80), 76, 2000L)
        )
        val model = V19StrokeStudio.build(current, history)
        assertEquals(41, model.current.size)
        assertEquals(41, model.ideal.size)
        assertEquals(41, model.ghost.size)
        assertTrue(model.corridorCm in .42..0.85)
        assertTrue(model.quality in 35..99)
    }

    @Test fun performanceCompareRanksStablePutterAndBuildsTrend() {
        val records = ArrayList<ShotRecord>()
        repeat(16) { i ->
            records += record(
                metrics(path = .15 + (i % 2) * .03, face = .12, launch = .10 + (i % 2) * .02),
                score = 92,
                timestamp = 1000L + i,
                putter = "Stable"
            )
        }
        repeat(16) { i ->
            records += record(
                metrics(path = 1.2 + (i % 3) * .3, face = 1.0, launch = .9 + (i % 3) * .35),
                score = 70,
                timestamp = 2000L + i,
                putter = "Wild"
            )
        }
        val report = V20PerformanceCompare.build(records)
        assertEquals(2, report.putters.size)
        assertEquals("Stable", report.putters.first().label)
        assertNotNull(report.trend)
    }

    @Test fun regressionGateRejectsMetricDrift() {
        val expected = listOf(
            V20ReferenceExpected("a", 1.40, .20, .10, .15),
            V20ReferenceExpected("b", 1.55, -.30, -.20, -.10)
        )
        val good = listOf(
            V20RegressionMeasurement("a", 1.42, .24, .12, .17),
            V20RegressionMeasurement("b", 1.52, -.28, -.18, -.08)
        )
        val bad = listOf(
            V20RegressionMeasurement("a", 1.70, 1.20, 1.4, 1.7),
            V20RegressionMeasurement("b", 1.52, -.28, -.18, -.08)
        )
        assertTrue(V20RegressionGate.evaluate(expected, good).passedGate)
        val failed = V20RegressionGate.evaluate(expected, bad)
        assertFalse(failed.passedGate)
        assertTrue("a" in failed.failedIds)
    }

    private fun metrics(
        speed: Double = 1.40,
        launch: Double = .20,
        path: Double? = .20,
        face: Double? = .15,
        back: Double = 15.0
    ) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = launch,
        headSpeedMps = 1.0,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = speed,
        impactOffsetMm = 1.0,
        measuredAtNs = System.nanoTime(),
        backswingMs = 410.0,
        downswingMs = 210.0,
        tempoRatio = 1.95,
        backswingLengthCm = back,
        peakHeadAccelerationMps2 = 1.2,
        confidence = .82
    )

    private fun record(
        metrics: ShotMetrics,
        score: Int,
        timestamp: Long,
        putter: String = "Test"
    ) = ShotRecord(
        metrics = metrics,
        result = SimResult(false, .02, 2.92, .08, 1.6),
        strokeScore = StrokeScore(score, score, score, score, score, score, score),
        mode = PracticeMode.PRACTICE,
        targetDistanceM = 3.0,
        putterProfileName = putter,
        timestampMs = timestamp
    )
}