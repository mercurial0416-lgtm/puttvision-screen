package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.roundToInt

data class StrokeScore(
    val total: Int,
    val face: Int,
    val path: Int,
    val tempo: Int,
    val impact: Int,
    val distance: Int,
    val consistency: Int
)

object StrokeScorer {

    fun score(
        metrics: ShotMetrics,
        result: SimResult?,
        recent: List<ShotRecord>
    ): StrokeScore {
        fun linear(error: Double, perfect: Double, zero: Double): Int {
            if (error <= perfect) return 100
            if (error >= zero) return 0
            val x = 1.0 - (error - perfect) / (zero - perfect)
            return (x * 100.0).roundToInt().coerceIn(0, 100)
        }

        val face = metrics.faceAngleDeg?.let {
            linear(abs(it), 0.15, 3.0)
        } ?: linear(abs(metrics.launchAngleDeg), 0.20, 3.5)

        val path = metrics.pathAngleDeg?.let {
            linear(abs(it), 0.25, 4.0)
        } ?: linear(abs(metrics.launchAngleDeg), 0.25, 4.0)

        val tempo = metrics.tempoRatio?.let {
            // This is a repeatability-oriented target for this app, not a claim
            // that one universal tempo ratio fits every golfer.
            linear(abs(it - 2.0), 0.12, 1.15)
        } ?: 65

        val impact = metrics.impactOffsetMm?.let {
            linear(abs(it), 2.0, 18.0)
        } ?: 65

        val distance = result?.let {
            linear(it.distanceToCupM * 100.0, 7.0, 120.0)
        } ?: 70

        val consistency =
            if (recent.size < 3) 70
            else {
                val launches = recent.takeLast(10).map { it.metrics.launchAngleDeg }
                val mean = launches.average()
                val mad = launches.map { abs(it - mean) }.average()
                linear(mad, 0.10, 2.0)
            }

        val weighted =
            face * 0.23 +
            path * 0.18 +
            tempo * 0.14 +
            impact * 0.14 +
            distance * 0.20 +
            consistency * 0.11

        return StrokeScore(
            total = weighted.roundToInt().coerceIn(0, 100),
            face = face,
            path = path,
            tempo = tempo,
            impact = impact,
            distance = distance,
            consistency = consistency
        )
    }
}
