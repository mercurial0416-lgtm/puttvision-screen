package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** A normalized putter-head point around impact. y=0 is impact, -y backswing, +y follow-through. */
data class V19StrokeNode(
    val tNorm: Double,
    val xCm: Double,
    val yCm: Double,
    val faceDeg: Double?
)

data class V19StrokeStudioModel(
    val current: List<V19StrokeNode>,
    val ideal: List<V19StrokeNode>,
    val ghost: List<V19StrokeNode>,
    val corridorCm: Double,
    val measuredTrace: Boolean,
    val pathRmsCm: Double,
    val faceRmsDeg: Double?,
    val quality: Int,
    val headline: String,
    val detail: String
)

/** Captures real preview-camera head centers without changing the persisted ShotMetrics schema. */
object V19StrokeTraceRuntime {
    private val samples = ArrayList<HeadSample>(220)
    private var impactNs: Long? = null

    @Volatile var latestTrace: List<V19StrokeNode> = emptyList()
        private set
    @Volatile var latestImpactNs: Long = Long.MIN_VALUE
        private set

    @Synchronized fun begin() {
        samples.clear()
        impactNs = null
        latestTrace = emptyList()
        latestImpactNs = Long.MIN_VALUE
    }

    @Synchronized fun add(sample: HeadSample) {
        samples += sample
        while (samples.size > 220) samples.removeAt(0)
    }

    @Synchronized fun impact(tNs: Long) {
        impactNs = tNs
    }

    @Synchronized fun finish(): List<V19StrokeNode> {
        val impact = impactNs ?: return emptyList()
        latestImpactNs = impact
        val center = samples.minByOrNull { abs(it.tNs - impact) } ?: return emptyList()
        val selected = samples.filter { it.tNs in (impact - 900_000_000L)..(impact + 520_000_000L) }
        if (selected.size < 4) {
            latestTrace = emptyList()
            return latestTrace
        }
        val preSpan = max(1L, impact - selected.first().tNs).toDouble()
        val postSpan = max(1L, selected.last().tNs - impact).toDouble()
        latestTrace = selected.map { s ->
            val rawT = if (s.tNs <= impact) -((impact - s.tNs) / preSpan) else (s.tNs - impact) / postSpan
            V19StrokeNode(
                tNorm = rawT.coerceIn(-1.0, 1.0),
                xCm = (s.centerCm.x - center.centerCm.x).toDouble(),
                yCm = (s.centerCm.y - center.centerCm.y).toDouble(),
                faceDeg = faceOf(s)
            )
        }.distinctBy { (it.tNorm * 1000).toInt() }
        return latestTrace
    }

    fun matchingTrace(measuredAtNs: Long): List<V19StrokeNode> =
        if (latestImpactNs != Long.MIN_VALUE && abs(latestImpactNs - measuredAtNs) <= 300_000_000L) latestTrace else emptyList()

    private fun faceOf(sample: HeadSample): Double {
        val vx = (sample.toeCm.x - sample.heelCm.x).toDouble()
        val vy = (sample.toeCm.y - sample.heelCm.y).toDouble()
        return normalizeFaceAngle(Math.toDegrees(atan2(vy, vx)))
    }
}

object V19StrokeStudio {
    fun build(metrics: ShotMetrics, recent: List<ShotRecord>): V19StrokeStudioModel {
        val measured = V19StrokeTraceRuntime.matchingTrace(metrics.measuredAtNs)
        val current = if (measured.size >= 4) normalizeMeasured(measured) else inferred(metrics)
        val ideal = ideal(metrics)
        val ghostMetrics = recent
            .filter { it.metrics.measuredAtNs != metrics.measuredAtNs }
            .maxByOrNull { it.strokeScore.total }
            ?.metrics
        val ghost = ghostMetrics?.let(::inferred).orEmpty()

        val pathRms = rmsPath(current, ideal)
        val faceRms = rmsFace(current)
        val sampleFactor = (current.size / 26.0).coerceIn(.35, 1.0)
        val traceFactor = if (measured.size >= 4) 1.0 else .72
        val quality = (100.0 * sampleFactor * traceFactor *
            (1.0 - (pathRms / 7.0).coerceIn(0.0, .42))).toInt().coerceIn(35, 99)

        val headline = when {
            pathRms <= .55 && (faceRms ?: 0.0) <= .65 -> "스트로크 코리더 안정"
            pathRms > 1.6 -> "헤드 패스 분산 큼"
            (faceRms ?: 0.0) > 1.25 -> "페이스 회전 분산 큼"
            else -> "스트로크 미세 조정 구간"
        }
        val detail = buildString {
            append(if (measured.size >= 4) "실측 헤드 궤적" else "측정 지표 기반 궤적")
            append(" · IDEAL RMS ${"%.2f".format(pathRms)}cm")
            faceRms?.let { append(" · FACE RMS ${"%.2f".format(it)}°") }
            if (ghost.isNotEmpty()) append(" · BEST 비교 가능")
        }
        return V19StrokeStudioModel(
            current = current,
            ideal = ideal,
            ghost = ghost,
            corridorCm = corridorFor(metrics),
            measuredTrace = measured.size >= 4,
            pathRmsCm = pathRms,
            faceRmsDeg = faceRms,
            quality = quality,
            headline = headline,
            detail = detail
        )
    }

