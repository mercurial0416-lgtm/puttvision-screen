package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Stable solo-coaching focus derived from repeated, trustworthy evidence rather than one noisy shot. */
enum class V46CoachFocus(val label: String) {
    FACE("페이스"),
    PATH("패스"),
    IMPACT("정타"),
    PACE_SHORT("거리감 · 짧음"),
    PACE_LONG("거리감 · 김"),
    TEMPO("템포"),
    REPEATABILITY("반복성"),
    BALANCED("균형 유지")
}

enum class V46CoachTrend(val label: String) {
    IMPROVING("개선 중"),
    STABLE("유지"),
    WORSENING("악화 중")
}

data class V46CoachSignal(
    val focus: V46CoachFocus,
    val score: Int,
    val evidenceShots: Int,
    val qualifiedShots: Int,
    val confidence: Double,
    val signedBias: Double?,
    val detail: String
)

data class V46CoachPrescription(
    val title: String,
    val cue: String,
    val shots: Int,
    val distanceM: Double,
    val sideSlopePct: Double,
    val longSlopePct: Double,
    val successRule: String
)

data class V46AdaptiveCoachSnapshot(
    val focus: V46CoachFocus,
    val score: Int,
    val confidence: Double,
    val evidenceShots: Int,
    val qualifiedShots: Int,
    val coveragePct: Int,
    val trend: V46CoachTrend,
    val trendDelta: Int,
    val heldByHysteresis: Boolean,
    val headline: String,
    val detail: String,
    val prescription: V46CoachPrescription,
    val alternatives: List<V46CoachSignal>
)

object V46AdaptiveCoachEngine {
    private const val WINDOW = 30
    private const val MIN_RECORDS = 6
    private const val MIN_SIGNAL_SCORE = 38
    private const val HOLD_SCORE = 42
    private const val SWITCH_MARGIN = 12

    fun analyze(
        recordsRaw: List<ShotRecord>,
        previousFocus: V46CoachFocus? = null
    ): V46AdaptiveCoachSnapshot? {
        val records = recordsRaw
            .sortedBy { it.timestampMs }
            .takeLast(WINDOW)
        if (records.size < MIN_RECORDS) return null

        val signals = buildSignals(records).sortedByDescending { it.score }
        val best = signals.firstOrNull()?.takeIf { it.score >= MIN_SIGNAL_SCORE }
        val previous = previousFocus
            ?.takeIf { it != V46CoachFocus.BALANCED }
            ?.let { focus -> signals.firstOrNull { it.focus == focus } }

        val held = best != null && previous != null && best.focus != previous.focus &&
            previous.score >= HOLD_SCORE && best.score < previous.score + SWITCH_MARGIN
        val selected = when {
            held -> previous
            best != null -> best
            else -> balancedSignal(records)
        }

        val trend = trendFor(records, selected.focus)
        val coverage = if (records.isEmpty()) 0 else
            (selected.qualifiedShots * 100.0 / records.size).roundToInt().coerceIn(0, 100)
        val confidence = selected.confidence.coerceIn(0.0, 1.0)
        val prescription = prescription(selected.focus, trend.first)
        val headline = when (selected.focus) {
            V46CoachFocus.BALANCED -> "큰 반복 결함 없음 · 랜덤 거리로 유지"
            else -> "오늘 1순위 · ${selected.focus.label} ${selected.score}/100"
        }
        val trendText = when (trend.first) {
            V46CoachTrend.IMPROVING -> "최근 절반에서 심각도 ${abs(trend.second)}점 감소"
            V46CoachTrend.WORSENING -> "최근 절반에서 심각도 ${trend.second}점 증가"
            V46CoachTrend.STABLE -> "최근 변화 ${if (trend.second >= 0) "+" else ""}${trend.second}점"
        }
        val holdText = if (held) " · 단발 변화 때문에 기존 포커스를 유지" else ""
        val detail = "${selected.detail} · 근거 ${selected.evidenceShots}/${selected.qualifiedShots}구 · 데이터 커버리지 ${coverage}% · $trendText$holdText"

        return V46AdaptiveCoachSnapshot(
            focus = selected.focus,
            score = selected.score,
            confidence = confidence,
            evidenceShots = selected.evidenceShots,
            qualifiedShots = selected.qualifiedShots,
            coveragePct = coverage,
            trend = trend.first,
            trendDelta = trend.second,
            heldByHysteresis = held,
            headline = headline,
            detail = detail,
            prescription = prescription,
            alternatives = signals.filter { it.focus != selected.focus }.take(3)
        )
    }

