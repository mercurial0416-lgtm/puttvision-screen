package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

enum class V20ReadMode(val label: String) {
    OFF("끄기"),
    AUTO("랜덤 경사 자동"),
    ALWAYS("항상 블라인드")
}

data class V20ReadFeedback(
    val active: Boolean,
    val revealed: Boolean,
    val chosenAimCm: Double? = null,
    val idealAimCm: Double? = null,
    val aimErrorCm: Double? = null,
    val chosenSpeedMps: Double? = null,
    val idealSpeedMps: Double? = null,
    val speedErrorMps: Double? = null,
    val grade: String = "--",
    val headline: String = "그린 읽기",
    val detail: String = ""
)

/**
 * PuttView-style training concept adapted for a TV: the solution stays hidden until the player
 * commits to a real launch line/speed. The player's launch itself is the answer, so no controller
 * input is required.
 */
object V20GreenReadTrainingRuntime {
    @Volatile var mode: V20ReadMode = V20ReadMode.AUTO
    @Volatile var feedback: V20ReadFeedback = V20ReadFeedback(active = false, revealed = false)
        private set

    fun shouldHideSolution(practiceMode: PracticeMode, settings: GreenSettings): Boolean = when (mode) {
        V20ReadMode.OFF -> false
        V20ReadMode.ALWAYS -> abs(settings.sideSlopePct) >= .05 || settings.terrainProfileId >= 0
        V20ReadMode.AUTO -> practiceMode == PracticeMode.RANDOM_SLOPE
    }

    fun prepare(practiceMode: PracticeMode, settings: GreenSettings) {
        val active = shouldHideSolution(practiceMode, settings)
        feedback = V20ReadFeedback(
            active = active,
            revealed = false,
            headline = if (active) "READ IT" else "그린 읽기",
            detail = if (active) "라인과 스피드를 먼저 결정하세요 · 정답은 샷 후 공개" else ""
        )
        if (active) GreenReadRuntime.prefetch(settings)
    }

    fun commit(metrics: ShotMetrics, practiceMode: PracticeMode, settings: GreenSettings) {
        if (!shouldHideSolution(practiceMode, settings)) {
            feedback = V20ReadFeedback(active = false, revealed = false)
            return
        }
        val actualAimCm = tan(Math.toRadians(metrics.launchAngleDeg)) * settings.holeDistanceM * 100.0
        val solved = GreenReadRuntime.peekOrSchedule(settings)
        feedback = feedback.copy(
            active = true,
            revealed = false,
            chosenAimCm = actualAimCm,
            chosenSpeedMps = metrics.ballSpeedMps,
            idealAimCm = solved?.aimOffsetCm,
            idealSpeedMps = solved?.recommendedBallSpeedMps,
            headline = "라인 확정",
            detail = "공이 멈추면 정답 라인을 공개합니다"
        )
    }

    fun reveal(settings: GreenSettings) {
        if (!feedback.active) return
        val solved = GreenReadRuntime.peek(settings) ?: GreenReadRuntime.peekOrSchedule(settings)
        if (solved == null) {
            feedback = feedback.copy(
                revealed = true,
                headline = "정답 계산 중",
                detail = "물리 솔버 완료 후 다음 샷에서 비교합니다"
            )
            return
        }
        val chosenAim = feedback.chosenAimCm ?: 0.0
        val chosenSpeed = feedback.chosenSpeedMps ?: 0.0
        val aimError = abs(chosenAim - solved.aimOffsetCm)
        val speedError = abs(chosenSpeed - solved.recommendedBallSpeedMps)
        val grade = when {
            aimError <= 3.0 && speedError <= .10 -> "S"
            aimError <= 6.0 && speedError <= .18 -> "A"
            aimError <= 11.0 && speedError <= .28 -> "B"
            aimError <= 18.0 && speedError <= .42 -> "C"
            else -> "D"
        }
        feedback = feedback.copy(
            revealed = true,
            idealAimCm = solved.aimOffsetCm,
            idealSpeedMps = solved.recommendedBallSpeedMps,
            aimErrorCm = aimError,
            speedErrorMps = speedError,
            grade = grade,
            headline = "GREEN READ $grade",
            detail = "에임 오차 ${"%.1f".format(aimError)}cm · 스피드 오차 ${"%.2f".format(speedError)}m/s"
        )
    }

