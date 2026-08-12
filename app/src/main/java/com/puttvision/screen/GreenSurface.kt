package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * V12 single source of truth for practice-green geometry.
 *
 * heightAt() returns physical elevation in metres. Local slopes are derived from
 * this scalar surface, so rendering, green reading and ball physics cannot drift
 * into mutually impossible slope fields.
 */
object GreenSurface {
    private const val GRADIENT_EPS_M = 0.015

    fun heightAt(profileId: Int, x: Double, y: Double, holeDistanceM: Double): Double {
        if (profileId < 0) return 0.0
        val d = holeDistanceM.coerceAtLeast(2.0)
        val lateral = (d * 0.24).coerceAtLeast(0.75)
        val t = (y / d).coerceIn(-0.25, 1.50)
        val u = (x / lateral).coerceIn(-2.0, 2.0)
        val p = profileId.coerceIn(0, 23)

        fun sidePlane(sidePct: Double): Double = -0.01 * sidePct * x
        fun longPlane(longPct: Double): Double = -0.01 * longPct * y
        fun bowl(ampM: Double, cx: Double = 0.0, cy: Double = 0.56, wx: Double = 1.0, wy: Double = 1.0): Double {
            val dx = (u - cx) / wx.coerceAtLeast(.2)
            val dy = (t - cy) / wy.coerceAtLeast(.2)
            return ampM * (dx * dx + dy * dy)
        }
        fun crown(ampM: Double, cx: Double = 0.0, cy: Double = 0.50, wx: Double = 1.0, wy: Double = 1.0): Double =
            -bowl(ampM, cx, cy, wx, wy)
        fun gaussianRidge(ampM: Double, centerU: Double, width: Double): Double {
            val z = (u - centerU) / width.coerceAtLeast(.15)
            return ampM * exp(-z * z)
        }

        return when (p) {
            // EASY
            0 -> 0.0008 * sin(t * PI * 2.0) + 0.0004 * cos(u * PI)
            1 -> sidePlane(0.34 + 0.16 * sin(t * PI)) + 0.0005 * sin(t * PI * 2.0)
            2 -> sidePlane(-0.34 - 0.16 * sin(t * PI)) + 0.0005 * sin(t * PI * 2.0)
            3 -> longPlane(-0.30) + 0.0008 * sin(t * PI * 2.0) + 0.00035 * sin(u * PI)
            4 -> longPlane(0.30) + 0.0008 * sin(t * PI * 2.0) - 0.00035 * sin(u * PI)
            5 -> bowl(0.0036, cy = 0.58, wx = 1.15, wy = 0.72) + 0.0004 * sin(t * PI * 2.0)

            // STANDARD
            6 -> sidePlane(0.64 + 0.26 * sin((t - .08) * PI)) + 0.0008 * sin(t * PI * 2.0)
            7 -> sidePlane(-0.64 - 0.26 * sin((t - .08) * PI)) + 0.0008 * sin(t * PI * 2.0)
            8 -> sidePlane(0.56 + 0.22 * sin(t * PI)) + longPlane(-0.52) + 0.0010 * sin(t * PI * 2.0)
            9 -> sidePlane(-0.56 - 0.22 * sin(t * PI)) + longPlane(-0.52) + 0.0010 * sin(t * PI * 2.0)
            10 -> sidePlane(0.58 + 0.20 * sin(t * PI * 1.4)) + longPlane(0.52) + 0.0010 * sin(t * PI * 2.0)
            11 -> sidePlane(-0.58 - 0.20 * sin(t * PI * 1.4)) + longPlane(0.52) + 0.0010 * sin(t * PI * 2.0)

            // ADVANCED
            12 -> sidePlane(0.88 * sin((t - .10) * PI * 2.0)) + 0.0021 * cos(t * PI * 2.0) + 0.0005 * u * t
            13 -> sidePlane(-0.88 * sin((t - .10) * PI * 2.0)) + 0.0021 * cos(t * PI * 2.0) - 0.0005 * u * t
            14 -> crown(0.0060, cy = 0.48, wx = 1.10, wy = 0.78) + 0.0007 * sin(t * PI * 2.0)
            15 -> bowl(0.0072, cy = 0.56, wx = 1.05, wy = 0.72) + 0.0008 * sin(t * PI * 2.0)
            16 -> sidePlane(0.14 + 1.00 * t * t) + longPlane(-0.12) + 0.0007 * cos(t * PI * 2.0)
            17 -> sidePlane(-0.14 - 1.00 * t * t) + longPlane(-0.12) + 0.0007 * cos(t * PI * 2.0)

            // EXPERT
            18 -> gaussianRidge(0.0062, 0.18 * sin(t * PI * 3.0), 0.38) + longPlane(-0.20) + 0.0014 * cos(t * PI * 2.0)
            19 -> bowl(0.0042, cx = 0.42 * sin((t + .08) * PI * 2.6), cy = t, wx = .48, wy = 8.0) + 0.0018 * cos(t * PI * 3.0)
            20 -> sidePlane(1.18 + 0.38 * sin((t - .08) * PI)) + longPlane(-1.02) + 0.0018 * cos(t * PI * 2.0)
            21 -> sidePlane(-1.18 - 0.38 * sin((t - .08) * PI)) + longPlane(1.02) + 0.0018 * cos(t * PI * 2.0)
            22 -> crown(0.0064, cy = .46, wx = .90, wy = .82) + sidePlane(0.64 * sin(t * PI * 2.0)) + 0.0010 * sin((u + t) * PI)
            else -> sidePlane(-0.48 + 1.12 * sin(t * PI * 2.2)) + longPlane(-0.42) +
                0.0030 * cos(t * PI * 2.0) + 0.0014 * sin((u * .8 + t * 1.4) * PI)
        }
    }

    /** Downhill-positive slope convention used by GreenPhysics. */
    fun slopeAt(profileId: Int, x: Double, y: Double, holeDistanceM: Double): TerrainSlope {
        if (profileId < 0) return TerrainSlope(0.0, 0.0)
        val e = GRADIENT_EPS_M
        val dx = (heightAt(profileId, x + e, y, holeDistanceM) - heightAt(profileId, x - e, y, holeDistanceM)) / (2.0 * e)
        val dy = (heightAt(profileId, x, y + e, holeDistanceM) - heightAt(profileId, x, y - e, holeDistanceM)) / (2.0 * e)
        return TerrainSlope(
            sidePct = (-100.0 * dx).coerceIn(-4.5, 4.5),
            longPct = (-100.0 * dy).coerceIn(-4.5, 4.5)
        )
    }
}
