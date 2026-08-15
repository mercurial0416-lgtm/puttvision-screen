package com.puttvision.screen

import kotlin.math.abs

/**
 * Runtime binding for calibrated stereo.
 *
 * V53 validates the calibration numbers themselves. V59 additionally proves that those numbers
 * belong to the exact capture configuration that is currently producing HFR pixels. A profile
 * that is stale, belongs to another camera/configuration, or has lost its rig pairing is rejected
 * before any 3D result can be exposed to downstream fusion.
 *
 * This is an operational safety gate, not a physical accuracy claim.
 */
data class V59CaptureSignature(
    val cameraId: String,
    val widthPx: Int,
    val heightPx: Int,
    val fps: Int,
    val sensorOrientationDeg: Int,
    val lensFacing: String,
    val captureMode: String
) {
    fun valid(): Boolean =
        cameraId.isNotBlank() &&
            widthPx > 0 && heightPx > 0 && fps > 0 &&
            sensorOrientationDeg in setOf(0, 90, 180, 270) &&
            lensFacing.isNotBlank() && captureMode.isNotBlank()

    fun stableKey(): String = listOf(
        cameraId.trim(),
        "${widthPx}x$heightPx",
        "${fps}fps",
        sensorOrientationDeg.toString(),
        lensFacing.trim().uppercase(),
        captureMode.trim().uppercase()
    ).joinToString("|")
}

data class V59BoundCameraCalibration(
    val signature: V59CaptureSignature,
    val calibration: V53CameraCalibration
)

data class V59StereoCalibrationProfile(
    val schemaVersion: Int = 1,
    val pairId: String,
    /** Changes whenever the physical two-phone setup is deliberately recalibrated. */
    val rigRevisionId: String,
    val first: V59BoundCameraCalibration,
    val second: V59BoundCameraCalibration,
    val calibratedAtMs: Long,
    /** Number of accepted calibration observations used by the producer. Informational only. */
    val acceptedObservationCount: Int
)

data class V59StereoCalibrationPolicy(
    /** Operational freshness only; this is not an accuracy threshold. */
    val maxProfileAgeMs: Long = 7L * 24L * 60L * 60L * 1000L,
    val maxClockSkewMs: Long = 5L * 60L * 1000L,
    val minAcceptedObservationCount: Int = 1,
    val v53Policy: V53TriangulationPolicy = V53TriangulationPolicy()
) {
    fun valid(): Boolean =
        maxProfileAgeMs > 0L && maxClockSkewMs >= 0L && minAcceptedObservationCount > 0 && v53Policy.valid()
}

data class V59StereoCalibrationDecision(
    val usableForStereo: Boolean,
    val reason: String,
    val firstCalibration: V53CameraCalibration? = null,
    val secondCalibration: V53CameraCalibration? = null
)

object V59StereoCalibrationGate {
    fun evaluate(
        profile: V59StereoCalibrationProfile?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        policy: V59StereoCalibrationPolicy = V59StereoCalibrationPolicy()
    ): V59StereoCalibrationDecision {
        if (!policy.valid()) return deny("calibration policy invalid")
        if (profile == null) return deny("stereo calibration missing")
        if (profile.schemaVersion != 1) return deny("stereo calibration schema unsupported")
        if (!currentFirst.valid() || !currentSecond.valid()) return deny("current capture signature invalid")
        if (!profile.first.signature.valid() || !profile.second.signature.valid()) return deny("stored capture signature invalid")
        if (profile.pairId.isBlank() || activePairId.isBlank() || profile.pairId != activePairId) {
            return deny("camera pairing changed")
        }
        if (profile.rigRevisionId.isBlank() || activeRigRevisionId.isBlank() || profile.rigRevisionId != activeRigRevisionId) {
            return deny("physical rig revision changed")
        }
        if (profile.first.signature.cameraId == profile.second.signature.cameraId) {
            return deny("stereo profile reuses one camera")
        }
        if (currentFirst.cameraId == currentSecond.cameraId) return deny("current stereo capture reuses one camera")
        if (profile.first.signature.stableKey() != currentFirst.stableKey()) return deny("first capture configuration changed")
        if (profile.second.signature.stableKey() != currentSecond.stableKey()) return deny("second capture configuration changed")
        if (profile.acceptedObservationCount < policy.minAcceptedObservationCount) {
            return deny("insufficient calibration observations")
        }
        if (profile.calibratedAtMs <= 0L) return deny("calibration timestamp missing")
        if (nowMs <= 0L) return deny("runtime timestamp invalid")
        if (profile.calibratedAtMs - nowMs > policy.maxClockSkewMs) return deny("calibration timestamp is in the future")
        if (nowMs - profile.calibratedAtMs > policy.maxProfileAgeMs) return deny("stereo calibration expired")

        val first = profile.first.calibration
        val second = profile.second.calibration
        if (!first.valid() || !second.valid()) return deny("camera calibration missing or invalid")
        if (first.calibratedAtMs > 0L && abs(first.calibratedAtMs - profile.calibratedAtMs) > policy.maxClockSkewMs) {
            return deny("first calibration provenance mismatch")
        }
        if (second.calibratedAtMs > 0L && abs(second.calibratedAtMs - profile.calibratedAtMs) > policy.maxClockSkewMs) {
            return deny("second calibration provenance mismatch")
        }
        if (first.rmsReprojectionPx > policy.v53Policy.maxCalibrationRmsPx ||
            second.rmsReprojectionPx > policy.v53Policy.maxCalibrationRmsPx
        ) {
            return deny("calibration reprojection error too high")
        }

        val baselineM = (first.extrinsics.originWorld - second.extrinsics.originWorld).norm()
        if (!baselineM.isFinite() || baselineM <= 1e-6) return deny("stereo baseline invalid")

        return V59StereoCalibrationDecision(
            usableForStereo = true,
            reason = "calibration bound to current capture configuration",
            firstCalibration = first,
            secondCalibration = second
        )
    }

    fun triangulateIfReady(
        profile: V59StereoCalibrationProfile?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        firstPixel: V53Pixel,
        secondPixel: V53Pixel,
        policy: V59StereoCalibrationPolicy = V59StereoCalibrationPolicy()
    ): V53TriangulationResult {
        val decision = evaluate(
            profile = profile,
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            activePairId = activePairId,
            activeRigRevisionId = activeRigRevisionId,
            nowMs = nowMs,
            policy = policy
        )
        if (!decision.usableForStereo) {
            return V53TriangulationResult(
                pointWorld = null,
                rayGapM = null,
                parallaxDeg = null,
                reprojectionErrorPx = null,
                geometryScore = 0,
                usableForFusion = false,
                reason = "V59 gate: ${decision.reason}"
            )
        }
        return V53StereoTriangulator.triangulate(
            firstCalibration = requireNotNull(decision.firstCalibration),
            firstPixel = firstPixel,
            secondCalibration = requireNotNull(decision.secondCalibration),
            secondPixel = secondPixel,
            policy = policy.v53Policy
        )
    }

    private fun deny(reason: String) = V59StereoCalibrationDecision(false, reason)
}
