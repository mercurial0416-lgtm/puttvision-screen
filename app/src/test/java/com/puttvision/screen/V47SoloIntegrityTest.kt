package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V47SoloIntegrityTest {
    private fun metrics(
        ball: Double = 1.4,
        launch: Double = .2,
        head: Double? = 1.0,
        face: Double? = .3,
        path: Double? = .1,
        impact: Double? = 2.0,
        confidence: Double? = .85,
        backswing: Double? = 500.0,
        downswing: Double? = 240.0,
        tempo: Double? = 2.08
    ) = ShotMetrics(
        ballSpeedMps = ball,
        launchAngleDeg = launch,
        headSpeedMps = head,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = if (head != null) ball / head else null,
        impactOffsetMm = impact,
        measuredAtNs = 1L,
        backswingMs = backswing,
        downswingMs = downswing,
        tempoRatio = tempo,
        backswingLengthCm = 22.0,
        peakHeadAccelerationMps2 = 1.5,
        rawBallSpeedMps = ball,
        estimatedMatDecelMps2 = .8,
        estimatedMatStimpM = 2.9,
        confidence = confidence
    )

    private fun record(
        timestamp: Long,
        profile: String = "owner",
        metrics: ShotMetrics = metrics(),
        target: Double = 3.0,
        stimp: Double = 2.8,
        side: Double = .0,
        long: Double = .0,
        terrain: Int = -1,
        result: SimResult? = SimResult(false, .1, 3.1, .14, 2.0),
        score: StrokeScore = StrokeScore(80, 80, 80, 80, 80, 80, 80)
    ) = ShotRecord(
        metrics = metrics,
        result = result,
        strokeScore = score,
        mode = PracticeMode.PRACTICE,
        targetDistanceM = target,
        stimpMeters = stimp,
        sideSlopePct = side,
        longSlopePct = long,
        terrainProfileId = terrain,
        userProfileId = profile,
        timestampMs = timestamp
    )

    @Test fun nonFiniteBallSpeedIsRejected() {
        val r = V47ShotGuard.normalize(metrics(ball = Double.NaN))
        assertFalse(r.accepted)
        assertEquals("BALL_SPEED_NON_FINITE", r.rejectReason)
    }

    @Test fun impossibleBallSpeedIsRejected() {
        assertFalse(V47ShotGuard.normalize(metrics(ball = 9.0)).accepted)
        assertFalse(V47ShotGuard.normalize(metrics(ball = .01)).accepted)
    }

    @Test fun invalidLaunchIsRejected() {
        assertFalse(V47ShotGuard.normalize(metrics(launch = Double.POSITIVE_INFINITY)).accepted)
        assertFalse(V47ShotGuard.normalize(metrics(launch = 19.0)).accepted)
    }

    @Test fun unsafeOptionalMetricsAreRemovedWithoutRejectingShot() {
        val raw = metrics(head = 9.0, face = 40.0, path = Double.NaN, impact = 200.0)
        val r = V47ShotGuard.normalize(raw)
        assertTrue(r.accepted)
        val m = requireNotNull(r.metrics)
        assertNull(m.headSpeedMps)
        assertNull(m.faceAngleDeg)
        assertNull(m.pathAngleDeg)
        assertNull(m.impactOffsetMm)
        assertTrue(r.sanitizedFields.containsAll(listOf("HEAD_SPEED", "FACE", "PATH", "IMPACT")))
    }

    @Test fun dependentFaceToPathAndSmashAreRecomputed() {
        val raw = metrics(ball = 1.5, head = 1.25, face = .8, path = -.2).copy(faceToPathDeg = 99.0, smash = 99.0)
        val m = requireNotNull(V47ShotGuard.normalize(raw).metrics)
        assertEquals(1.0, requireNotNull(m.faceToPathDeg), 1e-9)
        assertEquals(1.2, requireNotNull(m.smash), 1e-9)
    }

    @Test fun timingMatAndConfidenceChannelsAreBounded() {
        val raw = metrics(confidence = 1.5, backswing = 8.0, downswing = 9_000.0, tempo = 8.0).copy(
            backswingLengthCm = 900.0,
            peakHeadAccelerationMps2 = 900.0,
            rawBallSpeedMps = 99.0,
            estimatedMatDecelMps2 = -1.0,
            estimatedMatStimpM = 99.0
        )
        val r = V47ShotGuard.normalize(raw)
        val m = requireNotNull(r.metrics)
        assertEquals(.99, requireNotNull(m.confidence), 1e-9)
        assertNull(m.backswingMs)
        assertNull(m.downswingMs)
        assertNull(m.tempoRatio)
        assertNull(m.backswingLengthCm)
        assertNull(m.peakHeadAccelerationMps2)
        assertNull(m.rawBallSpeedMps)
        assertNull(m.estimatedMatDecelMps2)
        assertNull(m.estimatedMatStimpM)
    }

    @Test fun recordMetadataIsNormalizedIntoProductBounds() {
        val now = 1_000_000L
        val raw = record(
            timestamp = now,
            profile = "",
            target = Double.NaN,
            stimp = 99.0,
            side = -99.0,
            long = 99.0,
            terrain = 999
        )
        val r = V47RecordGuard.normalize(raw, "owner", now)
        assertEquals("owner", r.record.userProfileId)
        assertEquals(.0, r.record.targetDistanceM, 1e-9)
        assertEquals(5.0, r.record.stimpMeters, 1e-9)
        assertEquals(-10.0, r.record.sideSlopePct, 1e-9)
        assertEquals(10.0, r.record.longSlopePct, 1e-9)
        assertEquals(-1, r.record.terrainProfileId)
    }

    @Test fun resultFieldsAreNormalizedAndHoledCannotAlsoBeLipOut() {
        val rawResult = SimResult(true, .0, 3.0, -.4, 99.0, lipOut = true, cupContacts = 99)
        val r = V47RecordGuard.normalize(record(1_000L, result = rawResult), "owner", 2_000L).record.result
        requireNotNull(r)
        assertEquals(.0, r.distanceToCupM, 1e-9)
        assertEquals(20.5, r.elapsedSec, 1e-9)
        assertFalse(r.lipOut)
        assertEquals(20, r.cupContacts)
    }

    @Test fun nonFiniteResultIsDroppedInsteadOfPoisoningReports() {
        val rawResult = SimResult(false, Double.NaN, 3.0, .2, 1.0)
        val normalized = V47RecordGuard.normalize(record(1_000L, result = rawResult), "owner", 2_000L)
        assertNull(normalized.record.result)
        assertTrue(normalized.normalizedFields.contains("RESULT"))
    }

    @Test fun everyStrokeScoreChannelIsClamped() {
        val bad = StrokeScore(150, -5, 101, -1, 999, 102, -3)
        val score = V47RecordGuard.normalize(record(1_000L, score = bad), "owner", 2_000L).record.strokeScore
        assertEquals(100, score.total)
        assertEquals(0, score.face)
        assertEquals(100, score.path)
        assertEquals(0, score.tempo)
        assertEquals(100, score.impact)
        assertEquals(100, score.distance)
        assertEquals(0, score.consistency)
    }

    @Test fun historyIsProfileIsolatedChronologicalAndFutureSafe() {
        val now = 1_000_000L
        val input = listOf(
            record(900L, profile = "owner"),
            record(100L, profile = "owner"),
            record(200L, profile = "guest"),
            record(now + V47HistoryGuard.FUTURE_ALLOWANCE_MS + 1L, profile = "owner")
        )
        val report = V47HistoryGuard.prepare(input, "owner", now)
        assertEquals(listOf(100L, 900L), report.records.map { it.timestampMs })
        assertEquals(1, report.droppedProfile)
        assertEquals(1, report.droppedTimestamp)
    }

    @Test fun historyDropsInvalidMandatoryShots() {
        val report = V47HistoryGuard.prepare(
            listOf(record(100L), record(200L, metrics = metrics(ball = Double.NaN))),
            "owner",
            1_000L
        )
        assertEquals(1, report.records.size)
        assertEquals(1, report.droppedShot)
    }

    @Test fun historyDropsExactSemanticDuplicates() {
        val a = record(100L)
        val report = V47HistoryGuard.prepare(listOf(a, a.copy()), "owner", 1_000L)
        assertEquals(1, report.records.size)
        assertEquals(1, report.droppedDuplicates)
    }

    @Test fun historyCapsAnalyticsWindowAfterCleanup() {
        val input = (1..140).map { i -> record(i.toLong()) }
        val report = V47HistoryGuard.prepare(input, "owner", 1_000L)
        assertEquals(V47HistoryGuard.MAX_RECORDS, report.records.size)
        assertEquals(21L, report.records.first().timestampMs)
        assertEquals(140L, report.records.last().timestampMs)
    }

    @Test fun healthDoesNotClaimReadyBeforeEightCleanShots() {
        val health = V47SoloHealthEngine.build(healthInput(cleanRecords = 4))
        assertTrue(health.insufficientData)
        assertTrue(health.shortLabel.startsWith("DATA"))
    }

    @Test fun cleanSoftwareIntegrityCanReachAGradeWithoutClaimingHardwareAccuracy() {
        val health = V47SoloHealthEngine.build(healthInput(cleanRecords = 12))
        assertFalse(health.insufficientData)
        assertEquals("A", health.grade)
        assertTrue(health.score >= 90)
    }

    @Test fun degradedHfrCreatesConcreteNextAction() {
        val input = healthInput(cleanRecords = 12).copy(
            hfr = V43HfrHealthSummary(12, 1_400L, 5_500L, 1_800L, 30, 4)
        )
        val health = V47SoloHealthEngine.build(input)
        assertTrue(health.sections.first { it.name == "HFR" }.score < 60)
        assertTrue(health.nextActions.any { it.contains("HFR P95") })
    }

    @Test fun repeatedHfrFailureReasonIsSurfaced() {
        val input = healthInput(cleanRecords = 12).copy(
            hfrFailures = V45HfrFailureSummary(10, V45HfrFailureReason.CALIBRATION, 7)
        )
        val health = V47SoloHealthEngine.build(input)
        assertEquals("REPEAT", health.sections.first { it.name == "HFR FAIL" }.status)
        assertTrue(health.nextActions.any { it.contains("CALIBRATION") })
    }

    @Test fun multiPhoneIsOptionalWhenSoloAndDoesNotBlockOverallGrade() {
        val health = V47SoloHealthEngine.build(healthInput(cleanRecords = 12))
        val multi = health.sections.first { it.name == "MULTI PHONE" }
        assertTrue(multi.optional)
        assertEquals("OFF", multi.status)
        assertTrue(health.score >= 90)
    }

    @Test fun lowFacePathImpactCoverageIsVisible() {
        val now = 10_000L
        val sparse = (1..10).map { i ->
            record(i.toLong(), metrics = metrics(face = null, path = null, impact = null))
        }
        val history = V47HistoryGuard.prepare(sparse, "owner", now)
        val input = healthInput(cleanRecords = 10).copy(history = history)
        val health = V47SoloHealthEngine.build(input)
        val coverage = health.sections.first { it.name == "DATA COVERAGE" }
        assertEquals("LOW", coverage.status)
        assertTrue(health.nextActions.any { it.contains("커버리지") })
    }

    private fun healthInput(cleanRecords: Int): V47SoloHealthInput {
        val records = (1..cleanRecords).map { i -> record(i.toLong()) }
        val history = V47HistoryReport(records, cleanRecords, 0, 0, 0, 0, 0)
        val shot = V47ShotGuard.normalize(metrics())
        val hfr = V43HfrHealthSummary(10, 900L, 1_600L, 500L, 70, 0)
        val failures = V45HfrFailureSummary(0, null, 0)
        val companion = V16CompanionUiStatus(
            role = V16CompanionRole.OFF,
            host = null,
            view = V15CameraView.FACE_ON,
            peers = 0,
            received = 0,
            rejected = 0,
            featureTracks = 0,
            sessionCode = null,
            syncLabel = null,
            label = "꺼짐"
        )
        val stereo = V44StereoReadiness(
            ready = false,
            cameraId = null,
            view = null,
            score = 0,
            shotSkewMs = null,
            matchedFrames = 0,
            ballPairs = 0,
            putterPairs = 0,
            medianTimeDeltaMs = null,
            medianBallDeltaCm = null,
            reason = "보조폰 HFR track 없음"
        )
        return V47SoloHealthInput(shot, history, hfr, failures, companion, stereo)
    }
}
