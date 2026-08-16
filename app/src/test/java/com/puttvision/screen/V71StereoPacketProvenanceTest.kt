package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V71StereoPacketProvenanceTest {
    @Test
    fun hardwarelessSuiteRejectsWrongPhoneViewShotAndPayload() {
        val result = V71HardwarelessProvenanceSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(10, result.checksTotal)
        assertEquals(10, result.checksPassed)
    }

    @Test
    fun invalidPolicyFailsClosed() {
        val result = V71StereoPacketProvenanceGate.evaluate(
            localPacket = null,
            remotePacket = null,
            currentFirst = signature("a"),
            currentSecond = signature("b"),
            expectedLocalView = V15CameraView.PRIMARY,
            expectedRemoteView = V15CameraView.FACE_ON,
            nowMs = 1000L,
            policy = V71StereoPacketPolicy(maxEventSkewMs = 900L)
        )
        assertFalse(result.bound)
        assertTrue(result.reason.contains("policy"))
    }

    @Test
    fun malformedPayloadFailsClosedBeforeStereoReconstruction() {
        val nowMs = 10_000L
        val local = packet("a", V15CameraView.PRIMARY, nowMs - 10L)
        val remote = packet("b", V15CameraView.FACE_ON, nowMs - 8L)
        val malformed = local.copy(track = local.track.copy(frames = emptyList()))

        val result = V71StereoPacketProvenanceGate.evaluate(
            localPacket = malformed,
            remotePacket = remote,
            currentFirst = signature("a"),
            currentSecond = signature("b"),
            expectedLocalView = V15CameraView.PRIMARY,
            expectedRemoteView = V15CameraView.FACE_ON,
            nowMs = nowMs
        )

        assertFalse(result.bound)
        assertTrue(result.reason.contains("payload integrity"))
    }

    @Test
    fun runtimePublishesAndClearsVisibleStatus() {
        V71HardwarelessProvenanceRuntime.clear()
        assertNull(V71HardwarelessProvenanceRuntime.snapshot())
        val result = V71HardwarelessProvenanceRuntime.run()
        assertTrue(result.passed)
        assertTrue(V71HardwarelessProvenanceRuntime.snapshot()?.shortLabel()?.startsWith("PACKET BIND PASS") == true)
        V71HardwarelessProvenanceRuntime.clear()
        assertNull(V71HardwarelessProvenanceRuntime.snapshot())
    }

    private fun signature(cameraId: String) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = 1920,
        heightPx = 1080,
        fps = 240,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun packet(cameraId: String, view: V15CameraView, capturedAtMs: Long) = V43FeatureTrackPacket(
        cameraId = cameraId,
        view = view,
        capturedAtMs = capturedAtMs,
        sequence = 1L,
        track = HfrFeatureTrack(
            fps = 240,
            impactFrame = 0,
            frames = listOf(
                HfrFeatureFrame(
                    frame = 0,
                    timeFromImpactMs = 0.0,
                    ballXcm = 0.0,
                    ballYcm = 0.0,
                    heelXcm = -1.0,
                    heelYcm = 0.0,
                    toeXcm = 1.0,
                    toeYcm = 0.0,
                    markerAngleDeg = 0.0,
                    ballXpx = 960.0,
                    ballYpx = 540.0,
                    heelXpx = 900.0,
                    heelYpx = 540.0,
                    toeXpx = 1020.0,
                    toeYpx = 540.0
                )
            ),
            imageWidthPx = 1920,
            imageHeightPx = 1080
        ),
        receivedAtMs = capturedAtMs + 1L
    )
}