    private fun normalizeMeasured(points: List<V19StrokeNode>): List<V19StrokeNode> {
        if (points.isEmpty()) return emptyList()
        val impact = points.minByOrNull { abs(it.tNorm) } ?: points[points.size / 2]
        return points.map { V19StrokeNode(it.tNorm, it.xCm - impact.xCm, it.yCm - impact.yCm, it.faceDeg) }
    }

    /** Fallback when HFR produces metrics without preview head samples. */
    fun inferred(metrics: ShotMetrics): List<V19StrokeNode> {
        val back = metrics.backswingLengthCm?.coerceIn(7.0, 35.0) ?: 15.0
        val follow = (back * .62).coerceIn(5.0, 22.0)
        val path = Math.toRadians(metrics.pathAngleDeg ?: metrics.launchAngleDeg * .35)
        val faceAtImpact = metrics.faceAngleDeg ?: 0.0
        return (0..40).map { i ->
            val t = i / 20.0 - 1.0
            val longitudinal = if (t <= 0.0) back * t else follow * t
            val arc = sin(Math.PI * abs(t)) * (metrics.pathAngleDeg ?: 0.0).coerceIn(-3.0, 3.0) * .10
            val x = sin(path) * longitudinal + arc
            val y = cos(path) * longitudinal
            val face = faceAtImpact + t * (metrics.faceToPathDeg ?: 0.0) * .28
            V19StrokeNode(t, x, y, face)
        }
    }

    fun ideal(metrics: ShotMetrics): List<V19StrokeNode> {
        val back = metrics.backswingLengthCm?.coerceIn(7.0, 35.0) ?: 15.0
        val follow = (back * .64).coerceIn(5.0, 22.0)
        val arcAmp = (back * .025).coerceIn(.18, .75)
        return (0..40).map { i ->
            val t = i / 20.0 - 1.0
            val y = if (t <= 0.0) back * t else follow * t
            val x = -arcAmp * sin(Math.PI * abs(t))
            V19StrokeNode(t, x, y, 0.0)
        }
    }

    private fun corridorFor(metrics: ShotMetrics): Double {
        val distanceFactor = ((metrics.backswingLengthCm ?: 15.0) / 15.0).coerceIn(.7, 1.5)
        return (.55 * distanceFactor).coerceIn(.42, .85)
    }

    private fun rmsPath(current: List<V19StrokeNode>, ideal: List<V19StrokeNode>): Double {
        if (current.isEmpty() || ideal.isEmpty()) return 9.9
        var sum = 0.0
        var n = 0
        current.forEach { p ->
            val q = ideal.minByOrNull { abs(it.tNorm - p.tNorm) } ?: return@forEach
            val d = hypot(p.xCm - q.xCm, (p.yCm - q.yCm) * .08)
            sum += d * d
            n++
        }
        return if (n == 0) 9.9 else kotlin.math.sqrt(sum / n)
    }

    private fun rmsFace(current: List<V19StrokeNode>): Double? {
        val values = current.mapNotNull { it.faceDeg }
        if (values.size < 3) return null
        return kotlin.math.sqrt(values.sumOf { it * it } / values.size)
    }
}

object V19StrokeStudioRuntime {
    @Volatile var latest: V19StrokeStudioModel? = null
        private set

    fun update(metrics: ShotMetrics, recent: List<ShotRecord>) {
        latest = V19StrokeStudio.build(metrics, recent)
    }

    fun clear() { latest = null }
}
