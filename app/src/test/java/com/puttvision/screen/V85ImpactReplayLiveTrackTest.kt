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
        assertEquals(11, result.checksTotal)
        assertEquals(11, result.checksPassed)
    }

    @Test fun replayFrameUsesCaptureFpsForTimelineAlignment() {
        val track = fixtureTrack()
        val binding = V85ImpactReplayLiveTrack.bind(track)!!
        val before = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, 19, 20)!!
        val impact = V85ImpactReplayLiveTrack.modelAtReplayFrame(binding, 20, 20)!!
        assertFalse(before.impactReached)
        assertTrue(impact.impactReached)
    }

    @Test fun explicitPlayheadSupportsDownsampledReplayStride() {
        val track = fixtureTrack()
        val binding = V85ImpactReplayLiveTrack.bind(track)!!
        val oneSourceFrame = V85ImpactReplayLiveTrack.modelAtPlayheadMs(binding, 1000.0 / 240.0)!!
        val twoSourceFrames = V85ImpactReplayLiveTrack.modelAtPlayheadMs(binding, 2000.0 / 240.0)!!
        assertTrue(twoSourceFrames.ballTrail.size >= oneSourceFrame.ballTrail.size)
    }

    @Test fun unreadyTrackDoesNotLeakIntoReplay() {
        val bad = fixtureTrack().copy(imageHeightPx = null)
        assertNull(V85ImpactReplayLiveTrack.bind(bad))
    }

    @Test fun outOfFramePixelsAreRejectedInsteadOfClampedIntoReplay() {
        val track = fixtureTrack()
        val bad = track.copy(frames = track.frames.mapIndexed { index, frame ->
            if (index == 2) frame.copy(ballXpx = 1001.0) else frame
        })
        assertNull(V85ImpactReplayLiveTrack.bind(bad))
    }

    @Test fun inconsistentFrameTimingIsRejectedBeforeReplayProjection() {
        val track = fixtureTrack()
        val bad = track.copy(frames = track.frames.mapIndexed { index, frame ->
            if (index == 3) frame.copy(timeFromImpactMs = 99.0) else frame
        })
        assertNull(V85ImpactReplayLiveTrack.bind(bad))
    }

    private fun fixtureTrack() = HfrFeatureTrack(
        fps = 240,
        impactFrame = 100,
        imageWidthPx = 1000,
        imageHeightPx = 500,
        frames = listOf(
            HfrFeatureFrame(98, -2000.0 / 240.0, null, null, null, null, null, null, null, 430.0, 270.0, 380.0, 330.0, 480.0, 330.0),
            HfrFeatureFrame(99, -1000.0 / 240.0, null, null, null, null, null, null, null, 450.0, 260.0, 400.0, 320.0, 500.0, 320.0),
            HfrFeatureFrame(100, 0.0, null, null, null, null, null, null, null, 470.0, 250.0, 420.0, 310.0, 520.0, 310.0),
            HfrFeatureFrame(101, 1000.0 / 240.0, null, null, null, null, null, null, null, 500.0, 235.0, 445.0, 300.0, 545.0, 300.0),
            HfrFeatureFrame(102, 2000.0 / 240.0, null, null, null, null, null, null, null, 530.0, 220.0, 470.0, 290.0, 570.0, 290.0)
        )
    )
}
