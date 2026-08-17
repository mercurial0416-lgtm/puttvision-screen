package com.puttvision.screen

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V49Real30Test {

    @Test fun publicUpdaterRequiresPinnedHttpsAndHash() {
        val goodSha = "a".repeat(64)
        val publicApk = "https://razejagceyznnajioxgx.supabase.co/storage/v1/object/public/puttvision-update/releases/puttvision.apk"
        assertFalse(V49UpdatePolicy.validateManifestUrl("http://example.com/update.json").valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", publicApk, null), true).valid)
        assertFalse(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", "https://example.com/app.apk", goodSha), true).valid)
        assertTrue(V49UpdatePolicy.validateInfo(UpdateInfo(2, "2.0", publicApk, goodSha), true).valid)
    }

    @Test fun updaterRejectsOversizeStreamsAndNonUpgrade() {
        val input = V49UpdatePolicy.limited(ByteArrayInputStream(ByteArray(9)), 8)
        val out = ByteArray(16)
        val failed = runCatching { input.read(out) }.isFailure
        assertTrue(failed)
        assertFalse(V49UpdatePolicy.isUpgrade(12, 12))
        assertFalse(V49UpdatePolicy.isUpgrade(12, 11))
        assertTrue(V49UpdatePolicy.isUpgrade(12, 13))
    }

    @Test fun updaterCacheCleanupKeepsNewestBoundedAndPendingPathIsContained() {
        val root = Files.createTempDirectory("pv49-update").toFile()
        val updates = File(root, "updates").apply { mkdirs() }
        repeat(6) { i ->
            File(updates, "v$i.apk").apply {
                writeBytes(ByteArray(4))
                setLastModified(10_000L + i)
            }
        }
        V49UpdatePolicy.cleanCache(updates, nowMs = 20_000L)
        assertTrue(updates.listFiles()!!.count { it.extension == "apk" } <= V49UpdatePolicy.MAX_UPDATE_CACHE_FILES)
        val inside = File(updates, "safe.apk").apply { writeBytes(ByteArray(4)) }
        val outside = File(root, "evil.apk").apply { writeBytes(ByteArray(4)) }
        assertTrue(V49UpdatePolicy.pendingPathAllowed(root, inside.absolutePath))
        assertFalse(V49UpdatePolicy.pendingPathAllowed(root, outside.absolutePath))
        root.deleteRecursively()
    }

    @Test fun fusionUsesOnlyOneCameraPerPhysicalView() {
        val now = 100_000L
        val selected = V49FusionPolicy.select(
            listOf(
                measurement("p", V15CameraView.PRIMARY, now, .8),
                measurement("face-old", V15CameraView.FACE_ON, now - 20, .8),
                measurement("face-best", V15CameraView.FACE_ON, now - 10, .95),
                measurement("top", V15CameraView.TOP, now - 15, .8)
            ), now
        )
        assertEquals(3, selected.measurements.size)
        assertEquals(1, selected.droppedSameView)
        assertEquals(setOf(V15CameraView.FACE_ON, V15CameraView.TOP), selected.companionViews)
    }

    @Test fun fusionQuarantinesInvalidAndStalePackets() {
        val now = 100_000L
        val selected = V49FusionPolicy.select(
            listOf(
                measurement("p", V15CameraView.PRIMARY, now, .8),
                measurement("nan", V15CameraView.TOP, now, Double.NaN),
                measurement("stale", V15CameraView.FACE_ON, now - 2_000, .8)
            ), now
        )
        assertEquals(1, selected.measurements.size)
        assertEquals(1, selected.droppedInvalid)
        assertEquals(1, selected.droppedStale)
    }

    @Test fun fusionConfidenceNeedsViewDiversity() {
        assertTrue(V49FusionPolicy.confidenceSupportBonus(setOf(V15CameraView.TOP), 6) <
            V49FusionPolicy.confidenceSupportBonus(setOf(V15CameraView.TOP, V15CameraView.FACE_ON), 6))
        assertEquals(.94, V49FusionPolicy.confidenceCeiling(setOf(V15CameraView.TOP)), .0001)
        assertTrue(V49FusionPolicy.confidenceCeiling(setOf(V15CameraView.TOP, V15CameraView.FACE_ON)) > .94)
    }

    @Test fun fusionDiagnosticsExposeViewsDropsAndDiversity() {
        val now = 100_000L
        val fused = V37FeatureFusion.fuse(
            listOf(
                measurement("p", V15CameraView.PRIMARY, now, .80),
                measurement("top", V15CameraView.TOP, now - 10, .86),
                measurement("top2", V15CameraView.TOP, now - 15, .70),
                measurement("face", V15CameraView.FACE_ON, now - 20, .84)
            ), now
        )
        assertNotNull(fused)
        assertTrue(V37FeatureFusion.diagnostics.activeViews.contains("TOP"))
        assertTrue(V37FeatureFusion.diagnostics.diversityScore >= 78)
        assertTrue(V37FeatureFusion.diagnostics.droppedPackets >= 1)
    }

    @Test fun trainingCanPauseResumeRestartAndSkip() {
        GameEngine()
        val plan = tinyPlan()
        V31TrainingSessionRuntime.stop(true)
        assertTrue(V31TrainingSessionRuntime.start(plan))
        assertTrue(V31TrainingSessionRuntime.pause())
        assertTrue(V31TrainingSessionRuntime.progress().paused)
        assertTrue(V31TrainingSessionRuntime.resume())
        assertFalse(V31TrainingSessionRuntime.progress().paused)
        assertTrue(V31TrainingSessionRuntime.restartCurrentBlock())
        assertTrue(V31TrainingSessionRuntime.skipCurrentBlock())
        assertEquals(1, V31TrainingSessionRuntime.progress().blockIndex)
        V31TrainingSessionRuntime.stop(true)
    }

    @Test fun trainingCompletionBuildsWeakestRetryAndCompletionStats() {
        GameEngine()
        V31TrainingSessionRuntime.stop(true)
        assertTrue(V31TrainingSessionRuntime.start(tinyPlan(shots = 1)))
        V31TrainingSessionRuntime.onRecord(record(1, score = 60, launch = 2.0, finishY = 1.0, distance = 1.5))
        V31TrainingSessionRuntime.onRecord(record(2, score = 85, launch = .1, finishY = 2.0, distance = 2.0))
        V31TrainingSessionRuntime.onRecord(record(3, score = 85, launch = .1, finishY = 3.0, distance = 3.0))
        V31TrainingSessionRuntime.onRecord(record(4, score = 85, launch = .1, finishY = 2.0, distance = 2.0, holed = true))
        val completion = V31TrainingSessionRuntime.lastCompleted()
        assertNotNull(completion)
        assertEquals(4, completion!!.totalShots)
        assertNotNull(completion.weakestBlockTitle)
        assertTrue(V31TrainingSessionRuntime.retryWeakestBlock())
        assertTrue(V31TrainingSessionRuntime.progress().running)
        V31TrainingSessionRuntime.stop(true)
    }

    @Test fun sessionInsightsDetectMomentumDirectionAndPaceIndependently() {
        val records = (0 until 10).map { i ->
            record(
                ts = i.toLong(),
                score = if (i < 5) 68 else 86,
                launch = .9,
                finishY = 3.45,
                distance = 3.0,
                confidence = .85
            )
        }
        val s = V49SessionInsightsEngine.analyze(records)
        assertTrue((s.momentumDelta ?: 0.0) > 10)
        assertTrue((s.directionBiasDeg ?: 0.0) > .5)
        assertTrue((s.paceBiasCm ?: 0.0) > 25)
        assertTrue(s.directionLabel.contains("우측"))
        assertTrue(s.paceLabel.contains("길게"))
    }

    @Test fun sessionInsightsDetectConfidenceAndLateFade() {
        val records = (0 until 14).map { i ->
            val late = i >= 9
            record(
                ts = i.toLong(),
                score = if (late) 60 else 88,
                launch = .1,
                tempo = if (late) 3.0 + (i % 2) else 2.0,
                confidence = if (late) .52 else .90
            )
        }
        val s = V49SessionInsightsEngine.analyze(records)
        assertTrue((s.confidenceDeltaPct ?: 0.0) < -8)
        assertTrue(s.lateSessionFade)
    }

    @Test fun sessionInsightsTrackBestStreakAndBuildQuickPlan() {
        val records = (0 until 8).map { i ->
            record(ts = i.toLong(), score = if (i == 4) 97 else 84, launch = .1, confidence = .86)
        }
        val s = V49SessionInsightsEngine.analyze(records)
        assertEquals(97, s.personalBestScore)
        assertEquals(8, s.consistencyStreak)
        assertEquals(4, s.quickPlan.blocks.size)
        assertTrue(s.quickPlan.estimatedMinutes <= 10)
    }

    @Test fun pausedTvPolicyDoesNotSpinAtActiveRate() {
        val p = V31TrainingProgress(true, false, 0, 4, 1, 8, 1, 1, 1, 0, "warm", 1.5, "pause", paused = true)
        assertEquals(1_000L, V41TrainingTvPolicy.refreshDelayMs(p))
        assertTrue(V41TrainingTvPolicy.layout(1920, 1080).height >= 160f)
    }

    private fun measurement(id: String, view: V15CameraView, time: Long, confidence: Double) =
        V15CameraMeasurement(id, view, metrics(confidence = if (confidence.isFinite()) confidence else .8), confidence, time)

    private fun metrics(
        confidence: Double = .86,
        launch: Double = .1,
        tempo: Double? = 2.0,
        speed: Double = 1.35
    ) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = launch,
        headSpeedMps = 1.0,
        faceAngleDeg = .1,
        pathAngleDeg = .1,
        faceToPathDeg = .0,
        smash = 1.35,
        impactOffsetMm = 1.0,
        measuredAtNs = 1L,
        tempoRatio = tempo,
        rawBallSpeedMps = speed,
        confidence = confidence
    )

    private fun record(
        ts: Long,
        score: Int = 84,
        launch: Double = .1,
        tempo: Double? = 2.0,
        finishY: Double = 3.0,
        distance: Double = 3.0,
        confidence: Double = .86,
        holed: Boolean = false
    ) = ShotRecord(
        metrics = metrics(confidence, launch, tempo),
        result = SimResult(
            holed = holed,
            finishX = 0.0,
            finishY = finishY,
            distanceToCupM = kotlin.math.abs(finishY - distance),
            elapsedSec = 1.0
        ),
        strokeScore = StrokeScore(score, score, score, score, score, score, score),
        mode = PracticeMode.PRACTICE,
        targetDistanceM = distance,
        stimpMeters = 2.8,
        sideSlopePct = 0.0,
        longSlopePct = 0.0,
        terrainProfileId = -1,
        userProfileId = "owner",
        timestampMs = ts
    )

    private fun tinyPlan(shots: Int = 2) = V16DailyTrainingPlan(
        title = "test",
        estimatedMinutes = 2,
        blocks = listOf(
            V16TrainingBlock("warm", shots, 1.5, .0, .0, "start"),
            V16TrainingBlock("weak", shots, 2.0, .0, .0, "weak"),
            V16TrainingBlock("distance", shots, 3.0, .0, .0, "distance"),
            V16TrainingBlock("pressure", shots, 2.0, .0, .0, "pressure")
        ),
        reason = "test"
    )
}
