package com.puttvision.screen

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** More useful than a one-shot club recommendation: evaluate how each registered putter actually performs. */
data class V16PutterPerformance(
    val putterName: String,
    val shots: Int,
    val fitScore: Int,
    val faceStdDeg: Double?,
    val pathStdDeg: Double?,
    val impactStdMm: Double?,
    val launchStdDeg: Double,
    val speedCvPct: Double,
    val avgStrokeScore: Double,
    val makePct: Double,
    val avgMissCm: Double?,
    val rollEfficiency: Double?,
    val strengths: List<String>,
    val weaknesses: List<String>
)

data class V16PutterFit2Snapshot(
    val current: V16PutterPerformance?,
    val ranking: List<V16PutterPerformance>,
    val currentRecommendation: V15PutterFitRecommendation?,
    val verdict: String
)

object V16PutterFit2Runtime {
    @Volatile var snapshot: V16PutterFit2Snapshot = V16PutterFit2.analyze(emptyList())
        private set

    fun update(records: List<ShotRecord>) {
        snapshot = V16PutterFit2.analyze(records)
    }
}

object V16PutterFit2 {
    fun analyze(recordsRaw: List<ShotRecord>, currentName: String = ProductRuntime.putterProfileName): V16PutterFit2Snapshot {
        val groups = recordsRaw
            .filter { it.putterProfileName.isNotBlank() }
            .groupBy { it.putterProfileName }
        val ranking = groups.mapNotNull { (name, records) -> performance(name, records.takeLast(80)) }
            .sortedByDescending { it.fitScore }
        val current = ranking.firstOrNull { it.putterName == currentName }
        val basic = V15PutterFitter.fit(recordsRaw, currentName)
        val verdict = when {
            current == null -> "현재 퍼터 데이터가 부족합니다 · 같은 퍼터로 8구 이상 필요"
            ranking.size == 1 -> "현재 퍼터 적합도 ${current.fitScore}/100 · 다른 퍼터 데이터가 쌓이면 상대 비교 가능"
            ranking.first().putterName == currentName -> "등록 퍼터 중 현재 퍼터가 가장 안정적 · 적합도 ${current.fitScore}/100"
            else -> "${ranking.first().putterName}이 현재 퍼터보다 ${ranking.first().fitScore - current.fitScore}점 안정적"
        }
        return V16PutterFit2Snapshot(current, ranking, basic, verdict)
    }

    private fun performance(name: String, records: List<ShotRecord>): V16PutterPerformance? {
        if (records.size < 8) return null
        val faces = records.mapNotNull { it.metrics.faceAngleDeg }
        val paths = records.mapNotNull { it.metrics.pathAngleDeg }
        val impacts = records.mapNotNull { it.metrics.impactOffsetMm }
        val launch = records.map { it.metrics.launchAngleDeg }
        val speeds = records.map { it.metrics.ballSpeedMps }.filter { it.isFinite() && it > .05 }
        val misses = records.mapNotNull { it.result?.distanceToCupM?.times(100.0) }
        val rollQ = records.mapNotNull { V15PerformanceAnalyzer.assessRoll(it.metrics.roll)?.rollEfficiency?.toDouble() }
        val faceStd = std(faces)
        val pathStd = std(paths)
        val impactStd = std(impacts)
        val launchStd = std(launch) ?: 2.0
        val speedCv = cv(speeds)
        val stroke = records.map { it.strokeScore.total }.average()
        val make = records.count { it.result?.holed == true } * 100.0 / records.size
        val miss = misses.takeIf { it.isNotEmpty() }?.average()
        val roll = rollQ.takeIf { it.isNotEmpty() }?.average()

        var score = 100.0
        score -= (faceStd?.times(12.0) ?: 8.0).coerceAtMost(22.0)
        score -= (pathStd?.times(8.0) ?: 6.0).coerceAtMost(16.0)
        score -= (impactStd?.times(1.25) ?: 6.0).coerceAtMost(16.0)
        score -= (launchStd * 8.0).coerceAtMost(16.0)
        score -= (speedCv * .80).coerceAtMost(12.0)
        miss?.let { score -= (it / 18.0).coerceAtMost(10.0) }
        score += ((stroke - 75.0) * .18).coerceIn(-4.0, 4.0)
        roll?.let { score += ((it - 75.0) * .08).coerceIn(-3.0, 3.0) }
        if (records.size < 20) score = min(score, 88.0)

        val strengths = ArrayList<String>()
        val weaknesses = ArrayList<String>()
        if (faceStd != null) {
            if (faceStd <= .55) strengths += "페이스 반복성" else if (faceStd >= 1.0) weaknesses += "페이스 흔들림"
        }
        if (impactStd != null) {
            if (impactStd <= 3.5) strengths += "센터 임팩트" else if (impactStd >= 7.0) weaknesses += "정타 분산"
        }
        if (launchStd <= .55) strengths += "스타트라인" else if (launchStd >= 1.05) weaknesses += "출발각 분산"
        if (speedCv <= 6.0) strengths += "거리감 반복성" else if (speedCv >= 11.0) weaknesses += "볼스피드 편차"
        if (roll != null) {
            if (roll >= 82.0) strengths += "롤 품질" else if (roll < 68.0) weaknesses += "스키드/롤"
        }
        if (strengths.isEmpty()) strengths += "종합 밸런스"
        if (weaknesses.isEmpty()) weaknesses += "뚜렷한 약점 없음"

        return V16PutterPerformance(
            putterName = name,
            shots = records.size,
            fitScore = score.roundToInt().coerceIn(0, 100),
            faceStdDeg = faceStd,
            pathStdDeg = pathStd,
            impactStdMm = impactStd,
            launchStdDeg = launchStd,
            speedCvPct = speedCv,
            avgStrokeScore = stroke,
            makePct = make,
            avgMissCm = miss,
            rollEfficiency = roll,
            strengths = strengths,
            weaknesses = weaknesses
        )
    }

    private fun std(values: List<Double>): Double? {
        if (values.size < 4) return null
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / values.size)
    }

    private fun cv(values: List<Double>): Double {
        if (values.size < 4) return 0.0
        val mean = values.average().coerceAtLeast(.05)
        return (std(values) ?: .0) / mean * 100.0
    }
}
