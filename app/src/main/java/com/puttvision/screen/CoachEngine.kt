package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.sqrt

data class CoachFeedback(
    val headline: String,
    val detail: String,
    val priority: Int
)

object CoachEngine {

    fun diagnose(
        metrics: ShotMetrics,
        score: StrokeScore,
        recent: List<ShotRecord>
    ): CoachFeedback {
        val snapshot = V15PerformanceAnalyzer.analyze(metrics, recent)
        val trust = V16MetricConfidenceEstimator.estimate(metrics)
        val face = metrics.faceAngleDeg
        val path = metrics.pathAngleDeg
        val launch = metrics.launchAngleDeg

        // A large-looking value is not promoted to a root cause unless that feature itself is trustworthy.
        if (face != null && trust.face >= .58 && abs(face) >= 1.0) {
            val direction = if (face > 0) "열림" else "닫힘"
            return CoachFeedback(
                headline = "페이스 $direction ${"%+.2f".format(face)}°",
                detail = "신뢰도 ${(trust.face * 100).toInt()}% · 출발 방향의 1순위 원인 · ${snapshot.training.drill} · 목표 ${snapshot.training.target}",
                priority = 100
            )
        }

        if (path != null && trust.path >= .58 && abs(path) >= 1.3) {
            val direction = if (path > 0) "우측" else "좌측"
            return CoachFeedback(
                headline = "패스가 $direction ${"%+.2f".format(path)}°",
                detail = "신뢰도 ${(trust.path * 100).toInt()}% · 헤드 궤적 편차가 큼 · ${snapshot.signature.arcType.label} 타입 · 패스 재현성을 먼저 안정화",
                priority = 94
            )
        }

        metrics.impactOffsetMm?.let {
            if (trust.impact >= .48 && abs(it) >= 7.0) {
                val side = if (it > 0) "토 쪽" else "힐 쪽"
                return CoachFeedback(
                    headline = "임팩트 $side ${"%.1f".format(abs(it))}mm",
                    detail = "신뢰도 ${(trust.impact * 100).toInt()}% · 정타 편차가 큼 · ${snapshot.training.drill} · 목표 ${snapshot.training.target}",
                    priority = 92
                )
            }
        }

        if (trust.headSpeed >= .48 && snapshot.signature.decelerationRisk) {
            return CoachFeedback(
                headline = "임팩트 구간 감속 패턴",
                detail = "헤드 추적 신뢰도 ${(trust.headSpeed * 100).toInt()}% · 백스윙 크기보다 임팩트 이후 가속 유지가 우선 · ${snapshot.training.drill}",
                priority = 90
            )
        }

        snapshot.roll?.let { roll ->
            if ((metrics.roll?.confidence ?: .0) >= .48 && roll.rollEfficiency < 72) {
                return CoachFeedback(
                    headline = "롤 효율 ${roll.grade} · ${roll.rollEfficiency}점",
                    detail = roll.hint + " · 마킹볼 기준 스키드 " + (roll.skidDistanceCm?.let { "%.1fcm".format(it) } ?: "측정 부족"),
                    priority = 88
                )
            }
        }

        metrics.tempoRatio?.let {
            if (trust.headSpeed >= .48 && (it < 1.45 || it > 2.75)) {
                return CoachFeedback(
                    headline = "템포 편차 ${"%.2f".format(it)}:1",
                    detail = "백스윙과 다운스윙 리듬이 불안정 · ${snapshot.training.drill}",
                    priority = 84
                )
            }
        }

        if (recent.size >= 6) {
            persistentPattern(recent)?.let { return it }
        }

        if (trust.launch >= .55 && abs(launch) >= 0.8) {
            val side = if (launch > 0) "우측" else "좌측"
            return CoachFeedback(
                headline = "출발이 $side ${"%.2f".format(abs(launch))}°",
                detail = "START 신뢰도 ${(trust.launch * 100).toInt()}% · 페이스/패스가 크게 무너지진 않았지만 출발선 편차가 남음 · 반복성 ${snapshot.signature.repeatability}점",
                priority = 78
            )
        }

        if (snapshot.signature.repeatability < 78) {
            return CoachFeedback(
                headline = "단발 수치보다 반복성 ${snapshot.signature.repeatability}점",
                detail = "최근 샷 분산이 큼 · 한 샷 원인 단정 대신 ${snapshot.training.drill} · 목표 ${snapshot.training.target}",
                priority = 72
            )
        }

        val adaptive = V46AdaptiveCoachRuntime.snapshot
        if (adaptive != null && adaptive.focus != V46CoachFocus.BALANCED && adaptive.score >= 50) {
            return CoachFeedback(
                headline = "누적 포커스 · ${adaptive.focus.label} ${adaptive.score}/100",
                detail = "${adaptive.prescription.cue} · ${adaptive.trend.label} · 근거 ${adaptive.evidenceShots}/${adaptive.qualifiedShots}구",
                priority = 68
            )
        }

        val fitting = V15PutterFitter.fit(recent)
        val fittingText = fitting?.let {
            " · 피팅 ${it.balance.label}/${it.head.label} (${(it.confidence * 100).toInt()}%)"
        }.orEmpty()
        return CoachFeedback(
            headline = "${snapshot.signature.arcType.label} · 스트로크 안정적 · ${score.total}점",
            detail = "큰 결함 없음 · ${snapshot.training.title}: ${snapshot.training.drill}$fittingText",
            priority = 20
        )
    }

    private fun persistentPattern(recent: List<ShotRecord>): CoachFeedback? {
        val last = recent.takeLast(14)
        val trustedLaunch = last.mapNotNull { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            record.metrics.launchAngleDeg.takeIf { q.launch >= .55 && it.isFinite() }
        }.takeLast(10)
        if (trustedLaunch.size >= 6) {
            val right = trustedLaunch.count { it > .45 }
            val left = trustedLaunch.count { it < -.45 }
            if (right >= 6) {
                return CoachFeedback(
                    "최근 신뢰 샷 우측 미스 $right/${trustedLaunch.size}",
                    "단발성이 아님 · 페이스 오픈/어드레스 정렬을 먼저 확인하고 1.5m 스타트라인 드릴 권장",
                    76
                )
            }
            if (left >= 6) {
                return CoachFeedback(
                    "최근 신뢰 샷 좌측 미스 $left/${trustedLaunch.size}",
                    "단발성이 아님 · 페이스 닫힘/당겨치는 패스를 확인하고 1.5m 스타트라인 드릴 권장",
                    76
                )
            }
        }

        val impacts = last.mapNotNull { record ->
            val q = V16MetricConfidenceEstimator.estimate(record.metrics)
            record.metrics.impactOffsetMm?.takeIf { q.impact >= .48 && it.isFinite() }
        }.takeLast(10)
        if (impacts.size >= 6) {
            val avg = impacts.average()
            val std = sqrt(impacts.sumOf { (it - avg) * (it - avg) } / impacts.size)
            if (abs(avg) >= 4.5 && std <= 4.0) {
                return CoachFeedback(
                    "정타가 계속 ${if (avg > 0) "토" else "힐"} 쪽",
                    "신뢰 샷 평균 ${"%.1f".format(abs(avg))}mm · 우연한 미스보다 셋업/퍼터 길이 적합성을 같이 확인",
                    74
                )
            }
        }
        return null
    }
}
