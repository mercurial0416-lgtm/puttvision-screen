package com.puttvision.screen

import kotlin.math.max

/**
 * Geometry-sensitivity gate for calibrated stereo.
 *
 * This intentionally does NOT estimate real-world measurement accuracy. It asks a narrower safety
 * question: if plausible sub-pixel/pixel correspondence error is injected into the two observations,
 * how far can the triangulated point move? Geometry that is too sensitive is rejected before fusion.
 */
data class V60StereoUncertaintyPolicy(
    val minAssumedPixelSigmaPx: Double = 0.50,
    val calibrationRmsMultiplier: Double = 1.0,
    val maxPositionSensitivityM: Double = 0.030,
    val requireAllPerturbationsUsable: Boolean = true,
    val v59Policy: V59StereoCalibrationPolicy = V59StereoCalibrationPolicy()
) {
    fun valid(): Boolean =
        minAssumedPixelSigmaPx.isFinite() && minAssumedPixelSigmaPx > 0.0 &&
            calibrationRmsMultiplier.isFinite() && calibrationRmsMultiplier > 0.0 &&
            maxPositionSensitivityM.isFinite() && maxPositionSensitivityM > 0.0 &&
            v59Policy.valid()
}

data class V60StereoUncertaintyResult(
    val triangulation: V53TriangulationResult,
    /** Worst synthetic perturbation displacement. Geometry sensitivity only, not physical accuracy. */
    val positionSensitivityM: Double?,
    val assumedPixelSigmaPx: Double?,
    val usableForFusion: Boolean,
    val reason: String
)

object V60StereoUncertaintyGate {
    fun triangulateWithSensitivityGate(
        profile: V59StereoCalibrationProfile?,
        currentFirst: V59CaptureSignature,
        currentSecond: V59CaptureSignature,
        activePairId: String,
        activeRigRevisionId: String,
        nowMs: Long,
        firstPixel: V53Pixel,
        secondPixel: V53Pixel,
        policy: V60StereoUncertaintyPolicy = V60StereoUncertaintyPolicy()
    ): V60StereoUncertaintyResult {
        if (!policy.valid()) return blocked("uncertainty policy invalid")

        val decision = V59StereoCalibrationGate.evaluate(
            profile = profile,
            currentFirst = currentFirst,
            currentSecond = currentSecond,
            activePairId = activePairId,
            activeRigRevisionId = activeRigRevisionId,
            nowMs = nowMs,
            policy = policy.v59Policy
        )
        if (!decision.usableForStereo) return blocked("V59 gate: ${decision.reason}")

        val firstCalibration = requireNotNull(decision.firstCalibration)
        val secondCalibration = requireNotNull(decision.secondCalibration)
        val base = V53StereoTriangulator.triangulate(
            firstCalibration,
            firstPixel,
            secondCalibration,
            secondPixel,
            policy.v59Policy.v53Policy
        )
        val basePoint = base.pointWorld
        if (!base.usableForFusion || basePoint == null) {
            return V60StereoUncertaintyResult(base, null, null, false, "base triangulation rejected: ${base.reason}")
        }

        val sigma = max(
            policy.minAssumedPixelSigmaPx,
            max(firstCalibration.rmsReprojectionPx, secondCalibration.rmsReprojectionPx) * policy.calibrationRmsMultiplier
        )
        if (!sigma.isFinite() || sigma <= 0.0) return blocked("pixel sensitivity input invalid", base)

        val perturbations = listOf(
            Pair(V53Pixel(firstPixel.x + sigma, firstPixel.y), secondPixel),
            Pair(V53Pixel(firstPixel.x - sigma, firstPixel.y), secondPixel),
            Pair(V53Pixel(firstPixel.x, firstPixel.y + sigma), secondPixel),
            Pair(V53Pixel(firstPixel.x, firstPixel.y - sigma), secondPixel),
            Pair(firstPixel, V53Pixel(secondPixel.x + sigma, secondPixel.y)),
            Pair(firstPixel, V53Pixel(secondPixel.x - sigma, secondPixel.y)),
            Pair(firstPixel, V53Pixel(secondPixel.x, secondPixel.y + sigma)),
            Pair(firstPixel, V53Pixel(secondPixel.x, secondPixel.y - sigma))
        )

        var worst = 0.0
        var usableCount = 0
        for ((left, right) in perturbations) {
            val perturbed = V53StereoTriangulator.triangulate(
                firstCalibration,
                left,
                secondCalibration,
                right,
                policy.v59Policy.v53Policy
            )
            val point = perturbed.pointWorld
            if (!perturbed.usableForFusion || point == null) {
                if (policy.requireAllPerturbationsUsable) {
                    return V60StereoUncertaintyResult(
                        base,
                        null,
                        sigma,
                        false,
                        "geometry unstable under pixel perturbation: ${perturbed.reason}"
                    )
                }
                continue
            }
            usableCount += 1
            val displacement = (point - basePoint).norm()
            if (!displacement.isFinite()) return blocked("sensitivity displacement non-finite", base, sigma)
            worst = max(worst, displacement)
        }

        if (usableCount == 0) return blocked("no usable sensitivity perturbations", base, sigma)
        if (worst > policy.maxPositionSensitivityM) {
            return V60StereoUncertaintyResult(
                base,
                worst,
                sigma,
                false,
                "stereo geometry too sensitive to pixel correspondence"
            )
        }
        return V60StereoUncertaintyResult(
            triangulation = base,
            positionSensitivityM = worst,
            assumedPixelSigmaPx = sigma,
            usableForFusion = true,
            reason = "geometry passed pixel-sensitivity gate"
        )
    }

    private fun blocked(
        reason: String,
        triangulation: V53TriangulationResult = V53TriangulationResult(
            pointWorld = null,
            rayGapM = null,
            parallaxDeg = null,
            reprojectionErrorPx = null,
            geometryScore = 0,
            usableForFusion = false,
            reason = reason
        ),
        sigma: Double? = null
    ) = V60StereoUncertaintyResult(
        triangulation = triangulation,
        positionSensitivityM = null,
        assumedPixelSigmaPx = sigma,
        usableForFusion = false,
        reason = reason
    )
}
