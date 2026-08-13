package com.puttvision.screen

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class V15ArcType(val label: String) {
    STRAIGHT("스트레이트"),
    SLIGHT_ARC("슬라이트 아크"),
    STRONG_ARC("스트롱 아크")
}

data class V15RollAssessment(
    val grade: String,
    val skidDistanceCm: Double?,
    val spinRpm: Double?,
    val rollEfficiency: Int,
    val hint: String
)

data class V15StrokeSignature(
    val arcType: V15ArcType,
    val faceToPathDeg: Double?,
    val tempoRatio: Double?,
    val impactOffsetMm: Double?,
    val launchBiasDeg: Double,
    val faceConsistencyDeg: Double?,
    val pathConsistencyDeg: Double?,
    val speedConsistencyPct: Double?,
    val decelerationRisk: Boolean,
    val repeatability: Int
)

data class V15TrainingPlan(
    val title: String,
    val reason: String,
    val drill: String,
    val target: String,
    val priority: Int
)

data class V15PerformanceSnapshot(
    val signature: V15StrokeSignature,
    val roll: V15RollAssessment?,
    val training: V15TrainingPlan,
    val confidence: Double,
    val oneLine: String
)

object V15PerformanceAnalyzer {
    fun analyze(metrics: ShotMetrics, recent: List<ShotRecord>): V15PerformanceSnapshot {
        val relevant = recent.takeLast(20)
        val faceSeries = relevant.mapNotNull { it.metrics.faceAngleDeg }
        val pathSeries = relevant.mapNotNull { it.metrics.pathAngleDeg }
        val speedSeries = relevant.map { it.metrics.ballSpeedMps }.filter { it.isFinite() && it > 0.0 }
        val launchSeries = relevant.map { it.metrics.launchAngleDeg }

        val faceStd = std(faceSeries)
        val pathStd = std(pathSeries)
        val speedCv = if (speedSeries.size >= 3) {
            val avg = speedSeries.average().coerceAtLeast(.05)
            (std(speedSeries) ?: 0.0) / avg * 100.0
        } else null
        val launchStd = std(launchSeries)

        val arcMagnitude = listOfNotNull(metrics.pathAngleDeg?.let(::abs), metrics.faceToPathDeg?.let { abs(it) * .45 })
            .takeIf { it.isNotEmpty() }?.average() ?: abs(metrics.launchAngleDeg) * .55
        val arc = when {
            arcMagnitude < .65 -> V15ArcType.STRAIGHT
            arcMagnitude < 1.70 -> V15ArcType.SLIGHT_ARC
            else -> V15ArcType.STRONG_ARC
        }

        val decelRisk = when {
            metrics.downswingMs != null && metrics.backswingMs != null && metrics.downswingMs > metrics.backswingMs * .82 -> true
            metrics.peakHeadAccelerationMps2 != null && metrics.peakHeadAccelerationMps2 < .12 -> true
            metrics.tempoRatio != null && metrics.tempoRatio < 1.35 -> true
            else -> false
        }

        var repeatability = 100.0
        faceStd?.let { repeatability -= (it * 18.0).coerceAtMost(26.0) }
        pathStd?.let { repeatability -= (it * 12.0).coerceAtMost(22.0) }
        launchStd?.let { repeatability -= (it * 12.0).coerceAtMost(22.0) }
        speedCv?.let { repeatability -= (it * 1.7).coerceAtMost(20.0) }
        if (relevant.size < 5) repeatability = min(repeatability, 78.0)

        val signature = V15StrokeSignature(
            arcType = arc,
            faceToPathDeg = metrics.faceToPathDeg,
            tempoRatio = metrics.tempoRatio,
            impactOffsetMm = metrics.impactOffsetMm,
            launchBiasDeg = if (launchSeries.size >= 5) launchSeries.takeLast(10).average() else metrics.launchAngleDeg,
            faceConsistencyDeg = faceStd,
            pathConsistencyDeg = pathStd,
            speedConsistencyPct = speedCv,
            decelerationRisk = decelRisk,
            repeatability = repeatability.roundToInt().coerceIn(0, 100)
        )
        val roll = assessRoll(metrics.roll)
        val training = training(metrics, signature, roll)
        val confidence = ((metrics.confidence ?: .55) * .72 + min(1.0, relevant.size / 12.0) * .28).coerceIn(.30, .99)
        val oneLine = buildString {
            append(signature.arcType.label)
            append(" · 반복성 ${signature.repeatability}")
            if (roll != null) append(" · 롤 ${roll.grade}")
            if (decelRisk) append(" · 감속 주의")
        }
        return V15PerformanceSnapshot(signature, roll, training, confidence, oneLine)
    }

