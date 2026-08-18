package com.puttvision.screen

import kotlin.math.sqrt

/** Converts the authoritative V135 ball quaternion/COM state into a Filament column-major matrix. */
object V136BallPose {
    fun matrix(state: SimState?, x: Double, y: Double, fallbackZ: Double): FloatArray {
        val z = state?.ballCenterZM?.takeIf { it.isFinite() } ?: fallbackZ
        var w = state?.orientationW?.takeIf { it.isFinite() } ?: 1.0
        var qx = state?.orientationX?.takeIf { it.isFinite() } ?: 0.0
        var qy = state?.orientationY?.takeIf { it.isFinite() } ?: 0.0
        var qz = state?.orientationZ?.takeIf { it.isFinite() } ?: 0.0
        val mag = sqrt(w * w + qx * qx + qy * qy + qz * qz)
        if (!mag.isFinite() || mag < 1e-10) {
            w = 1.0; qx = 0.0; qy = 0.0; qz = 0.0
        } else {
            w /= mag; qx /= mag; qy /= mag; qz /= mag
        }

        val xx = qx * qx
        val yy = qy * qy
        val zz = qz * qz
        val xy = qx * qy
        val xz = qx * qz
        val yz = qy * qz
        val wx = w * qx
        val wy = w * qy
        val wz = w * qz

        // Filament's TransformManager consumes a column-major 4x4 transform.
        return floatArrayOf(
            (1.0 - 2.0 * (yy + zz)).toFloat(),
            (2.0 * (xy + wz)).toFloat(),
            (2.0 * (xz - wy)).toFloat(),
            0f,
            (2.0 * (xy - wz)).toFloat(),
            (1.0 - 2.0 * (xx + zz)).toFloat(),
            (2.0 * (yz + wx)).toFloat(),
            0f,
            (2.0 * (xz + wy)).toFloat(),
            (2.0 * (yz - wx)).toFloat(),
            (1.0 - 2.0 * (xx + yy)).toFloat(),
            0f,
            x.toFloat(), y.toFloat(), (z + 0.020).toFloat(), 1f
        )
    }
}
