package com.puttvision.screen

import android.content.Context
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Per-metric trust instead of one misleading global Q number. */
data class V16MetricConfidence(
    val ballSpeed: Double,
    val launch: Double,
    val headSpeed: Double,
    val face: Double,
    val path: Double,
    val impact: Double,
    val roll: Double
) {
    fun weakest(): Pair<String, Double> = listOf(
        "BALL" to ballSpeed,
        "START" to launch,
        "HEAD" to headSpeed,
        "FACE" to face,
        "PATH" to path,
        "IMPACT" to impact,
        "ROLL" to roll
    ).minBy { it.second }
}

object V16MetricConfidenceEstimator {
    fun estimate(m: ShotMetrics): V16MetricConfidence {
        val base = (m.confidence ?: .55).coerceIn(.20, .99)
        val sampleBoost = when {
            m.rawBallSpeedMps != null -> .07
            else -> .0
        }
        val rollQ = m.roll?.confidence ?: .0
        fun q(present: Boolean, penalty: Double = .0, boost: Double = .0): Double =
            if (!present) .0 else (base + sampleBoost + boost - penalty).coerceIn(.08, .99)

        val ball = q(m.ballSpeedMps.isFinite() && m.ballSpeedMps > .05, boost = .07)
        val launch = q(m.launchAngleDeg.isFinite(), penalty = if (abs(m.launchAngleDeg) > 8.0) .08 else .0, boost = .04)
        val head = q(m.headSpeedMps != null, penalty = .08)
        val face = q(m.faceAngleDeg != null, penalty = .10, boost = if (m.faceToPathDeg != null) .03 else .0)
        val path = q(m.pathAngleDeg != null, penalty = .08)
        val impact = q(m.impactOffsetMm != null, penalty = .16)
        val roll = if (m.roll == null) .0 else (base * .38 + rollQ * .62).coerceIn(.08, .98)
        return V16MetricConfidence(ball, launch, head, face, path, impact, roll)
    }

    fun shouldReject(m: ShotMetrics): Boolean {
        val q = estimate(m)
        return q.ballSpeed < .42 || q.launch < .40 || m.ballSpeedMps !in .08..8.0 || abs(m.launchAngleDeg) > 18.0
    }
}

data class V16PersonalBaseline(
    val sampleCount: Int,
    val faceMean: Double?,
    val faceStd: Double?,
    val pathMean: Double?,
    val pathStd: Double?,
    val launchMean: Double,
    val launchStd: Double,
    val impactMeanMm: Double?,
    val impactStdMm: Double?,
    val speedMean: Double,
    val speedCvPct: Double,
    val makePct: Double,
    val avgMissCm: Double?
)

data class V16PatternInsight(
    val headline: String,
    val detail: String,
    val severity: Int,
    val evidenceShots: Int,
    val recommendedDistanceM: Double,
    val recommendedSideSlopePct: Double = 0.0,
    val recommendedLongSlopePct: Double = 0.0
)

data class V16PersonalCoachSnapshot(
    val baseline: V16PersonalBaseline,
    val topInsights: List<V16PatternInsight>,
    val primary: V16PatternInsight,
    val improvementScore: Int
)