    fun assessRoll(roll: BallRollMetrics?): V15RollAssessment? {
        roll ?: return null
        val skid = roll.skidDistanceCm
        val rpm = roll.spinRpm
        var score = 88.0
        if (skid != null) {
            score -= when {
                skid <= 6.0 -> 0.0
                skid <= 12.0 -> (skid - 6.0) * 2.0
                else -> 12.0 + (skid - 12.0) * 2.8
            }
        } else score -= 12.0
        if ((roll.confidence ?: .0) < .55) score -= 8.0
        val efficiency = score.roundToInt().coerceIn(0, 100)
        val grade = when {
            efficiency >= 90 -> "A+"
            efficiency >= 82 -> "A"
            efficiency >= 72 -> "B"
            efficiency >= 60 -> "C"
            else -> "D"
        }
        val hint = when {
            skid == null -> "마킹볼 인식이 부족해 스키드 구간을 더 모아야 함"
            skid <= 7.0 -> "임팩트 직후 롤 전환이 빠름"
            skid <= 13.0 -> "스키드가 약간 김 · 로프트/상향타격과 임팩트 위치 확인"
            else -> "스키드가 김 · 볼을 때리기보다 굴리는 임팩트로 교정 권장"
        }
        return V15RollAssessment(grade, skid, rpm, efficiency, hint)
    }

    private fun training(metrics: ShotMetrics, s: V15StrokeSignature, roll: V15RollAssessment?): V15TrainingPlan {
        val face = metrics.faceAngleDeg
        if (face != null && abs(face) >= .85) {
            return V15TrainingPlan(
                "페이스 제로 드릴",
                "임팩트 페이스 ${"%+.2f".format(face)}°가 출발선을 가장 크게 흔듦",
                "1.5m 직선에서 티 2개로 45mm 게이트를 만들고 10구 연속 통과",
                "페이스 ±0.5° / 출발각 ±0.5°",
                100
            )
        }
        metrics.impactOffsetMm?.let { impact ->
            if (abs(impact) >= 6.0) {
                return V15TrainingPlan(
                    "센터 임팩트 드릴",
                    "정타가 ${if (impact > 0) "토" else "힐"} 쪽 ${"%.1f".format(abs(impact))}mm로 치우침",
                    "퍼터 양쪽에 티 게이트를 두고 헤드 중심으로 15구 반복",
                    "임팩트 오프셋 ±3mm",
                    95
                )
            }
        }
        if (s.decelerationRisk) {
            return V15TrainingPlan(
                "가속 유지 드릴",
                "다운스윙 감속 패턴이 감지됨",
                "백스윙 길이를 줄이고 임팩트 뒤 20cm까지 같은 리듬으로 통과",
                "템포 1.6~2.4:1 / 감속 경고 0회",
                90
            )
        }
        if (roll != null && roll.rollEfficiency < 72) {
            return V15TrainingPlan(
                "롤 전환 드릴",
                roll.hint,
                "마킹볼 10구를 같은 위치에서 치고 스키드 거리 편차를 비교",
                "스키드 10cm 이하",
                85
            )
        }
        if (s.repeatability < 76) {
            return V15TrainingPlan(
                "반복성 드릴",
                "최근 스트로크 분산이 큼",
                "같은 2m 직선 퍼트를 10구씩 3세트, 세트 사이 어드레스만 다시 잡기",
                "반복성 85+",
                80
            )
        }
        return V15TrainingPlan(
            "거리 편차 압축",
            "큰 기계적 결함보다 거리감 미세 편차를 줄일 단계",
            "2m/4m/6m를 각 5구씩 랜덤 순서로 반복",
            "평균 홀 거리 오차 30cm 이하",
            40
        )
    }

