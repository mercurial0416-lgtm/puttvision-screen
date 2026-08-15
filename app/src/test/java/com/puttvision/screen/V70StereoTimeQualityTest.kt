package com.puttvision.screen

import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V70StereoTimeQualityTest {
    @After fun cleanup() {
        V50HfrCaptureClockRuntime.clear()
        V41HfrFeatureTrackRuntime.clear()
        V70RemoteTimingRuntime.clear()
    }

    @Test fun bounded_capture_time_passes_pair_gate() {
        val track = pixelTrack()
        V50HfrCaptureClockRuntime.onRecordingStarted(File("shot.mp4"), 240, 99_950L)
        V41HfrFeatureTrackRuntime.publish(track, nowMs = 100_100L)
        val local = V41HfrFeatureTrackRuntime.freshSnapshot(100_100L, 5_000L)
        assertNotNull(local)

        val remote = V70RemoteTimingEvidence(
            cameraId = "remote",
            sequence = 2,
            eventAtMs = requireNotNull(local).publishedAtMs + 18L,
            receivedAtMs = 100_100L,
            timeSource = "CAMERAX_START+FRAME",
            uncertaintyMs = 12L,
            pixelTrack = true
        )
        val result = V70StereoTimeQualityGate.evaluate(local, remote, nowMs = 100_100L)
        assertTrue(result.reason, result.accepted)
    }

    @Test fun publication_fallback_is_never_measurement_time() {
        val track = pixelTrack()
        V41HfrFeatureTrackRuntime.publish(track, nowMs = 100_100L)
        val local = requireNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(100_100L, 5_000L))
        val remote = V70RemoteTimingEvidence(
            "remote", 2, 100_100L, 100_100L, "CAMERAX_START+FRAME", 12L, true
        )
        val result = V70StereoTimeQualityGate.evaluate(local, remote, nowMs = 100_100L)
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("fallback"))
    }

    @Test fun excessive_uncertainty_and_wrong_shot_fail_closed() {
        val local = HfrFeatureTrackSnapshot(
            track = pixelTrack(),
            publishedAtMs = 100_000L,
            storedAtMs = 100_100L,
            timeSource = "CAMERAX_START+FRAME",
            timeUncertaintyMs = 12L
        )
        val uncertain = V70RemoteTimingEvidence("remote", 2, 100_020L, 100_100L, "CAMERAX_START+FRAME", 900L, true)
        assertFalse(V70StereoTimeQualityGate.evaluate(local, uncertain, 100_100L).accepted)

        val wrongShot = uncertain.copy(eventAtMs = 100_900L, uncertaintyMs = 12L)
        val result = V70StereoTimeQualityGate.evaluate(local, wrongShot, 101_000L)
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("skew") || result.reason.contains("implausible"))
    }

    @Test fun pixel_transport_carries_current_hfr_timing_evidence() {
        val track = pixelTrack()
        V50HfrCaptureClockRuntime.onRecordingStarted(File("shot.mp4"), 240, 99_950L)
        V41HfrFeatureTrackRuntime.publish(track, nowMs = 100_100L)
        val event = V41HfrFeatureTrackRuntime.latestPublishedAtMs
        val lines = V64PixelTrackTransport.encodeLines(
            code = "1234",
            cameraId = "remote",
            view = V15CameraView.FACE_ON,
            track = track,
            capturedAtMs = event,
            legacySequence = 10L,
            pixelSequence = 11L
        )
        val pixel = lines.single { it.pixelTrack }
        val decoded = requireNotNull(V64PixelTrackTransport.decodeLine(pixel.raw, "1234", nowMs = 100_100L))
        val evidence = requireNotNull(decoded.timingEvidence)
        assertTrue(evidence.timeSource == "CAMERAX_START+FRAME")
        assertTrue(evidence.uncertaintyMs in 1L..80L)
        assertNotNull(V70RemoteTimingRuntime.latest("remote", 100_100L))
    }

    @Test fun arbitrary_track_does_not_borrow_latest_timing_evidence() {
        val latest = pixelTrack()
        V50HfrCaptureClockRuntime.onRecordingStarted(File("shot.mp4"), 240, 99_950L)
        V41HfrFeatureTrackRuntime.publish(latest, nowMs = 100_100L)
        val other = latest.copy(impactFrame = 11)
        val lines = V64PixelTrackTransport.encodeLines(
            "1234", "remote", V15CameraView.FACE_ON, other, 100_000L, 20L, 21L
        )
        val decoded = requireNotNull(V64PixelTrackTransport.decodeLine(lines.single { it.pixelTrack }.raw, "1234", 100_100L))
        assertTrue(decoded.timingEvidence == null)
    }

    private fun pixelTrack(): HfrFeatureTrack {
        val frames = (0 until 7).map { i ->
            HfrFeatureFrame(
                frame = 10 + i,
                timeFromImpactMs = i * (1000.0 / 240.0),
                ballXcm = i * 0.6,
                ballYcm = 8.0 + i * 0.9,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = 900.0 + i * 3.0,
                ballYpx = 540.0 - i * 2.0
            )
        }
        return HfrFeatureTrack(
            fps = 240,
            impactFrame = 10,
            frames = frames,
            imageWidthPx = 1920,
            imageHeightPx = 1080
        )
    }
}
