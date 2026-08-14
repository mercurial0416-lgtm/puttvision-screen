package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Session-local signals that answer different questions than the long-window adaptive coach. */
data class V49SessionInsights(
    val sampleCount: Int,
    val momentumDelta: Double?,
    val momentumLabel: String,
    val directionBiasDeg: Double?,
    val directionLabel: String,
    val paceBiasCm: Double?,
    val paceLabel: String,
    val confidenceDeltaPct: Double?,
    val confidenceLabel: String,
    val lateSessionFade: Boolean,
    val fadeLabel: String,
    val personalBestScore: Int?,
    val personalBestLabel: String,
    val consistencyStreak: Int,
    val streakLabel: String,
    val quickPlan: V16DailyTrainingPlan,
    val quickPlanReason: String
) {
    val headline: String
        get() = listOf(momentumLabel, directionLabel, paceLabel).firstOrNull { !it.contains("데이터") } ?: "세션 데이터 수집 중"
}

object V49SessionInsightsEngine {
    fun analyze(recordsRaw: List<ShotRecord>): V49SessionInsights {
        val records = recordsRaw.sortedBy { it.timestampMs }.takeLast(40)
        val recent10 = records.takeLast(10)

        // Feature 17: short-term momentum compares consecutive five-shot windows.
        val momentum = if (recent10.size >= 10) {
            recent10.takeLast(5).map { it.strokeScore.total }.average() - recent10.take(5).map { it.strokeScore.total }.average()
        } else null
        val momentumLabel = when {
            momentum == null -> "모멘텀 · 데이터 10구 필요"
            momentum >= 6.0 -> "모멘텀 상승 · 최근 5구 ${"%+.1f".format(momentum)}점"
            momentum <= -6.0 -> "모멘텀 하락 · 최근 5구 ${"%+.1f".format(momentum)}점"
            else -> "모멘텀 안정 · ${"%+.1f".format(momentum)}점"
        }

        // Feature 18: persistent start-line side bias is reported separately from generic score.
        val launches = records.takeLast(20).mapNotNull { r ->
            val q = V16MetricConfidenceEstimator.estimate(r.metrics)
            r.metrics.launchAngleDeg.takeIf { q.launch >= .55 && it.isFinite() }
        }
        val directionBias = median(launches)
        val sidePersistent = directionBias?.let { bias ->
            if (bias >= 0) launches.count { it > .35 } else launches.count { it < -.35 }
        } ?: 0
        val directionLabel = when {
            directionBias == null || launches.size < 6 -> "방향 편향 · 데이터 부족"
            abs(directionBias) < .35 -> "방향 편향 거의 없음 · 중앙값 ${"%+.2f".format(directionBias)}°"
            else -> "${if (directionBias > 0) "우측" else "좌측"} 출발 반복 · ${"%+.2f".format(directionBias)}° · $sidePersistent/${launches.size}구"
        }

        // Feature 19: pace bias only uses simple greens, avoiding break/terrain contamination.
        val pace = records.takeLast(24).mapNotNull { r ->
            val result = r.result ?: return@mapNotNull null
            if (r.terrainProfileId != -1 || abs(r.sideSlopePct) > .6 || abs(r.longSlopePct) > .8) return@mapNotNull null
            if (!result.finishY.isFinite() || r.targetDistanceM < 1.0) return@mapNotNull null
            (result.finishY - r.targetDistanceM) * 100.0
        }
        val paceBias = median(pace)
        val paceLabel = when {
            paceBias == null || pace.size < 5 -> "거리 편향 · 평지 데이터 부족"
            abs(paceBias) < 15.0 -> "거리감 중립 · 중앙 오차 ${"%+.0f".format(paceBias)}cm"
            paceBias < 0 -> "짧게 남는 경향 · ${"%.0f".format(abs(paceBias))}cm"
            else -> "길게 지나가는 경향 · +${"%.0f".format(paceBias)}cm"
        }

        // Feature 20: detect measurement-confidence deterioration within the same session.
        val confidenceValues = recent10.mapNotNull { it.metrics.confidence?.takeIf(Double::isFinite) }
        val confidenceDelta = if (confidenceValues.size >= 8) {
            val split = confidenceValues.size / 2
            (confidenceValues.drop(split).average() - confidenceValues.take(split).average()) * 100.0
        } else null
        val confidenceLabel = when {
            confidenceDelta == null -> "측정 신뢰도 추세 · 데이터 부족"
            confidenceDelta <= -8.0 -> "측정 신뢰도 하락 · ${"%.0f".format(confidenceDelta)}%p"
            confidenceDelta >= 6.0 -> "측정 신뢰도 회복 · +${"%.0f".format(confidenceDelta)}%p"
            else -> "측정 신뢰도 안정 · ${"%+.0f".format(confidenceDelta)}%p"
        }

        // Feature 21: late-session fade needs score degradation plus either confidence or tempo instability.
        val last20 = records.takeLast(20)
        val scoreFade = if (last20.size >= 12) {
            last20.takeLast(5).map { it.strokeScore.total }.average() - last20.take(5).map { it.strokeScore.total }.average()
        } else 0.0
        val recentTempo = last20.takeLast(6).mapNotNull { it.metrics.tempoRatio?.takeIf(Double::isFinite) }
        val tempoSpread = if (recentTempo.size >= 4) recentTempo.maxOrNull()!! - recentTempo.minOrNull()!! else 0.0
        val fade = last20.size >= 12 && scoreFade <= -9.0 && ((confidenceDelta ?: 0.0) <= -5.0 || tempoSpread >= .75)
        val fadeLabel = if (fade) {
            "후반 퍼포먼스 저하 감지 · 점수 ${"%.0f".format(scoreFade)}점 · 루틴 리셋 권장"
        } else "후반 급격한 저하 없음"

        // Feature 22: explicit personal-best shot makes improvement visible without averaging it away.
        val best = records.maxByOrNull { it.strokeScore.total }
        val bestLabel = best?.let { "세션 BEST ${it.strokeScore.total}점 · START ${"%+.2f".format(it.metrics.launchAngleDeg)}°" }
            ?: "세션 BEST · 데이터 없음"

        // Feature 23: trailing high-quality streak rewards repeatability, not a single lucky make.
        var streak = 0
        for (r in records.asReversed()) {
            val q = r.metrics.confidence ?: .55
            val good = r.strokeScore.total >= 80 && abs(r.metrics.launchAngleDeg) <= .8 && q >= .60
            if (!good) break
            streak++
        }
        val streakLabel = when {
            streak >= 5 -> "고품질 ${streak}구 연속 · 기준선 안정"
            streak >= 2 -> "고품질 ${streak}구 연속"
            else -> "고품질 연속 기록 대기"
        }

        // Feature 24: build a session-specific quick plan from the strongest current-session issue.
        val issue = chooseIssue(directionBias, paceBias, confidenceDelta, fade)
        val quickPlan = buildQuickPlan(issue, directionBias, paceBias)
        return V49SessionInsights(
            sampleCount = records.size,
            momentumDelta = momentum,
            momentumLabel = momentumLabel,
            directionBiasDeg = directionBias,
            directionLabel = directionLabel,
            paceBiasCm = paceBias,
            paceLabel = paceLabel,
            confidenceDeltaPct = confidenceDelta,
            confidenceLabel = confidenceLabel,
            lateSessionFade = fade,
            fadeLabel = fadeLabel,
            personalBestScore = best?.strokeScore?.total,
            personalBestLabel = bestLabel,
            consistencyStreak = streak,
            streakLabel = streakLabel,
            quickPlan = quickPlan,
            quickPlanReason = issue
        )
    }

