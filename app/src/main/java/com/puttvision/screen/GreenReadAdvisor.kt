package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.max

data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val paceHint: String
)

object GreenReadAdvisor {
    fun read(settings: GreenSettings): GreenRead {
        val d = settings.holeDistanceM
        val speedFactor = settings.stimpMeters / 2.8

        // UX helper approximation. The physics engine remains the source of truth.
        val breakCm =
            settings.sideSlopePct * d * d * 1.85 * speedFactor

        val aim = -breakCm * 0.72

        val pace = when {
            settings.longSlopePct >= 1.5 -> "내리막 · 약하게"
            settings.longSlopePct <= -1.5 -> "오르막 · 강하게"
            abs(settings.sideSlopePct) >= 2.5 -> "브레이크 크게"
            else -> "기준 페이스"
        }

        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aim,
            paceHint = pace
        )
    }
}