object V16PersonalCoach {
    fun build(recordsRaw: List<ShotRecord>): V16PersonalCoachSnapshot? {
        val records = recordsRaw.takeLast(120)
        if (records.size < 8) return null
        val recent = records.takeLast(min(40, records.size))
        val face = recent.mapNotNull { it.metrics.faceAngleDeg }
        val path = recent.mapNotNull { it.metrics.pathAngleDeg }
        val launch = recent.map { it.metrics.launchAngleDeg }
        val impact = recent.mapNotNull { it.metrics.impactOffsetMm }
        val speed = recent.map { it.metrics.ballSpeedMps }.filter { it.isFinite() && it > .05 }
        val misses = recent.mapNotNull { it.result?.distanceToCupM?.times(100.0) }
        val baseline = V16PersonalBaseline(
            sampleCount = recent.size,
            faceMean = meanOrNull(face),
            faceStd = std(face),
            pathMean = meanOrNull(path),
            pathStd = std(path),
            launchMean = launch.average(),
            launchStd = std(launch) ?: .0,
            impactMeanMm = meanOrNull(impact),
            impactStdMm = std(impact),
            speedMean = speed.takeIf { it.isNotEmpty() }?.average() ?: .0,
            speedCvPct = cv(speed),
            makePct = recent.count { it.result?.holed == true } * 100.0 / recent.size,
            avgMissCm = meanOrNull(misses)
        )

        val insights = ArrayList<V16PatternInsight>()
        contextualDistancePattern(recent)?.let(insights::add)
        contextualFastGreenPattern(recent)?.let(insights::add)
        contextualBreakPattern(recent)?.let(insights::add)
        persistentFacePattern(baseline)?.let(insights::add)
        persistentImpactPattern(baseline)?.let(insights::add)
        consistencyPattern(baseline)?.let(insights::add)
        if (insights.isEmpty()) {
            insights += V16PatternInsight(
                "큰 고질 패턴 없음",
                "최근 ${recent.size}구에서 반복되는 큰 방향/정타 결함이 없습니다. 랜덤 거리로 거리감 분산을 더 줄이는 단계입니다.",
                25,
                recent.size,
                4.0
            )
        }
        val sorted = insights.sortedByDescending { it.severity }.take(4)
        val primary = sorted.first()

        // Compare older vs newest 10 shots using stroke score. Positive means the user is actually improving.
        val improvement = if (records.size >= 20) {
            val old = records.takeLast(20).take(10).map { it.strokeScore.total }.average()
            val newest = records.takeLast(10).map { it.strokeScore.total }.average()
            (50.0 + (newest - old) * 3.0).roundToInt().coerceIn(0, 100)
        } else 50
        return V16PersonalCoachSnapshot(baseline, sorted, primary, improvement)
    }

    private fun contextualDistancePattern(records: List<ShotRecord>): V16PatternInsight? {
        val short = records.filter { it.targetDistanceM < 4.0 }
        val long = records.filter { it.targetDistanceM >= 4.0 }
        if (short.size < 5 || long.size < 5) return null
        val shortMiss = meanOrNull(short.mapNotNull { it.result?.distanceToCupM?.times(100.0) }) ?: return null
        val longMiss = meanOrNull(long.mapNotNull { it.result?.distanceToCupM?.times(100.0) }) ?: return null
        val longImpact = meanOrNull(long.mapNotNull { it.metrics.impactOffsetMm?.let(::abs) }) ?: .0
        if (longMiss > max(38.0, shortMiss * 1.55)) {
            return V16PatternInsight(
                "4m 이상에서 거리 오차 급증",
                "짧은 퍼트 평균 ${"%.0f".format(shortMiss)}cm 대비 4m+는 ${"%.0f".format(longMiss)}cm입니다. 장거리에서 정타 편차 ${"%.1f".format(longImpact)}mm와 스트로크 크기 재현성을 우선 훈련합니다.",
                92,
                long.size,
                5.0
            )
        }
        return null
    }

    private fun contextualFastGreenPattern(records: List<ShotRecord>): V16PatternInsight? {
        val normal = records.filter { it.stimpMeters < 3.2 }
        val fast = records.filter { it.stimpMeters >= 3.2 }
        if (normal.size < 5 || fast.size < 5) return null
        val normalMiss = meanOrNull(normal.mapNotNull { it.result?.distanceToCupM?.times(100.0) }) ?: return null
        val fastMiss = meanOrNull(fast.mapNotNull { it.result?.distanceToCupM?.times(100.0) }) ?: return null
        if (fastMiss > max(32.0, normalMiss * 1.45)) {
            return V16PatternInsight(
                "빠른 그린에서 거리감 손실",
                "GREEN 3.2+에서 평균 오차가 ${"%.0f".format(fastMiss)}cm로 증가합니다. 같은 거리에서 백스윙 크기를 줄이는 적응 훈련이 필요합니다.",
                84,
                fast.size,
                3.0
            )
        }
        return null
    }