    private fun std(values: List<Double>): Double? {
        if (values.size < 3) return null
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

enum class V15PutterBalance(val label: String) {
    FACE_BALANCED("페이스 밸런스"),
    SLIGHT_TOE_HANG("약한 토행"),
    TOE_HANG("토행")
}

enum class V15PutterHead(val label: String) {
    BLADE("블레이드"),
    MID_MALLET("미드 말렛"),
    HIGH_MOI_MALLET("고MOI 말렛")
}

data class V15PutterFitRecommendation(
    val sampleCount: Int,
    val balance: V15PutterBalance,
    val head: V15PutterHead,
    val suggestedLengthDeltaMm: Int,
    val suggestedLieDeltaDeg: Double,
    val confidence: Double,
    val reason: String
)

object V15PutterFitter {
    fun fit(recordsRaw: List<ShotRecord>, putterName: String? = ProductRuntime.putterProfileName): V15PutterFitRecommendation? {
        val records = recordsRaw
            .filter { putterName.isNullOrBlank() || it.putterProfileName == putterName }
            .takeLast(60)
        if (records.size < 20) return null

        val paths = records.mapNotNull { it.metrics.pathAngleDeg }
        val faces = records.mapNotNull { it.metrics.faceAngleDeg }
        val impacts = records.mapNotNull { it.metrics.impactOffsetMm }
        val faceToPath = records.mapNotNull { it.metrics.faceToPathDeg }
        val arc = paths.map(::abs).takeIf { it.isNotEmpty() }?.average()
            ?: records.map { abs(it.metrics.launchAngleDeg) }.average()
        val faceStd = std(faces) ?: .9
        val impactStd = std(impacts) ?: 6.0
        val impactBias = impacts.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val closureBias = faceToPath.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        val balance = when {
            arc < .75 -> V15PutterBalance.FACE_BALANCED
            arc < 1.65 -> V15PutterBalance.SLIGHT_TOE_HANG
            else -> V15PutterBalance.TOE_HANG
        }
        val head = when {
            faceStd > 1.05 || impactStd > 7.5 -> V15PutterHead.HIGH_MOI_MALLET
            faceStd > .62 || impactStd > 4.5 -> V15PutterHead.MID_MALLET
            else -> V15PutterHead.BLADE
        }

        // Camera-only fitting cannot know posture/eye-line perfectly. These are intentionally small
        // trial deltas, never presented as a final club-building specification.
        val lengthDelta = when {
            impactBias > 6.0 -> -5
            impactBias < -6.0 -> 5
            else -> 0
        }
        val lieDelta = when {
            closureBias > 1.2 -> -0.5
            closureBias < -1.2 -> 0.5
            else -> 0.0
        }
        val confidence = (0.52 + min(40, records.size - 20) / 40.0 * .22 +
            (1.0 - min(1.0, faceStd / 2.5)) * .12 +
            (1.0 - min(1.0, impactStd / 15.0)) * .10).coerceIn(.45, .90)
        val reason = "${records.size}구 · 평균 아크 ${"%.2f".format(arc)}° · 페이스 σ ${"%.2f".format(faceStd)}° · 임팩트 σ ${"%.1f".format(impactStd)}mm"
        return V15PutterFitRecommendation(records.size, balance, head, lengthDelta, lieDelta, confidence, reason)
    }

    private fun std(values: List<Double>): Double? {
        if (values.size < 4) return null
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

data class V15GhostReference(
    val record: ShotRecord,
    val trail: List<Pair<Double, Double>>
)

data class V15GhostComparison(
    val scoreDelta: Int,
    val launchDeltaDeg: Double,
    val ballSpeedDeltaMps: Double,
    val finishDeltaCm: Double?,
    val beatGhost: Boolean,
    val headline: String,
    val reference: V15GhostReference
)

object V15GhostRuntime {
    private val references = ArrayList<V15GhostReference>()
    @Volatile var lastComparison: V15GhostComparison? = null
        private set

    @Synchronized
    fun seed(records: List<ShotRecord>) {
        references.clear()
        records
            .filter { it.result != null }
            .groupBy { key(it) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.strokeScore.total } }
            .sortedByDescending { it.timestampMs }
            .take(32)
            .forEach { record -> references += V15GhostReference(record, simulate(record)) }
        lastComparison = null
    }

    @Synchronized
    fun compare(record: ShotRecord, actualTrail: List<Pair<Double, Double>>): V15GhostComparison? {
        val ref = bestReference(record) ?: return null
        if (ref.record.timestampMs == record.timestampMs) return null
        val finishDelta = if (record.result != null && ref.record.result != null) {
            hypot(
                record.result.finishX - ref.record.result.finishX,
                record.result.finishY - ref.record.result.finishY
            ) * 100.0
        } else null
        val scoreDelta = record.strokeScore.total - ref.record.strokeScore.total
        val launchDelta = record.metrics.launchAngleDeg - ref.record.metrics.launchAngleDeg
        val speedDelta = record.metrics.ballSpeedMps - ref.record.metrics.ballSpeedMps
        val beat = scoreDelta > 0 || (scoreDelta == 0 && (record.result?.distanceToCupM ?: 99.0) < (ref.record.result?.distanceToCupM ?: 99.0))
        val headline = if (beat) "고스트 기록 갱신 +${max(0, scoreDelta)}" else "고스트 대비 ${scoreDelta}점"
        return V15GhostComparison(scoreDelta, launchDelta, speedDelta, finishDelta, beat, headline, ref).also {
            lastComparison = it
        }
    }

    @Synchronized
    fun consider(record: ShotRecord) {
        if (record.result == null) return
        val existing = bestReference(record)
        val better = existing == null || record.strokeScore.total > existing.record.strokeScore.total ||
            (record.strokeScore.total == existing.record.strokeScore.total &&
                (record.result.distanceToCupM < (existing.record.result?.distanceToCupM ?: 99.0)))
        if (better) {
            references.removeAll { key(it.record) == key(record) }
            references += V15GhostReference(record, simulate(record))
            while (references.size > 32) references.removeAt(0)
        }
    }

    @Synchronized
    fun referenceForCurrent(settings: GreenSettings): V15GhostReference? {
        if (references.isEmpty()) return null
        return references.minByOrNull {
            abs(it.record.targetDistanceM - settings.holeDistanceM) * 3.0 +
                abs(it.record.sideSlopePct - settings.sideSlopePct) * .3 +
                abs(it.record.longSlopePct - settings.longSlopePct) * .3 +
                if (it.record.terrainProfileId == settings.terrainProfileId) 0.0 else 2.0
        }
    }

    private fun bestReference(record: ShotRecord): V15GhostReference? {
        val exact = references.filter { key(it.record) == key(record) }
        if (exact.isNotEmpty()) return exact.maxByOrNull { it.record.strokeScore.total }
        return references.minByOrNull { abs(it.record.targetDistanceM - record.targetDistanceM) }
    }

    private fun key(record: ShotRecord): String = buildString {
        append((record.targetDistanceM * 2.0).roundToInt())
        append('|').append(record.terrainProfileId)
        append('|').append(record.mode.name)
    }

    private fun simulate(record: ShotRecord): List<Pair<Double, Double>> {
        val settings = GreenSettings(
            stimpMeters = record.stimpMeters,
            holeDistanceM = record.targetDistanceM.coerceAtLeast(.5),
            sideSlopePct = record.sideSlopePct,
            longSlopePct = record.longSlopePct,
            terrainProfileId = record.terrainProfileId
        )
        val physics = GreenPhysics()
        val state = physics.launch(record.metrics, settings)
        var guard = 0
        while (state.running && guard++ < 1600) physics.step(state, settings, .0125)
        return state.trail.toList()
    }
}

enum class V15CameraView { PRIMARY, FACE_ON, DOWN_THE_LINE, TOP }

data class V15CameraMeasurement(
    val cameraId: String,
    val view: V15CameraView,
    val metrics: ShotMetrics,
    val confidence: Double = metrics.confidence ?: .55,
    val receivedAtMs: Long = System.currentTimeMillis()
)

object V15MultiCameraFusion {
    fun fuse(measurementsRaw: List<V15CameraMeasurement>): ShotMetrics? {
        val measurements = measurementsRaw
            .filter { System.currentTimeMillis() - it.receivedAtMs <= 1800L }
            .sortedByDescending { if (it.view == V15CameraView.PRIMARY) 1 else 0 }
        if (measurements.isEmpty()) return null
        val primary = measurements.firstOrNull { it.view == V15CameraView.PRIMARY } ?: measurements.first()

        fun weight(m: V15CameraMeasurement): Double {
            val viewBoost = when (m.view) {
                V15CameraView.PRIMARY -> 1.0
                V15CameraView.FACE_ON -> 1.08
                V15CameraView.DOWN_THE_LINE -> 1.08
                V15CameraView.TOP -> 1.15
            }
            return m.confidence.coerceIn(.15, 1.0) * viewBoost
        }
        fun avg(selector: (ShotMetrics) -> Double): Double {
            var sw = 0.0
            var sum = 0.0
            measurements.forEach { m ->
                val v = selector(m.metrics)
                if (v.isFinite()) {
                    val w = weight(m)
                    sw += w
                    sum += v * w
                }
            }
            return if (sw > 0.0) sum / sw else selector(primary.metrics)
        }
        fun avgOpt(selector: (ShotMetrics) -> Double?): Double? {
            var sw = 0.0
            var sum = 0.0
            measurements.forEach { m ->
                val v = selector(m.metrics)
                if (v != null && v.isFinite()) {
                    val w = weight(m)
                    sw += w
                    sum += v * w
                }
            }
            return if (sw > 0.0) sum / sw else null
        }
        val bestRoll = measurements.filter { it.metrics.roll != null }.maxByOrNull(::weight)?.metrics?.roll
        val fusedConfidence = (measurements.sumOf { weight(it) } / measurements.size).coerceIn(.25, .99)
        return primary.metrics.copy(
            ballSpeedMps = avg { it.ballSpeedMps },
            launchAngleDeg = avg { it.launchAngleDeg },
            headSpeedMps = avgOpt { it.headSpeedMps },
            faceAngleDeg = avgOpt { it.faceAngleDeg },
            pathAngleDeg = avgOpt { it.pathAngleDeg },
            faceToPathDeg = avgOpt { it.faceToPathDeg },
            smash = avgOpt { it.smash },
            impactOffsetMm = avgOpt { it.impactOffsetMm },
            backswingMs = avgOpt { it.backswingMs },
            downswingMs = avgOpt { it.downswingMs },
            tempoRatio = avgOpt { it.tempoRatio },
            backswingLengthCm = avgOpt { it.backswingLengthCm },
            peakHeadAccelerationMps2 = avgOpt { it.peakHeadAccelerationMps2 },
            confidence = fusedConfidence,
            roll = bestRoll
        )
    }
}

/**
 * Shared ingress for companion phones. A secondary capture surface only needs to submit a
 * V15CameraMeasurement; every primary launch automatically consumes recent companion samples.
 */
object V15CompanionRuntime {
    private val latest = ConcurrentHashMap<String, V15CameraMeasurement>()

    fun submit(measurement: V15CameraMeasurement) {
        if (measurement.cameraId.isBlank()) return
        latest[measurement.cameraId] = measurement
        cleanup()
    }

    fun clear() = latest.clear()

    fun fusePrimary(primary: ShotMetrics): ShotMetrics {
        cleanup()
        val now = System.currentTimeMillis()
        val list = ArrayList<V15CameraMeasurement>()
        list += V15CameraMeasurement("primary", V15CameraView.PRIMARY, primary, primary.confidence ?: .60, now)
        list += latest.values.filter { now - it.receivedAtMs <= 1300L }
        return V15MultiCameraFusion.fuse(list) ?: primary
    }

    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - 4000L
        latest.entries.removeIf { it.value.receivedAtMs < cutoff }
    }
}

object V15CompanionWire {
    fun encode(m: V15CameraMeasurement): String = JSONObject().apply {
        put("cameraId", m.cameraId)
        put("view", m.view.name)
        put("confidence", m.confidence)
        put("receivedAtMs", m.receivedAtMs)
        put("ballSpeedMps", m.metrics.ballSpeedMps)
        put("launchAngleDeg", m.metrics.launchAngleDeg)
        m.metrics.headSpeedMps?.let { put("headSpeedMps", it) }
        m.metrics.faceAngleDeg?.let { put("faceAngleDeg", it) }
        m.metrics.pathAngleDeg?.let { put("pathAngleDeg", it) }
        m.metrics.faceToPathDeg?.let { put("faceToPathDeg", it) }
        m.metrics.impactOffsetMm?.let { put("impactOffsetMm", it) }
        m.metrics.tempoRatio?.let { put("tempoRatio", it) }
    }.toString()

    fun decode(raw: String): V15CameraMeasurement? = runCatching {
        val j = JSONObject(raw)
        val speed = j.getDouble("ballSpeedMps")
        val launch = j.getDouble("launchAngleDeg")
        val metrics = ShotMetrics(
            ballSpeedMps = speed,
            launchAngleDeg = launch,
            headSpeedMps = j.optDoubleOrNull("headSpeedMps"),
            faceAngleDeg = j.optDoubleOrNull("faceAngleDeg"),
            pathAngleDeg = j.optDoubleOrNull("pathAngleDeg"),
            faceToPathDeg = j.optDoubleOrNull("faceToPathDeg"),
            smash = null,
            impactOffsetMm = j.optDoubleOrNull("impactOffsetMm"),
            measuredAtNs = System.nanoTime(),
            tempoRatio = j.optDoubleOrNull("tempoRatio"),
            confidence = j.optDouble("confidence", .55)
        )
        V15CameraMeasurement(
            cameraId = j.optString("cameraId", "companion"),
            view = runCatching { V15CameraView.valueOf(j.optString("view", V15CameraView.FACE_ON.name)) }
                .getOrDefault(V15CameraView.FACE_ON),
            metrics = metrics,
            confidence = j.optDouble("confidence", .55),
            receivedAtMs = System.currentTimeMillis()
        )
    }.getOrNull()

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = optDouble(name, Double.NaN)
        return value.takeIf { it.isFinite() }
    }
}

enum class V15FlowStage { IDLE, READY, IMPACT, ANALYZING, ROLLING, RESULT, REARM }

data class V15FlowSnapshot(
    val stage: V15FlowStage,
    val changedAtMs: Long,
    val generation: Long,
    val note: String
)

object V15AutoFlowRuntime {
    @Volatile private var state = V15FlowSnapshot(V15FlowStage.IDLE, System.currentTimeMillis(), 0L, "대기")

    fun snapshot(): V15FlowSnapshot = state
    fun ready(note: String = "공 감지 · 자동 대기") = move(V15FlowStage.READY, note)
    fun impact() = move(V15FlowStage.IMPACT, "임팩트 감지")
    fun analyzing() = move(V15FlowStage.ANALYZING, "영상 분석")
    fun rolling() = move(V15FlowStage.ROLLING, "TV 롤")
    fun result() = move(V15FlowStage.RESULT, "결과/코칭")
    fun rearm() = move(V15FlowStage.REARM, "다음 공 자동 대기")
    fun idle() = move(V15FlowStage.IDLE, "대기")

    fun shouldAutoRearm(nowMs: Long = System.currentTimeMillis()): Boolean =
        state.stage == V15FlowStage.RESULT && nowMs - state.changedAtMs >= 1100L

    @Synchronized private fun move(stage: V15FlowStage, note: String) {
        state = V15FlowSnapshot(stage, System.currentTimeMillis(), state.generation + 1L, note)
    }
}
