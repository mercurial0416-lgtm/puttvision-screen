package com.puttvision.screen

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V106HfrSnapshotProvenanceTest {
    @After
    fun tearDown() {
        V41HfrFeatureTrackRuntime.clear()
    }

    @Test
    fun snapshotFailsClosedIfPublishedGeometryNoLongerMatchesBoundFingerprint() {
        val track = validTrack()
        val decision = V98HfrPublicationGate.publish(track, nowMs = 10_000L)
        assertTrue(decision.accepted)
        assertNotNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_000L))

        @Suppress("UNCHECKED_CAST")
        val exposedFrames = V41HfrFeatureTrackRuntime.latest!!.frames as MutableList<HfrFeatureFrame>
        exposedFrames[0] = exposedFrames[0].copy(ballXcm = exposedFrames[0].ballXcm!! + 0.01)

        assertNull(V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_000L))
    }

    @Test
    fun unboundLegacyPublicationStillUsesExistingFreshnessBehavior() {
        val track = validTrack()
        assertTrue(V41HfrFeatureTrackRuntime.publish(track, nowMs = 20_000L))

        val snapshot = V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 20_000L)

        assertNotNull(snapshot)
        assertNull(snapshot?.provenanceFingerprint)
    }

    private fun validTrack(): HfrFeatureTrack = HfrFeatureTrack(
        fps = 240,
        impactFrame = 50,
        frames = (48..52).map { frame ->
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = (frame - 50) * 1000.0 / 240.0,
                ballXcm = (frame - 48) * 0.1,
                ballYcm = (frame - 48) * 0.2,
                heelXcm = (frame - 48) * 0.1 - 1.0,
                heelYcm = (frame - 48) * 0.2 - 0.5,
                toeXcm = (frame - 48) * 0.1 + 1.0,
                toeYcm = (frame - 48) * 0.2 + 0.5,
                markerAngleDeg = 0.2
            )
        }
    )
}