    private fun buildSignals(records: List<ShotRecord>): List<V46CoachSignal> {
        val out = ArrayList<V46CoachSignal>()
        directionalSignal(records, V46CoachFocus.FACE)?.let(out::add)
        directionalSignal(records, V46CoachFocus.PATH)?.let(out::add)
        impactSignal(records)?.let(out::add)
        paceSignal(records)?.let(out::add)
        tempoSignal(records)?.let(out::add)
        repeatabilitySignal(records)?.let(out::add)
        return out
    }

    private fun directionalSignal(records: List<ShotRecord>, focus: V46CoachFocus): V46CoachSignal? {
        val values = ArrayList<Pair<Double, Double>>()
        records.forEach { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            val value = when (focus) {
                V46CoachFocus.FACE -> record.metrics.faceAngleDeg
                V46CoachFocus.PATH -> record.metrics.pathAngleDeg
                else -> null
            } ?: return@forEach
            val trust = if (focus == V46CoachFocus.FACE) q.face else q.path
            if (trust >= .55 && value.isFinite()) values += value to trust
        }
        if (values.size < 5) return null
        val raw = values.map { it.first }
        val bias = median(raw) ?: return null
        val magnitude = median(raw.map(::abs)) ?: return null
        val positive = raw.count { it > .25 }
        val negative = raw.count { it < -.25 }
        val persistent = max(positive, negative)
        val persistence = persistent.toDouble() / raw.size
        val threshold = if (focus == V46CoachFocus.FACE) .28 else .38
        val scale = if (focus == V46CoachFocus.FACE) 1.05 else 1.35
        val magnitudeScore = ((magnitude - threshold) / scale * 72.0).coerceIn(0.0, 72.0)
        val persistenceScore = ((persistence - .45) / .45 * 28.0).coerceIn(0.0, 28.0)
        val score = (magnitudeScore + persistenceScore).roundToInt().coerceIn(0, 100)
        val confidence = values.map { it.second }.average()
        val side = if (bias >= 0.0) {
            if (focus == V46CoachFocus.FACE) "오픈" else "우측"
        } else {
            if (focus == V46CoachFocus.FACE) "클로즈" else "좌측"
        }
        return V46CoachSignal(
            focus = focus,
            score = score,
            evidenceShots = persistent,
            qualifiedShots = raw.size,
            confidence = confidence,
            signedBias = bias,
            detail = "${focus.label} 중앙값 ${"%+.2f".format(bias)}° · $side 방향 반복 ${(persistence * 100).roundToInt()}%"
        )
    }

    private fun impactSignal(records: List<ShotRecord>): V46CoachSignal? {
        val values = ArrayList<Pair<Double, Double>>()
        records.forEach { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            val impact = record.metrics.impactOffsetMm ?: return@forEach
            if (q.impact >= .48 && impact.isFinite()) values += impact to q.impact
        }
        if (values.size < 5) return null
        val raw = values.map { it.first }
        val bias = median(raw) ?: return null
        val absMedian = median(raw.map(::abs)) ?: return null
        val persistent = raw.count { abs(it) >= 3.5 }
        val persistence = persistent.toDouble() / raw.size
        val score = (
            ((absMedian - 1.8) / 7.0 * 74.0).coerceIn(0.0, 74.0) +
                ((persistence - .35) / .55 * 26.0).coerceIn(0.0, 26.0)
            ).roundToInt().coerceIn(0, 100)
        return V46CoachSignal(
            focus = V46CoachFocus.IMPACT,
            score = score,
            evidenceShots = persistent,
            qualifiedShots = raw.size,
            confidence = values.map { it.second }.average(),
            signedBias = bias,
            detail = "정타 중앙값 ${"%+.1f".format(bias)}mm · 3.5mm+ 편차 ${(persistence * 100).roundToInt()}%"
        )
    }

