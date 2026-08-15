package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V65CameraIntrinsicsMetadataTest {
    private fun metadata(
        skew: Double = 0.0,
        active: V65RectI = V65RectI(0, 0, 4000, 3000),
        pre: V65RectI = V65RectI(0, 0, 4000, 3000),
        distortion: List<Double>? = listOf(0.0, 0.0, 0.0, 0.0, 0.0)
    ) = V65SensorIntrinsicMetadata(
        cameraId = "0",
        fxPx = 3200.0,
        fyPx = 3180.0,
        cxPx = 2000.0,
        cyPx = 1500.0,
        skewPx = skew,
        preCorrectionActiveArray = pre,
        activeArray = active,
        distortion = distortion,
        sensorOrientationDeg = 90,
        lensFacing = 1
    )

    @Test fun explicitCenterCropAndScaleProducesFrameIntrinsics() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(0, 375, 4000, 2625),
                outputWidthPx = 1920,
                outputHeightPx = 1080,
                rotationDeg = 0,
                coordinateSpace = V65FrameCoordinateSpace.PRE_CORRECTION_SENSOR
            )
        )

        assertTrue(result.usableForV61)
        val k = requireNotNull(result.intrinsics)
        assertEquals(1536.0, k.fx, 1e-9)
        assertEquals(1526.4, k.fy, 1e-9)
        assertEquals(960.0, k.cx, 1e-9)
        assertEquals(540.0, k.cy, 1e-9)
    }

    @Test fun metadataWithoutExplicitFrameMappingNeverBecomesV61Intrinsics() {
        val result = V65CameraIntrinsicBinder.bind(metadata(), null)
        assertFalse(result.usableForV61)
        assertNull(result.intrinsics)
        assertTrue(result.reason.contains("mapping"))
    }

    @Test fun processedCoordinatesRejectNonTrivialDistortion() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(distortion = listOf(0.02, -0.01, 0.0, 0.0, 0.0)),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(0, 375, 4000, 2625),
                outputWidthPx = 1920,
                outputHeightPx = 1080,
                rotationDeg = 0,
                coordinateSpace = V65FrameCoordinateSpace.PROCESSED_ACTIVE_ARRAY
            )
        )
        assertFalse(result.usableForV61)
        assertTrue(result.reason.contains("distortion"))
    }

    @Test fun processedCoordinatesRejectActiveArrayGeometryMismatch() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(active = V65RectI(20, 10, 3980, 2990)),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(20, 380, 3980, 2608),
                outputWidthPx = 1920,
                outputHeightPx = 1080,
                rotationDeg = 0,
                coordinateSpace = V65FrameCoordinateSpace.PROCESSED_ACTIVE_ARRAY
            )
        )
        assertFalse(result.usableForV61)
        assertTrue(result.reason.contains("pre-correction"))
    }

    @Test fun unsupportedSkewFailsClosedInsteadOfBeingSilentlyDropped() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(skew = 2.0),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(0, 375, 4000, 2625),
                outputWidthPx = 1920,
                outputHeightPx = 1080,
                rotationDeg = 0,
                coordinateSpace = V65FrameCoordinateSpace.PRE_CORRECTION_SENSOR
            )
        )
        assertFalse(result.usableForV61)
        assertTrue(result.reason.contains("skew"))
    }

    @Test fun rotatedVideoFrameRequiresCoupledExtrinsicTransform() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(0, 375, 4000, 2625),
                outputWidthPx = 1080,
                outputHeightPx = 1920,
                rotationDeg = 90,
                coordinateSpace = V65FrameCoordinateSpace.PRE_CORRECTION_SENSOR
            )
        )
        assertFalse(result.usableForV61)
        assertTrue(result.reason.contains("extrinsic-axis"))
    }

    @Test fun cropOutsideSensorArrayFailsClosed() {
        val result = V65CameraIntrinsicBinder.bind(
            metadata(),
            V65SensorToFrameMapping(
                sourceCropPx = V65RectI(0, 0, 4200, 3000),
                outputWidthPx = 1920,
                outputHeightPx = 1080,
                rotationDeg = 0,
                coordinateSpace = V65FrameCoordinateSpace.PRE_CORRECTION_SENSOR
            )
        )
        assertFalse(result.usableForV61)
        assertTrue(result.reason.contains("outside"))
    }
}