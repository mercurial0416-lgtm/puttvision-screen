package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V81LabVerdictAndLiveTrackTest {
    @Test
    fun pixelTrackProjectsToNormalizedReplayOverlay() {
        val frames = (0 until 5).map { i ->
            HfrFeatureFrame(
                frame = 10 + i,
                timeFromImpactMs = i * (1000.0 / 240.0),
                ballXcm = 0.0,
                ballYcm = 0.0,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = 960.0 + i * 10.0,
                ballYpx = 540.0 - i * 5.0,
                heelXpx = 850.0 + i * 2.0,
                heelYpx = 650.0,
                toeXpx = 1050.0 + i * 2.0,
                toeYpx = 650.0
            )
        }
        val overlay = V81LiveTrackProjector.from(HfrFeatureTrack(240, 10, frames, 1920, 1080))
        assertTrue(overlay.reason, overlay.ready)
        assertEquals(5, overlay.ball.size)
        assertEquals(5, overlay.putter.size)
        assertTrue(overlay.ball.all { it.x01 in 0.0..1.0 && it.y01 in 0.0..1.0 })
        assertTrue(kotlin.math.abs(overlay.putter.first().faceAngleDeg) < 0.001)
    }

    @Test
    fun missingFrameShapeFailsClosed() {
        val track = HfrFeatureTrack(
            fps = 240,
            impactFrame = 0,
            frames = emptyList(),
            imageWidthPx = null,
            imageHeightPx = null
        )
        val overlay = V81LiveTrackProjector.from(track)
        assertFalse(overlay.ready)
        assertTrue(overlay.reason.contains("width"))
    }

    @Test
    fun labVerdictFailsClosedBeforeSelftestRuns() {
        V72HardwarelessSelfTestRuntime.clear()
        V75HardwarelessSelfTestHistoryRuntime.reset()
        val verdict = V81LabVerdictEngine.snapshot()
        assertFalse(verdict.passed)
        assertEquals("SELFTEST NOT RUN", verdict.failedStage)
        assertTrue(verdict.diagnosticsText().contains("real-device calibration"))
    }
}
