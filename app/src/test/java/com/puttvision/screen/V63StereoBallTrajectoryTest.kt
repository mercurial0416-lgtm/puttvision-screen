package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V63StereoBallTrajectoryTest {
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )
    private val intrinsics = V53CameraIntrinsics(1200.0, 1200.0, 960.0, 540.0)

    private fun signature(id: String, fps: Int = 240, width: Int = 1920, height: Int = 1080) = V59CaptureSignature(
        cameraId = id,
        widthPx = width,
        heightPx = height,
        fps = fps,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun calibration(x: Double) = V53CameraCalibration(
        intrinsics = intrinsics,
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, -1.5)),
        rmsReprojectionPx = 0.15,
        calibratedAtMs = 100_000L
    )

    private val leftCal = calibration(-0.15)
    private val rightCal = calibration(0.15)

    private fun profile() = V59StereoCalibrationProfile(
        pairId = "pair-a",
        rigRevisionId = "rig-a",
        first = V59BoundCameraCalibration(signature("left"), leftCal),
        second = V59BoundCameraCalibration(signature("right"), rightCal),
        calibratedAtMs = 100_000L,
        acceptedObservationCount = 5
    )

    private fun track(
        calibration: V53CameraCalibration,
        sampleCount: Int = 7,
        vx: Double = 0.12,
        vy: Double = 1.20,
        zAt: (Int) -> Double = { 0.021 },
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 240
    ): HfrFeatureTrack {
        val frames = (0 until sampleCount).map { i ->
            val timeMs = i * (1000.0 / fps.toDouble())
            val seconds = timeMs / 1000.0
            val point = V53Vec3(
                x = 0.01 + vx * seconds,
                y = 0.18 + vy * seconds,
                z = zAt(i)
            )
            val pixel = requireNotNull(V53StereoProjection.project(calibration, point))
            HfrFeatureFrame(
                frame = 10 + i,
                timeFromImpactMs = timeMs,
                ballXcm = point.x * 100.0,
                ballYcm = point.y * 100.0,
                heelXcm = null,
                heelYcm = null,
                toeXcm = null,
                toeYcm = null,
                markerAngleDeg = null,
                ballXpx = pixel.x,
                ballYpx = pixel.y
            )
        }
        return HfrFeatureTrack(
            fps = fps,
            impactFrame = 10,
            frames = frames,
            imageWidthPx = width,
            imageHeightPx = height
        )
    }

    private fun reconstruct(
        local: HfrFeatureTrack = track(leftCal),
        remote: HfrFeatureTrack = track(rightCal),
        firstSignature: V59CaptureSignature = signature("left"),
        secondSignature: V59CaptureSignature = signature("right"),
        policy: V63StereoBallPolicy = V63StereoBallPolicy()
    ) = V63StereoBallTrajectoryReconstructor.reconstruct(
        localTrack = local,
        localView = V15CameraView.PRIMARY,
        remoteTrack = remote,
        remoteView = V15CameraView.FACE_ON,
        profile = profile(),
        currentFirst = firstSignature,
        currentSecond = secondSignature,
        activePairId = "pair-a",
        activeRigRevisionId = "rig-a",
        nowMs = 100_500L,
        policy = policy
    )

    @Test fun exactSyntheticBallMotionProducesGated3dTrajectory() {
        val result = reconstruct()

        assertTrue(result.usableForMeasurementValidation)
        assertTrue(result.samples.size >= 5)
        assertEquals(1.205985, requireNotNull(result.horizontalSpeedMps), 1e-4)
        assertEquals(Math.toDegrees(kotlin.math.atan2(0.12, 1.20)), requireNotNull(result.startDirectionDeg), 1e-4)
        assertEquals(0.0, requireNotNull(result.verticalSpeedMps), 1e-4)
        assertTrue(requireNotNull(result.verticalSpreadM) < 1e-6)
        assertTrue(result.samples.all { it.positionSensitivityM >= 0.0 && it.reprojectionErrorPx < 1e-4 })
    }

    @Test fun captureFpsMismatchIsBlockedBeforeTriangulationByV67() {
        val result = reconstruct(firstSignature = signature("left", fps = 120))
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.samples.isEmpty())
        assertTrue(result.reason.contains("V67 gate"))
        assertTrue(result.reason.contains("fps"))
    }

    @Test fun captureShapeMismatchIsBlockedBeforeTriangulationByV67() {
        val result = reconstruct(firstSignature = signature("left", width = 1280, height = 720))
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.samples.isEmpty())
        assertTrue(result.reason.contains("V67 gate"))
        assertTrue(result.reason.contains("shape"))
    }

    @Test fun remoteTrackShapeMismatchIsBlockedBeforeTriangulationByV67() {
        val result = reconstruct(remote = track(rightCal, width = 1280, height = 720))
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.samples.isEmpty())
        assertTrue(result.reason.contains("second camera"))
    }

    @Test fun tooFewStereoSamplesCannotBecomeMeasurementCandidate() {
        val result = reconstruct(
            local = track(leftCal, sampleCount = 4),
            remote = track(rightCal, sampleCount = 4)
        )
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.reason.contains("insufficient"))
    }

    @Test fun implausibleVerticalSpreadFailsClosedAfterGeometryPasses() {
        val varyingZ: (Int) -> Double = { index -> 0.020 + index * 0.003 }
        val result = reconstruct(
            local = track(leftCal, zAt = varyingZ),
            remote = track(rightCal, zAt = varyingZ),
            policy = V63StereoBallPolicy(maxVerticalSpreadM = 0.010)
        )
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.samples.size >= 5)
        assertTrue(result.reason.contains("vertical spread"))
    }

    @Test fun sensitivityGateCanConservativelyDisableOtherwiseValidTrajectory() {
        val result = reconstruct(
            policy = V63StereoBallPolicy(
                v60Policy = V60StereoUncertaintyPolicy(maxPositionSensitivityM = 0.000001)
            )
        )
        assertFalse(result.usableForMeasurementValidation)
        assertTrue(result.reason.contains("insufficient V60-approved"))
    }
}
