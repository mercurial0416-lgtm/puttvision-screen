package com.puttvision.screen

import kotlin.math.abs

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
        val face = metrics.faceAngleDeg
        val path = metrics.pathAngleDeg
        val launch = metrics.launchAngleDeg

        if (face != null && abs(face) >= 1.0) {
            val direction = if (face > 0) "열림" else "닫힘"
            return CoachFeedback(
                headline = "페이스 $direction ${"%+.2f".format(face)}°",
                detail = "출발 방향에 가장 큰 영향. 임팩트 직전 페이스를 먼저 안정화.",
                priority = 100
            )
        }

        if (path != null && abs(path) >= 1.3) {
            val direction = if (path > 0) "우측" else "좌측"
            return CoachFeedback(
                headline = "패스가 $direction ${"%+.2f".format(path)}°",
                detail = "헤드 궤적 편차가 큼. 백스윙-임팩트 구간을 목표선에 더 일정하게.",
                priority = 90
            )
        }

        metrics.impactOffsetMm?.let {
            if (abs(it) >= 7.0) {
                val side = if (it > 0) "토 쪽" else "힐 쪽"
                return CoachFeedback(
                    headline = "임팩트 $side ${"%.1f".format(abs(it))}mm",
                    detail = "정타 편차가 큼. 어드레스 거리와 손 위치를 고정.",
                    priority = 85
                )
            }
        }

        metrics.tempoRatio?.let {
            if (it < 1.45 || it > 2.75) {
                return CoachFeedback(
                    headline = "템포 편차 ${"%.2f".format(it)}:1",
                    detail = "백스윙과 다운스윙 리듬이 불안정. 힘보다 동일한 템포 반복이 우선.",
                    priority = 80
                )
            }
        }

        if (abs(launch) >= 0.8) {
            val side = if (launch > 0) "우측" else "좌측"
            return CoachFeedback(
                headline = "출발이 $side ${"%.2f".format(abs(launch))}°",
                detail = "페이스/패스는 크게 무너지지 않았지만 출발선 편차가 남아있음.",
                priority = 75
            )
        }

        if (recent.size >= 5) {
            val launchSigns = recent.takeLast(8).map { it.metrics.launchAngleDeg }
            val right = launchSigns.count { it > 0.45 }
            val left = launchSigns.count { it < -0.45 }

            if (right >= 5) {
                return CoachFeedback(
                    headline = "최근 미스가 우측으로 반복",
                    detail = "단발성보다 패턴 문제. 페이스 오픈 또는 어드레스 정렬을 우선 확인.",
                    priority = 70
                )
            }

            if (left >= 5) {
                return CoachFeedback(
                    headline = "최근 미스가 좌측으로 반복",
                    detail = "단발성보다 패턴 문제. 페이스 닫힘 또는 당겨치는 패스를 확인.",
                    priority = 70
                )
            }
        }

        return CoachFeedback(
            headline = "스트로크 안정적 · ${score.total}점",
            detail = "큰 결함 없음. 지금은 거리감과 반복성 편차를 줄이는 구간.",
            priority = 20
        )
    }
}
