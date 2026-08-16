package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V85ImpactReplayLiveTrackTest {
    @Test fun hardwarelessSuitePasses() {
        val result = V85HardwarelessImpactReplayLiveTrackSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(7, result.checksTotal)
        assertEquals(7, result.checksPassed)
    }

    @Test fun replayFrameUsesCaptureFpsForTimelineAlignment() {
        val track = fixtureTrack()
        val binding = V85ImpactReplayLiveTrack.bind(track)!!
        val before = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, 19, 20)!!
        val impact = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, 20, 20)!!
        assertFalse(before.impactReached)
        assertTrue(impact.impactReached)
    }

    @Test fun unreadyTrackDoesNotLeakIntoReplay() {
        val bad = fixtureTrack().copy(imageHeightPx = null)
        assertNull(V85ImpactReplayLiveTrack.bind(bad))
    }

    private fun fixtureTrack() = HfrFeatureTrack(
        fps = 240,
        impactFrame = 100,
        imageWidthPx = 1000,
        imageHeightPx = 500,
        frames = listOf(
            HfrFeatureFrame(99, -1000.0 / 240.0, null, null, null, null, null, null, null, 450.0, 260.0, 400.0, 320.0, 500.0, 320.0),
            HfrFeatureFrame(100, 0.0, null, null, null, null, null, null, null, 470.0, 250.0, 420.0, 310.0, 520.0, 310.0),
            HfrFeatureFrame(101, 1000.0 / 240.0, null, null, null, null, null, null, null, 500.0, 235.0, 445.0, 300.0, 545.0, 300.0)
        )
    )
}