    fun cycleMode(): V20ReadMode {
        val values = V20ReadMode.entries
        mode = values[(values.indexOf(mode) + 1) % values.size]
        feedback = V20ReadFeedback(active = false, revealed = false)
        return mode
    }

    fun reset() {
        feedback = V20ReadFeedback(active = false, revealed = false)
    }
}

data class V20PerformanceRow(
    val label: String,
    val shots: Int,
    val score: Double,
    val launchAbsDeg: Double,
    val launchStdDeg: Double,
    val faceAbsDeg: Double?,
    val pathAbsDeg: Double?,
    val distanceErrorCm: Double?,
    val makePct: Double
)

data class V20TrendComparison(
    val baselineShots: Int,
    val recentShots: Int,
    val scoreDelta: Double,
    val launchStdDeltaDeg: Double,
    val faceAbsDeltaDeg: Double?,
    val distanceErrorDeltaCm: Double?,
    val improved: Boolean,
    val summary: String
)

data class V20PerformanceReport(
    val putters: List<V20PerformanceRow>,
    val trend: V20TrendComparison?,
    val headline: String,
    val detail: String
)

object V20PerformanceCompare {
    fun build(records: List<ShotRecord>): V20PerformanceReport {
        val clean = records.filter { it.metrics.confidence == null || (it.metrics.confidence ?: 0.0) >= .35 }
        val putters = clean
            .filter { !it.putterProfileName.isNullOrBlank() }
            .groupBy { it.putterProfileName!!.trim() }
            .mapNotNull { (name, shots) -> if (shots.size >= 6) row(name, shots) else null }
            .sortedByDescending { it.score }

        val trend = if (clean.size >= 24) {
            val recentN = minOf(20, clean.size / 2)
            val recent = clean.takeLast(recentN)
            val baseline = clean.dropLast(recentN).takeLast(recentN)
            compareWindows(baseline, recent)
        } else null

        val headline = when {
            putters.size >= 2 -> "퍼터 비교 · ${putters.first().label} 우세"
            trend?.improved == true -> "최근 스트로크 개선 중"
            trend != null -> "최근 변동 구간 확인 필요"
            else -> "비교 데이터 수집 중"
        }
        val detail = when {
            putters.size >= 2 -> "${putters.first().shots}구 기준 종합 ${"%.1f".format(putters.first().score)}점 · 2위와 ${"%.1f".format(putters.first().score - putters[1].score)}점 차"
            trend != null -> trend.summary
            else -> "동일 퍼터 6구 이상 또는 전체 24구 이상에서 전후 비교가 열립니다"
        }
        return V20PerformanceReport(putters, trend, headline, detail)
    }

    private fun row(label: String, records: List<ShotRecord>): V20PerformanceRow {
        val launches = records.map { it.metrics.launchAngleDeg }
        val faces = records.mapNotNull { it.metrics.faceAngleDeg }
        val paths = records.mapNotNull { it.metrics.pathAngleDeg }
        val dist = records.mapNotNull { it.result?.distanceToCupM?.times(100.0) }
        val makes = records.count { it.result?.holed == true }
        val avgScore = records.map { it.strokeScore.total.toDouble() }.average()
        val launchAbs = launches.map(::abs).average()
        val launchStd = std(launches)
        val faceAbs = faces.takeIf { it.isNotEmpty() }?.map(::abs)?.average()
        val pathAbs = paths.takeIf { it.isNotEmpty() }?.map(::abs)?.average()
        val distError = dist.takeIf { it.isNotEmpty() }?.average()
        val makePct = makes * 100.0 / records.size
        val precisionPenalty = launchStd * 4.0 + (faceAbs ?: .7) * 2.4 + (distError ?: 18.0) * .07
        val score = (avgScore * .72 + makePct * .18 + 18.0 - precisionPenalty).coerceIn(0.0, 100.0)
        return V20PerformanceRow(label, records.size, score, launchAbs, launchStd, faceAbs, pathAbs, distError, makePct)
    }

