package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V71StereoPacketProvenanceTest {
    @Test
    fun hardwarelessSuiteRejectsWrongPhoneViewAndShot() {
        val result = V71HardwarelessProvenanceSuite.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(8, result.checksTotal)
        assertEquals(8, result.checksPassed)
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
}
