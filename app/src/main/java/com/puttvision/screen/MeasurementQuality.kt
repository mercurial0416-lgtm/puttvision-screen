package com.puttvision.screen

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CalibrationAssessment(
    val score: Int,
    val grade: String,
    val blocked: Boolean,
    val hint: String
)

object CalibrationQuality {
    fun evaluate(result: CalibrationResult): CalibrationAssessment {
        val pts = result.imagePoints
        val w = result.frameInfo.width.toDouble().coerceAtLeast(1.0)
        val h = result.frameInfo.height.toDouble().coerceAtLeast(1.0)
        if (pts.size != 4) {
            return CalibrationAssessment(0, "POOR", true, "마커 4개를 모두 화면 안에 넣어주세요")
        }

        fun dist(a: PointF, b: PointF): Double =
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

        // AutoCalibrator resolves BL, BR, TR, TL in this order.
        val bl = pts[0]
        val br = pts[1]
        val tr = pts[2]
        val tl = pts[3]

        val polygonArea = abs(
            (bl.x * br.y - br.x * bl.y) +
                (br.x * tr.y - tr.x * br.y) +
                (tr.x * tl.y - tl.x * tr.y) +
                (tl.x * bl.y - bl.x * tl.y)
        ).toDouble() * 0.5
        val coverage = (polygonArea / (w * h)).coerceIn(0.0, 1.0)

        // A useful calibration should occupy a meaningful part of the frame,
        // but still leave enough margin so none of the QR markers are clipped.
        val coverageScore = when {
            coverage < 0.08 -> (coverage / 0.08 * 35.0)
            coverage <= 0.52 -> 35.0
            coverage < 0.78 -> 35.0 * (1.0 - (coverage - 0.52) / 0.26 * 0.55)
            else -> 14.0
        }.coerceIn(0.0, 35.0)

        val bottom = dist(bl, br)
        val top = dist(tl, tr)
        val left = dist(bl, tl)
        val right = dist(br, tr)
        fun balance(a: Double, b: Double): Double =
            if (max(a, b) <= 1e-6) 0.0 else min(a, b) / max(a, b)
        val edgeBalance = (balance(bottom, top) + balance(left, right)) * 0.5
        val edgeScore = 25.0 * edgeBalance.coerceIn(0.0, 1.0)

        val diagA = dist(bl, tr)
        val diagB = dist(br, tl)
        val diagonalBalance = balance(diagA, diagB)
        val diagonalScore = 20.0 * diagonalBalance.coerceIn(0.0, 1.0)

        val minMargin = pts.minOf { p ->
            min(
                min(p.x.toDouble() / w, (w - p.x) / w),
                min(p.y.toDouble() / h, (h - p.y) / h)
            )
        }.coerceIn(0.0, 0.5)
        val marginScore = (minMargin / 0.075 * 20.0).coerceIn(0.0, 20.0)

        val score = (coverageScore + edgeScore + diagonalScore + marginScore)
            .toInt()
            .coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "EXCELLENT"
            score >= 80 -> "GOOD"
            score >= 68 -> "OK"
            score >= 55 -> "ADJUST"
            else -> "POOR"
        }

        val hint = when {
            minMargin < 0.035 -> "마커가 화면 끝에 너무 가깝습니다 · 폰을 조금 멀리 이동하세요"
            coverage < 0.08 -> "마커 영역이 너무 작습니다 · 폰을 조금 가까이 이동하세요"
            edgeBalance < 0.58 -> "카메라가 한쪽으로 치우쳤습니다 · 매트 중앙을 정면으로 맞추세요"
            diagonalBalance < 0.72 -> "카메라 기울기가 큽니다 · 폰 수평을 맞추세요"
            score < 68 -> "폰 위치를 조금 조정하면 측정 정확도가 좋아집니다"
            else -> "캘리브레이션 품질이 안정적입니다"
        }

        return CalibrationAssessment(
            score = score,
            grade = grade,
            blocked = score < 55,
            hint = hint
        )
    }
}

data class SessionReportData(
    val shots: Int,
    val made: Int,
    val makePct: Double,
    val overallScore: Int,
    val avgStrokeScore: Double,
    val avgLaunchDeg: Double,
    val launchStdDeg: Double,
    val avgDistanceErrorCm: Double?,
    val avgConfidencePct: Double?,
    val headline: String,
    val detail: String,
    val plan: AutoCoachPlan
)

data class AutoCoachPlan(
    val title: String,
    val detail: String,
    val entranceMode: Int,
    val patternIndex: Int,
    val distanceM: Int,
    val greenPresetIndex: Int,
    val shotCount: Int
)