    /** Pace is isolated to simple greens so break/terrain does not masquerade as distance control. */
    private fun paceSignal(records: List<ShotRecord>): V46CoachSignal? {
        val pace = records.mapNotNull { record ->
            val result = record.result ?: return@mapNotNull null
            if (record.terrainProfileId != -1 || abs(record.sideSlopePct) > .60 || abs(record.longSlopePct) > .80) {
                return@mapNotNull null
            }
            if (record.targetDistanceM < 1.0 || !result.finishY.isFinite()) return@mapNotNull null
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            if (q.ballSpeed < .55) return@mapNotNull null
            (result.finishY - record.targetDistanceM) to q.ballSpeed
        }
        if (pace.size < 5) return null
        val raw = pace.map { it.first }
        val bias = median(raw) ?: return null
        val absMedian = median(raw.map(::abs)) ?: return null
        val direction = if (bias < 0.0) V46CoachFocus.PACE_SHORT else V46CoachFocus.PACE_LONG
        val sameDirection = if (bias < 0.0) raw.count { it < -.12 } else raw.count { it > .12 }
        val persistence = sameDirection.toDouble() / raw.size
        val score = (
            ((absMedian - .12) / .70 * 76.0).coerceIn(0.0, 76.0) +
                ((persistence - .45) / .45 * 24.0).coerceIn(0.0, 24.0)
            ).roundToInt().coerceIn(0, 100)
        return V46CoachSignal(
            focus = direction,
            score = score,
            evidenceShots = sameDirection,
            qualifiedShots = raw.size,
            confidence = pace.map { it.second }.average(),
            signedBias = bias,
            detail = "평지 계열 종방향 중앙 오차 ${"%+.0f".format(bias * 100.0)}cm · 같은 방향 ${(persistence * 100).roundToInt()}%"
        )
    }

    private fun tempoSignal(records: List<ShotRecord>): V46CoachSignal? {
        val values = records.mapNotNull { record ->
            val ratio = record.metrics.tempoRatio ?: return@mapNotNull null
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            if (q.headSpeed < .45 || !ratio.isFinite() || ratio !in .5..5.0) return@mapNotNull null
            ratio to q.headSpeed
        }
        if (values.size < 5) return null
        val ratios = values.map { it.first }
        val errors = ratios.map { abs(it - 2.0) }
        val errorMedian = median(errors) ?: return null
        val unstable = ratios.count { it !in 1.45..2.75 }
        val spread = robustSpread(ratios)
        val score = (
            ((errorMedian - .22) / .95 * 62.0).coerceIn(0.0, 62.0) +
                (unstable.toDouble() / ratios.size * 20.0).coerceIn(0.0, 20.0) +
                ((spread - .12) / .65 * 18.0).coerceIn(0.0, 18.0)
            ).roundToInt().coerceIn(0, 100)
        return V46CoachSignal(
            focus = V46CoachFocus.TEMPO,
            score = score,
            evidenceShots = unstable,
            qualifiedShots = ratios.size,
            confidence = values.map { it.second }.average(),
            signedBias = (median(ratios) ?: 2.0) - 2.0,
            detail = "템포 중앙값 ${"%.2f".format(median(ratios) ?: 0.0)}:1 · 허용범위 밖 $unstable/${ratios.size}구"
        )
    }

