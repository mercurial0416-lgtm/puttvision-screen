package com.puttvision.screen

object ProductCalibrationQuality {
    fun evaluate(result: CalibrationResult): CalibrationAssessment {
        val geometry = CalibrationQuality.evaluate(result)
        val frame = result.frameQuality ?: return geometry

        val score = (geometry.score * 0.64 + frame.overallScore * 0.36)
            .toInt()
            .coerceIn(0, 100)

        val hardImageFailure =
            frame.overallScore < 43 ||
                frame.sharpnessScore < 34 ||
                frame.motionScore < 38 ||
                frame.brightness < 32.0 ||
                frame.brightness > 232.0

        val grade = when {
            score >= 90 -> "EXCELLENT"
            score >= 80 -> "GOOD"
            score >= 68 -> "OK"
            score >= 58 -> "ADJUST"
            else -> "POOR"
        }

        val hint = when {
            hardImageFailure -> frame.hint
            frame.overallScore < 65 -> frame.hint
            geometry.score < 68 -> geometry.hint
            else -> "캘리브레이션 ${score}점 · ${frame.hint}"
        }

        return CalibrationAssessment(
            score = score,
            grade = grade,
            blocked = geometry.blocked || hardImageFailure || score < 58,
            hint = hint
        )
    }
}
