package com.puttvision.screen

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Metrics are fused independently because each camera view has different observability. */
enum class V37Feature { BALL_SPEED, START_LINE, HEAD_SPEED, FACE, PATH, IMPACT, BACKSWING, DOWNSWING, TEMPO, BACKSWING_LENGTH, ACCELERATION }

data class V37FusionDiagnostics(
    val companionCount: Int,
    val acceptedFeatures: Int,
    val rejectedOutliers: Int,
    val freshestAgeMs: Long?,
    val confidenceBefore: Double,
    val confidenceAfter: Double,
    val activeViews: String = "-",
    val droppedPackets: Int = 0,
    val diversityScore: Int = 0
) {
    val label: String get() = if (companionCount == 0) {
        "단일폰${if (droppedPackets > 0) " · drop $droppedPackets" else ""}"
    } else {
        "${companionCount + 1}폰 · $activeViews · feature $acceptedFeatures · outlier $rejectedOutliers · drop $droppedPackets"
    }
}

private data class V37Candidate(val measurement: V15CameraMeasurement, val value: Double, val weight: Double)
private data class V37FusedValue(val value: Double?, val contributors: Int, val rejected: Int)

object V37FeatureFusion {
    private const val maxAgeMs = V49FusionPolicy.MAX_AGE_MS
    private val emptyDiagnostics = V37FusionDiagnostics(0, 0, 0, null, .0, .0)

    @Volatile var diagnostics = emptyDiagnostics
        private set

    internal fun resetDiagnostics() { diagnostics = emptyDiagnostics }

    fun fuse(measurementsRaw: List<V15CameraMeasurement>, nowMs: Long = System.currentTimeMillis()): ShotMetrics? {
        val selection = V49FusionPolicy.select(measurementsRaw, nowMs)
        val measurements = selection.measurements
        if (measurements.isEmpty()) {
            diagnostics = emptyDiagnostics.copy(
                droppedPackets = selection.droppedInvalid + selection.droppedStale + selection.droppedSameView
            )
            return null
        }
        val primary = measurements.firstOrNull { it.view == V15CameraView.PRIMARY }
            ?: measurements.maxByOrNull { it.confidence }
            ?: run { resetDiagnostics(); return null }

        var acceptedFeatures = 0
        var rejectedOutliers = 0
        fun required(feature: V37Feature, selector: (ShotMetrics) -> Double, tolerance: Double): Double {
            val r = fuseValue(measurements, primary, feature, { selector(it.metrics) }, tolerance, nowMs)
            if (r.contributors >= 2) acceptedFeatures++
            rejectedOutliers += r.rejected
            return r.value ?: selector(primary.metrics)
        }
        fun optional(feature: V37Feature, selector: (ShotMetrics) -> Double?, tolerance: Double): Double? {
            val r = fuseValue(measurements, primary, feature, { selector(it.metrics) }, tolerance, nowMs)
            if (r.contributors >= 2) acceptedFeatures++
            rejectedOutliers += r.rejected
            return r.value ?: selector(primary.metrics)
        }

        val ball = required(V37Feature.BALL_SPEED, { it.ballSpeedMps }, .55)
        val launch = required(V37Feature.START_LINE, { it.launchAngleDeg }, 4.0)
        val head = optional(V37Feature.HEAD_SPEED, { it.headSpeedMps }, .80)
        val face = optional(V37Feature.FACE, { it.faceAngleDeg }, 5.0)
        val path = optional(V37Feature.PATH, { it.pathAngleDeg }, 5.5)
        val impact = optional(V37Feature.IMPACT, { it.impactOffsetMm }, 18.0)
        val backswing = optional(V37Feature.BACKSWING, { it.backswingMs }, 140.0)
        val downswing = optional(V37Feature.DOWNSWING, { it.downswingMs }, 120.0)
        val tempo = optional(V37Feature.TEMPO, { it.tempoRatio }, .65)
        val length = optional(V37Feature.BACKSWING_LENGTH, { it.backswingLengthCm }, 12.0)
        val accel = optional(V37Feature.ACCELERATION, { it.peakHeadAccelerationMps2 }, 2.2)

        val faceToPath = if (face != null && path != null) normalizeAngle(face - path) else primary.metrics.faceToPathDeg
        val smash = if (head != null && head > .03) (ball / head).coerceIn(.05, 3.0) else primary.metrics.smash
        val agreeingViews = measurements
            .filter { it.view != V15CameraView.PRIMARY && featureAgreement(primary, it) >= .55 }
            .mapTo(linkedSetOf()) { it.view }
        val primaryConfidence = (primary.metrics.confidence ?: primary.confidence).coerceIn(.15, .98)
        val supportBonus = V49FusionPolicy.confidenceSupportBonus(agreeingViews, acceptedFeatures)
        val penalty = min(.10, rejectedOutliers * .012)
        val confidenceCeiling = V49FusionPolicy.confidenceCeiling(selection.companionViews)
        val confidence = (primaryConfidence + supportBonus - penalty).coerceIn(.20, confidenceCeiling)
        val bestRoll = measurements
            .filter { it.metrics.roll != null }
            .maxByOrNull { baseWeight(it, V37Feature.BALL_SPEED, nowMs) }
            ?.metrics?.roll ?: primary.metrics.roll

        val companions = measurements.count { it.view != V15CameraView.PRIMARY }
        val diversityScore = when (selection.companionViews.size) {
            0 -> 0
            1 -> 45
            2 -> 78
            else -> 100
        }
        diagnostics = V37FusionDiagnostics(
            companionCount = companions,
            acceptedFeatures = acceptedFeatures,
            rejectedOutliers = rejectedOutliers,
            freshestAgeMs = measurements.filter { it.view != V15CameraView.PRIMARY }.minOfOrNull { max(0L, nowMs - it.receivedAtMs) },
            confidenceBefore = primaryConfidence,
            confidenceAfter = confidence,
            activeViews = selection.companionViews.joinToString("+") { it.name },
            droppedPackets = selection.droppedInvalid + selection.droppedStale + selection.droppedSameView,
            diversityScore = diversityScore
        )

        return primary.metrics.copy(
            ballSpeedMps = ball,
            launchAngleDeg = launch,
            headSpeedMps = head,
            faceAngleDeg = face,
            pathAngleDeg = path,
            faceToPathDeg = faceToPath,
            smash = smash,
            impactOffsetMm = impact,
            backswingMs = backswing,
            downswingMs = downswing,
            tempoRatio = tempo,
            backswingLengthCm = length,
            peakHeadAccelerationMps2 = accel,
            confidence = confidence,
            roll = bestRoll
        )
    }

