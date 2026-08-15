package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calibration-gated stereo geometry foundation.
 *
 * This code deliberately does not consume the current planar HFR cm tracks: true stereo needs
 * per-camera image correspondences plus intrinsic/extrinsic calibration. Until those inputs exist,
 * callers must keep using V44 as TRACK READY only and must not claim calibrated 3D accuracy.
 */
data class V53Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: V53Vec3) = V53Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: V53Vec3) = V53Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double) = V53Vec3(x * scale, y * scale, z * scale)
    fun dot(other: V53Vec3): Double = x * other.x + y * other.y + z * other.z
    fun norm(): Double = sqrt(dot(this))
    fun normalized(): V53Vec3? {
        val n = norm()
        if (!n.isFinite() || n <= 1e-12) return null
        return this * (1.0 / n)
    }
    fun finite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
}

data class V53CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double
) {
    fun valid(): Boolean = fx.isFinite() && fy.isFinite() && cx.isFinite() && cy.isFinite() && fx > 1e-6 && fy > 1e-6
}

/** Row-major rotation mapping camera-space vectors into world-space. originWorld is camera center. */
data class V53CameraExtrinsics(
    val rotationWorldFromCamera: DoubleArray,
    val originWorld: V53Vec3
) {
    fun valid(): Boolean {
        val r = rotationWorldFromCamera
        if (r.size != 9 || r.any { !it.isFinite() } || !originWorld.finite()) return false
        val row0 = V53Vec3(r[0], r[1], r[2])
        val row1 = V53Vec3(r[3], r[4], r[5])
        val row2 = V53Vec3(r[6], r[7], r[8])
        val rows = listOf(row0, row1, row2)
        if (rows.any { abs(it.norm() - 1.0) > 0.08 }) return false
        if (abs(row0.dot(row1)) > 0.08 || abs(row0.dot(row2)) > 0.08 || abs(row1.dot(row2)) > 0.08) return false
        val det =
            r[0] * (r[4] * r[8] - r[5] * r[7]) -
                r[1] * (r[3] * r[8] - r[5] * r[6]) +
                r[2] * (r[3] * r[7] - r[4] * r[6])
        return det.isFinite() && det > 0.75 && det < 1.25
    }

    fun rotateCameraToWorld(v: V53Vec3): V53Vec3? {
        if (!valid() || !v.finite()) return null
        val r = rotationWorldFromCamera
        return V53Vec3(
            r[0] * v.x + r[1] * v.y + r[2] * v.z,
            r[3] * v.x + r[4] * v.y + r[5] * v.z,
            r[6] * v.x + r[7] * v.y + r[8] * v.z
        )
    }
}

data class V53CameraCalibration(
    val intrinsics: V53CameraIntrinsics,
    val extrinsics: V53CameraExtrinsics,
    val rmsReprojectionPx: Double,
    val calibratedAtMs: Long = 0L
) {
    fun valid(): Boolean =
        intrinsics.valid() && extrinsics.valid() && rmsReprojectionPx.isFinite() && rmsReprojectionPx >= 0.0
}

data class V53Pixel(val x: Double, val y: Double) {
    fun valid(): Boolean = x.isFinite() && y.isFinite()
}

data class V53Ray(val origin: V53Vec3, val direction: V53Vec3)

data class V53TriangulationPolicy(
    val minParallaxDeg: Double = 1.0,
    val maxRayGapM: Double = 0.020,
    val maxCalibrationRmsPx: Double = 2.5
) {
    fun valid(): Boolean = minParallaxDeg.isFinite() && minParallaxDeg > 0.0 &&
        maxRayGapM.isFinite() && maxRayGapM > 0.0 &&
        maxCalibrationRmsPx.isFinite() && maxCalibrationRmsPx > 0.0
}

data class V53TriangulationResult(
    val pointWorld: V53Vec3?,
    val rayGapM: Double?,
    val parallaxDeg: Double?,
    /** Geometry-only quality score. This is not a real-device measurement-accuracy percentage. */
    val geometryScore: Int,
    val usableForFusion: Boolean,
    val reason: String
)

object V53StereoTriangulator {
    fun ray(calibration: V53CameraCalibration, pixel: V53Pixel): V53Ray? {
        if (!calibration.valid() || !pixel.valid()) return null
        val k = calibration.intrinsics
        val cameraDirection = V53Vec3(
            (pixel.x - k.cx) / k.fx,
            (pixel.y - k.cy) / k.fy,
            1.0
        ).normalized() ?: return null
        val worldDirection = calibration.extrinsics.rotateCameraToWorld(cameraDirection)?.normalized() ?: return null
        return V53Ray(calibration.extrinsics.originWorld, worldDirection)
    }

