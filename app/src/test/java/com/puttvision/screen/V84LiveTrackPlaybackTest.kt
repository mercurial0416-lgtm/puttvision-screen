package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V84LiveTrackPlaybackTest {
    @Test fun playbackSuitePasses() {
        val result = V84HardwarelessPlaybackSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(8, result.checksTotal)
        assertEquals(8, result.checksPassed)
    }

    @Test fun emptyOverlayCannotStartPlayback() {
        val empty = V81LiveTrackOverlay(emptyList(), emptyList(), 0, 240, 1920, 1080, false, "blocked")
        assertEquals(null, V84LiveTrackPlayback.initial(empty))
    }

    @Test fun invalidImpactTimePausesFailClosed() {
        val overlay = V81LiveTrackOverlay(
            ball = listOf(V81LiveTrackPoint(0, -5.0, .4, .5), V81LiveTrackPoint(1, 5.0, .6, .5), V81LiveTrackPoint(2, 10.0, .7, .5)),
            putter = listOf(V81LivePutterPose(0, -5.0, .4, .6, 0.0), V81LivePutterPose(1, 5.0, .5, .6, 0.0)),
            impactFrame = 1, fps = 240, sourceWidthPx = 1920, sourceHeightPx = 1080, ready = true, reason = "fixture"
        )
        val start = V84LiveTrackPlayback.initial(overlay)!!
        val playing = V84LiveTrackPlayback.setPlaying(start, true)
        val next = V84LiveTrackPlayback.advance(playing, 10.0, Double.NaN)
        assertFalse(next.playing)
    }
}