    private fun contextualBreakPattern(records: List<ShotRecord>): V16PatternInsight? {
        val breakShots = records.filter { abs(it.sideSlopePct) >= 1.0 }
        if (breakShots.size < 7) return null
        val wrongSide = breakShots.count { r ->
            val finish = r.result?.finishX ?: return@count false
            val expectedBreakSign = kotlin.math.sign(r.sideSlopePct)
            kotlin.math.sign(finish) == expectedBreakSign && abs(finish) > .08
        }
        if (wrongSide * 1.0 / breakShots.size >= .55) {
            return V16PatternInsight(
                "브레이크를 덜 보고 있음",
                "경사 퍼트 ${breakShots.size}구 중 ${wrongSide}구가 경사 방향으로 남았습니다. 에임을 현재보다 한 단계 더 보게 훈련합니다.",
                82,
                breakShots.size,
                3.5,
                recommendedSideSlopePct = 1.8
            )
        }
        return null
    }

    private fun persistentFacePattern(b: V16PersonalBaseline): V16PatternInsight? {
        val mean = b.faceMean ?: return null
        if (abs(mean) < .65) return null
        val side = if (mean > 0) "오픈" else "클로즈"
        return V16PatternInsight(
            "페이스 $side 편향이 습관화",
            "최근 평균 페이스 ${"%+.2f".format(mean)}°입니다. 단발 미스가 아니라 개인 기준선 자체가 치우쳐 있습니다.",
            88,
            b.sampleCount,
            1.5
        )
    }

    private fun persistentImpactPattern(b: V16PersonalBaseline): V16PatternInsight? {
        val mean = b.impactMeanMm ?: return null
        if (abs(mean) < 4.0) return null
        return V16PatternInsight(
            "정타가 계속 ${if (mean > 0) "토" else "힐"} 쪽",
            "평균 ${"%.1f".format(abs(mean))}mm 편향입니다. 셋업과 퍼터 길이/라이 적합성도 함께 확인합니다.",
            80,
            b.sampleCount,
            2.0
        )
    }

    private fun consistencyPattern(b: V16PersonalBaseline): V16PatternInsight? {
        if (b.launchStd < .85 && b.speedCvPct < 8.0) return null
        return V16PatternInsight(
            "반복성부터 압축 필요",
            "출발각 σ ${"%.2f".format(b.launchStd)}° · 볼스피드 변동 ${"%.1f".format(b.speedCvPct)}%입니다. 같은 셋업으로 10구 묶음 훈련이 우선입니다.",
            74,
            b.sampleCount,
            3.0
        )
    }

    private fun meanOrNull(v: List<Double>): Double? = v.takeIf { it.isNotEmpty() }?.average()
    private fun std(v: List<Double>): Double? {
        if (v.size < 3) return null
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }
    private fun cv(v: List<Double>): Double {
        if (v.size < 3) return 0.0
        val m = v.average().coerceAtLeast(.05)
        return (std(v) ?: .0) / m * 100.0
    }
}

data class V16TrainingBlock(
    val title: String,
    val shots: Int,
    val distanceM: Double,
    val sideSlopePct: Double,
    val longSlopePct: Double,
    val successRule: String
)

data class V16DailyTrainingPlan(
    val title: String,
    val estimatedMinutes: Int,
    val blocks: List<V16TrainingBlock>,
    val reason: String
)

object V16TrainingPlanner {
    fun build(snapshot: V16PersonalCoachSnapshot?): V16DailyTrainingPlan {
        val primary = snapshot?.primary
        val focusDistance = primary?.recommendedDistanceM ?: 3.0
        val focusSide = primary?.recommendedSideSlopePct ?: .0
        val blocks = listOf(
            V16TrainingBlock("워밍업 스타트라인", 8, 1.5, .0, .0, "출발각 ±0.7°"),
            V16TrainingBlock("오늘의 취약점", 12, focusDistance, focusSide, primary?.recommendedLongSlopePct ?: .0, primary?.headline ?: "반복성 80+"),
            V16TrainingBlock("거리 랜덤", 10, max(3.0, focusDistance), .0, .0, "컵 잔여 35cm 이하"),
            V16TrainingBlock("압박 마무리", 5, 2.0, .0, .0, "3연속 성공")
        )
        return V16DailyTrainingPlan(
            title = "오늘의 15분 퍼팅",
            estimatedMinutes = 15,
            blocks = blocks,
            reason = primary?.detail ?: "기준 데이터를 만드는 기본 루틴입니다."
        )
    }
}

