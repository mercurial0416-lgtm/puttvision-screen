package com.puttvision.screen

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * First measurement-facing consumer of calibrated stereo: BALL only.
 *
 * Every sample must originate from V55 time-matched raw pixels and pass V59 + V53 + V60. The
 * output remains isolated from FACE/PATH and from ShotMetrics until real-device validation proves
 * it is safe to promote. Derived speed/direction are geometry outputs, not physical accuracy claims.
 */
data class V63StereoBallSample(
    val timeFromImpactMs: Double,
    val localFrame: Int,
    val remoteFrame: Int,
    val pairDeltaMs: Double,
    val pointWorldM: V53Vec3,
    val geometryScore: Int,
    val positionSensitivityM: Double,
    val reprojectionErrorPx: Double
)

data class V63StereoBallPolicy(
    val minUsableSamples: Int = 5,
    val minPostImpactSpanMs: Double = 12.0,
    val maxPairDeltaMs: Double = 6.5,
    /** Sanity gate for a rolling putt over the short HFR fitting window; not an accuracy bound. */
    val maxVerticalSpreadM: Double = 0.030,
    val maxDerivedSpeedMps: Double = 6.0,
    val v60Policy: V60StereoUncertaintyPolicy = V60StereoUncertaintyPolicy()
) {
    fun valid(): Boolean =
        minUsableSamples >= 3 &&
            minPostImpactSpanMs.isFinite() && minPostImpactSpanMs > 0.0 &&
            maxPairDeltaMs.isFinite() && maxPairDeltaMs >= 0.0 &&
            maxVerticalSpreadM.isFinite() && maxVerticalSpreadM > 0.0 &&
            maxDerivedSpeedMps.isFinite() && maxDerivedSpeedMps > 0.0 &&
            v60Policy.valid()
}

data class V63StereoBallTrajectory(
    val usableForMeasurementValidation: Boolean,
    val samples: List<V63StereoBallSample>,
    val horizontalSpeedMps: Double?,
    /** Degrees relative to world +Y (mat-forward); + means world +X. */
    val startDirectionDeg: Double?,
    val verticalSpeedMps: Double?,
    val verticalSpreadM: Double?,
    val reason: String
)

