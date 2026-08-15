package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Produces V59-bound stereo calibration from validated camera intrinsics and repeated observations
 * of a known planar target (the putting mat is a convenient target through V23).
 *
 * This deliberately does NOT estimate camera intrinsics from a single plane. Callers must supply
 * intrinsics from a separately validated source. The producer solves only camera pose/extrinsics,
 * checks calibrated-plane geometry and reprojection residuals, then lets V59/V60 remain the final
 * runtime safety gates. No value in this file is a physical real-device accuracy claim.
 */
data class V61PlanePointM(val x: Double, val y: Double) {
    fun valid(): Boolean = x.isFinite() && y.isFinite()
}

data class V61CalibrationObservation(
    val signature: V59CaptureSignature,
    val intrinsics: V53CameraIntrinsics,
    val imagePointsPx: List<V53Pixel>,
    val worldPointsM: List<V61PlanePointM>
)

data class V61CalibrationProducerPolicy(
    val minObservationsPerCamera: Int = 3,
    val maxPoseReprojectionRmsPx: Double = 2.5,
    /** Calibrated pinhole homography columns should have nearly equal scale. */
    val maxAxisScaleRatio: Double = 1.18,
    /** Absolute cosine between the first two recovered rotation axes before orthogonalization. */
    val maxAxisOrthogonalityCos: Double = 0.16,
    val minPlaneAreaM2: Double = 0.002,
    val minStereoBaselineM: Double = 0.02
) {
    fun valid(): Boolean =
        minObservationsPerCamera > 0 &&
            maxPoseReprojectionRmsPx.isFinite() && maxPoseReprojectionRmsPx > 0.0 &&
            maxAxisScaleRatio.isFinite() && maxAxisScaleRatio >= 1.0 &&
            maxAxisOrthogonalityCos.isFinite() && maxAxisOrthogonalityCos in 0.0..1.0 &&
            minPlaneAreaM2.isFinite() && minPlaneAreaM2 > 0.0 &&
            minStereoBaselineM.isFinite() && minStereoBaselineM > 0.0
}

data class V61CameraPoseResult(
    val calibration: V53CameraCalibration?,
    val acceptedObservationCount: Int,
    val reprojectionRmsPx: Double?,
    val reason: String
) {
    val usable: Boolean get() = calibration != null
}

data class V61StereoProfileResult(
    val profile: V59StereoCalibrationProfile?,
    val first: V61CameraPoseResult,
    val second: V61CameraPoseResult,
    val reason: String
) {
    val usable: Boolean get() = profile != null
}

object V61StereoCalibrationProducer {
    fun observationFromV23(
        detection: V23MarkerlessDetection,
        signature: V59CaptureSignature,
        intrinsics: V53CameraIntrinsics
    ): V61CalibrationObservation? {
        val image = detection.fitImagePoints()
        val worldCm = detection.realPointsCm()
        if (image.size != 4 || worldCm.size != 4) return null
        return V61CalibrationObservation(
            signature = signature,
            intrinsics = intrinsics,
            imagePointsPx = image.map { V53Pixel(it.x.toDouble(), it.y.toDouble()) },
            worldPointsM = worldCm.map { V61PlanePointM(it.x / 100.0, it.y / 100.0) }
        )
    }

