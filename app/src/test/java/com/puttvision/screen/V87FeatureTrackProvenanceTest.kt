package com.puttvision.screen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V87FeatureTrackProvenanceTest {
    private fun track() = HfrFeatureTrack(
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

    @Test
    fun featureTrackWireRejectsUnknownCameraViewInsteadOfAssumingPrimary() {
        val packet = V43FeatureTrackPacket("cam-face", V15CameraView.FACE_ON, 20_000L, 7L, track())
        val raw = V43FeatureTrackWire.encode("PAIR1234", packet)
        val valid = V43FeatureTrackWire.decode(raw, "PAIR1234", 20_500L)
        assertNotNull(valid)
        assertEquals(V15CameraView.FACE_ON, valid!!.view)
        val malformed = JSONObject(raw).put("view", "SIDEWAYS_OR_UNKNOWN").toString()
        assertNull(V43FeatureTrackWire.decode(malformed, "PAIR1234", 20_500L))
    }

    @Test
    fun featureTrackWireRejectsUnboundedOrAmbiguousCameraIdentity() {
        val packet = V43FeatureTrackPacket("cam-face", V15CameraView.FACE_ON, 30_000L, 8L, track())
        val raw = V43FeatureTrackWire.encode("PAIR1234", packet)
        assertNull(V43FeatureTrackWire.decode(JSONObject(raw).put("camera", "x".repeat(V43FeatureTrackWire.MAX_CAMERA_ID_LENGTH + 1)).toString(), "PAIR1234", 30_500L))
        assertNull(V43FeatureTrackWire.decode(JSONObject(raw).put("camera", "cam\nface").toString(), "PAIR1234", 30_500L))
        assertNull(V43FeatureTrackWire.decode(JSONObject(raw).put("camera", "카메라-1").toString(), "PAIR1234", 30_500L))
        assertEquals("cam-face_1", V43FeatureTrackWire.normalizeCameraId("  cam-face_1  "))
    }

    @Test
    fun remoteRuntimeRejectsCameraIdChangingPhysicalView() {
        V43RemoteFeatureTrackRuntime.clear()
        val base = V43FeatureTrackPacket("stable-camera-1", V15CameraView.FACE_ON, 40_000L, 1L, track(), 40_000L)
        assertTrue(V43RemoteFeatureTrackRuntime.publish(base))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(base.copy(view = V15CameraView.DOWN_THE_LINE, capturedAtMs = 40_010L, sequence = 2L, receivedAtMs = 40_010L)))
        val retained = V43RemoteFeatureTrackRuntime.fresh(nowMs = 40_020L)
        assertEquals(1, retained.size)
        assertEquals(V15CameraView.FACE_ON, retained.single().view)
        V43RemoteFeatureTrackRuntime.clear()
    }

    @Test
    fun remoteRuntimeRejectsPacketsWhoseCaptureClockIsIncoherentWithReceipt() {
        V43RemoteFeatureTrackRuntime.clear()
        val base = V43FeatureTrackPacket("cam-1", V15CameraView.FACE_ON, 50_000L, 1L, track(), 50_100L)
        assertTrue(V43RemoteFeatureTrackRuntime.publish(base))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(base.copy(cameraId = "cam-2", capturedAtMs = 70_000L, receivedAtMs = 50_100L)))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(base.copy(cameraId = "cam-3", capturedAtMs = 10_000L, receivedAtMs = 50_100L)))
        assertFalse(V43RemoteFeatureTrackRuntime.publish(base.copy(cameraId = "bad id", sequence = 2L)))
        V43RemoteFeatureTrackRuntime.clear()
    }
}
