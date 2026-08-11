package com.puttvision.screen

import kotlin.math.abs

data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val paceHint: String
)

object GreenReadAdvisor {
    fun read(settings: GreenSettings): GreenRead {
        val d = settings.holeDistanceM
        val speedFactor = settings.stimpMeters / 2.8

        // Approximate the spatial profile by sampling the intended roll line.
        // The real GreenPhysics simulation remains the source of truth.
        val terrainSamples = if (settings.terrainProfileId >= 0) {
            (1..7).map { i ->
                GreenTerrain.slopeAt(
                    profileId = settings.terrainProfileId,
                    x = 0.0,
                    y = d * i / 8.0,
                    holeDistanceM = d
                )
            }
        } else {
            emptyList()
        }
        val terrainSide = terrainSamples.map { it.sidePct }.average().takeIf { !it.isNaN() } ?: 0.0
        val terrainLong = terrainSamples.map { it.longPct }.average().takeIf { !it.isNaN() } ?: 0.0
        val effectiveSide = settings.sideSlopePct + terrainSide
        val effectiveLong = settings.longSlopePct + terrainLong

        val breakCm = effectiveSide * d * d * 1.85 * speedFactor
        val aim = -breakCm * 0.72

        val pace = when {
            effectiveLong >= 1.5 -> "내리막 · 약하게"
            effectiveLong <= -1.5 -> "오르막 · 강하게"
            abs(effectiveSide) >= 2.5 -> "브레이크 크게"
            else -> "기준 페이스"
        }

        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aim,
            paceHint = pace
        )
    }
}
