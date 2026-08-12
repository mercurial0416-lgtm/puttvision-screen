package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.tan


data class GreenRead(
    val estimatedBreakCm: Double,
    val aimOffsetCm: Double,
    val cupCount: Double,
    val putterHeadCount: Double,
    val aimSideLabel: String,
    val effectiveSideSlopePct: Double,
    val effectiveLongSlopePct: Double,
    val paceHint: String,
    val recommendedBallSpeedMps: Double,
    val recommendedLaunchAngleDeg: Double,
    val solverMissCm: Double,
    val predictedTrail: List<Pair<Double, Double>>
)

object GreenReadAdvisor {
    private const val CUP_DIAMETER_CM = 10.8
    private const val STIMP_LAUNCH_MPS = 1.95072
    private val physics = GreenPhysics()

    private data class Key(
        val profile: Int, val distance100: Int, val stimp100: Int,
        val side100: Int, val long100: Int, val putter100: Int
    )
    private data class Candidate(val angleDeg: Double, val speed: Double, val result: SimResult, val objective: Double)
    private data class Trace(val result: SimResult, val trail: List<Pair<Double, Double>>)

    private val cache = object : LinkedHashMap<Key, GreenRead>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GreenRead>?): Boolean = size > 64
    }

    @Synchronized
    fun read(settings: GreenSettings): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = Key(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt()
        )
        cache[key]?.let { return it }
        val solved = solve(settings, putterWidth)
        cache[key] = solved
        return solved
    }

    private fun solve(settings: GreenSettings, putterWidth: Double): GreenRead {
        val d = settings.holeDistanceM.coerceIn(0.5, 20.0)
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)
        val flatSpeed = (STIMP_LAUNCH_MPS * sqrt(d / stimp)).coerceIn(.20, 5.0)
        val minSpeed = (flatSpeed * .45).coerceIn(.15, 4.7)
        val maxSpeed = (flatSpeed * 1.55).coerceIn(minSpeed + .05, 5.0)

        var best: Candidate? = null
        val coarseSpeedStep = (maxSpeed - minSpeed) / 14.0
        for (angleStep in -10..10) {
            val angle = angleStep * 3.0
            for (speedStep in 0..14) {
                val speed = minSpeed + coarseSpeedStep * speedStep
                val c = candidate(settings, angle, speed, flatSpeed)
                if (best == null || c.objective < best!!.objective) best = c
            }
        }

        val coarse = best ?: candidate(settings, 0.0, flatSpeed, flatSpeed)
        best = coarse
        val refineSpeedSpan = maxOf(.10, coarseSpeedStep * 1.25)
        for (ai in -6..6) {
            val angle = (coarse.angleDeg + ai * .5).coerceIn(-35.0, 35.0)
            for (si in -6..6) {
                val speed = (coarse.speed + refineSpeedSpan * si / 6.0).coerceIn(.15, 5.0)
                val c = candidate(settings, angle, speed, flatSpeed)
                if (c.objective < best!!.objective) best = c
            }
        }

        val b = best!!
        val trace = simulateTrace(settings, b.speed, b.angleDeg)
        val aimCm = tan(Math.toRadians(b.angleDeg)) * d * 100.0
        val magnitude = abs(aimCm)
        val straight = simulate(settings, b.speed, 0.0)
        val breakCm = straight.finishX * 100.0

        val corridor = (1..11).map { i ->
            val y = d * i / 12.0
            val center = GreenTerrain.effectiveSlopeAt(settings, 0.0, y)
            val left = GreenTerrain.effectiveSlopeAt(settings, -0.12, y)
            val right = GreenTerrain.effectiveSlopeAt(settings, 0.12, y)
            TerrainSlope(
                center.sidePct * .60 + left.sidePct * .20 + right.sidePct * .20,
                center.longPct * .60 + left.longPct * .20 + right.longPct * .20
            )
        }
        val effectiveSide = corridor.map { it.sidePct }.average()
        val effectiveLong = corridor.map { it.longPct }.average()
        val ratio = b.speed / flatSpeed.coerceAtLeast(.1)
        val pace = when {
            ratio <= .78 -> "강한 내리막 · 매우 약하게"
            ratio <= .91 -> "내리막 · 약하게"
            ratio >= 1.22 -> "강한 오르막 · 강하게"
            ratio >= 1.08 -> "오르막 · 조금 강하게"
            abs(effectiveSide) >= 3.0 -> "브레이크 큼 · 끝까지 읽기"
            abs(effectiveSide) >= 1.6 -> "브레이크 중간"
            else -> "기준 페이스"
        }
        val side = when {
            magnitude < 1.5 -> "센터"
            aimCm < 0.0 -> "홀 왼쪽"
            else -> "홀 오른쪽"
        }
        return GreenRead(
            estimatedBreakCm = breakCm,
            aimOffsetCm = aimCm,
            cupCount = magnitude / CUP_DIAMETER_CM,
            putterHeadCount = magnitude / putterWidth,
            aimSideLabel = side,
            effectiveSideSlopePct = effectiveSide,
            effectiveLongSlopePct = effectiveLong,
            paceHint = pace,
            recommendedBallSpeedMps = b.speed,
            recommendedLaunchAngleDeg = b.angleDeg,
            solverMissCm = trace.result.distanceToCupM * 100.0,
            predictedTrail = trace.trail
        )
    }

    private fun candidate(settings: GreenSettings, angle: Double, speed: Double, flatSpeed: Double): Candidate {
        val result = simulate(settings, speed, angle)
        val regularizer = abs(angle) * .00015 + abs(speed - flatSpeed) * .00025
        val objective = if (result.holed) -1.0 + regularizer else result.distanceToCupM + regularizer
        return Candidate(angle, speed, result, objective)
    }

    private fun shot(speed: Double, angle: Double) = ShotMetrics(
        ballSpeedMps = speed,
        launchAngleDeg = angle,
        headSpeedMps = null,
        faceAngleDeg = null,
        pathAngleDeg = null,
        faceToPathDeg = null,
        smash = null,
        impactOffsetMm = null,
        measuredAtNs = 0L
    )

    private fun simulate(settings: GreenSettings, speed: Double, angle: Double): SimResult =
        simulateTrace(settings, speed, angle, keepTrail = false).result

    private fun simulateTrace(
        settings: GreenSettings,
        speed: Double,
        angle: Double,
        keepTrail: Boolean = true
    ): Trace {
        val state = physics.launch(shot(speed, angle), settings)
        var result: SimResult? = null
        repeat(900) {
            result = physics.step(state, settings, .025)
            if (result != null) return@repeat
        }
        val final = result ?: SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(state.x, state.y - settings.holeDistanceM),
            elapsedSec = state.elapsed
        )
        val trail = if (keepTrail) {
            state.trail.toList().ifEmpty { listOf(0.0 to 0.0, state.x to state.y) }
        } else emptyList()
        return Trace(final, trail)
    }
}
