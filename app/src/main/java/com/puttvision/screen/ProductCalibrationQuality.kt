package com.puttvision.screen

object ProductCalibrationQuality {
    fun evaluate(result: CalibrationResult): CalibrationAssessment {
        val geometry = CalibrationQuality.evaluate(result)
        val frame = result.frameQuality ?: return geometry
        val reprojection = result.reprojectionRmsPx
        val overDetermined = result.fitPointCount >= 6 && reprojection != null && reprojection.isFinite()
        val reprojectionScore = if (overDetermined) {
            when {
                reprojection!! <= .8 -> 100
                reprojection <= 1.5 -> 92
                reprojection <= 2.5 -> 78
                reprojection <= 4.0 -> 58
                else -> 25
            }
        } else null

        val score = if (reprojectionScore != null) {
            (geometry.score * .47 + frame.overallScore * .31 + reprojectionScore * .22).toInt()
        } else {
            (geometry.score * .64 + frame.overallScore * .36).toInt()
        }.coerceIn(0, 100)

        val hardImageFailure =
            frame.overallScore < 43 ||
                frame.sharpnessScore < 34 ||
                frame.motionScore < 38 ||
                frame.brightness < 32.0 ||
                frame.brightness > 232.0
        val reprojectionFailure = overDetermined && reprojection!! > 5.0

        val grade = when {
            score >= 90 -> "EXCELLENT"
            score >= 80 -> "GOOD"
            score >= 68 -> "OK"
            score >= 58 -> "ADJUST"
            else -> "POOR"
        }

        val hint = when {
            reprojectionFailure -> "마커 재투영 오차 ${"%.1f".format(reprojection)}px · 마커 평탄도/렌즈 위치를 확인하세요"
            hardImageFailure -> frame.hint
            overDetermined && reprojection!! > 2.5 -> "PV2 ${result.fitPointCount}점 · RMS ${"%.2f".format(reprojection)}px · 카메라/마커 정렬을 조금 조정하세요"
            frame.overallScore < 65 -> frame.hint
            geometry.score < 68 -> geometry.hint
            overDetermined -> "PV2 ${result.fitPointCount}점 · RMS ${"%.2f".format(reprojection)}px · 렌즈 보정 ${if (result.lensK1 != null) "적용" else "불필요"}"
            else -> "캘리브레이션 ${score}점 · ${frame.hint}"
        }

        return CalibrationAssessment(
            score = score,
            grade = grade,
            blocked = geometry.blocked || hardImageFailure || reprojectionFailure || score < 58,
            hint = hint
        )
    }
}
