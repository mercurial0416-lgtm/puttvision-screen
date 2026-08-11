package com.puttvision.screen

import kotlin.math.abs

data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val cupCount: Double,
    val putterHeadCount: Double,
    val aimSideLabel: String,
    val effectiveSideSlopePct: Double,
    val effectiveLongSlopePct: Double,
    val paceHint: String
)

object GreenReadAdvisor {
    private const val CUP_DIAMETER_CM = 10.8
    private const val PUTTER_HEAD_WIDTH_CM = 11.5

    fun read(settings: GreenSettings): GreenRead {
        val d = settings.holeDistanceM
        val speedFactor = settings.stimpMeters / 2.8

        // Sample the actual selected terrain along the intended roll corridor.
        // GreenPhysics is still the source of truth for the final ball path.
        val terrainSamples = if (settings.terrainProfileId >= 0) {
            (1..11).map { i ->
                val y = d * i / 12.0
                val center = GreenTerrain.slopeAt(
                    profileId = settings.terrainProfileId,
                    x = 0.0,
                    y = y,
                    holeDistanceM = d
                )
                val left = GreenTerrain.slopeAt(
                    profileId = settings.terrainProfileId,
                    x = -0.12,
                    y = y,
                    holeDistanceM = d
                )
                val right = GreenTerrain.slopeAt(
                    profileId = settings.terrainProfileId,
                    x = 0.12,
                    y = y,
                    holeDistanceM = d
                )
                TerrainSlope(
                    sidePct = center.sidePct * 0.60 + left.sidePct * 0.20 + right.sidePct * 0.20,
                    longPct = center.longPct * 0.60 + left.longPct * 0.20 + right.longPct * 0.20
                )
            }
        } else {
            emptyList()
        }

        val terrainSide = terrainSamples.map { it.sidePct }.average().takeIf { !it.isNaN() } ?: 0.0
        val terrainLong = terrainSamples.map { it.longPct }.average().takeIf { !it.isNaN() } ?: 0.0
        val effectiveSide = settings.sideSlopePct + terrainSide
        val effectiveLong = settings.longSlopePct + terrainLong

        // A practical green-reading estimate. Faster greens and longer putts
        // require more start-line allowance. The TV UI labels this as a guide,
        // while the actual rendered roll is always produced by GreenPhysics.
        val breakCm = effectiveSide * d * d * 1.85 * speedFactor
        val aim = -breakCm * 0.72
        val magnitude = abs(aim)

        val side = when {
            magnitude < 1.5 -> "센터"
            aim < 0.0 -> "홀 왼쪽"
            else -> "홀 오른쪽"
        }

        val pace = when {
            effectiveLong >= 2.2 -> "강한 내리막 · 매우 약하게"
            effectiveLong >= 1.2 -> "내리막 · 약하게"
            effectiveLong <= -2.2 -> "강한 오르막 · 강하게"
            effectiveLong <= -1.2 -> "오르막 · 강하게"
            abs(effectiveSide) >= 3.0 -> "브레이크 큼 · 끝까지 읽기"
            abs(effectiveSide) >= 1.6 -> "브레이크 중간"
            else -> "기준 페이스"
        }

        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aim,
            cupCount = magnitude / CUP_DIAMETER_CM,
            putterHeadCount = magnitude / PUTTER_HEAD_WIDTH_CM,
            aimSideLabel = side,
            effectiveSideSlopePct = effectiveSide,
            effectiveLongSlopePct = effectiveLong,
            paceHint = pace
        )
    }
}