    private fun repeatabilitySignal(records: List<ShotRecord>): V46CoachSignal? {
        val launches = records.mapNotNull { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            record.metrics.launchAngleDeg.takeIf { q.launch >= .55 && it.isFinite() }
        }
        val speeds = records.mapNotNull { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            record.metrics.ballSpeedMps.takeIf { q.ballSpeed >= .58 && it.isFinite() && it > .05 }
        }
        val n = min(launches.size, speeds.size)
        if (n < 6) return null
        val launchSpread = robustSpread(launches)
        val speedCv = robustCvPct(speeds)
        val score = (
            ((launchSpread - .30) / 1.10 * 58.0).coerceIn(0.0, 58.0) +
                ((speedCv - 5.0) / 15.0 * 42.0).coerceIn(0.0, 42.0)
            ).roundToInt().coerceIn(0, 100)
        val evidence = records.count { abs(it.metrics.launchAngleDeg) >= .70 }
        return V46CoachSignal(
            focus = V46CoachFocus.REPEATABILITY,
            score = score,
            evidenceShots = evidence,
            qualifiedShots = n,
            confidence = records.map { V16MetricConfidenceEstimator.estimate(it.metrics).launch }.average().coerceIn(0.0, 1.0),
            signedBias = null,
            detail = "강건 출발각 분산 ${"%.2f".format(launchSpread)}° · 볼스피드 강건 CV ${"%.1f".format(speedCv)}%"
        )
    }

    private fun trendFor(records: List<ShotRecord>, focus: V46CoachFocus): Pair<V46CoachTrend, Int> {
        if (focus == V46CoachFocus.BALANCED || records.size < 12) return V46CoachTrend.STABLE to 0
        val recent = records.takeLast(min(20, records.size))
        val split = recent.size / 2
        val old = signalForFocus(recent.take(split), focus)?.score
        val newest = signalForFocus(recent.drop(split), focus)?.score
        if (old == null || newest == null) return V46CoachTrend.STABLE to 0
        val delta = newest - old
        val trend = when {
            delta <= -8 -> V46CoachTrend.IMPROVING
            delta >= 8 -> V46CoachTrend.WORSENING
            else -> V46CoachTrend.STABLE
        }
        return trend to delta
    }

    private fun signalForFocus(records: List<ShotRecord>, focus: V46CoachFocus): V46CoachSignal? = when (focus) {
        V46CoachFocus.FACE, V46CoachFocus.PATH -> directionalSignal(records, focus)
        V46CoachFocus.IMPACT -> impactSignal(records)
        V46CoachFocus.PACE_SHORT, V46CoachFocus.PACE_LONG -> paceSignal(records)?.takeIf { it.focus == focus }
        V46CoachFocus.TEMPO -> tempoSignal(records)
        V46CoachFocus.REPEATABILITY -> repeatabilitySignal(records)
        V46CoachFocus.BALANCED -> null
    }

    private fun balancedSignal(records: List<ShotRecord>) = V46CoachSignal(
        focus = V46CoachFocus.BALANCED,
        score = 20,
        evidenceShots = records.size,
        qualifiedShots = records.size,
        confidence = records.map { (it.metrics.confidence ?: .55).coerceIn(.2, .99) }.average(),
        signedBias = null,
        detail = "최근 ${records.size}구에서 반복되는 단일 결함이 임계치를 넘지 않음"
    )