object V63StereoBallTrajectoryReconstructor {
    fun reconstruct(
        localTrack: HfrFeatureTrack,
        localView: V15CameraView,
        remoteTrack: HfrFeatureTrack,
        remoteView: V15CameraView,
        profile: V59StereoCalibrationProfile?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        policy: V63StereoBallPolicy = V63StereoBallPolicy()
    ): V63StereoBallTrajectory {
        if (!policy.valid()) return blocked("stereo BALL policy invalid")
        val local = V55PixelTrackValidator.normalize(localTrack, localView, allowPrimary = true)
            ?: return blocked("local raw-pixel track invalid")
        val remote = V55PixelTrackValidator.normalize(remoteTrack, remoteView)
            ?: return blocked("remote raw-pixel track invalid")
        val localByFrame = local.frames.associateBy { it.frame }
        val pairs = V55StereoPixelMatcher.ballPairs(local, localView, remote, remoteView)
        if (pairs.isEmpty()) return blocked("no time-matched BALL pixel pairs")

        val usable = ArrayList<V63StereoBallSample>()
        for (pair in pairs) {
            if (pair.deltaMs > policy.maxPairDeltaMs) continue
            val localFrame = localByFrame[pair.localFrame] ?: continue
            if (localFrame.timeFromImpactMs < -1e-6) continue
            val gated = V60StereoUncertaintyGate.triangulateWithSensitivityGate(
                profile = profile,
                currentFirst = currentFirst,
                currentSecond = currentSecond,
                activePairId = activePairId,
                activeRigRevisionId = activeRigRevisionId,
                nowMs = nowMs,
                firstPixel = pair.localPixel,
                secondPixel = pair.remotePixel,
                policy = policy.v60Policy
            )
            val point = gated.triangulation.pointWorld
            val sensitivity = gated.positionSensitivityM
            val reprojection = gated.triangulation.reprojectionErrorPx
            if (!gated.usableForFusion || point == null || sensitivity == null || reprojection == null) continue
            usable += V63StereoBallSample(
                timeFromImpactMs = localFrame.timeFromImpactMs,
                localFrame = pair.localFrame,
                remoteFrame = pair.remoteFrame,
                pairDeltaMs = pair.deltaMs,
                pointWorldM = point,
                geometryScore = gated.triangulation.geometryScore,
                positionSensitivityM = sensitivity,
                reprojectionErrorPx = reprojection
            )
        }
        val samples = usable.sortedBy { it.timeFromImpactMs }
        if (samples.size < policy.minUsableSamples) {
            return blocked("insufficient V60-approved BALL samples", samples)
        }
        val span = samples.last().timeFromImpactMs - samples.first().timeFromImpactMs
        if (!span.isFinite() || span < policy.minPostImpactSpanMs) {
            return blocked("post-impact stereo time span too short", samples)
        }
        val zValues = samples.map { it.pointWorldM.z }
        val verticalSpread = (zValues.maxOrNull() ?: return blocked("BALL z missing", samples)) -
            (zValues.minOrNull() ?: return blocked("BALL z missing", samples))
        if (!verticalSpread.isFinite() || verticalSpread > policy.maxVerticalSpreadM) {
            return V63StereoBallTrajectory(false, samples, null, null, null, verticalSpread, "BALL vertical spread sanity gate failed")
        }

        val vx = slope(samples) { it.pointWorldM.x } ?: return blocked("BALL x velocity fit failed", samples)
        val vy = slope(samples) { it.pointWorldM.y } ?: return blocked("BALL y velocity fit failed", samples)
        val vz = slope(samples) { it.pointWorldM.z } ?: return blocked("BALL z velocity fit failed", samples)
        val speed = hypot(vx, vy)
        if (!speed.isFinite() || speed <= 0.01 || speed > policy.maxDerivedSpeedMps) {
            return V63StereoBallTrajectory(false, samples, speed.takeIf { it.isFinite() }, null, vz, verticalSpread, "derived BALL speed sanity gate failed")
        }
        val direction = Math.toDegrees(atan2(vx, vy))
        if (!direction.isFinite() || !vz.isFinite()) return blocked("derived BALL kinematics non-finite", samples)

        return V63StereoBallTrajectory(
            usableForMeasurementValidation = true,
            samples = samples,
            horizontalSpeedMps = speed,
            startDirectionDeg = direction,
            verticalSpeedMps = vz,
            verticalSpreadM = verticalSpread,
            reason = "BALL trajectory passed calibrated stereo geometry gates; real-device validation still required"
        )
    }

    /** Least-squares slope in units/second. */
    private fun slope(samples: List<V63StereoBallSample>, value: (V63StereoBallSample) -> Double): Double? {
        val times = samples.map { it.timeFromImpactMs / 1000.0 }
        val meanT = times.average()
        val meanV = samples.map(value).average()
        var numerator = 0.0
        var denominator = 0.0
        for (i in samples.indices) {
            val dt = times[i] - meanT
            numerator += dt * (value(samples[i]) - meanV)
            denominator += dt * dt
        }
        if (!numerator.isFinite() || !denominator.isFinite() || denominator <= 1e-12) return null
        return (numerator / denominator).takeIf { it.isFinite() }
    }

    private fun blocked(reason: String, samples: List<V63StereoBallSample> = emptyList()) = V63StereoBallTrajectory(
        usableForMeasurementValidation = false,
        samples = samples,
        horizontalSpeedMps = null,
        startDirectionDeg = null,
        verticalSpeedMps = null,
        verticalSpreadM = null,
        reason = reason
    )
}