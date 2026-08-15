package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V60StereoUncertaintyTest {
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )

    private fun signature(cameraId: String) =
        V59CaptureSignature(cameraId, 1920, 1080, 240, 90, "BACK", "HFR")

    private fun calibration(x: Double, rms: Double = 0.4) = V53CameraCalibration(
        intrinsics = V53CameraIntrinsics(1_200.0, 1_200.0, 960.0, 540.0),
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, 0.0)),
        rmsReprojectionPx = rms,
        calibratedAtMs = 1_000_000L
    )

    private fun profile(baselineHalfM: Double = 0.15, rms: Double = 0.4) =
        V59StereoCalibrationProfile(
            pairId = "pair-A",
            rigRevisionId = "rig-7",
            first = V59BoundCameraCalibration(signature("0"), calibration(-baselineHalfM, rms)),
            second = V59BoundCameraCalibration(signature("1"), calibration(baselineHalfM, rms)),
            calibratedAtMs = 1_000_000L,
            acceptedObservationCount = 12
        )

    private fun run(
        profile: V59StereoCalibrationProfile,
        target: V53Vec3,
        policy: V60StereoUncertaintyPolicy = V60StereoUncertaintyPolicy()
    ): V60StereoUncertaintyResult {
        val left = requireNotNull(V53StereoProjection.project(profile.first.calibration, target))
        val right = requireNotNull(V53StereoProjection.project(profile.second.calibration, target))
        return V60StereoUncertaintyGate.triangulateWithSensitivityGate(
            profile,
            signature("0"),
            signature("1"),
            "pair-A",
            "rig-7",
            1_100_000L,
            left,
            right,
            policy
        )
    }

    @Test fun healthySyntheticGeometryPassesSensitivityGate() {
        val result = run(profile(), V53Vec3(0.03, -0.02, 2.0))
        assertTrue(result.usableForFusion)
        assertTrue(result.triangulation.usableForFusion)
        assertNotNull(result.positionSensitivityM)
        assertTrue(requireNotNull(result.positionSensitivityM) < 0.03)
        assertTrue(requireNotNull(result.assumedPixelSigmaPx) >= 0.5)
    }

    @Test fun strictSensitivityLimitFailsClosedWithoutChangingBaseTriangulation() {
        val result = run(
            profile(),
            V53Vec3(0.03, -0.02, 2.0),
            V60StereoUncertaintyPolicy(maxPositionSensitivityM = 0.00001)
        )
        assertFalse(result.usableForFusion)
        assertTrue(result.triangulation.usableForFusion)
        assertTrue(result.reason.contains("sensitive"))
    }

    @Test fun weakLongRangeGeometryCannotSneakThroughSensitivityLayer() {
        val result = run(profile(baselineHalfM = 0.01), V53Vec3(0.0, 0.0, 12.0))
        assertFalse(result.usableForFusion)
        assertFalse(result.triangulation.usableForFusion)
    }

    @Test fun calibrationResidualRaisesAssumedPixelSigma() {
        val result = run(profile(rms = 1.2), V53Vec3(0.0, 0.0, 2.0))
        assertTrue(requireNotNull(result.assumedPixelSigmaPx) >= 1.2)
    }

    @Test fun v59MismatchStillFailsBeforeSensitivityAnalysis() {
        val p = profile()
        val target = V53Vec3(0.0, 0.0, 2.0)
        val left = requireNotNull(V53StereoProjection.project(p.first.calibration, target))
        val right = requireNotNull(V53StereoProjection.project(p.second.calibration, target))
        val result = V60StereoUncertaintyGate.triangulateWithSensitivityGate(
            p,
            V59CaptureSignature("0", 1920, 1080, 120, 90, "BACK", "HFR"),
            signature("1"),
            "pair-A",
            "rig-7",
            1_100_000L,
            left,
            right
        )
        assertFalse(result.usableForFusion)
        assertTrue(result.reason.startsWith("V59 gate:"))
    }
}
