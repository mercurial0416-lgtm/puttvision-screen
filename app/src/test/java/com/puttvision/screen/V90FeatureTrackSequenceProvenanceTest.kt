package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class V90FeatureTrackSequenceProvenanceTest {
    @Before
    fun setUp() {
        V43RemoteFeatureTrackRuntime.clear()
    }

    @After
    fun tearDown() {
        V43RemoteFeatureTrackRuntime.clear()
    }

    @Test
    fun duplicateSequenceCannotBeReintroducedWithNewerTimestamp() {
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(sequence = 7L, capturedAtMs = 10_000L)))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(packet(sequence = 7L, capturedAtMs = 10_500L)))
        assertEquals(1, V43RemoteFeatureTrackRuntime.retainedTrackCount())
    }

    @Test
    fun regressedAndNegativeSequencesFailClosedWhileNextSequenceAdvances() {
        assertFalse(V43RemoteFeatureTrackRuntime.publish(packet(sequence = -1L, capturedAtMs = 9_000L)))
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(sequence = 10L, capturedAtMs = 10_000L)))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(packet(sequence = 9L, capturedAtMs = 10_100L)))
        assertTrue(V43RemoteFeatureTrackRuntime.publish(packet(sequence = 11L, capturedAtMs = 10_200L)))
        assertEquals(listOf(11L, 10L), V43RemoteFeatureTrackRuntime.fresh(nowMs = 10_300L).map { it.sequence })
    }

    private fun packet(sequence: Long, capturedAtMs: Long) = V43FeatureTrackPacket(
        cameraId = "face-cam",
        view = V15CameraView.FACE_ON,
        capturedAtMs = capturedAtMs,
        sequence = sequence,
        track = validTrack(),
        receivedAtMs = capturedAtMs
    )

    private fun validTrack() = HfrFeatureTrack(
        fps = 240,
        impactFrame = 50,
        frames = (0 until 12).map { i ->
            HfrFeatureFrame(
                frame = 44 + i,
                timeFromImpactMs = (i - 6) * 1000.0 / 240.0,
                ballXcm = i * .1,
                ballYcm = i * .2,
                heelXcm = i * .1 - 1.0,
                heelYcm = i * .2 - .5,
                toeXcm = i * .1 + 1.0,
                toeYcm = i * .2 + .5,
                markerAngleDeg = .2
            )
        }
    )
}
