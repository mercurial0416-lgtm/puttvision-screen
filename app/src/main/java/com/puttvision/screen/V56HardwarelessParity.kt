package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shared presentation model used to make the no-hardware lab expose the same Green Read facts
 * as the real camera/TV product. It never invents a second physics answer: every value comes from
 * the existing GreenRead result that production uses.
 */
data class V56GreenReadPresentation(
    val aimText: String,
    val paceText: String,
    val recommendedBallSpeedMps: Double,
    val targetCupSpeedMps: Double,
    val cupCount: Double,
    val trail: List<Pair<Double, Double>>,
    val apexIndex: Int?
)

object V56GreenReadPresentationBuilder {
    private const val MIN_VISIBLE_APEX_M = 0.005

    fun from(
        read: GreenRead,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): V56GreenReadPresentation {
        val cups = read.cupCount.coerceAtLeast(0.0)
        val aim = if (cups < 0.05 || read.aimSideLabel == "센터") {
            "센터"
        } else {
            "${read.aimSideLabel} ${"%.1f".format(cups)}컵"
        }
        return V56GreenReadPresentation(
            aimText = aim,
            paceText = read.paceHint,
            recommendedBallSpeedMps = read.recommendedBallSpeedMps,
            targetCupSpeedMps = targetCupSpeedMps,
            cupCount = cups,
            trail = read.predictedTrail.toList(),
            apexIndex = apexIndex(read.predictedTrail)
        )
    }

    fun apexIndex(points: List<Pair<Double, Double>>): Int? {
        if (points.size < 3) return null
        val first = points.first()
        val last = points.last()
        val dx = last.first - first.first
        val dy = last.second - first.second
        val length = hypot(dx, dy)
        if (!length.isFinite() || length <= 1e-9) return null

        var bestIndex = -1
        var bestDistance = 0.0
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            if (!p.first.isFinite() || !p.second.isFinite()) continue
            val distance = abs(dy * (p.first - first.first) - dx * (p.second - first.second)) / length
            if (distance > bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
        return bestIndex.takeIf { it >= 0 && bestDistance >= MIN_VISIBLE_APEX_M }
    }
}

/**
 * Short-lived bridge for the no-hardware replay UI.
 *
 * Every synthetic shot now proves both halves of stereo safety: V68 must reconstruct known truth
 * through the production path, and V69 must reject intentionally corrupted capture bindings.
 */
object V56HardwarelessParityRuntime {
    @Volatile private var latest: V56GreenReadPresentation? = null

    fun publish(read: GreenRead?) {
        if (read == null) {
            latest = null
            V68HardwarelessStereoRuntime.clear()
            V69HardwarelessStereoGuardRuntime.clear()
            return
        }
        val base = V56GreenReadPresentationBuilder.from(read)
        val stereo = V68HardwarelessStereoRuntime.run(
            read.recommendedBallSpeedMps,
            read.recommendedLaunchAngleDeg
        )
        val guards = V69HardwarelessStereoGuardRuntime.run()
        latest = base.copy(
            paceText = "${base.paceText} · ${stereo.shortLabel()} · ${guards.shortLabel()}"
        )
    }

    fun snapshot(): V56GreenReadPresentation? = latest

    fun clear() {
        latest = null
        V68HardwarelessStereoRuntime.clear()
        V69HardwarelessStereoGuardRuntime.clear()
    }
}
