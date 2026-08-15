package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V67StereoFrameBindingTest {
    private fun signature(width: Int = 1920, height: Int = 1080, fps: Int = 240) = V59CaptureSignature(
        cameraId = "cam-a",
        widthPx = width,
        heightPx = height,
        fps = fps,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun track(width: Int? = 1920, height: Int? = 1080, fps: Int = 240) = HfrFeatureTrack(
        fps = fps,
        impactFrame = 1,
        frames = listOf(
            HfrFeatureFrame(0, -1000.0 / fps, 0.0, 0.0, null, null, null, null, null),
            HfrFeatureFrame(1, 0.0, 0.0, 0.0, null, null, null, null, null),
            HfrFeatureFrame(2, 1000.0 / fps, 0.0, 0.1, null, null, null, null, null),
            HfrFeatureFrame(3, 2000.0 / fps, 0.0, 0.2, null, null, null, null, null)
        ),
        imageWidthPx = width,
        imageHeightPx = height
    )

    @Test fun exactShapeAndFpsBind() {
        val result = V67StereoFrameBindingGate.evaluate(track(), signature())
        assertTrue(result.bound)
    }

    @Test fun widthMismatchFailsClosed() {
        val result = V67StereoFrameBindingGate.evaluate(track(width = 1280), signature())
        assertFalse(result.bound)
        assertTrue(result.reason.contains("shape"))
    }

    @Test fun heightMismatchFailsClosed() {
        val result = V67StereoFrameBindingGate.evaluate(track(height = 720), signature())
        assertFalse(result.bound)
        assertTrue(result.reason.contains("shape"))
    }

    @Test fun fpsMismatchFailsClosedByDefault() {
        val result = V67StereoFrameBindingGate.evaluate(track(fps = 120), signature())
        assertFalse(result.bound)
        assertTrue(result.reason.contains("fps"))
    }

    @Test fun missingSourceShapeFailsClosed() {
        val result = V67StereoFrameBindingGate.evaluate(track(width = null), signature())
        assertFalse(result.bound)
        assertTrue(result.reason.contains("width"))
    }

    @Test fun pairReportsWhichCameraFailed() {
        val result = V67StereoFrameBindingGate.evaluatePair(
            firstTrack = track(),
            firstSignature = signature(),
            secondTrack = track(height = 720),
            secondSignature = signature()
        )
        assertFalse(result.bound)
        assertTrue(result.reason.contains("second camera"))
    }
}
