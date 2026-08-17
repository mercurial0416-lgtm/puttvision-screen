package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.max

/** Pure, allocation-light guards for the V124 SOLO screen-golf HUD. */
object V125ScreenGolfHudSafety {
    const val MIN_TARGET_M = 0.20
    const val MAX_TARGET_M = 33.0
    const val MAX_STIMP_M = 20.0
    const val MAX_SLOPE_PCT = 20.0
    const val MAX_BALL_SPEED_MPS = 8.0
    const val MAX_REMAIN_M = 50.0
    const val MAX_TRAIL_POINTS = 24

    fun targetM(raw: Double): Double =
        raw.takeIf { it.isFinite() }?.coerceIn(MIN_TARGET_M, MAX_TARGET_M) ?: MIN_TARGET_M

    fun stimpM(raw: Double): Double =
        raw.takeIf { it.isFinite() }?.coerceIn(0.0, MAX_STIMP_M) ?: 0.0

    fun slopePct(raw: Double): Double =
        raw.takeIf { it.isFinite() }?.coerceIn(-MAX_SLOPE_PCT, MAX_SLOPE_PCT) ?: 0.0

    fun normalizedProgress(yM: Double, targetM: Double): Double {
        val target = targetM(targetM)
        return (yM / target).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
    }

    fun normalizedLateral(xM: Double, halfWidthM: Double = 2.5): Double {
        val width = halfWidthM.takeIf { it.isFinite() && it > 0.05 } ?: 2.5
        return (xM / width).takeIf { it.isFinite() }?.coerceIn(-1.0, 1.0) ?: 0.0
    }

    fun liveRemainingM(ballX: Double, ballY: Double, targetRawM: Double): Double {
        if (!ballX.isFinite() || !ballY.isFinite()) return 0.0
        val target = targetM(targetRawM)
        return hypot(ballX, target - ballY).takeIf { it.isFinite() }?.coerceIn(0.0, MAX_REMAIN_M) ?: 0.0
    }

    fun ballSpeedMps(vx: Double, vy: Double): Double =
        if (!vx.isFinite() || !vy.isFinite()) 0.0
        else hypot(vx, vy).takeIf { it.isFinite() }?.coerceIn(0.0, MAX_BALL_SPEED_MPS) ?: 0.0

    fun resultDistanceCm(distanceM: Double): Double =
        distanceM.takeIf { it.isFinite() && it >= 0.0 }
            ?.coerceAtMost(MAX_REMAIN_M)
            ?.times(100.0)
            ?: 0.0

    /**
     * Returns at most [MAX_TRAIL_POINTS] valid finite points while preserving the first and last
     * valid endpoints. Malformed points are discarded before they can reach Canvas/Path APIs.
     */
    fun trailSamples(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val valid = points.filter { (x, y) ->
            x.isFinite() && y.isFinite() && x in -50.0..50.0 && y in -5.0..MAX_TARGET_M + 5.0
        }
        if (valid.size <= MAX_TRAIL_POINTS) return valid
        val last = valid.lastIndex
        val out = ArrayList<Pair<Double, Double>>(MAX_TRAIL_POINTS)
        var previousIndex = -1
        for (slot in 0 until MAX_TRAIL_POINTS) {
            val index = ((slot.toDouble() * last) / (MAX_TRAIL_POINTS - 1)).toInt().coerceIn(0, last)
            if (index != previousIndex) {
                out += valid[index]
                previousIndex = index
            }
        }
        if (out.lastOrNull() != valid.last()) {
            if (out.size >= MAX_TRAIL_POINTS) out[out.lastIndex] = valid.last() else out += valid.last()
        }
        return out
    }

    fun qualityCacheRefreshDue(nowMs: Long, lastSampleMs: Long, intervalMs: Long = 900L): Boolean {
        val interval = max(100L, intervalMs)
        if (lastSampleMs <= 0L) return true
        val elapsed = nowMs - lastSampleMs
        return elapsed < 0L || elapsed >= interval
    }
}
