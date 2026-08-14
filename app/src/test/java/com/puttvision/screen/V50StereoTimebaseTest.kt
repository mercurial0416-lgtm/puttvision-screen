package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class V50StereoTimebaseTest {
    @After fun cleanup() {
        V41HfrFeatureTrackRuntime.clear()
        V50HfrCaptureClockRuntime.clear()
    }

    private fun track(fps: Int = 240, impact: Int = 120): HfrFeatureTrack {
        val frameMs = 1000.0 / fps
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = impact,
            frames = (-10..10).map { offset ->
                val t = offset * frameMs
                val x = t * .02
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

    @Test fun captureStartAndImpactFrameProduceEventClockSeparateFromPublishClock() {
        V50HfrCaptureClockRuntime.onRecordingStarted(File("shot.mp4"), 240, startedAtMs = 10_000L)
        V41HfrFeatureTrackRuntime.publish(track(), nowMs = 14_000L)

        val snap = requireNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 14_100L))
        assertEquals(10_500L, snap.publishedAtMs)
        assertEquals(14_000L, snap.storedAtMs)
        assertEquals("CAMERAX_START+FRAME", snap.timeSource)
        assertTrue(snap.timeUncertaintyMs in 12L..50L)
    }

    @Test fun freshnessUsesAnalysisStorageTimeNotOlderPhysicalImpactTime() {
        V50HfrCaptureClockRuntime.onRecordingStarted(File("shot.mp4"), 240, startedAtMs = 10_000L)
        V41HfrFeatureTrackRuntime.publish(track(), nowMs = 14_000L)
        assertNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 14_900L, maxAgeMs = 1_000L))
        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 15_100L, maxAgeMs = 1_000L))
    }

    @Test fun decoderAcceptsDelayedHfrTrackWhenPhysicalImpactIsStillBounded() {
        val packet = V43FeatureTrackPacket("cam-top", V15CameraView.TOP, 10_500L, 7L, track())
        val wire = V43FeatureTrackWire.encode("ABCDEFGH", packet)
        val decoded = V43FeatureTrackWire.decode(wire, "ABCDEFGH", nowMs = 14_000L)
        assertNotNull(decoded)
        assertEquals(10_500L, requireNotNull(decoded).capturedAtMs)
        assertEquals(14_000L, decoded.receivedAtMs)
        val compatibility = V50FeatureTrackWire.decode(wire, "ABCDEFGH", nowMs = 14_000L)
        assertNotNull(compatibility)
    }

    @Test fun samePhysicalShotPairsEvenWhenAnalysisPublicationIsSeveralSecondsLater() {
        val local = HfrFeatureTrackSnapshot(
            track = track(),
            publishedAtMs = 10_500L,
            storedAtMs = 14_000L,
            timeSource = "CAMERAX_START+FRAME",
            timeUncertaintyMs = 12L
        )
        val remote = V43FeatureTrackPacket("cam-top", V15CameraView.TOP, 10_520L, 1L, track(), receivedAtMs = 14_050L)
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 14_100L, maxAgeMs = 10_000L)
        assertTrue(result.ready)
        assertEquals(20L, result.shotSkewMs)
    }

    @Test fun similarPublishTimingCannotHideDifferentPhysicalShots() {
        val local = HfrFeatureTrackSnapshot(track(), 10_500L, 14_000L)
        val remote = V43FeatureTrackPacket("cam-top", V15CameraView.TOP, 13_900L, 1L, track(), receivedAtMs = 14_050L)
        val result = V44StereoReadinessEngine.best(local, listOf(remote), nowMs = 14_100L, maxAgeMs = 10_000L)
        assertFalse(result.ready)
        assertTrue(result.reason.contains("같은 샷"))
        assertEquals(3_400L, result.shotSkewMs)
    }

    @Test fun missingCaptureEpochFallsBackWithoutClaimingHardwareTiming() {
        V41HfrFeatureTrackRuntime.publish(track(), nowMs = 20_000L)
        val snap = requireNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(20_100L))
        assertEquals(20_000L, snap.publishedAtMs)
        assertEquals("PUBLISH_FALLBACK", snap.timeSource)
        assertTrue(snap.timeUncertaintyMs >= 1_000L)
    }
}
