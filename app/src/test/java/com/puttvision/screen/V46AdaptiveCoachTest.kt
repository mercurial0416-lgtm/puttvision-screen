package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V46AdaptiveCoachTest {

    @Test fun lowConfidenceFaceDoesNotBecomeRootCause() {
        val records = (0 until 10).map { i ->
            record(ts = i.toLong(), confidence = .25, face = 2.2, path = .1, launch = .12)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        assertFalse(snap.focus == V46CoachFocus.FACE)
    }

    @Test fun persistentTrustedFaceBiasBecomesPrimaryFocus() {
        val records = (0 until 12).map { i ->
            record(ts = i.toLong(), confidence = .86, face = 1.15, path = .15, launch = .9)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        assertEquals(V46CoachFocus.FACE, snap.focus)
        assertTrue(snap.score >= 60)
        assertTrue(snap.evidenceShots >= 8)
    }

    @Test fun oneHugeFaceOutlierIsSuppressedByRobustCenter() {
        val records = (0 until 11).map { i ->
            record(ts = i.toLong(), confidence = .86, face = if (i == 10) 7.0 else .12, path = .1, launch = .12)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        assertFalse(snap.focus == V46CoachFocus.FACE)
    }

    @Test fun chronicShortPaceIsSeparatedFromDirectionalErrors() {
        val records = (0 until 10).map { i ->
            record(ts = i.toLong(), distance = 4.0, finishY = 3.35, launch = .05, face = .05, path = .05)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        assertEquals(V46CoachFocus.PACE_SHORT, snap.focus)
        assertTrue(snap.prescription.cue.contains("피니시"))
    }

    @Test fun chronicLongPaceGetsDifferentPrescription() {
        val records = (0 until 10).map { i ->
            record(ts = i.toLong(), distance = 4.0, finishY = 4.70, launch = .05, face = .05, path = .05)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        assertEquals(V46CoachFocus.PACE_LONG, snap.focus)
        assertTrue(snap.prescription.cue.contains("백스윙"))
    }

    @Test fun hysteresisKeepsExistingFocusWhenChallengerOnlyBarelyWins() {
        val records = (0 until 12).map { i ->
            record(ts = i.toLong(), face = .80, path = 1.10, launch = .70)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records, V46CoachFocus.FACE))
        assertEquals(V46CoachFocus.FACE, snap.focus)
        assertTrue(snap.heldByHysteresis)
    }

    @Test fun clearlyStrongerProblemCanBreakHysteresis() {
        val records = (0 until 12).map { i ->
            record(ts = i.toLong(), face = .58, path = 2.10, launch = .75)
        }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records, V46CoachFocus.FACE))
        assertEquals(V46CoachFocus.PATH, snap.focus)
        assertFalse(snap.heldByHysteresis)
    }

    @Test fun trendDetectsMeaningfulImprovement() {
        val old = (0 until 10).map { i -> record(ts = i.toLong(), face = 1.65, path = .1, launch = 1.1) }
        val newer = (10 until 20).map { i -> record(ts = i.toLong(), face = .72, path = .1, launch = .45) }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(old + newer, V46CoachFocus.FACE))
        assertEquals(V46CoachFocus.FACE, snap.focus)
        assertEquals(V46CoachTrend.IMPROVING, snap.trend)
        assertTrue(snap.trendDelta <= -8)
    }

    @Test fun adaptivePlanChangesOnlyWeaknessBlock() {
        val records = (0 until 12).map { i -> record(ts = i.toLong(), face = 1.20, path = .1, launch = .85) }
        val snap = requireNotNull(V46AdaptiveCoachEngine.analyze(records))
        val base = V16TrainingPlanner.build(null)
        val adapted = V46AdaptiveTrainingPlan.adapt(base, snap)
        assertEquals(base.blocks[0], adapted.blocks[0])
        assertEquals(snap.prescription.title, adapted.blocks[1].title)
        assertEquals(base.blocks[2], adapted.blocks[2])
        assertTrue(adapted.title.contains(snap.focus.label))
    }

    @Test fun switchingProfilesDoesNotInheritFocusHysteresis() {
        V46AdaptiveCoachRuntime.clear()
        val a = (0 until 12).map { i ->
            record(ts = i.toLong(), profile = "A", face = 1.15, path = .1, launch = .8)
        }
        V46AdaptiveCoachRuntime.update(a)
        assertEquals(V46CoachFocus.FACE, V46AdaptiveCoachRuntime.snapshot?.focus)

        val b = (0 until 12).map { i ->
            record(ts = (100 + i).toLong(), profile = "B", face = .80, path = 1.10, launch = .7)
        }
        V46AdaptiveCoachRuntime.update(b)
        assertEquals(V46CoachFocus.PATH, V46AdaptiveCoachRuntime.snapshot?.focus)
        assertFalse(V46AdaptiveCoachRuntime.snapshot?.heldByHysteresis ?: true)
        V46AdaptiveCoachRuntime.clear()
    }

    @Test fun instantCoachDoesNotCallLowTrustFaceAConfirmedCause() {
        V46AdaptiveCoachRuntime.clear()
        val recent = (0 until 8).map { i -> record(ts = i.toLong(), face = .1, path = .1, launch = .1) }
        val current = metrics(confidence = .25, face = 2.4, path = .1, launch = .1)
        val feedback = CoachEngine.diagnose(current, score(88), recent)
        assertFalse(feedback.headline.startsWith("페이스"))
    }

    @Test fun shuffledImportedHistoryIsAnalyzedChronologically() {
        val chronological = (0 until 10).map { i ->
            record(ts = i.toLong(), face = if (i < 5) 1.5 else .7, path = .1, launch = .7)
        }
        val a = requireNotNull(V46AdaptiveCoachEngine.analyze(chronological, V46CoachFocus.FACE))
        val b = requireNotNull(V46AdaptiveCoachEngine.analyze(chronological.reversed(), V46CoachFocus.FACE))
        assertEquals(a.focus, b.focus)
        assertEquals(a.trend, b.trend)
        assertEquals(a.trendDelta, b.trendDelta)
    }

    private fun metrics(
        confidence: Double = .86,
        face: Double? = .1,
        path: Double? = .1,
        launch: Double = .1,
        impact: Double? = 1.0,
        tempo: Double? = 2.0,
        speed: Double = 1.35
    ) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = launch,
        headSpeedMps = 1.0,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = 1.35,
        impactOffsetMm = impact,
        measuredAtNs = 1L,
        tempoRatio = tempo,
        rawBallSpeedMps = speed,
        confidence = confidence
    )

    private fun record(
        ts: Long,
        profile: String = "owner",
        confidence: Double = .86,
        face: Double? = .1,
        path: Double? = .1,
        launch: Double = .1,
        impact: Double? = 1.0,
        tempo: Double? = 2.0,
        distance: Double = 3.0,
        finishY: Double = distance,
        finishX: Double = 0.0,
        sideSlope: Double = 0.0,
        longSlope: Double = 0.0,
        terrain: Int = -1
    ) = ShotRecord(
        metrics = metrics(confidence, face, path, launch, impact, tempo),
        result = SimResult(
            holed = false,
            finishX = finishX,
            finishY = finishY,
            distanceToCupM = kotlin.math.hypot(finishX, finishY - distance),
            elapsedSec = 1.0
        ),
        strokeScore = score(82),
        mode = PracticeMode.PRACTICE,
        targetDistanceM = distance,
        stimpMeters = 2.8,
        sideSlopePct = sideSlope,
        longSlopePct = longSlope,
        terrainProfileId = terrain,
        userProfileId = profile,
        timestampMs = ts
    )

    private fun score(v: Int) = StrokeScore(v, v, v, v, v, v, v)
}
