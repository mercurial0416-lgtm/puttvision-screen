package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V52StereoHistoryFreshnessTest {
    private fun track(shiftX: Double = 0.0): HfrFeatureTrack {
        val fps = 240
        val impact = 100
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = impact,
            frames = (-10..10).map { offset ->
                val t = offset * 1000.0 / fps
                val x = shiftX + t * .02
                val y = t * .035
                HfrFeatureFrame(
                    frame = impact + offset,
                    timeFromImpactMs = t,
                    ballXcm = x,
                    ballYcm = y,
                    heelXcm = x - 1.2,
                    heelYcm = y - .4,
                    toeXcm = x + 1.2,
                    toeYcm = y + .4,
                    markerAngleDeg = .2
                )
            }
        )
    }

    private fun packet(
        eventAt: Long,
        receivedAt: Long,
        sequence: Long,
        shiftX: Double = 0.0
    ) = V43FeatureTrackPacket(
        cameraId = "cam-top",
        view = V15CameraView.TOP,
        capturedAtMs = eventAt,
        sequence = sequence,
        track = track(shiftX),
        receivedAtMs = receivedAt
    )

    @Test fun featureWireAllowsAnalysisDelayButStillBoundsAncientEvents() {
        val source = packet(eventAt = 10_000L, receivedAt = 10_000L, sequence = 7L)
        val raw = V43FeatureTrackWire.encode("PAIR1234", source)

        val delayed = V43FeatureTrackWire.decode(raw, "PAIR1234", nowMs = 18_000L)
        assertNotNull(delayed)
        assertEquals(10_000L, delayed!!.capturedAtMs)
        assertEquals(18_000L, delayed.receivedAtMs)

        assertNull(
            V43FeatureTrackWire.decode(
                raw,
                "PAIR1234",
                nowMs = 10_000L + V43FeatureTrackWire.MAX_EVENT_AGE_MS + 1L
            )
        )
    }

    @Test fun measurementFreshnessRemainsStrictWhileHfrTrackCanArriveLate() {
        val metrics = ShotMetrics(
            ballSpeedMps = 1.2,
            launchAngleDeg = .1,
            headSpeedMps = .9,
            faceAngleDeg = .1,
            pathAngleDeg = .1,
            faceToPathDeg = .0,
            smash = 1.3,
            impactOffsetMm = .0,
            measuredAtNs = 1L,
            confidence = .9
        )
        val measurement = V15CameraMeasurement("cam", V15CameraView.TOP, metrics, .9, 10_000L)
        val raw = V43CompanionWire.encodeMeasurement("PAIR1234", measurement, 0L, 1L)
        assertNull(V43CompanionWire.decodeMeasurement(raw, "PAIR1234", nowMs = 18_000L))
    }

    @Test fun remoteRuntimeKeepsSeveralRecentShotsPerCamera() {
        V43RemoteFeatureTrackRuntime.clear()
        try {
            assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(10_000L, 18_000L, 1L)))
            assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(12_500L, 18_100L, 2L)))
            assertEquals(1, V43RemoteFeatureTrackRuntime.size())
            assertEquals(2, V43RemoteFeatureTrackRuntime.retainedTrackCount())
            assertEquals(2, V43RemoteFeatureTrackRuntime.fresh(nowMs = 18_200L, maxAgeMs = 2_000L).size)
        } finally {
            V43RemoteFeatureTrackRuntime.clear()
        }
    }

    @Test fun stereoCanSelectOlderSameShotAfterNewerShotArrives() {
        val local = HfrFeatureTrackSnapshot(
            track = track(),
            publishedAtMs = 10_000L,
            storedAtMs = 18_000L,
            timeSource = "VIDEO_START_PLUS_FRAME",
            timeUncertaintyMs = 80L
        )
        val sameShot = packet(eventAt = 10_100L, receivedAt = 18_050L, sequence = 1L)
        val newerDifferentShot = packet(eventAt = 12_500L, receivedAt = 18_100L, sequence = 2L)

        val result = V44StereoReadinessEngine.best(
            local = local,
            remotePackets = listOf(newerDifferentShot, sameShot),
            nowMs = 18_200L,
            maxAgeMs = 2_000L
        )
        assertTrue(result.ready)
        assertEquals("cam-top", result.cameraId)
        assertEquals(100L, result.shotSkewMs)
    }

    @Test fun localFreshnessUsesAnalysisStorageTimeNotImpactTime() {
        val local = HfrFeatureTrackSnapshot(
            track = track(),
            publishedAtMs = 1_000L,
            storedAtMs = 10_000L,
            timeSource = "VIDEO_START_PLUS_FRAME",
            timeUncertaintyMs = 100L
        )
        val remote = packet(eventAt = 1_100L, receivedAt = 10_100L, sequence = 1L)
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 10_200L, maxAgeMs = 1_000L)
        assertTrue(result.ready)
        assertEquals(100L, result.shotSkewMs)
    }
}
