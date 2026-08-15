package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V53StereoTriangulationTest {
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )

    private fun calibration(
        x: Double,
        rms: Double = 0.35,
        fx: Double = 1_200.0,
        fy: Double = 1_200.0
    ) = V53CameraCalibration(
        intrinsics = V53CameraIntrinsics(fx, fy, 960.0, 540.0),
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, 0.0)),
        rmsReprojectionPx = rms,
        calibratedAtMs = 1_000L
    )

    @Test fun calibratedConvergingRaysRecoverKnownPoint() {
        val left = calibration(-0.15)
        val right = calibration(0.15)
        val target = V53Vec3(0.04, -0.03, 2.0)
        val leftPixel = requireNotNull(V53StereoProjection.project(left, target))
        val rightPixel = requireNotNull(V53StereoProjection.project(right, target))

        val result = V53StereoTriangulator.triangulate(left, leftPixel, right, rightPixel)

        assertTrue(result.usableForFusion)
        val point = requireNotNull(result.pointWorld)
        assertEquals(target.x, point.x, 1e-6)
        assertEquals(target.y, point.y, 1e-6)
        assertEquals(target.z, point.z, 1e-6)
        assertTrue(requireNotNull(result.rayGapM) < 1e-6)
        assertTrue(requireNotNull(result.parallaxDeg) > 1.0)
        assertTrue(requireNotNull(result.reprojectionErrorPx) < 1e-6)
        assertTrue(result.geometryScore > 50)
    }

    @Test fun invalidIntrinsicsFailClosedBeforeRayConstruction() {
        val bad = calibration(-0.15, fx = 0.0)
        val good = calibration(0.15)
        val result = V53StereoTriangulator.triangulate(
            bad,
            V53Pixel(960.0, 540.0),
            good,
            V53Pixel(950.0, 540.0)
        )
        assertFalse(result.usableForFusion)
        assertNull(result.pointWorld)
        assertTrue(result.reason.contains("calibration"))
    }

    @Test fun highCalibrationResidualCannotBePresentedAsUsable3d() {
        val left = calibration(-0.15, rms = 3.5)
        val right = calibration(0.15)
        val target = V53Vec3(0.0, 0.0, 2.0)
        val leftPixel = requireNotNull(V53StereoProjection.project(left, target))
        val rightPixel = requireNotNull(V53StereoProjection.project(right, target))

        val result = V53StereoTriangulator.triangulate(left, leftPixel, right, rightPixel)
        assertFalse(result.usableForFusion)
        assertNull(result.pointWorld)
        assertEquals(0, result.geometryScore)
        assertTrue(result.reason.contains("reprojection"))
    }

    @Test fun tinyBaselineAtLongRangeIsRejectedForWeakParallax() {
        val left = calibration(-0.0005)
        val right = calibration(0.0005)
        val target = V53Vec3(0.0, 0.0, 20.0)
        val leftPixel = requireNotNull(V53StereoProjection.project(left, target))
        val rightPixel = requireNotNull(V53StereoProjection.project(right, target))

        val result = V53StereoTriangulator.triangulate(left, leftPixel, right, rightPixel)
        assertFalse(result.usableForFusion)
        assertNotNull(result.pointWorld)
        assertTrue(requireNotNull(result.parallaxDeg) < 1.0)
        assertTrue(result.reason.contains("parallax"))
    }

    @Test fun mismatchedCorrespondenceIsRejectedByReprojectionGate() {
        val left = calibration(-0.15)
        val right = calibration(0.15)
        val target = V53Vec3(0.02, 0.01, 2.0)
        val leftPixel = requireNotNull(V53StereoProjection.project(left, target))
        val rightPixel = requireNotNull(V53StereoProjection.project(right, target))
        val mismatchedRight = rightPixel.copy(y = rightPixel.y + 8.0)

        val result = V53StereoTriangulator.triangulate(
            left,
            leftPixel,
            right,
            mismatchedRight,
            V53TriangulationPolicy(
                maxRayGapM = 1.0,
                maxTriangulationReprojectionPx = 1.0
            )
        )

        assertFalse(result.usableForFusion)
        assertNotNull(result.pointWorld)
        assertTrue(requireNotNull(result.reprojectionErrorPx) > 1.0)
        assertTrue(result.reason.contains("reprojection"))
    }

    @Test fun identicalCameraCentersRejectNearParallelGeometry() {
        val first = calibration(0.0)
        val second = calibration(0.0)
        val pixel = V53Pixel(960.0, 540.0)
        val result = V53StereoTriangulator.triangulate(first, pixel, second, pixel)
        assertFalse(result.usableForFusion)
        assertNull(result.pointWorld)
        assertTrue(result.reason.contains("parallel"))
    }

    @Test fun reflectionMatrixIsRejectedAsCameraRotation() {
        val reflected = V53CameraExtrinsics(
            doubleArrayOf(
                -1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0
            ),
            V53Vec3(0.0, 0.0, 0.0)
        )
        assertFalse(reflected.valid())
    }
}
