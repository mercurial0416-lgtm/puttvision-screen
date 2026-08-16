package com.puttvision.screen

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V98HfrPublicationIntegrityTest {
    @After
    fun tearDown() {
        V41HfrFeatureTrackRuntime.clear()
    }

    @Test
    fun validTrackIsAcceptedAndPublished() {
        val track = validTrack()
        val decision = V98HfrPublicationGate.publish(track, nowMs = 10_000L)

        assertTrue(decision.accepted)
        assertEquals(V98HfrPublicationStatus.ACCEPTED, decision.status)
        assertEquals(V101HfrPublicationProvenance.fingerprint(track), decision.provenanceFingerprint)
        assertEquals(64, decision.provenanceFingerprint?.length)
        assertTrue(V41HfrFeatureTrackRuntime.latest != null)
    }

    @Test
    fun fingerprintChangesWhenAnyPublishedGeometryChanges() {
        val a = validTrack()
        val b = a.copy(
            frames = a.frames.mapIndexed { index, frame ->
                if (index == 0) frame.copy(ballXcm = frame.ballXcm!! + 0.001) else frame
            }
        )

        assertNotEquals(
            V101HfrPublicationProvenance.fingerprint(a),
            V101HfrPublicationProvenance.fingerprint(b)
        )
    }

    @Test
    fun malformedTrackIsRejectedInsteadOfMasqueradingAsSuccess() {
        val track = validTrack().let { good ->
            good.copy(frames = good.frames.filterNot { it.frame == good.impactFrame })
        }

        val decision = V98HfrPublicationGate.publish(track, nowMs = 10_000L)

        assertFalse(decision.accepted)
        assertEquals(V98HfrPublicationStatus.REJECTED_INTEGRITY, decision.status)
        assertNull(decision.provenanceFingerprint)
        assertEquals(null, V41HfrFeatureTrackRuntime.latest)
        assertEquals(10_000L, V41HfrFeatureTrackRuntime.latestRejectedAtMs)
    }

    @Test
    fun mixedSourceShapeTrackFailsClosed() {
        val good = validTrack()
        val withPixelsButNoShape = good.copy(
            frames = good.frames.mapIndexed { index, frame ->
                if (index == 0) frame.copy(ballXpx = 120.0, ballYpx = 240.0) else frame
            }
        )

        val decision = V98HfrPublicationGate.publish(withPixelsButNoShape, nowMs = 11_000L)

        assertFalse(decision.accepted)
        assertEquals(V98HfrPublicationStatus.REJECTED_INTEGRITY, decision.status)
        assertNull(decision.provenanceFingerprint)
    }

    private fun validTrack(): HfrFeatureTrack = HfrFeatureTrack(
        fps = 240,
        impactFrame = 50,
        frames = (44..56).map { frame ->
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = (frame - 50) * 1000.0 / 240.0,
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
