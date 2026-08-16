package com.puttvision.screen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V96HfrRejectedTrackFreshnessTest {
    @After
    fun tearDown() {
        V41HfrFeatureTrackRuntime.clear()
    }

    @Test
    fun rejectedNewTrackKeepsDiagnosticGeometryButRevokesFreshSnapshot() {
        val good = track()
        assertTrue(V41HfrFeatureTrackRuntime.publish(good, nowMs = 10_000L))
        assertNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_050L))

        val malformed = good.copy(frames = good.frames.filterNot { it.frame == good.impactFrame })
        assertFalse(V41HfrFeatureTrackRuntime.publish(malformed, nowMs = 10_100L))

        // Keep the last known-good track inspectable for diagnostics, but never expose it as current
        // evidence after a newer producer attempt failed provenance validation.
        assertEquals(good.frames, V41HfrFeatureTrackRuntime.latest?.frames)
        assertEquals(10_000L, V41HfrFeatureTrackRuntime.latestStoredAtMs)
        assertEquals(10_100L, V41HfrFeatureTrackRuntime.latestRejectedAtMs)
        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_101L))
    }

    @Test
    fun nextValidTrackClearsRejectionWatermarkAndBecomesFreshAgain() {
        val good = track()
        assertTrue(V41HfrFeatureTrackRuntime.publish(good, nowMs = 20_000L))

        val forged = good.copy(frames = good.frames.map {
            if (it.frame == good.impactFrame + 1) it.copy(timeFromImpactMs = 99.0) else it
        })
        assertFalse(V41HfrFeatureTrackRuntime.publish(forged, nowMs = 20_100L))
        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 20_101L))

        assertTrue(V41HfrFeatureTrackRuntime.publish(good, nowMs = 20_200L))
        assertEquals(0L, V41HfrFeatureTrackRuntime.latestRejectedAtMs)
        assertNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 20_201L))
    }

    @Test
    fun negativeFreshnessWindowFailsClosed() {
        assertTrue(V41HfrFeatureTrackRuntime.publish(track(), nowMs = 30_000L))
        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 30_001L, maxAgeMs = -1L))
    }

    private fun track(): HfrFeatureTrack {
        val fps = 240
        val impact = 50
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = impact,
            frames = (44..56).map { frame ->
                HfrFeatureFrame(
                    frame = frame,
                    timeFromImpactMs = (frame - impact) * 1000.0 / fps.toDouble(),
                    ballXcm = (frame - 44) * 0.1,
                    ballYcm = (frame - 44) * 0.2,
                    heelXcm = (frame - 44) * 0.1 - 1.0,
                    heelYcm = (frame - 44) * 0.2 - 0.5,
                    toeXcm = (frame - 44) * 0.1 + 1.0,
                    toeYcm = (frame - 44) * 0.2 + 0.5,
                    markerAngleDeg = 0.2
                )
            }
        )
    }
}
