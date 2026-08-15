package com.puttvision.screen

import kotlin.math.sqrt

/**
 * Fail-closed stability gate for repeated planar stereo-calibration observations.
 *
 * V61 intentionally uses a per-landmark median to reduce detector spikes, but a plausible median
 * must not hide a physically unstable capture. V66 measures every raw observation against that
 * median before V61 is allowed to solve camera pose. Thresholds are pixel-domain regression gates,
 * not physical accuracy claims.
 */
data class V66CalibrationStabilityPolicy(
    val maxLandmarkRadialDeviationPx: Double = 4.0,
    val maxObservationRmsDeviationPx: Double = 2.5,
    val maxUnstableObservationFraction: Double = 0.0
) {
    fun valid(): Boolean =
        maxLandmarkRadialDeviationPx.isFinite() && maxLandmarkRadialDeviationPx > 0.0 &&
            maxObservationRmsDeviationPx.isFinite() && maxObservationRmsDeviationPx > 0.0 &&
            maxUnstableObservationFraction.isFinite() && maxUnstableObservationFraction in 0.0..1.0
}

data class V66CalibrationStabilityResult(
    val stable: Boolean,
    val medianPixels: List<V53Pixel>,
    val worstLandmarkDeviationPx: Double?,
    val worstObservationRmsDeviationPx: Double?,
    val unstableObservationCount: Int,
    val reason: String
)

object V66CalibrationObservationGate {
    fun evaluate(
        observations: List<V61CalibrationObservation>,
        policy: V66CalibrationStabilityPolicy = V66CalibrationStabilityPolicy()
    ): V66CalibrationStabilityResult {
        if (!policy.valid()) return deny("stability policy invalid")
        if (observations.isEmpty()) return deny("calibration observations missing")
        val pointCount = observations.first().imagePointsPx.size
        if (pointCount < 4 || observations.any { it.imagePointsPx.size != pointCount }) {
            return deny("calibration correspondence count unstable")
        }
        if (observations.any { observation -> observation.imagePointsPx.any { !it.valid() } }) {
            return deny("calibration pixel invalid")
        }

        val medians = (0 until pointCount).map { index ->
            V53Pixel(
                x = median(observations.map { it.imagePointsPx[index].x }),
                y = median(observations.map { it.imagePointsPx[index].y })
            )
        }
        if (medians.any { !it.valid() }) return deny("calibration median invalid")

        var worstLandmark = 0.0
        var worstObservationRms = 0.0
        var unstableCount = 0
        observations.forEach { observation ->
            var squared = 0.0
            var observationWorst = 0.0
            observation.imagePointsPx.indices.forEach { index ->
                val dx = observation.imagePointsPx[index].x - medians[index].x
                val dy = observation.imagePointsPx[index].y - medians[index].y
                val radial = sqrt(dx * dx + dy * dy)
                if (!radial.isFinite()) return deny("calibration deviation invalid")
                squared += radial * radial
                observationWorst = maxOf(observationWorst, radial)
                worstLandmark = maxOf(worstLandmark, radial)
            }
            val rms = sqrt(squared / pointCount.toDouble())
            if (!rms.isFinite()) return deny("calibration observation RMS invalid")
            worstObservationRms = maxOf(worstObservationRms, rms)
            if (observationWorst > policy.maxLandmarkRadialDeviationPx ||
                rms > policy.maxObservationRmsDeviationPx
            ) unstableCount++
        }

        val unstableFraction = unstableCount.toDouble() / observations.size.toDouble()
        if (unstableFraction > policy.maxUnstableObservationFraction) {
            return V66CalibrationStabilityResult(
                stable = false,
                medianPixels = medians,
                worstLandmarkDeviationPx = worstLandmark,
                worstObservationRmsDeviationPx = worstObservationRms,
                unstableObservationCount = unstableCount,
                reason = "repeated calibration detections unstable"
            )
        }
        return V66CalibrationStabilityResult(
            stable = true,
            medianPixels = medians,
            worstLandmarkDeviationPx = worstLandmark,
            worstObservationRmsDeviationPx = worstObservationRms,
            unstableObservationCount = unstableCount,
            reason = "repeated calibration detections stable in pixel domain"
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) * 0.5
    }

    private fun deny(reason: String) = V66CalibrationStabilityResult(
        stable = false,
        medianPixels = emptyList(),
        worstLandmarkDeviationPx = null,
        worstObservationRmsDeviationPx = null,
        unstableObservationCount = 0,
        reason = reason
    )
}

/**
 * Safe entry point for new stereo calibration flows. It refuses to call V61 until both cameras
 * have stable repeated pixel observations. V59/V60 remain the downstream calibration/uncertainty
 * gates; this wrapper adds no physical accuracy claim.
 */
object V66StableStereoCalibrationProducer {
    fun buildStereoProfile(
        firstObservations: List<V61CalibrationObservation>,
        secondObservations: List<V61CalibrationObservation>,
        pairId: String,
        rigRevisionId: String,
        calibratedAtMs: Long,
        producerPolicy: V61CalibrationProducerPolicy = V61CalibrationProducerPolicy(),
        stabilityPolicy: V66CalibrationStabilityPolicy = V66CalibrationStabilityPolicy()
    ): V61StereoProfileResult {
        val firstStability = V66CalibrationObservationGate.evaluate(firstObservations, stabilityPolicy)
        if (!firstStability.stable) {
            val failed = V61CameraPoseResult(null, firstObservations.size, null, firstStability.reason)
            return V61StereoProfileResult(null, failed, V61CameraPoseResult(null, 0, null, "not evaluated"), "first camera calibration rejected: ${firstStability.reason}")
        }
        val secondStability = V66CalibrationObservationGate.evaluate(secondObservations, stabilityPolicy)
        if (!secondStability.stable) {
            val failed = V61CameraPoseResult(null, secondObservations.size, null, secondStability.reason)
            return V61StereoProfileResult(null, V61CameraPoseResult(null, firstObservations.size, null, "stability passed; pose not evaluated"), failed, "second camera calibration rejected: ${secondStability.reason}")
        }
        return V61StereoCalibrationProducer.buildStereoProfile(
            firstObservations = firstObservations,
            secondObservations = secondObservations,
            pairId = pairId,
            rigRevisionId = rigRevisionId,
            calibratedAtMs = calibratedAtMs,
            policy = producerPolicy
        )
    }
}