    fun solveCameraPose(
        observations: List<V61CalibrationObservation>,
        calibratedAtMs: Long,
        policy: V61CalibrationProducerPolicy = V61CalibrationProducerPolicy()
    ): V61CameraPoseResult {
        if (!policy.valid()) return failCamera("producer policy invalid")
        if (calibratedAtMs <= 0L) return failCamera("calibration timestamp invalid")
        if (observations.size < policy.minObservationsPerCamera) {
            return failCamera("insufficient calibration observations", observations.size)
        }

        val first = observations.first()
        if (!first.signature.valid()) return failCamera("capture signature invalid", observations.size)
        if (!first.intrinsics.valid()) return failCamera("validated camera intrinsics missing", observations.size)
        if (first.imagePointsPx.size < 4 || first.imagePointsPx.size != first.worldPointsM.size) {
            return failCamera("calibration correspondence count invalid", observations.size)
        }
        if (!validWorldPlane(first.worldPointsM, policy.minPlaneAreaM2)) {
            return failCamera("calibration plane geometry invalid", observations.size)
        }

        for (observation in observations) {
            if (!observation.signature.valid() || observation.signature.stableKey() != first.signature.stableKey()) {
                return failCamera("capture configuration changed during calibration", observations.size)
            }
            if (!sameIntrinsics(observation.intrinsics, first.intrinsics)) {
                return failCamera("camera intrinsics changed during calibration", observations.size)
            }
            if (observation.imagePointsPx.size != first.imagePointsPx.size ||
                observation.worldPointsM.size != first.worldPointsM.size ||
                !sameWorldPoints(observation.worldPointsM, first.worldPointsM)
            ) {
                return failCamera("calibration target changed during calibration", observations.size)
            }
            if (observation.imagePointsPx.any { !pixelInFrame(it, observation.signature) }) {
                return failCamera("calibration pixel outside active frame", observations.size)
            }
        }

        // Median per landmark makes repeated capture robust to a small number of detector spikes.
        val aggregatePixels = first.imagePointsPx.indices.map { index ->
            V53Pixel(
                median(observations.map { it.imagePointsPx[index].x }),
                median(observations.map { it.imagePointsPx[index].y })
            )
        }
        val normalized = aggregatePixels.map { pixel ->
            V61PlanePointM(
                (pixel.x - first.intrinsics.cx) / first.intrinsics.fx,
                (pixel.y - first.intrinsics.cy) / first.intrinsics.fy
            )
        }

        val homography = solvePlanarHomography(first.worldPointsM, normalized)
            ?: return failCamera("planar pose solve singular", observations.size)
        val c1 = V53Vec3(homography[0], homography[3], homography[6])
        val c2 = V53Vec3(homography[1], homography[4], homography[7])
        val c3 = V53Vec3(homography[2], homography[5], 1.0)
        val n1 = c1.norm()
        val n2 = c2.norm()
        if (!n1.isFinite() || !n2.isFinite() || n1 <= 1e-9 || n2 <= 1e-9) {
            return failCamera("planar pose axes invalid", observations.size)
        }
        val scaleRatio = max(n1, n2) / minOf(n1, n2)
        val axisCos = abs(c1.dot(c2) / (n1 * n2)).coerceIn(0.0, 1.0)
        if (scaleRatio > policy.maxAxisScaleRatio) {
            return failCamera("planar pose scale inconsistency too high", observations.size)
        }
        if (axisCos > policy.maxAxisOrthogonalityCos) {
            return failCamera("planar pose orthogonality residual too high", observations.size)
        }

        val baseScale = 2.0 / (n1 + n2)
        val candidates = listOf(1.0, -1.0).mapNotNull { sign ->
            buildCalibrationCandidate(
                intrinsics = first.intrinsics,
                worldPoints = first.worldPointsM,
                aggregatePixels = aggregatePixels,
                c1 = c1,
                c2 = c2,
                c3 = c3,
                scale = baseScale * sign,
                calibratedAtMs = calibratedAtMs
            )
        }
        val best = candidates.minByOrNull { it.rmsReprojectionPx }
            ?: return failCamera("camera pose is behind calibration plane", observations.size)
        if (best.rmsReprojectionPx > policy.maxPoseReprojectionRmsPx) {
            return V61CameraPoseResult(
                calibration = null,
                acceptedObservationCount = observations.size,
                reprojectionRmsPx = best.rmsReprojectionPx,
                reason = "pose reprojection error too high"
            )
        }
        return V61CameraPoseResult(
            calibration = best,
            acceptedObservationCount = observations.size,
            reprojectionRmsPx = best.rmsReprojectionPx,
            reason = "camera pose solved from validated intrinsics and planar target"
        )
    }