/**
 * Device-specific correction profile. It never invents absolute truth: correction factors are only
 * accepted after reference samples are explicitly supplied by a calibration flow.
 */
data class V16DeviceCalibrationProfile(
    val model: String,
    val sampleCount: Int,
    val speedScale: Double,
    val launchBiasDeg: Double,
    val confidenceBoost: Double,
    val updatedAtMs: Long
) {
    fun apply(m: ShotMetrics): ShotMetrics = m.copy(
        ballSpeedMps = (m.ballSpeedMps * speedScale).coerceIn(.05, 10.0),
        launchAngleDeg = m.launchAngleDeg - launchBiasDeg,
        rawBallSpeedMps = m.rawBallSpeedMps ?: m.ballSpeedMps,
        confidence = ((m.confidence ?: .55) + confidenceBoost).coerceIn(.20, .99)
    )
}

data class V16DeviceCalibrationSample(
    val measuredSpeedMps: Double,
    val referenceSpeedMps: Double,
    val measuredLaunchDeg: Double,
    val referenceLaunchDeg: Double = 0.0
)

object V16DeviceCalibrator {
    fun fit(model: String, samples: List<V16DeviceCalibrationSample>): V16DeviceCalibrationProfile? {
        val good = samples.filter {
            it.measuredSpeedMps in .08..8.0 && it.referenceSpeedMps in .08..8.0 &&
                abs(it.measuredLaunchDeg) <= 20.0 && abs(it.referenceLaunchDeg) <= 20.0
        }
        if (good.size < 5) return null
        val ratios = good.map { it.referenceSpeedMps / it.measuredSpeedMps }.sorted()
        val middle = ratios.drop((ratios.size * .15).toInt()).dropLast((ratios.size * .15).toInt())
        val speedScale = (middle.takeIf { it.isNotEmpty() } ?: ratios).average().coerceIn(.82, 1.18)
        val launchBias = good.map { it.measuredLaunchDeg - it.referenceLaunchDeg }.sorted().let { it[it.size / 2] }.coerceIn(-3.0, 3.0)
        return V16DeviceCalibrationProfile(
            model = model,
            sampleCount = good.size,
            speedScale = speedScale,
            launchBiasDeg = launchBias,
            confidenceBoost = min(.08, good.size * .003),
            updatedAtMs = System.currentTimeMillis()
        )
    }
}

class V16DeviceCalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("v16_device_calibration", Context.MODE_PRIVATE)

    fun load(model: String): V16DeviceCalibrationProfile? {
        if (prefs.getString("model", null) != model) return null
        val count = prefs.getInt("count", 0)
        if (count < 5) return null
        return V16DeviceCalibrationProfile(
            model = model,
            sampleCount = count,
            speedScale = prefs.getFloat("speedScale", 1f).toDouble(),
            launchBiasDeg = prefs.getFloat("launchBias", 0f).toDouble(),
            confidenceBoost = prefs.getFloat("confidenceBoost", 0f).toDouble(),
            updatedAtMs = prefs.getLong("updatedAt", 0L)
        )
    }

    fun save(profile: V16DeviceCalibrationProfile) {
        prefs.edit()
            .putString("model", profile.model)
            .putInt("count", profile.sampleCount)
            .putFloat("speedScale", profile.speedScale.toFloat())
            .putFloat("launchBias", profile.launchBiasDeg.toFloat())
            .putFloat("confidenceBoost", profile.confidenceBoost.toFloat())
            .putLong("updatedAt", profile.updatedAtMs)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

object V16Runtime {
    @Volatile var personalCoach: V16PersonalCoachSnapshot? = null
        private set
    @Volatile var trainingPlan: V16DailyTrainingPlan = V16TrainingPlanner.build(null)
        private set

    fun update(records: List<ShotRecord>) {
        personalCoach = V16PersonalCoach.build(records)
        trainingPlan = V16TrainingPlanner.build(personalCoach)
    }
}
