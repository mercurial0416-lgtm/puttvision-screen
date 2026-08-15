package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V61StereoCalibrationProducerTest {
    private val intrinsics = V53CameraIntrinsics(1200.0, 1200.0, 960.0, 540.0)
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )
    private val plane = listOf(
        V61PlanePointM(-0.20, 0.00),
        V61PlanePointM(0.20, 0.00),
        V61PlanePointM(0.20, 0.60),
        V61PlanePointM(-0.20, 0.60)
    )

    private fun signature(cameraId: String, width: Int = 1920) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = width,
        heightPx = 1080,
        fps = 240,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun knownCalibration(x: Double) = V53CameraCalibration(
        intrinsics = intrinsics,
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, -1.5)),
        rmsReprojectionPx = 0.0,
        calibratedAtMs = 100_000L
    )

    private fun observations(cameraId: String, x: Double): List<V61CalibrationObservation> {
        val calibration = knownCalibration(x)
        val base = plane.map { point ->
            requireNotNull(V53StereoProjection.project(calibration, V53Vec3(point.x, point.y, 0.0)))
        }
        return listOf(-0.20, 0.0, 0.20).map { jitter ->
            V61CalibrationObservation(
                signature = signature(cameraId),
                intrinsics = intrinsics,
                imagePointsPx = base.mapIndexed { index, pixel ->
                    val direction = if (index % 2 == 0) 1.0 else -1.0
                    V53Pixel(pixel.x + jitter * direction, pixel.y - jitter * direction)
                },
                worldPointsM = plane
            )
        }
    }

    @Test fun repeatedPlanarObservationsRecoverKnownCameraPose() {
        val result = V61StereoCalibrationProducer.solveCameraPose(
            observations = observations("cam-left", -0.15),
            calibratedAtMs = 100_000L
        )

        assertTrue(result.usable)
        assertEquals(3, result.acceptedObservationCount)
        val calibration = requireNotNull(result.calibration)
        assertEquals(-0.15, calibration.extrinsics.originWorld.x, 1e-5)
        assertEquals(0.0, calibration.extrinsics.originWorld.y, 1e-5)
        assertEquals(-1.5, calibration.extrinsics.originWorld.z, 1e-5)
        assertTrue(requireNotNull(result.reprojectionRmsPx) < 0.01)
    }

    @Test fun stereoProfilePassesV59OnlyAfterBothPosesAreProduced() {
        val produced = V61StereoCalibrationProducer.buildStereoProfile(
            firstObservations = observations("cam-left", -0.15),
            secondObservations = observations("cam-right", 0.15),
            pairId = "pair-a",
            rigRevisionId = "rig-7",
            calibratedAtMs = 100_000L
        )

        assertTrue(produced.usable)
        val profile = requireNotNull(produced.profile)
        assertEquals(3, profile.acceptedObservationCount)
        val decision = V59StereoCalibrationGate.evaluate(
            profile = profile,
            currentFirst = signature("cam-left"),
            currentSecond = signature("cam-right"),
            activePairId = "pair-a",
            activeRigRevisionId = "rig-7",
            nowMs = 100_500L
        )
        assertTrue(decision.usableForStereo)
    }

    @Test fun missingValidatedIntrinsicsFailsClosed() {
        val bad = observations("cam-left", -0.15).map {
            it.copy(intrinsics = V53CameraIntrinsics(0.0, 1200.0, 960.0, 540.0))
        }
        val result = V61StereoCalibrationProducer.solveCameraPose(bad, 100_000L)
        assertFalse(result.usable)
        assertNull(result.calibration)
        assertTrue(result.reason.contains("intrinsics"))
    }

    @Test fun captureConfigurationChangingMidCalibrationFailsClosed() {
        val mixed = observations("cam-left", -0.15).toMutableList()
        mixed[2] = mixed[2].copy(signature = signature("cam-left", width = 1280))
        val result = V61StereoCalibrationProducer.solveCameraPose(mixed, 100_000L)
        assertFalse(result.usable)
        assertTrue(result.reason.contains("configuration changed"))
    }

    @Test fun degeneratePlaneCannotProducePose() {
        val line = listOf(
            V61PlanePointM(0.0, 0.0),
            V61PlanePointM(0.1, 0.0),
            V61PlanePointM(0.2, 0.0),
            V61PlanePointM(0.3, 0.0)
        )
        val bad = observations("cam-left", -0.15).map { it.copy(worldPointsM = line) }
        val result = V61StereoCalibrationProducer.solveCameraPose(bad, 100_000L)
        assertFalse(result.usable)
        assertTrue(result.reason.contains("plane geometry"))
    }

    @Test fun distortedCorrespondenceCannotMasqueradeAsCalibratedPose() {
        val bad = observations("cam-left", -0.15).map { observation ->
            observation.copy(
                imagePointsPx = observation.imagePointsPx.mapIndexed { index, pixel ->
                    if (index == 2) V53Pixel(pixel.x + 110.0, pixel.y - 70.0) else pixel
                }
            )
        }
        val result = V61StereoCalibrationProducer.solveCameraPose(bad, 100_000L)
        assertFalse(result.usable)
        assertNull(result.calibration)
        assertTrue(
            result.reason.contains("inconsistency") ||
                result.reason.contains("orthogonality") ||
                result.reason.contains("reprojection")
        )
    }

    @Test fun samePhysicalCameraCannotCreateStereoProfile() {
        val produced = V61StereoCalibrationProducer.buildStereoProfile(
            firstObservations = observations("same-camera", -0.15),
            secondObservations = observations("same-camera", 0.15),
            pairId = "pair-a",
            rigRevisionId = "rig-7",
            calibratedAtMs = 100_000L
        )
        assertFalse(produced.usable)
        assertNull(produced.profile)
        assertTrue(produced.reason.contains("reuses one camera"))
    }

    @Test fun tooSmallBaselineFailsBeforeV59ProfileIsCreated() {
        val produced = V61StereoCalibrationProducer.buildStereoProfile(
            firstObservations = observations("cam-left", -0.005),
            secondObservations = observations("cam-right", 0.005),
            pairId = "pair-a",
            rigRevisionId = "rig-7",
            calibratedAtMs = 100_000L
        )
        assertFalse(produced.usable)
        assertNull(produced.profile)
        assertTrue(produced.reason.contains("baseline"))
    }
}