    private fun fuseValue(
        measurements: List<V15CameraMeasurement>,
        primary: V15CameraMeasurement,
        feature: V37Feature,
        selector: (V15CameraMeasurement) -> Double?,
        tolerance: Double,
        nowMs: Long
    ): V37FusedValue {
        val raw = measurements.mapNotNull { m ->
            val value = selector(m)?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val weight = baseWeight(m, feature, nowMs)
            if (!weight.isFinite() || weight <= .0) null else V37Candidate(m, value, weight)
        }
        if (raw.isEmpty()) return V37FusedValue(null, 0, 0)

        val primaryValue = raw.firstOrNull { it.measurement.cameraId == primary.cameraId }?.value
        val reference = primaryValue ?: weightedMedian(raw)
        val adaptiveTolerance = when (feature) {
            V37Feature.BALL_SPEED -> max(tolerance, abs(reference) * .28)
            else -> tolerance
        }
        val accepted = raw.filter { candidate ->
            val delta = angleAwareDelta(feature, candidate.value, reference)
            candidate.measurement.cameraId == primary.cameraId || delta <= adaptiveTolerance
        }
        val rejected = raw.size - accepted.size
        if (accepted.isEmpty()) return V37FusedValue(primaryValue, if (primaryValue == null) 0 else 1, rejected)

        val sumWeight = accepted.sumOf { it.weight }
        if (sumWeight <= .0) return V37FusedValue(reference, accepted.size, rejected)
        val value = if (feature in setOf(V37Feature.START_LINE, V37Feature.FACE, V37Feature.PATH)) {
            normalizeAngle(reference + accepted.sumOf { shortestAngleDelta(it.value, reference) * it.weight } / sumWeight)
        } else accepted.sumOf { it.value * it.weight } / sumWeight
        return V37FusedValue(value, accepted.size, rejected)
    }

