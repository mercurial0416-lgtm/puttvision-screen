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
        assertEquals(
            decision.provenanceFingerprint,
            V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_000L)?.provenanceFingerprint
        )
    }

    @Test
    fun longTrackFingerprintsExactCompactRuntimePublication() {
        val longTrack = validTrack().copy(
            impactFrame = 50,
            frames = (20..80).map { frame ->
                HfrFeatureFrame(
                    frame = frame,
                    timeFromImpactMs = (frame - 50) * 1000.0 / 240.0,
                    ballXcm = frame * 0.01,
                    ballYcm = frame * 0.02,
                    heelXcm = frame * 0.01 - 1.0,
                    heelYcm = frame * 0.02 - 0.5,
                    toeXcm = frame * 0.01 + 1.0,
                    toeYcm = frame * 0.02 + 0.5,
                    markerAngleDeg = 0.2
                )
            }
        )

        val decision = V98HfrPublicationGate.publish(longTrack, nowMs = 10_000L)
        val published = V41HfrFeatureTrackRuntime.latest!!
        val snapshot = V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 10_000L)

        assertTrue(decision.accepted)
        assertEquals(32, published.frames.size)
        assertEquals(
            V101HfrPublicationProvenance.fingerprint(published),
            decision.provenanceFingerprint
        )
        assertEquals(decision.provenanceFingerprint, snapshot?.provenanceFingerprint)
        assertNotEquals(
            V101HfrPublicationProvenance.fingerprint(longTrack),
            decision.provenanceFingerprint
        )
    }

    @Test
    fun rawRepublishCannotInheritPreviousFingerprint() {
        val first = validTrack()
        val firstDecision = V98HfrPublicationGate.publish(first, nowMs = 10_000L)
        assertTrue(firstDecision.accepted)
        assertTrue(V41HfrFeatureTrackRuntime.latestProvenanceFingerprint != null)

        val second = first.copy(
            frames = first.frames.mapIndexed { index, frame ->
                if (index == 0) frame.copy(ballXcm = frame.ballXcm!! + 0.001) else frame
            }
        )
        assertTrue(V41HfrFeatureTrackRuntime.publish(second, nowMs = 11_000L))

        assertNull(V41HfrFeatureTrackRuntime.latestProvenanceFingerprint)
        assertNull(
            V41HfrFeatureTrackRuntime.freshSnapshot(nowMs = 11_000L)?.provenanceFingerprint
        )
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