object SessionCoach {
    fun build(records: List<ShotRecord>, currentDistanceM: Int): SessionReportData {
        if (records.isEmpty()) {
            return SessionReportData(
                shots = 0,
                made = 0,
                makePct = 0.0,
                overallScore = 0,
                avgStrokeScore = 0.0,
                avgLaunchDeg = 0.0,
                launchStdDeg = 0.0,
                avgDistanceErrorCm = null,
                avgConfidencePct = null,
                headline = "아직 분석할 샷이 없습니다",
                detail = "한 샷 이상 측정하면 자동 코치가 다음 훈련을 추천합니다.",
                plan = AutoCoachPlan("스타트라인 10구", "3m 플랫 그린에서 기준선을 만듭니다.", 0, 0, 3, 0, 10)
            )
        }

        val shots = records.size
        val made = records.count { it.result?.holed == true }
        val makePct = made * 100.0 / shots
        val launches = records.map { it.metrics.launchAngleDeg }
        val avgLaunch = launches.average()
        val variance = launches.map { (it - avgLaunch) * (it - avgLaunch) }.average()
        val launchStd = sqrt(variance)
        val avgStroke = records.map { it.strokeScore.total }.average()
        val distanceErrors = records.mapNotNull { it.result?.distanceToCupM?.times(100.0) }
        val avgDistanceError = distanceErrors.takeIf { it.isNotEmpty() }?.average()
        val confidences = records.mapNotNull { it.metrics.confidence?.times(100.0) }
        val avgConfidence = confidences.takeIf { it.isNotEmpty() }?.average()

        val directionPenalty = (abs(avgLaunch) * 10.0 + launchStd * 12.0).coerceAtMost(38.0)
        val distancePenalty = ((avgDistanceError ?: 0.0) * 0.22).coerceAtMost(26.0)
        val qualityPenalty = if (avgConfidence != null) ((82.0 - avgConfidence).coerceAtLeast(0.0) * 0.45).coerceAtMost(18.0) else 4.0
        val makeBonus = (makePct * 0.12).coerceAtMost(12.0)
        val overall = (88.0 - directionPenalty - distancePenalty - qualityPenalty + makeBonus)
            .toInt()
            .coerceIn(0, 100)

        val plan: AutoCoachPlan
        val headline: String
        val detail: String

        when {
            avgConfidence != null && avgConfidence < 72.0 -> {
                headline = "측정 환경부터 안정화"
                detail = "평균 측정 신뢰도가 ${"%.0f".format(avgConfidence)}%입니다. 조명과 마커 위치를 먼저 안정화하면 분석값이 더 믿을 만해집니다."
                plan = AutoCoachPlan("품질 체크 10구", "3m 플랫 그린에서 카메라 품질을 먼저 안정화합니다.", 0, 0, 3, 0, 10)
            }
            abs(avgLaunch) >= 1.15 -> {
                val side = if (avgLaunch > 0) "우측" else "좌측"
                headline = "스타트라인 $side 편향"
                detail = "평균 출발선이 ${"%+.2f".format(avgLaunch)}°입니다. 짧은 거리에서 페이스와 스타트라인을 먼저 맞추는 게 효율적입니다."
                plan = AutoCoachPlan("스타트라인 교정 15구", "3m 플랫 그린 · 방향 일관성 집중", 0, 0, 3, 0, 15)
            }
            launchStd >= 0.95 -> {
                headline = "방향 일관성 개선"
                detail = "출발선 편차가 ±${"%.2f".format(launchStd)}° 수준입니다. 같은 셋업과 템포를 반복하는 훈련이 필요합니다."
                plan = AutoCoachPlan("일관성 15구", "4m 고정거리 · 플랫 그린", 0, 0, 4, 0, 15)
            }
            avgDistanceError != null && avgDistanceError >= 32.0 -> {
                headline = "거리감 훈련 우선"
                detail = "평균 컵 잔여거리가 ${"%.0f".format(avgDistanceError)}cm입니다. 방향보다 거리 변화 적응을 먼저 잡는 편이 좋습니다."
                plan = AutoCoachPlan("랜덤 거리 15구", "2~15m 랜덤 거리로 스피드 컨트롤을 훈련합니다.", 0, 1, currentDistanceM.coerceIn(4, 7), 0, 15)
            }
            makePct >= 70.0 && shots >= 8 -> {
                headline = "난이도를 올려도 됩니다"
                detail = "성공률 ${"%.0f".format(makePct)}%로 현재 조건은 안정적입니다. 거리와 브레이크 난도를 한 단계 올립니다."
                plan = AutoCoachPlan("브레이크 챌린지 15구", "한 단계 어려운 그린에서 실전 리드를 훈련합니다.", 2, 0, (currentDistanceM + 1).coerceAtMost(10), 8, 15)
            }
            else -> {
                headline = "기본기가 안정화되는 중"
                detail = "큰 편향은 없습니다. 같은 조건에서 반복성을 조금 더 만든 뒤 난도를 올리는 게 좋습니다."
                plan = AutoCoachPlan("기본기 10구", "현재 거리에서 플랫 그린 반복", 0, 0, currentDistanceM.coerceIn(3, 8), 0, 10)
            }
        }

        return SessionReportData(
            shots = shots,
            made = made,
            makePct = makePct,
            overallScore = overall,
            avgStrokeScore = avgStroke,
            avgLaunchDeg = avgLaunch,
            launchStdDeg = launchStd,
            avgDistanceErrorCm = avgDistanceError,
            avgConfidencePct = avgConfidence,
            headline = headline,
            detail = detail,
            plan = plan
        )
    }
}
