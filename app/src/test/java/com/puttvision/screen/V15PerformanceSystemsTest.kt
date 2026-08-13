package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V15PerformanceSystemsTest {

    @Test fun trajectoryGateAcceptsContinuousPuttAndRejectsJump() {
        val good = (0..10).map { i ->
            val t = i * .008
            V14TimedPoint(t, i * .06, i * 1.18)
        }
        val goodVerdict = V15TrajectoryGate.validate(good)
        assertTrue(goodVerdict.accepted)
        assertTrue(goodVerdict.score >= .58)

        val bad = good.toMutableList().also {
            it[5] = V14TimedPoint(it[5].tSec, 80.0, -40.0)
        }
        val badVerdict = V15TrajectoryGate.validate(bad)
        assertFalse(badVerdict.accepted)
    }

    @Test fun performanceAnalyzerFindsRepeatableStraightStroke() {
        val history = (0 until 10).map { i ->
            record(
                metrics = metrics(
                    speed = 1.40 + (i % 2) * .01,
                    launch = if (i % 2 == 0) .12 else -.10,
                    face = .10,
                    path = .16,
                    impact = 1.5
                ),
                score = 91,
                timestamp = 1000L + i
            )
        }
        val snapshot = V15PerformanceAnalyzer.analyze(history.last().metrics, history)
        assertEquals(V15ArcType.STRAIGHT, snapshot.signature.arcType)
        assertTrue(snapshot.signature.repeatability >= 80)
        assertTrue(snapshot.training.priority <= 80)
    }

    @Test fun putterFitterRequiresVolumeThenReturnsRecommendation() {
        val tooFew = (0 until 12).map { i -> record(metrics(path = .9, face = .4), 82, 1000L + i) }
        assertEquals(null, V15PutterFitter.fit(tooFew, null))

        val enough = (0 until 28).map { i ->
            record(
                metrics = metrics(
                    path = .8 + (i % 3 - 1) * .08,
                    face = .35 + (i % 4 - 2) * .07,
                    impact = (i % 5 - 2) * 1.1
                ),
                score = 84 + i % 4,
                timestamp = 2000L + i
            )
        }
        val fit = V15PutterFitter.fit(enough, null)
        assertNotNull(fit)
        assertEquals(28, fit!!.sampleCount)
        assertTrue(fit.confidence in .45..0.90)
    }

    @Test fun multiCameraFusionWeightsCompanionsWithoutBreakingPrimary() {
        val primary = metrics(speed = 1.40, launch = .60, face = .70, path = .45, confidence = .55)
        val top = metrics(speed = 1.44, launch = .20, face = .20, path = .25, confidence = .90)
        val fused = V15MultiCameraFusion.fuse(
            listOf(
                V15CameraMeasurement("p", V15CameraView.PRIMARY, primary, .55),
                V15CameraMeasurement("t", V15CameraView.TOP, top, .90)
            )
        )
        assertNotNull(fused)
        assertTrue(fused!!.ballSpeedMps in 1.40..1.45)
        assertTrue(fused.faceAngleDeg!! < primary.faceAngleDeg!!)
        assertTrue((fused.confidence ?: 0.0) > .55)
    }

    @Test fun ghostComparesAgainstHistoricalBest() {
        val best = record(metrics(speed = 1.35, launch = .15), 90, 1000L, distance = 3.0, miss = .10)
        V15GhostRuntime.seed(listOf(best))
        val current = record(metrics(speed = 1.36, launch = .10), 94, 2000L, distance = 3.0, miss = .06)
        val comparison = V15GhostRuntime.compare(current, emptyList())
        assertNotNull(comparison)
        assertTrue(comparison!!.beatGhost)
        assertEquals(4, comparison.scoreDelta)
    }

    @Test fun newCompetitiveModesScoreAndAdvance() {
        val settings = GreenSettings()
        val engine = GameModeEngine(settings)
        engine.configurePlayers(2)
        engine.setMode(PracticeMode.DART)
        engine.onResult(SimResult(false, .0, settings.holeDistanceM - .15, .15, 1.0))
        assertTrue(engine.status.lastPoints >= 150)
        engine.prepareNextIfNeeded()
        assertEquals(2, engine.status.activePlayer)

        engine.setMode(PracticeMode.CURLING)
        engine.onResult(SimResult(true, 0.0, settings.holeDistanceM, 0.0, 1.0))
        assertEquals(250, engine.status.lastPoints)
    }

    private fun metrics(
        speed: Double = 1.40,
        launch: Double = .2,
        head: Double = 1.0,
        face: Double? = .2,
        path: Double? = .3,
        impact: Double? = 1.0,
        confidence: Double = .78
    ) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = launch,
        headSpeedMps = head,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = speed / head,
        impactOffsetMm = impact,
        measuredAtNs = 1L,
        backswingMs = 410.0,
        downswingMs = 210.0,
        tempoRatio = 1.95,
        backswingLengthCm = 15.0,
        peakHeadAccelerationMps2 = 1.2,
        confidence = confidence
    )

    private fun record(
        metrics: ShotMetrics,
        score: Int,
        timestamp: Long,
        distance: Double = 3.0,
        miss: Double = .12
    ) = ShotRecord(
        metrics = metrics,
        result = SimResult(false, miss, distance, miss, 1.5),
        strokeScore = StrokeScore(score, score, score, score, score, score, score),
        mode = PracticeMode.PRACTICE,
        targetDistanceM = distance,
        putterProfileName = "테스트 퍼터",
        timestampMs = timestamp
    )
}