    fun triangulate(
        firstCalibration: V53CameraCalibration,
        firstPixel: V53Pixel,
        secondCalibration: V53CameraCalibration,
        secondPixel: V53Pixel,
        policy: V53TriangulationPolicy = V53TriangulationPolicy()
    ): V53TriangulationResult {
        if (!policy.valid()) return failure("triangulation policy invalid")
        if (!firstCalibration.valid() || !secondCalibration.valid()) return failure("camera calibration missing or invalid")
        if (firstCalibration.rmsReprojectionPx > policy.maxCalibrationRmsPx ||
            secondCalibration.rmsReprojectionPx > policy.maxCalibrationRmsPx
        ) return failure("calibration reprojection error too high")

        val first = ray(firstCalibration, firstPixel) ?: return failure("first camera ray invalid")
        val second = ray(secondCalibration, secondPixel) ?: return failure("second camera ray invalid")

        val d1 = first.direction
        val d2 = second.direction
        val w0 = first.origin - second.origin
        val a = d1.dot(d1)
        val b = d1.dot(d2)
        val c = d2.dot(d2)
        val d = d1.dot(w0)
        val e = d2.dot(w0)
        val denominator = a * c - b * b
        if (!denominator.isFinite() || abs(denominator) < 1e-8) return failure("camera rays nearly parallel")

        val s = (b * e - c * d) / denominator
        val t = (a * e - b * d) / denominator
        if (!s.isFinite() || !t.isFinite() || s <= 0.0 || t <= 0.0) return failure("triangulated point behind camera")

        val p1 = first.origin + d1 * s
        val p2 = second.origin + d2 * t
        val midpoint = (p1 + p2) * 0.5
        if (!midpoint.finite()) return failure("triangulated point non-finite")

        val gap = (p1 - p2).norm()
        val dot = d1.dot(d2).coerceIn(-1.0, 1.0)
        val parallax = Math.toDegrees(acos(dot))
        if (!gap.isFinite() || !parallax.isFinite()) return failure("triangulation geometry non-finite")

        val parallaxFactor = ((parallax - policy.minParallaxDeg) / 8.0).coerceIn(0.0, 1.0)
        val gapFactor = (1.0 - gap / policy.maxRayGapM).coerceIn(0.0, 1.0)
        val worstRms = max(firstCalibration.rmsReprojectionPx, secondCalibration.rmsReprojectionPx)
        val calibrationFactor = (1.0 - worstRms / policy.maxCalibrationRmsPx).coerceIn(0.0, 1.0)
        val score = (100.0 * (0.45 * gapFactor + 0.35 * parallaxFactor + 0.20 * calibrationFactor))
            .toInt().coerceIn(0, 100)

        val reason = when {
            parallax < policy.minParallaxDeg -> "parallax too small"
            gap > policy.maxRayGapM -> "ray agreement too weak"
            else -> "geometry ready for downstream validation"
        }
        return V53TriangulationResult(
            pointWorld = midpoint,
            rayGapM = gap,
            parallaxDeg = parallax,
            geometryScore = score,
            usableForFusion = reason.startsWith("geometry ready"),
            reason = reason
        )
    }

    private fun failure(reason: String) = V53TriangulationResult(
        pointWorld = null,
        rayGapM = null,
        parallaxDeg = null,
        geometryScore = 0,
        usableForFusion = false,
        reason = reason
    )
}

/** Test/helper projection using the same world-from-camera convention as the triangulator. */
object V53StereoProjection {
    fun project(calibration: V53CameraCalibration, pointWorld: V53Vec3): V53Pixel? {
        if (!calibration.valid() || !pointWorld.finite()) return null
        val r = calibration.extrinsics.rotationWorldFromCamera
        val delta = pointWorld - calibration.extrinsics.originWorld
        // cameraFromWorld uses R^T because R is an orthonormal worldFromCamera rotation.
        val camera = V53Vec3(
            r[0] * delta.x + r[3] * delta.y + r[6] * delta.z,
            r[1] * delta.x + r[4] * delta.y + r[7] * delta.z,
            r[2] * delta.x + r[5] * delta.y + r[8] * delta.z
        )
        if (!camera.finite() || camera.z <= 1e-9) return null
        val k = calibration.intrinsics
        return V53Pixel(
            k.fx * camera.x / camera.z + k.cx,
            k.fy * camera.y / camera.z + k.cy
        )
    }
}
