package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V37FeatureFusionTest {
    private fun metrics(
        ball: Double = 1.5,
        launch: Double = 0.0,
        face: Double? = 0.0,
        path: Double? = 0.0,
        impact: Double? = 0.0,
        confidence: Double = .85
    ) = ShotMetrics(
        ballSpeedMps = ball,
        launchAngleDeg = launch,
        headSpeedMps = 1.25,
        faceAngleDeg = face,
        pathAngleDeg = path,
        faceToPathDeg = if (face != null && path != null) face - path else null,
        smash = 1.2,
        impactOffsetMm = impact,
        measuredAtNs = 1_000_000_000L,
        backswingMs = 480.0,
        downswingMs = 230.0,
        tempoRatio = 2.09,
        backswingLengthCm = 22.0,
        peakHeadAccelerationMps2 = 1.4,
        confidence = confidence
    )

    private fun measurement(
        id: String,
        view: V15CameraView,
        metrics: ShotMetrics,
        now: Long,
        ageMs: Long = 0L,
        confidence: Double = metrics.confidence ?: .85
    ) = V15CameraMeasurement(id, view, metrics, confidence, now - ageMs)

    @Test fun faceOnHasMoreAuthorityForFaceThanDownTheLine() {
        val now = 10_000L
        val primary = measurement("primary", V15CameraView.PRIMARY, metrics(face = 0.0, path = 0.0), now)
        val faceOn = measurement("face", V15CameraView.FACE_ON, metrics(face = 2.0, path = 4.0, confidence = .9), now)
        val downLine = measurement("dtl", V15CameraView.DOWN_THE_LINE, metrics(face = 4.0, path = 2.0, confidence = .9), now)
        val fused = requireNotNull(V37FeatureFusion.fuse(listOf(primary, faceOn, downLine), now))
        assertTrue(requireNotNull(fused.faceAngleDeg) < 2.0)
        assertTrue(requireNotNull(fused.pathAngleDeg) < 2.0)
        assertTrue(requireNotNull(fused.faceAngleDeg) > 1.0)
        assertTrue(requireNotNull(fused.pathAngleDeg) > 1.0)
        assertEquals(requireNotNull(fused.faceAngleDeg) - requireNotNull(fused.pathAngleDeg), requireNotNull(fused.faceToPathDeg), 1e-9)
    }

    @Test fun topViewGetsMoreAuthorityForBallAndStartLine() {
        val now = 20_000L
        val primary = measurement("primary", V15CameraView.PRIMARY, metrics(ball = 1.40, launch = 0.0), now)
        val faceOn = measurement("face", V15CameraView.FACE_ON, metrics(ball = 1.60, launch = 1.0, confidence = .9), now)
        val top = measurement("top", V15CameraView.TOP, metrics(ball = 1.60, launch = 1.0, confidence = .9), now)
        val faceOnly = requireNotNull(V37FeatureFusion.fuse(listOf(primary, faceOn), now))
        val topOnly = requireNotNull(V37FeatureFusion.fuse(listOf(primary, top), now))
        assertTrue(topOnly.ballSpeedMps > faceOnly.ballSpeedMps)
        assertTrue(topOnly.launchAngleDeg > faceOnly.launchAngleDeg)
    }

    @Test fun impossibleCompanionFeatureIsRejectedWithoutDiscardingOtherCameras() {
        val now = 30_000L
        val primary = measurement("primary", V15CameraView.PRIMARY, metrics(face = .2, path = .1), now)
        val good = measurement("face", V15CameraView.FACE_ON, metrics(face = .5, path = .2, confidence = .92), now)
        val bad = measurement("bad", V15CameraView.TOP, metrics(face = 30.0, path = -28.0, confidence = .99), now)
        val fused = requireNotNull(V37FeatureFusion.fuse(listOf(primary, good, bad), now))
        assertTrue(requireNotNull(fused.faceAngleDeg) in .2..1.0)
        assertTrue(requireNotNull(fused.pathAngleDeg) in .1..1.0)
        assertTrue(V37FeatureFusion.diagnostics.rejectedOutliers >= 2)
        assertTrue(V37FeatureFusion.diagnostics.companionCount == 2)
    }

    @Test fun staleCompanionCannotMoveTheShot() {
        val now = 40_000L
        val primaryMetrics = metrics(ball = 1.5, launch = .3, face = .2, path = .1)
        val primary = measurement("primary", V15CameraView.PRIMARY, primaryMetrics, now)
        val stale = measurement("stale", V15CameraView.TOP, metrics(ball = 3.0, launch = 8.0, face = 8.0, path = 8.0), now, ageMs = 1_800L)
        val fused = requireNotNull(V37FeatureFusion.fuse(listOf(primary, stale), now))
        assertEquals(primaryMetrics.ballSpeedMps, fused.ballSpeedMps, 1e-9)
        assertEquals(primaryMetrics.launchAngleDeg, fused.launchAngleDeg, 1e-9)
        assertEquals(primaryMetrics.faceAngleDeg!!, fused.faceAngleDeg!!, 1e-9)
        assertEquals(0, V37FeatureFusion.diagnostics.companionCount)
    }

    @Test fun olderButValidSampleGetsLessInfluence() {
        val now = 50_000L
        val primary = measurement("primary", V15CameraView.PRIMARY, metrics(ball = 1.4), now)
        val fresh = measurement("fresh", V15CameraView.TOP, metrics(ball = 1.8, confidence = .9), now, ageMs = 100L)
        val old = measurement("old", V15CameraView.TOP, metrics(ball = 1.8, confidence = .9), now, ageMs = 1_100L)
        val freshFused = requireNotNull(V37FeatureFusion.fuse(listOf(primary, fresh), now))
        val oldFused = requireNotNull(V37FeatureFusion.fuse(listOf(primary, old), now))
        assertTrue(freshFused.ballSpeedMps > oldFused.ballSpeedMps)
    }
}
