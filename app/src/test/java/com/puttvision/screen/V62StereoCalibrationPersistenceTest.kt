package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V62StereoCalibrationPersistenceTest {
    private val identity = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, 1.0, 0.0,
        0.0, 0.0, 1.0
    )

    private fun signature(id: String) = V59CaptureSignature(
        cameraId = id,
        widthPx = 1920,
        heightPx = 1080,
        fps = 240,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun calibration(x: Double) = V53CameraCalibration(
        intrinsics = V53CameraIntrinsics(1200.0, 1200.0, 960.0, 540.0),
        extrinsics = V53CameraExtrinsics(identity.copyOf(), V53Vec3(x, 0.0, -1.5)),
        rmsReprojectionPx = 0.35,
        calibratedAtMs = 100_000L
    )

    private fun profile() = V59StereoCalibrationProfile(
        pairId = "pair-a",
        rigRevisionId = "rig-3",
        first = V59BoundCameraCalibration(signature("left"), calibration(-0.15)),
        second = V59BoundCameraCalibration(signature("right"), calibration(0.15)),
        calibratedAtMs = 100_000L,
        acceptedObservationCount = 4
    )

    private val leftPoints = listOf(
        V53Pixel(800.0, 700.0), V53Pixel(1100.0, 700.0),
        V53Pixel(1080.0, 980.0), V53Pixel(820.0, 980.0)
    )
    private val rightPoints = listOf(
        V53Pixel(650.0, 700.0), V53Pixel(950.0, 700.0),
        V53Pixel(930.0, 980.0), V53Pixel(670.0, 980.0)
    )

    private fun witness(shiftX: Double = 0.0, shiftY: Double = 0.0, at: Long = 100_100L) = V62RigWitness(
        firstSignatureKey = signature("left").stableKey(),
        secondSignatureKey = signature("right").stableKey(),
        firstLandmarksPx = leftPoints.map { V53Pixel(it.x + shiftX, it.y + shiftY) },
        secondLandmarksPx = rightPoints.map { V53Pixel(it.x + shiftX, it.y + shiftY) },
        capturedAtMs = at
    )

    private fun stored() = V62StoredStereoCalibration(
        profile = profile(),
        witness = witness(at = 100_000L),
        storedAtMs = 100_000L
    )

    @Test fun codecRoundTripPreservesCalibrationAndWitness() {
        val raw = V62StereoCalibrationCodec.encode(stored())
        val decoded = V62StereoCalibrationCodec.decode(raw)

        assertNotNull(decoded)
        decoded ?: return
        assertEquals("pair-a", decoded.profile.pairId)
        assertEquals("rig-3", decoded.profile.rigRevisionId)
        assertEquals(-0.15, decoded.profile.first.calibration.extrinsics.originWorld.x, 1e-9)
        assertEquals(4, decoded.witness.firstLandmarksPx.size)
        assertEquals(leftPoints[2].x, decoded.witness.firstLandmarksPx[2].x, 1e-9)
    }

    @Test fun corruptPayloadDoesNotDecodeAsCalibration() {
        val raw = V62StereoCalibrationCodec.encode(stored())
        assertNull(V62StereoCalibrationCodec.decode(raw.dropLast(9)))
    }

    @Test fun matchingFreshWitnessAllowsStoredProfile() {
        val result = V62StereoCalibrationValidator.evaluate(
            stored = stored(),
            currentFirst = signature("left"),
            currentSecond = signature("right"),
            activePairId = "pair-a",
            activeRigRevisionId = "rig-3",
            nowMs = 100_500L,
            currentWitness = witness(shiftX = 1.0, shiftY = -1.0, at = 100_400L)
        )

        assertTrue(result.usableForStereo)
        assertNotNull(result.profile)
        assertTrue(requireNotNull(result.witnessRmsDriftPx) < 2.0)
    }

    @Test fun missingFreshWitnessFailsClosedByDefault() {
        val result = V62StereoCalibrationValidator.evaluate(
            stored(), signature("left"), signature("right"), "pair-a", "rig-3", 100_500L, null
        )
        assertFalse(result.usableForStereo)
        assertNull(result.profile)
        assertTrue(result.reason.contains("witness required"))
    }

    @Test fun movedProjectionInvalidatesCalibrationEvenWhenV59StillMatches() {
        val result = V62StereoCalibrationValidator.evaluate(
            stored = stored(),
            currentFirst = signature("left"),
            currentSecond = signature("right"),
            activePairId = "pair-a",
            activeRigRevisionId = "rig-3",
            nowMs = 100_500L,
            currentWitness = witness(shiftX = 15.0, at = 100_400L)
        )

        assertFalse(result.usableForStereo)
        assertNull(result.profile)
        assertTrue(result.reason.contains("drift"))
        assertTrue(requireNotNull(result.witnessMaxDriftPx) >= 15.0)
    }

    @Test fun oneBadLandmarkIsCaughtByMaxDriftGate() {
        val current = witness(at = 100_400L).copy(
            firstLandmarksPx = leftPoints.mapIndexed { index, pixel ->
                if (index == 0) V53Pixel(pixel.x + 20.0, pixel.y) else pixel
            }
        )
        val result = V62StereoCalibrationValidator.evaluate(
            stored(), signature("left"), signature("right"), "pair-a", "rig-3", 100_500L, current
        )
        assertFalse(result.usableForStereo)
        assertTrue(result.reason.contains("point drift"))
    }

    @Test fun changedPairOrRigNeverResurrectsStoredProfile() {
        val pairChanged = V62StereoCalibrationValidator.evaluate(
            stored(), signature("left"), signature("right"), "pair-b", "rig-3", 100_500L, witness(at = 100_400L)
        )
        val rigChanged = V62StereoCalibrationValidator.evaluate(
            stored(), signature("left"), signature("right"), "pair-a", "rig-4", 100_500L, witness(at = 100_400L)
        )
        assertFalse(pairChanged.usableForStereo)
        assertFalse(rigChanged.usableForStereo)
        assertTrue(pairChanged.reason.contains("V59 gate"))
        assertTrue(rigChanged.reason.contains("V59 gate"))
    }

    @Test fun staleWitnessCannotUnlockStereo() {
        val result = V62StereoCalibrationValidator.evaluate(
            stored = stored(),
            currentFirst = signature("left"),
            currentSecond = signature("right"),
            activePairId = "pair-a",
            activeRigRevisionId = "rig-3",
            nowMs = 2L * 24L * 60L * 60L * 1000L,
            currentWitness = witness(at = 100_100L),
            policy = V62PersistencePolicy(maxWitnessAgeMs = 60_000L)
        )
        assertFalse(result.usableForStereo)
        assertTrue(result.reason.contains("stale"))
    }
}