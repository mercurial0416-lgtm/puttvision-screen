package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V44StereoReadinessTest {
    private fun track(
        fps: Int = 240,
        impact: Int = 100,
        shiftX: Double = 0.0,
        withPutter: Boolean = true,
        frameOffsets: IntRange = -10..10
    ): HfrFeatureTrack {
        val frameMs = 1000.0 / fps
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = impact,
            frames = frameOffsets.map { offset ->
                val t = offset * frameMs
                val x = shiftX + t * .02
                val y = t * .035
                HfrFeatureFrame(
                    frame = impact + offset,
                    timeFromImpactMs = t,
                    ballXcm = x,
                    ballYcm = y,
                    heelXcm = if (withPutter) x - 1.2 else null,
                    heelYcm = if (withPutter) y - .4 else null,
                    toeXcm = if (withPutter) x + 1.2 else null,
                    toeYcm = if (withPutter) y + .4 else null,
                    markerAngleDeg = .2
                )
            }
        )
    }

    private fun packet(
        id: String = "cam-top",
        view: V15CameraView = V15CameraView.TOP,
        capturedAtMs: Long = 10_100L,
        sequence: Long = 1L,
        track: HfrFeatureTrack = track()
    ) = V43FeatureTrackPacket(id, view, capturedAtMs, sequence, track)

    @Test fun remotePrimaryViewIsRejected() {
        assertFalse(V44TrackValidator.inspect(track(), V15CameraView.PRIMARY).valid)
    }

    @Test fun impossibleFpsIsRejected() {
        assertFalse(V44TrackValidator.inspect(track(fps = 30), V15CameraView.TOP).valid)
        assertFalse(V44TrackValidator.inspect(track(fps = 960), V15CameraView.TOP).valid)
    }

    @Test fun duplicateFrameIdsAreRejected() {
        val base = track()
        val broken = base.copy(frames = base.frames + base.frames.last())
        assertFalse(V44TrackValidator.inspect(broken, V15CameraView.TOP).valid)
    }

    @Test fun partialBallCoordinatesAreRejected() {
        val base = track()
        val frames = base.frames.toMutableList()
        frames[3] = frames[3].copy(ballYcm = null)
        assertFalse(V44TrackValidator.inspect(base.copy(frames = frames), V15CameraView.TOP).valid)
    }

    @Test fun frameTimeMismatchIsRejected() {
        val base = track()
        val frames = base.frames.toMutableList()
        frames[5] = frames[5].copy(timeFromImpactMs = 123.0)
        assertFalse(V44TrackValidator.inspect(base.copy(frames = frames), V15CameraView.TOP).valid)
    }

    @Test fun hugeCoordinatesAreRejected() {
        val base = track()
        val frames = base.frames.toMutableList()
        frames[4] = frames[4].copy(ballXcm = 900.0)
        assertFalse(V44TrackValidator.inspect(base.copy(frames = frames), V15CameraView.TOP).valid)
    }

    @Test fun validOutOfOrderFramesAreCanonicalized() {
        val base = track()
        val reversed = base.copy(frames = base.frames.reversed())
        val normalized = requireNotNull(V44TrackValidator.normalize(reversed, V15CameraView.TOP))
        assertEquals(normalized.frames.map { it.frame }.sorted(), normalized.frames.map { it.frame })
    }

    @Test fun matcherAlignsDifferentFrameRatesByImpactRelativeTime() {
        val local = track(fps = 240, frameOffsets = -12..12)
        val remote = track(fps = 120, frameOffsets = -6..6)
        val pairs = V44StereoMatcher.match(local, remote)
        assertTrue(pairs.size >= 11)
        assertTrue(pairs.map { it.deltaMs }.maxOrNull()!! <= 6.0)
    }

    @Test fun cleanComplementaryTrackBecomesStereoReadyInput() {
        val local = HfrFeatureTrackSnapshot(track(), 10_000L)
        val result = V44StereoReadinessEngine.best(local, listOf(packet()), nowMs = 10_500L)
        assertTrue(result.ready)
        assertTrue(result.ballPairs >= V44StereoReadinessEngine.MIN_BALL_PAIRS)
        assertTrue(result.putterPairs >= V44StereoReadinessEngine.MIN_PUTTER_PAIRS)
        assertTrue(result.score >= 60)
    }

    @Test fun planarCalibrationDisagreementBlocksReadiness() {
        val local = HfrFeatureTrackSnapshot(track(), 10_000L)
        val remote = packet(track = track(shiftX = 12.0))
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 10_500L)
        assertFalse(result.ready)
        assertTrue(result.reason.contains("평면"))
    }

    @Test fun differentShotTimeWindowBlocksReadiness() {
        val local = HfrFeatureTrackSnapshot(track(), 10_000L)
        val remote = packet(capturedAtMs = 12_500L)
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 12_600L)
        assertFalse(result.ready)
        assertTrue(result.reason.contains("같은 샷"))
    }

    @Test fun missingPutterCorrespondenceBlocksFullStereoPrep() {
        val local = HfrFeatureTrackSnapshot(track(), 10_000L)
        val remote = packet(track = track(withPutter = false))
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 10_500L)
        assertFalse(result.ready)
        assertTrue(result.reason.contains("PUTTER"))
    }

    @Test fun bestCandidatePrefersReadyAlignedCamera() {
        val local = HfrFeatureTrackSnapshot(track(), 10_000L)
        val bad = packet(id = "bad", view = V15CameraView.FACE_ON, track = track(shiftX = 10.0))
        val good = packet(id = "good", view = V15CameraView.TOP, sequence = 2L)
        val result = V44StereoReadinessEngine.best(local, listOf(bad, good), nowMs = 10_500L)
        assertTrue(result.ready)
        assertEquals("good", result.cameraId)
    }

    @Test fun remoteRuntimeRejectsOlderPacketAndBoundsCameraCount() {
        V43RemoteFeatureTrackRuntime.clear()
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(id = "a", capturedAtMs = 1000L, sequence = 2L)))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(packet(id = "a", capturedAtMs = 1100L, sequence = 1L)))
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(id = "b", capturedAtMs = 2000L, sequence = 1L)))
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(id = "c", capturedAtMs = 3000L, sequence = 1L)))
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(id = "d", capturedAtMs = 4000L, sequence = 1L)))
        assertEquals(3, V43RemoteFeatureTrackRuntime.size())
        val fresh = V43RemoteFeatureTrackRuntime.fresh(nowMs = 4500L, maxAgeMs = 5000L)
        assertFalse(fresh.any { it.cameraId == "a" })
        V43RemoteFeatureTrackRuntime.clear()
    }

    @Test fun staleRemoteTracksAreEvictedNotJustHidden() {
        V43RemoteFeatureTrackRuntime.clear()
        V43RemoteFeatureTrackRuntime.publish(packet(id = "stale", capturedAtMs = 1000L))
        assertTrue(V43RemoteFeatureTrackRuntime.fresh(nowMs = 5000L, maxAgeMs = 1000L).isEmpty())
        assertEquals(0, V43RemoteFeatureTrackRuntime.size())
    }
}