    private fun prescription(focus: V46CoachFocus, trend: V46CoachTrend): V46CoachPrescription {
        val extra = if (trend == V46CoachTrend.WORSENING) 2 else 0
        return when (focus) {
            V46CoachFocus.FACE -> V46CoachPrescription(
                "스타트라인 페이스 고정", "임팩트 순간 페이스만 목표선에 직각", 10 + extra, 1.5, .0, .0,
                "10구 중 8구 출발각 ±0.7°"
            )
            V46CoachFocus.PATH -> V46CoachPrescription(
                "게이트 패스 반복", "헤드를 목표선 방향으로 임팩트 후 10cm 통과", 10 + extra, 2.0, .0, .0,
                "PATH ±0.8° 8/10"
            )
            V46CoachFocus.IMPACT -> V46CoachPrescription(
                "센터 컨택트", "공 중심과 퍼터 중심점 하나만 맞추기", 10 + extra, 2.0, .0, .0,
                "IMPACT ±4mm 8/10"
            )
            V46CoachFocus.PACE_SHORT -> V46CoachPrescription(
                "4m 도달 거리", "임팩트에서 멈추지 말고 피니시 길이까지 보내기", 10 + extra, 4.0, .0, .0,
                "컵 기준 ±35cm 8/10"
            )
            V46CoachFocus.PACE_LONG -> V46CoachPrescription(
                "4m 거리 압축", "힘을 빼려 하지 말고 백스윙 길이만 한 단계 줄이기", 10 + extra, 4.0, .0, .0,
                "컵 기준 ±35cm 8/10"
            )
            V46CoachFocus.TEMPO -> V46CoachPrescription(
                "2:1 리듬", "백스윙은 여유 있게, 전환 후 한 번에 통과", 10 + extra, 3.0, .0, .0,
                "TEMPO 1.45~2.75:1 8/10"
            )
            V46CoachFocus.REPEATABILITY -> V46CoachPrescription(
                "동일 셋업 10구", "발 위치·공 위치를 바꾸지 말고 같은 루틴 반복", 10 + extra, 3.0, .0, .0,
                "출발각 ±0.7° 8/10"
            )
            V46CoachFocus.BALANCED -> V46CoachPrescription(
                "랜덤 거리 유지", "수치 수정 없이 루틴과 거리감만 유지", 10, 4.0, .0, .0,
                "컵 기준 35cm 이하 7/10"
            )
        }
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** 1.4826 * MAD is resistant to one-off bad detections and roughly comparable to standard deviation. */
    private fun robustSpread(values: List<Double>): Double {
        val center = median(values) ?: return .0
        val mad = median(values.map { abs(it - center) }) ?: return .0
        return mad * 1.4826
    }

    private fun robustCvPct(values: List<Double>): Double {
        val center = (median(values) ?: return .0).coerceAtLeast(.05)
        return robustSpread(values) / center * 100.0
    }
}

object V46AdaptiveTrainingPlan {
    fun adapt(base: V16DailyTrainingPlan, snapshot: V46AdaptiveCoachSnapshot?): V16DailyTrainingPlan {
        val snap = snapshot ?: return base
        if (base.blocks.size < 2) return base
        val p = snap.prescription
        val blocks = base.blocks.toMutableList()
        blocks[1] = V16TrainingBlock(
            title = p.title,
            shots = p.shots.coerceIn(8, 14),
            distanceM = p.distanceM.coerceIn(1.0, 8.0),
            sideSlopePct = p.sideSlopePct.coerceIn(-3.0, 3.0),
            longSlopePct = p.longSlopePct.coerceIn(-3.0, 3.0),
            successRule = p.successRule
        )
        return base.copy(
            title = "오늘의 15분 퍼팅 · ${snap.focus.label}",
            blocks = blocks,
            reason = "${snap.headline} · ${p.cue}"
        )
    }
}

/** Keeps focus sticky within a user profile; switching profiles cannot inherit another player's weakness. */
object V46AdaptiveCoachRuntime {
    @Volatile var snapshot: V46AdaptiveCoachSnapshot? = null
        private set
    @Volatile private var profileId: String? = null

    @Synchronized
    fun update(records: List<ShotRecord>) {
        if (records.isEmpty()) {
            snapshot = null
            profileId = null
            return
        }
        val currentProfile = records.last().userProfileId
        val previous = if (profileId == currentProfile) snapshot?.focus else null
        profileId = currentProfile
        snapshot = V46AdaptiveCoachEngine.analyze(records, previous)
    }

    @Synchronized
    fun clear() {
        snapshot = null
        profileId = null
    }
}
