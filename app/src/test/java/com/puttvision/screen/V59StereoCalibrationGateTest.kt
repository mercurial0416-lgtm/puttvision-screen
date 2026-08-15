package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V59StereoCalibrationGateTest {
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )

    private fun signature(cameraId: String, fps: Int = 240, width: Int = 1920, height: Int = 1080) =
        V59CaptureSignature(cameraId, width, height, fps, 90, "BACK", "HFR")

    private fun calibration(x: Double, atMs: Long = 1_000_000L, rms: Double = 0.4) = V53CameraCalibration(
        intrinsics = V53CameraIntrinsics(1_200.0, 1_200.0, 960.0, 540.0),
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, 0.0)),
        rmsReprojectionPx = rms,
        calibratedAtMs = atMs
    )

    private fun profile(
        atMs: Long = 1_000_000L,
        firstSignature: V59CaptureSignature = signature("0"),
        secondSignature: V59CaptureSignature = signature("1"),
        firstCalibration: V53CameraCalibration = calibration(-0.15, atMs),
        secondCalibration: V53CameraCalibration = calibration(0.15, atMs),
        observations: Int = 12
    ) = V59StereoCalibrationProfile(
        pairId = "pair-A",
        rigRevisionId = "rig-7",
        first = V59BoundCameraCalibration(firstSignature, firstCalibration),
        second = V59BoundCameraCalibration(secondSignature, secondCalibration),
        calibratedAtMs = atMs,
        acceptedObservationCount = observations
    )

    @Test fun exactCurrentCaptureConfigurationPassesGate() {
        val decision = V59StereoCalibrationGate.evaluate(
            profile = profile(),
            currentFirst = signature("0"),
            currentSecond = signature("1"),
            activePairId = "pair-A",
            activeRigRevisionId = "rig-7",
            nowMs = 1_100_000L
        )
        assertTrue(decision.usableForStereo)
        assertTrue(decision.firstCalibration != null)
        assertTrue(decision.secondCalibration != null)
    }

    @Test fun fpsOrResolutionChangeInvalidatesStoredCalibration() {
        val fpsDecision = V59StereoCalibrationGate.evaluate(
            profile(), signature("0", fps = 120), signature("1"), "pair-A", "rig-7", 1_100_000L
        )
        assertFalse(fpsDecision.usableForStereo)
        assertTrue(fpsDecision.reason.contains("configuration"))

        val sizeDecision = V59StereoCalibrationGate.evaluate(
            profile(), signature("0", width = 1280, height = 720), signature("1"), "pair-A", "rig-7", 1_100_000L
        )
        assertFalse(sizeDecision.usableForStereo)
        assertTrue(sizeDecision.reason.contains("configuration"))
    }

    @Test fun pairingOrPhysicalRigRevisionChangeFailsClosed() {
        val pairDecision = V59StereoCalibrationGate.evaluate(
            profile(), signature("0"), signature("1"), "pair-B", "rig-7", 1_100_000L
        )
        assertFalse(pairDecision.usableForStereo)
        assertTrue(pairDecision.reason.contains("pairing"))

        val rigDecision = V59StereoCalibrationGate.evaluate(
            profile(), signature("0"), signature("1"), "pair-A", "rig-8", 1_100_000L
        )
        assertFalse(rigDecision.usableForStereo)
        assertTrue(rigDecision.reason.contains("rig"))
    }

    @Test fun staleOrHighResidualCalibrationCannotEnter3d() {
        val stalePolicy = V59StereoCalibrationPolicy(maxProfileAgeMs = 1_000L)
        val stale = V59StereoCalibrationGate.evaluate(
            profile(), signature("0"), signature("1"), "pair-A", "rig-7", 1_002_000L, stalePolicy
        )
        assertFalse(stale.usableForStereo)
        assertTrue(stale.reason.contains("expired"))

        val highResidual = profile(firstCalibration = calibration(-0.15, rms = 3.0))
        val residual = V59StereoCalibrationGate.evaluate(
            highResidual, signature("0"), signature("1"), "pair-A", "rig-7", 1_100_000L
        )
        assertFalse(residual.usableForStereo)
        assertTrue(residual.reason.contains("reprojection"))
    }

    @Test fun gatedTriangulationRecoversSyntheticPointOnlyWhenReady() {
        val readyProfile = profile()
        val target = V53Vec3(0.03, -0.02, 2.0)
        val left = requireNotNull(V53StereoProjection.project(readyProfile.first.calibration, target))
        val right = requireNotNull(V53StereoProjection.project(readyProfile.second.calibration, target))

        val ready = V59StereoCalibrationGate.triangulateIfReady(
            readyProfile, signature("0"), signature("1"), "pair-A", "rig-7", 1_100_000L, left, right
        )
        assertTrue(ready.usableForFusion)
        assertEquals(target.x, requireNotNull(ready.pointWorld).x, 1e-6)

        val blocked = V59StereoCalibrationGate.triangulateIfReady(
            readyProfile, signature("0", fps = 120), signature("1"), "pair-A", "rig-7", 1_100_000L, left, right
        )
        assertFalse(blocked.usableForFusion)
        assertNull(blocked.pointWorld)
        assertEquals(0, blocked.geometryScore)
        assertTrue(blocked.reason.startsWith("V59 gate:"))
    }

    @Test fun oneCameraCannotMasqueradeAsStereoPair() {
        val same = signature("0")
        val badProfile = profile(secondSignature = same)
        val decision = V59StereoCalibrationGate.evaluate(
            badProfile, same, same, "pair-A", "rig-7", 1_100_000L
        )
        assertFalse(decision.usableForStereo)
        assertTrue(decision.reason.contains("one camera"))
    }
}
