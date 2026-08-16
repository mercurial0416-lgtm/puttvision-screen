package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V83LiveTrackOverlayViewTest {
    @Test fun visualSuitePassesDeterministicReplayChecks() {
        val result = V83HardwarelessLiveTrackVisualSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(7, result.checksTotal)
        assertEquals(7, result.checksPassed)
    }

    @Test fun plannerNeverCallsImagePlaneOrientationCalibratedFace() {
        val overlay = V81LiveTrackOverlay(
            ball = listOf(
                V81LiveTrackPoint(0, -4.0, .4, .6),
                V81LiveTrackPoint(1, 0.0, .5, .5),
                V81LiveTrackPoint(2, 4.0, .6, .4)
            ),
            putter = listOf(
                V81LivePutterPose(0, -4.0, .4, .7, 3.0),
                V81LivePutterPose(1, 0.0, .5, .7, 4.0)
            ),
            impactFrame = 1,
            fps = 240,
            sourceWidthPx = 1920,
            sourceHeightPx = 1080,
            ready = true,
            reason = "fixture"
        )
        val model = V83LiveTrackRenderPlanner.plan(overlay, 0.0)
        assertTrue(model.ready)
        assertTrue(model.imageFaceLabel.startsWith("IMAGE FACE"))
        assertFalse(model.imageFaceLabel.contains("WORLD"))
    }

    @Test fun invalidTimingFailsClosed() {
        val overlay = V81LiveTrackOverlay(emptyList(), emptyList(), 0, 240, 1920, 1080, true, "fixture")
        assertFalse(V83LiveTrackRenderPlanner.plan(overlay, Double.POSITIVE_INFINITY).ready)
    }
}