    private fun baseWeight(m: V15CameraMeasurement, feature: V37Feature, nowMs: Long): Double {
        if (!m.confidence.isFinite()) return .0
        val age = max(0L, nowMs - m.receivedAtMs)
        val freshness = when {
            age <= 220L -> 1.0
            age <= 500L -> .90
            age <= 850L -> .72
            age <= maxAgeMs -> .50
            else -> .0
        }
        val view = when (feature) {
            V37Feature.BALL_SPEED -> when (m.view) {
                V15CameraView.TOP -> 1.30; V15CameraView.DOWN_THE_LINE -> 1.08; V15CameraView.FACE_ON -> .82; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.START_LINE -> when (m.view) {
                V15CameraView.TOP -> 1.35; V15CameraView.DOWN_THE_LINE -> 1.12; V15CameraView.FACE_ON -> .82; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.HEAD_SPEED -> when (m.view) {
                V15CameraView.DOWN_THE_LINE -> 1.30; V15CameraView.FACE_ON -> 1.05; V15CameraView.TOP -> .90; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.FACE -> when (m.view) {
                V15CameraView.FACE_ON -> 1.38; V15CameraView.TOP -> 1.18; V15CameraView.DOWN_THE_LINE -> .70; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.PATH -> when (m.view) {
                V15CameraView.DOWN_THE_LINE -> 1.38; V15CameraView.TOP -> 1.16; V15CameraView.FACE_ON -> .72; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.IMPACT -> when (m.view) {
                V15CameraView.TOP -> 1.38; V15CameraView.FACE_ON -> 1.12; V15CameraView.DOWN_THE_LINE -> .92; V15CameraView.PRIMARY -> 1.0
            }
            V37Feature.BACKSWING, V37Feature.DOWNSWING, V37Feature.TEMPO, V37Feature.BACKSWING_LENGTH, V37Feature.ACCELERATION -> when (m.view) {
                V15CameraView.DOWN_THE_LINE -> 1.24; V15CameraView.FACE_ON -> 1.12; V15CameraView.TOP -> .90; V15CameraView.PRIMARY -> 1.0
            }
        }
        return (m.confidence.coerceIn(.10, 1.0) * freshness * view).coerceAtLeast(.0)
    }

    private fun featureAgreement(primary: V15CameraMeasurement, other: V15CameraMeasurement): Double {
        var checks = 0
        var passed = 0
        fun check(a: Double?, b: Double?, tolerance: Double, angle: Boolean = false) {
            if (a == null || b == null || !a.isFinite() || !b.isFinite()) return
            checks++
            val delta = if (angle) abs(shortestAngleDelta(b, a)) else abs(b - a)
            if (delta <= tolerance) passed++
        }
        check(primary.metrics.ballSpeedMps, other.metrics.ballSpeedMps, max(.55, primary.metrics.ballSpeedMps * .28))
        check(primary.metrics.launchAngleDeg, other.metrics.launchAngleDeg, 4.0, true)
        check(primary.metrics.faceAngleDeg, other.metrics.faceAngleDeg, 5.0, true)
        check(primary.metrics.pathAngleDeg, other.metrics.pathAngleDeg, 5.5, true)
        check(primary.metrics.impactOffsetMm, other.metrics.impactOffsetMm, 18.0)
        return if (checks == 0) .0 else passed.toDouble() / checks
    }

    private fun weightedMedian(values: List<V37Candidate>): Double {
        val sorted = values.sortedBy { it.value }
        val half = sorted.sumOf { it.weight } / 2.0
        var running = 0.0
        for (value in sorted) {
            running += value.weight
            if (running >= half) return value.value
        }
        return sorted.last().value
    }

    private fun angleAwareDelta(feature: V37Feature, a: Double, b: Double): Double =
        if (feature in setOf(V37Feature.START_LINE, V37Feature.FACE, V37Feature.PATH)) abs(shortestAngleDelta(a, b)) else abs(a - b)

    private fun shortestAngleDelta(value: Double, reference: Double): Double {
        var d = normalizeAngle(value) - normalizeAngle(reference)
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun normalizeAngle(value: Double): Double {
        var v = value % 360.0
        if (v > 180.0) v -= 360.0
        if (v <= -180.0) v += 360.0
        return v
    }
}

/** Raw companion ingress retained separately so the old transport remains backward compatible. */
object V37FeatureFusionRuntime {
    private val latest = ConcurrentHashMap<String, V15CameraMeasurement>()

    fun submit(measurement: V15CameraMeasurement) {
        if (measurement.cameraId.isBlank() || measurement.view == V15CameraView.PRIMARY || !measurement.confidence.isFinite()) return
        latest.compute(measurement.cameraId) { _, current ->
            if (current == null || measurement.receivedAtMs >= current.receivedAtMs) measurement else current
        }
        cleanup()
    }

    fun clear() {
        latest.clear()
        V37FeatureFusion.resetDiagnostics()
    }

    fun fusePrimary(primary: ShotMetrics): ShotMetrics {
        cleanup()
        val now = System.currentTimeMillis()
        val measurements = ArrayList<V15CameraMeasurement>()
        measurements += V15CameraMeasurement("primary", V15CameraView.PRIMARY, primary, primary.confidence ?: .60, now)
        measurements += latest.values
        return V37FeatureFusion.fuse(measurements, now) ?: primary
    }

    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - 4_000L
        latest.entries.removeIf { it.value.receivedAtMs < cutoff || !it.value.confidence.isFinite() }
    }
}