    private fun compareWindows(baseline: List<ShotRecord>, recent: List<ShotRecord>): V20TrendComparison {
        val a = row("baseline", baseline)
        val b = row("recent", recent)
        val scoreDelta = b.score - a.score
        val launchDelta = b.launchStdDeg - a.launchStdDeg
        val faceDelta = if (a.faceAbsDeg != null && b.faceAbsDeg != null) b.faceAbsDeg - a.faceAbsDeg else null
        val distDelta = if (a.distanceErrorCm != null && b.distanceErrorCm != null) b.distanceErrorCm - a.distanceErrorCm else null
        val improvementPoints = scoreDelta - launchDelta * 2.0 - (faceDelta ?: 0.0) - (distDelta ?: 0.0) * .05
        val improved = improvementPoints > 1.0
        val summary = "종합 ${if (scoreDelta >= 0) "+" else ""}${"%.1f".format(scoreDelta)}점 · 출발 분산 ${if (launchDelta >= 0) "+" else ""}${"%.2f".format(launchDelta)}°" +
            (distDelta?.let { " · 거리오차 ${if (it >= 0) "+" else ""}${"%.1f".format(it)}cm" } ?: "")
        return V20TrendComparison(a.shots, b.shots, scoreDelta, launchDelta, faceDelta, distDelta, improved, summary)
    }

    private fun std(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val avg = values.average()
        return sqrt(values.sumOf { (it - avg) * (it - avg) } / values.size)
    }
}

object V20PerformanceRuntime {
    @Volatile var report: V20PerformanceReport = V20PerformanceReport(emptyList(), null, "비교 데이터 수집 중", "")
        private set

    fun update(records: List<ShotRecord>) {
        report = V20PerformanceCompare.build(records)
    }
}

data class V20ReferenceExpected(
    val id: String,
    val ballSpeedMps: Double,
    val launchDeg: Double,
    val faceDeg: Double? = null,
    val pathDeg: Double? = null,
    val ballToleranceMps: Double = .08,
    val launchToleranceDeg: Double = .35,
    val faceToleranceDeg: Double = .55,
    val pathToleranceDeg: Double = .65
)

data class V20RegressionMeasurement(
    val id: String,
    val ballSpeedMps: Double,
    val launchDeg: Double,
    val faceDeg: Double? = null,
    val pathDeg: Double? = null
)

data class V20RegressionReport(
    val total: Int,
    val passed: Int,
    val failedIds: List<String>,
    val ballMae: Double,
    val launchMae: Double,
    val faceMae: Double?,
    val pathMae: Double?,
    val passedGate: Boolean
)

/** Deterministic gate that can be fed by real-device/video reference fixtures in CI. */
object V20RegressionGate {
    fun evaluate(expected: List<V20ReferenceExpected>, measured: List<V20RegressionMeasurement>): V20RegressionReport {
        val byId = measured.associateBy { it.id }
        val failures = ArrayList<String>()
        val ballErrors = ArrayList<Double>()
        val launchErrors = ArrayList<Double>()
        val faceErrors = ArrayList<Double>()
        val pathErrors = ArrayList<Double>()
        expected.forEach { e ->
            val m = byId[e.id]
            if (m == null) {
                failures += e.id
                return@forEach
            }
            val be = abs(m.ballSpeedMps - e.ballSpeedMps)
            val le = abs(m.launchDeg - e.launchDeg)
            ballErrors += be
            launchErrors += le
            var failed = be > e.ballToleranceMps || le > e.launchToleranceDeg
            if (e.faceDeg != null) {
                val error = m.faceDeg?.let { abs(it - e.faceDeg) }
                if (error == null || error > e.faceToleranceDeg) failed = true else faceErrors += error
            }
            if (e.pathDeg != null) {
                val error = m.pathDeg?.let { abs(it - e.pathDeg) }
                if (error == null || error > e.pathToleranceDeg) failed = true else pathErrors += error
            }
            if (failed) failures += e.id
        }
        fun avg(values: List<Double>) = if (values.isEmpty()) 0.0 else values.average()
        return V20RegressionReport(
            total = expected.size,
            passed = expected.size - failures.size,
            failedIds = failures,
            ballMae = avg(ballErrors),
            launchMae = avg(launchErrors),
            faceMae = faceErrors.takeIf { it.isNotEmpty() }?.average(),
            pathMae = pathErrors.takeIf { it.isNotEmpty() }?.average(),
            passedGate = expected.isNotEmpty() && failures.isEmpty()
        )
    }
}