    fun buildStereoProfile(
        firstObservations: List<V61CalibrationObservation>,
        secondObservations: List<V61CalibrationObservation>,
        pairId: String,
        rigRevisionId: String,
        calibratedAtMs: Long,
        policy: V61CalibrationProducerPolicy = V61CalibrationProducerPolicy()
    ): V61StereoProfileResult {
        val first = solveCameraPose(firstObservations, calibratedAtMs, policy)
        val second = solveCameraPose(secondObservations, calibratedAtMs, policy)
        if (!first.usable) return V61StereoProfileResult(null, first, second, "first camera calibration rejected: ${first.reason}")
        if (!second.usable) return V61StereoProfileResult(null, first, second, "second camera calibration rejected: ${second.reason}")
        if (pairId.isBlank() || rigRevisionId.isBlank()) {
            return V61StereoProfileResult(null, first, second, "pair or rig identity missing")
        }
        val firstSignature = firstObservations.first().signature
        val secondSignature = secondObservations.first().signature
        if (firstSignature.cameraId == secondSignature.cameraId) {
            return V61StereoProfileResult(null, first, second, "stereo calibration reuses one camera")
        }
        val firstCalibration = requireNotNull(first.calibration)
        val secondCalibration = requireNotNull(second.calibration)
        val baselineM = (firstCalibration.extrinsics.originWorld - secondCalibration.extrinsics.originWorld).norm()
        if (!baselineM.isFinite() || baselineM < policy.minStereoBaselineM) {
            return V61StereoProfileResult(null, first, second, "stereo baseline too small for profile")
        }
        val profile = V59StereoCalibrationProfile(
            pairId = pairId,
            rigRevisionId = rigRevisionId,
            first = V59BoundCameraCalibration(firstSignature, firstCalibration),
            second = V59BoundCameraCalibration(secondSignature, secondCalibration),
            calibratedAtMs = calibratedAtMs,
            acceptedObservationCount = minOf(first.acceptedObservationCount, second.acceptedObservationCount)
        )
        return V61StereoProfileResult(profile, first, second, "stereo calibration profile produced; V59/V60 validation still required")
    }

    private fun buildCalibrationCandidate(
        intrinsics: V53CameraIntrinsics,
        worldPoints: List<V61PlanePointM>,
        aggregatePixels: List<V53Pixel>,
        c1: V53Vec3,
        c2: V53Vec3,
        c3: V53Vec3,
        scale: Double,
        calibratedAtMs: Long
    ): V53CameraCalibration? {
        var r1 = (c1 * scale).normalized() ?: return null
        val rawR2 = c2 * scale
        var r2 = (rawR2 - r1 * r1.dot(rawR2)).normalized() ?: return null
        var r3 = cross(r1, r2).normalized() ?: return null
        // Recompute r2 to keep a right-handed orthonormal basis after numerical cleanup.
        r2 = cross(r3, r1).normalized() ?: return null
        r3 = cross(r1, r2).normalized() ?: return null
        val t = c3 * scale

        val allInFront = worldPoints.all { p ->
            val cameraZ = r1.z * p.x + r2.z * p.y + t.z
            cameraZ.isFinite() && cameraZ > 1e-6
        }
        if (!allInFront) return null

        // R_cameraFromWorld has r1/r2/r3 as columns. V53 stores its transpose: worldFromCamera.
        val worldFromCamera = doubleArrayOf(
            r1.x, r1.y, r1.z,
            r2.x, r2.y, r2.z,
            r3.x, r3.y, r3.z
        )
        val cameraCenter = V53Vec3(
            -r1.dot(t),
            -r2.dot(t),
            -r3.dot(t)
        )
        val provisional = V53CameraCalibration(
            intrinsics = intrinsics,
            extrinsics = V53CameraExtrinsics(worldFromCamera, cameraCenter),
            rmsReprojectionPx = 0.0,
            calibratedAtMs = calibratedAtMs
        )
        if (!provisional.valid()) return null
        var squared = 0.0
        for (index in worldPoints.indices) {
            val p = worldPoints[index]
            val projected = V53StereoProjection.project(provisional, V53Vec3(p.x, p.y, 0.0)) ?: return null
            val dx = projected.x - aggregatePixels[index].x
            val dy = projected.y - aggregatePixels[index].y
            squared += dx * dx + dy * dy
        }
        val rms = sqrt(squared / worldPoints.size.coerceAtLeast(1))
        if (!rms.isFinite()) return null
        return provisional.copy(rmsReprojectionPx = rms)
    }