    private fun chooseIssue(direction: Double?, paceCm: Double?, confidenceDelta: Double?, fade: Boolean): String = when {
        confidenceDelta != null && confidenceDelta <= -8.0 -> "카메라/측정 신뢰도 회복"
        fade -> "후반 루틴 재정렬"
        direction != null && abs(direction) >= .65 -> "스타트라인 ${if (direction > 0) "우측" else "좌측"} 편향 교정"
        paceCm != null && abs(paceCm) >= 25.0 -> "거리감 ${if (paceCm > 0) "오버" else "쇼트"} 교정"
        else -> "반복성 유지"
    }

    private fun buildQuickPlan(issue: String, direction: Double?, paceCm: Double?): V16DailyTrainingPlan {
        val focusDistance = when {
            paceCm != null && abs(paceCm) >= 25 -> 4.0
            else -> 2.5
        }
        val side = if (direction != null && abs(direction) >= .65) (-direction * .35).coerceIn(-1.2, 1.2) else .0
        return V16DailyTrainingPlan(
            title = "10분 퀵 교정",
            estimatedMinutes = 10,
            blocks = listOf(
                V16TrainingBlock("퀵 워밍업 스타트라인", 5, 1.5, .0, .0, "출발각 ±0.7°"),
                V16TrainingBlock("퀵 핵심 · $issue", 8, focusDistance, side, .0, "80점+ 또는 50cm 이내"),
                V16TrainingBlock("퀵 거리 랜덤", 6, max(3.0, focusDistance), .0, .0, "컵 잔여 35cm 이하"),
                V16TrainingBlock("퀵 압박 마무리", 3, 2.0, .0, .0, "3연속 성공")
            ),
            reason = issue
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }
}

object V49SessionInsightsRuntime {
    @Volatile var snapshot: V49SessionInsights = V49SessionInsightsEngine.analyze(emptyList())
        private set

    fun update(records: List<ShotRecord>) { snapshot = V49SessionInsightsEngine.analyze(records) }
}
