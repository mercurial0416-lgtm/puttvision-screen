package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V66CalibrationObservationStabilityTest {
    private val intrinsics = V53CameraIntrinsics(1200.0, 1200.0, 960.0, 540.0)
    private val plane = listOf(
        V61PlanePointM(-0.20, 0.00),
        V61PlanePointM(0.20, 0.00),
        V61PlanePointM(0.20, 0.60),
        V61PlanePointM(-0.20, 0.60)
    )

    private fun signature(cameraId: String) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = 1920,
        heightPx = 1080,
        fps = 240,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun observations(cameraId: String, offsetX: Double = 0.0): List<V61CalibrationObservation> {
        val base = listOf(
            V53Pixel(800.0 + offsetX, 540.0),
            V53Pixel(1120.0 + offsetX, 540.0),
            V53Pixel(1120.0 + offsetX, 1020.0),
            V53Pixel(800.0 + offsetX, 1020.0)
        )
        return listOf(-0.35, 0.0, 0.35).map { jitter ->
            V61CalibrationObservation(
                signature = signature(cameraId),
                intrinsics = intrinsics,
                imagePointsPx = base.mapIndexed { index, p ->
                    val sign = if (index % 2 == 0) 1.0 else -1.0
                    V53Pixel(p.x + jitter * sign, p.y - jitter * sign)
                },
                worldPointsM = plane
            )
        }
    }

    @Test fun stableRepeatedDetectionsPassPixelDomainGate() {
        val result = V66CalibrationObservationGate.evaluate(observations("left"))
        assertTrue(result.stable)
        assertTrue(requireNotNull(result.worstLandmarkDeviationPx) < 1.0)
        assertTrue(requireNotNull(result.worstObservationRmsDeviationPx) < 1.0)
    }

    @Test fun oneWildFrameCannotHideBehindMedian() {
        val samples = observations("left").toMutableList()
        samples[2] = samples[2].copy(
            imagePointsPx = samples[2].imagePointsPx.mapIndexed { index, p ->
                if (index == 2) V53Pixel(p.x + 18.0, p.y - 12.0) else p
            }
        )
        val result = V66CalibrationObservationGate.evaluate(samples)
        assertFalse(result.stable)
        assertTrue(result.unstableObservationCount >= 1)
        assertTrue(requireNotNull(result.worstLandmarkDeviationPx) > 10.0)
    }

    @Test fun permissiveOutlierFractionStillRequiresExplicitPolicy() {
        val samples = observations("left").toMutableList()
        samples[2] = samples[2].copy(
            imagePointsPx = samples[2].imagePointsPx.map { V53Pixel(it.x + 7.0, it.y) }
        )
        val strict = V66CalibrationObservationGate.evaluate(samples)
        val explicit = V66CalibrationObservationGate.evaluate(
            samples,
            V66CalibrationStabilityPolicy(maxUnstableObservationFraction = 0.34)
        )
        assertFalse(strict.stable)
        assertTrue(explicit.stable)
    }

    @Test fun stableWrapperStillRejectsBadStereoGeometryDownstream() {
        val produced = V66StableStereoCalibrationProducer.buildStereoProfile(
            firstObservations = observations("same-camera"),
            secondObservations = observations("same-camera", 30.0),
            pairId = "pair-a",
            rigRevisionId = "rig-a",
            calibratedAtMs = 100_000L
        )
        assertFalse(produced.usable)
        assertNull(produced.profile)
        assertTrue(produced.reason.contains("reuses one camera") || produced.reason.contains("pose"))
    }

    @Test fun correspondenceCountChangesFailClosedBeforePoseSolve() {
        val samples = observations("left").toMutableList()
        samples[1] = samples[1].copy(imagePointsPx = samples[1].imagePointsPx.dropLast(1))
        val result = V66CalibrationObservationGate.evaluate(samples)
        assertFalse(result.stable)
        assertTrue(result.reason.contains("count"))
    }
}