    /** Returns row-major 3x3 H with H[8] fixed to 1. */
    private fun solvePlanarHomography(
        world: List<V61PlanePointM>,
        normalizedImage: List<V61PlanePointM>
    ): DoubleArray? {
        if (world.size != normalizedImage.size || world.size < 4) return null
        val rows = world.size * 2
        val a = Array(rows) { DoubleArray(9) }
        var row = 0
        for (i in world.indices) {
            val X = world[i].x
            val Y = world[i].y
            val x = normalizedImage[i].x
            val y = normalizedImage[i].y
            if (!X.isFinite() || !Y.isFinite() || !x.isFinite() || !y.isFinite()) return null
            a[row][0] = X; a[row][1] = Y; a[row][2] = 1.0
            a[row][6] = -x * X; a[row][7] = -x * Y; a[row][8] = x
            row++
            a[row][3] = X; a[row][4] = Y; a[row][5] = 1.0
            a[row][6] = -y * X; a[row][7] = -y * Y; a[row][8] = y
            row++
        }
        // Four landmarks give an 8x8 exact system. More landmarks use normal equations least squares.
        val ata = Array(8) { DoubleArray(9) }
        for (r in 0 until rows) {
            for (i in 0 until 8) {
                for (j in 0 until 8) ata[i][j] += a[r][i] * a[r][j]
                ata[i][8] += a[r][i] * a[r][8]
            }
        }
        val h8 = gaussianSolve(ata) ?: return null
        return doubleArrayOf(
            h8[0], h8[1], h8[2],
            h8[3], h8[4], h8[5],
            h8[6], h8[7], 1.0
        )
    }

    private fun gaussianSolve(input: Array<DoubleArray>): DoubleArray? {
        val n = 8
        if (input.size != n || input.any { it.size != n + 1 }) return null
        val m = Array(n) { input[it].copyOf() }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (!m[pivot][col].isFinite() || abs(m[pivot][col]) < 1e-12) return null
            if (pivot != col) {
                val tmp = m[pivot]; m[pivot] = m[col]; m[col] = tmp
            }
            val divisor = m[col][col]
            for (j in col until n + 1) m[col][j] /= divisor
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                if (!factor.isFinite()) return null
                for (j in col until n + 1) m[r][j] -= factor * m[col][j]
            }
        }
        return DoubleArray(n) { m[it][n] }.takeIf { values -> values.all { it.isFinite() } }
    }

    private fun validWorldPlane(points: List<V61PlanePointM>, minArea: Double): Boolean {
        if (points.size < 4 || points.any { !it.valid() }) return false
        var twiceArea = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twiceArea += a.x * b.y - b.x * a.y
        }
        return abs(twiceArea) * 0.5 >= minArea
    }

    private fun sameWorldPoints(a: List<V61PlanePointM>, b: List<V61PlanePointM>): Boolean =
        a.size == b.size && a.indices.all { abs(a[it].x - b[it].x) < 1e-9 && abs(a[it].y - b[it].y) < 1e-9 }

    private fun sameIntrinsics(a: V53CameraIntrinsics, b: V53CameraIntrinsics): Boolean =
        a.valid() && b.valid() &&
            abs(a.fx - b.fx) < 1e-6 && abs(a.fy - b.fy) < 1e-6 &&
            abs(a.cx - b.cx) < 1e-6 && abs(a.cy - b.cy) < 1e-6

    private fun pixelInFrame(pixel: V53Pixel, signature: V59CaptureSignature): Boolean =
        pixel.valid() && pixel.x >= 0.0 && pixel.y >= 0.0 &&
            pixel.x < signature.widthPx.toDouble() && pixel.y < signature.heightPx.toDouble()

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) * 0.5
    }

    private fun cross(a: V53Vec3, b: V53Vec3) = V53Vec3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )

    private fun failCamera(reason: String, count: Int = 0) = V61CameraPoseResult(null, count, null, reason)
}