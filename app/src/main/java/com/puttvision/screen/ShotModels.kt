package com.puttvision.screen

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.hypot

data class HeadSample(
    val centerCm: PointF,
    val heelCm: PointF,
    val toeCm: PointF,
    val tNs: Long
)

data class BallSample(
    val pCm: PointF,
    val tNs: Long
)

data class ShotMetrics(
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val headSpeedMps: Double?,
    val faceAngleDeg: Double?,
    val pathAngleDeg: Double?,
    val faceToPathDeg: Double?,
    val smash: Double?,
    val impactOffsetMm: Double?,
    val measuredAtNs: Long,

    val backswingMs: Double? = null,
    val downswingMs: Double? = null,
    val tempoRatio: Double? = null,
    val backswingLengthCm: Double? = null,
    val peakHeadAccelerationMps2: Double? = null,

    val rawBallSpeedMps: Double? = null,
    val estimatedMatDecelMps2: Double? = null,
    val estimatedMatStimpM: Double? = null,

    val confidence: Double? = null,

    // V14 optional marked-ball skid/roll channel. Null when no usable dot/line is visible.
    val roll: BallRollMetrics? = null,

    val uncertainty: MeasurementUncertainty? = null
)

class ShotTracker {
    private val balls = ArrayList<BallSample>(64)
    private val heads = ArrayList<HeadSample>(128)
    private var origin: PointF? = null
    private var impactNs: Long? = null
    private var armed = false
    private var finalized = false

    fun arm() {
        balls.clear()
        heads.clear()
        origin = null
        impactNs = null
        armed = true
        finalized = false
    }

    fun cancel() { armed = false }
    fun isArmed(): Boolean = armed
    fun hasImpact(): Boolean = impactNs != null

    fun addHead(sample: HeadSample) {
        if (!armed || finalized) return
        heads += sample
        while (heads.size > 180) heads.removeAt(0)
    }

    fun addBall(sample: BallSample) {
        if (!armed || finalized) return
        if (origin == null) {
            origin = PointF(sample.pCm.x, sample.pCm.y)
            balls += sample
            return
        }
        val last = balls.lastOrNull()
        if (last != null) {
            val step = hypot(
                (sample.pCm.x - last.pCm.x).toDouble(),
                (sample.pCm.y - last.pCm.y).toDouble()
            )
            if (step > 30.0) return
            if (step < 0.15 && impactNs != null) return
        }
        balls += sample
        if (impactNs == null) {
            val o = origin!!
            val d = hypot(
                (sample.pCm.x - o.x).toDouble(),
                (sample.pCm.y - o.y).toDouble()
            )
            if (d >= 1.2) impactNs = sample.tNs
        }
    }

    fun maybeFinalize(): ShotMetrics? {
        if (!armed || finalized || impactNs == null || balls.size < 4) return null
        val o = origin ?: return null
        val distance = hypot(
            (balls.last().pCm.x - o.x).toDouble(),
            (balls.last().pCm.y - o.y).toDouble()
        )
        if (distance < 22.0) return null
        val result = calculate()
        if (result != null) {
            finalized = true
            armed = false
        }
        return result
    }

    private fun calculate(): ShotMetrics? {
        val tImpact = impactNs ?: return null
        val start = balls.firstOrNull() ?: return null
        val earlyPoints = balls.mapNotNull { s ->
            val d = hypot(
                (s.pCm.x - start.pCm.x).toDouble(),
                (s.pCm.y - start.pCm.y).toDouble()
            )
            if (d in .4..20.0 && s.tNs >= start.tNs) {
                V14TimedPoint(
                    (s.tNs - start.tNs) / 1_000_000_000.0,
                    (s.pCm.x - start.pCm.x).toDouble(),
                    (s.pCm.y - start.pCm.y).toDouble()
                )
            } else null
        }.take(18)
        val fit = V14RobustKinematics.fit(earlyPoints)
        val fallback = balls.firstOrNull {
            hypot(
                (it.pCm.x - start.pCm.x).toDouble(),
                (it.pCm.y - start.pCm.y).toDouble()
            ) >= 12.0
        } ?: balls.getOrNull(minOf(3, balls.lastIndex)) ?: return null

        val ballSpeed: Double
        val launch: Double
        if (fit != null) {
            ballSpeed = fit.speedMps
            launch = fit.launchAngleDeg
        } else {
            val dt = (fallback.tNs - start.tNs) / 1_000_000_000.0
            if (dt <= 0.0) return null
            val dx = (fallback.pCm.x - start.pCm.x).toDouble()
            val dy = (fallback.pCm.y - start.pCm.y).toDouble()
            ballSpeed = (hypot(dx, dy) / 100.0) / dt
            launch = Math.toDegrees(atan2(dx, dy))
        }

        val pre = heads.filter { it.tNs <= tImpact }.takeLast(8)
        val impactHead = heads.minByOrNull { kotlin.math.abs(it.tNs - tImpact) }
        var headSpeed: Double? = null
        var path: Double? = null
        if (pre.size >= 2) {
            val a = pre.first()
            val b = pre.last()
            val hdt = (b.tNs - a.tNs) / 1_000_000_000.0
            if (hdt > 0.0) {
                val hdx = (b.centerCm.x - a.centerCm.x).toDouble()
                val hdy = (b.centerCm.y - a.centerCm.y).toDouble()
                headSpeed = (hypot(hdx, hdy) / 100.0) / hdt
                path = Math.toDegrees(atan2(hdx, hdy))
            }
        }
        val face = impactHead?.let {
            val vx = (it.toeCm.x - it.heelCm.x).toDouble()
            val vy = (it.toeCm.y - it.heelCm.y).toDouble()
            normalizeFaceAngle(Math.toDegrees(atan2(vy, vx)))
        }
        val faceToPath = if (face != null && path != null) face - path else null
        val smash = if (headSpeed != null && headSpeed > 0.05) ballSpeed / headSpeed else null
        val impactOffset = impactHead?.let { (start.pCm.x - it.centerCm.x) * 10.0 }
        val confidence = if (fit != null) {
            (0.48 + (fit.sampleCount.coerceAtMost(12) / 12.0) * .22 - (fit.rmsCm / 2.5).coerceAtMost(.12)).coerceIn(.40, .72)
        } else .42
        return ShotMetrics(
            ballSpeedMps = ballSpeed,
            launchAngleDeg = launch,
            headSpeedMps = headSpeed,
            faceAngleDeg = face,
            pathAngleDeg = path,
            faceToPathDeg = faceToPath,
            smash = smash,
            impactOffsetMm = impactOffset,
            measuredAtNs = tImpact,
            rawBallSpeedMps = ballSpeed,
            confidence = confidence,
            uncertainty = MeasurementUncertaintyEstimator.forNormal(
                ballSpeedMps = ballSpeed,
                headSpeedMps = headSpeed,
                faceAngleDeg = face,
                pathAngleDeg = path,
                impactOffsetMm = impactOffset,
                confidence = confidence
            )
        )
    }
}
