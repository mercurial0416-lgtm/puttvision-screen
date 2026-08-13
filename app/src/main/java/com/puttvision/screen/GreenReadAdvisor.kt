package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.math.tan


data class GreenReadKey(
    val profile: Int,
    val distance100: Int,
    val stimp100: Int,
    val side100: Int,
    val long100: Int,
    val putter100: Int,
    val startX100: Int,
    val startY100: Int,
    val pace100: Int
)

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
    val solverReliable: Boolean,
    val predictedTrail: List<Pair<Double, Double>>
)

object GreenReadAdvisor {
    private const val CUP_DIAMETER_CM = 10.8
    private const val STIMP_LAUNCH_MPS = 1.95072
    private const val RELIABLE_MISS_CM = 8.0
    private val physics = GreenPhysics()
    private val cacheLock = Any()

    private data class Candidate(
        val angleDeg: Double,
        val speed: Double,
        val cupSpeedMps: Double,
        val objective: Double
    )
    private data class Trace(val result: SimResult, val trail: List<Pair<Double, Double>>)
    private data class PaceEvidence(val closestDistanceM: Double, val cupSpeedMps: Double, val crossedCupPlane: Boolean)

    private val cache = object : LinkedHashMap<GreenReadKey, GreenRead>(96, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GreenReadKey, GreenRead>?): Boolean = size > 96
    }

    fun key(
        settings: GreenSettings,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): GreenReadKey {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val start = V26BallStartRuntime.current(settings)
        return GreenReadKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100.0).toInt(),
            (settings.stimpMeters * 100.0).toInt(),
            (settings.sideSlopePct * 100.0).toInt(),
            (settings.longSlopePct * 100.0).toInt(),
            (putterWidth * 100.0).toInt(),
            (start.first * 100.0).toInt(),
            (start.second * 100.0).toInt(),
            (targetCupSpeedMps * 100.0).toInt()
        )
    }

    fun read(
        settings: GreenSettings,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = key(settings, targetCupSpeedMps)
        synchronized(cacheLock) { cache[key]?.let { return it } }

        // Never hold cacheLock during the expensive inverse simulation. This is
        // what makes GreenReadRuntime.peek() genuinely non-blocking on the UI/TV.
        val solved = solve(settings.copy(), putterWidth, targetCupSpeedMps)
        synchronized(cacheLock) {
            cache[key]?.let { return it }
            cache[key] = solved
        }
        return solved
    }

    fun peekCached(settings: GreenSettings): GreenRead? =
        synchronized(cacheLock) { cache[key(settings)] }

    private fun solve(settings: GreenSettings, putterWidth: Double, targetCupSpeedMps: Double): GreenRead {
        val start = V26BallStartRuntime.current(settings)
        val toCupX = -start.first
        val toCupY = settings.holeDistanceM - start.second
        val d = hypot(toCupX, toCupY).coerceIn(0.5, 20.0)
        val directAngleDeg = Math.toDegrees(kotlin.math.atan2(toCupX, toCupY))
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)
        val flatSpeed = (STIMP_LAUNCH_MPS * sqrt(d / stimp)).coerceIn(.20, 5.0)
        val minSpeed = (flatSpeed * .45).coerceIn(.15, 4.7)
        val maxSpeed = (flatSpeed * 1.55).coerceIn(minSpeed + .05, 5.0)

        var best: Candidate? = null
        val coarseSpeedStep = (maxSpeed - minSpeed) / 14.0
        for (angleStep in -10..10) {
            val angle = directAngleDeg + angleStep * 3.0
            for (speedStep in 0..14) {
                val speed = minSpeed + coarseSpeedStep * speedStep
                val c = candidate(settings, angle, speed, flatSpeed, targetCupSpeedMps)
                if (best == null || c.objective < best!!.objective) best = c
            }
        }

        val coarse = best ?: candidate(settings, 0.0, flatSpeed, flatSpeed, targetCupSpeedMps)
        best = coarse
        val refineSpeedSpan = maxOf(.10, coarseSpeedStep * 1.25)
        for (ai in -6..6) {
            val angle = (coarse.angleDeg + ai * .5).coerceIn(-45.0, 45.0)
            for (si in -6..6) {
                val speed = (coarse.speed + refineSpeedSpan * si / 6.0).coerceIn(.15, 5.0)
                val c = candidate(settings, angle, speed, flatSpeed, targetCupSpeedMps)
                if (c.objective < best!!.objective) best = c
            }
        }

        // V12 final pass: 0.1 degree / 0.01 m/s local search around the best.
        val refined = best!!
        for (ai in -5..5) {
            val angle = (refined.angleDeg + ai * .10).coerceIn(-45.0, 45.0)
            for (si in -5..5) {
                val speed = (refined.speed + si * .010).coerceIn(.15, 5.0)
                val c = candidate(settings, angle, speed, flatSpeed, targetCupSpeedMps)
                if (c.objective < best!!.objective) best = c
            }
        }

        val b = best!!
        val trace = simulateTrace(settings, b.speed, b.angleDeg)
        val aimCm = tan(Math.toRadians(b.angleDeg - directAngleDeg)) * d * 100.0
        val magnitude = abs(aimCm)
        val straight = simulate(settings, b.speed, directAngleDeg)
        val ux = toCupX / d
        val uy = toCupY / d
        val perpX = -uy
        val perpY = ux
        val breakCm = ((straight.finishX) * perpX + (straight.finishY - settings.holeDistanceM) * perpY) * 100.0

        val corridor = (1..11).map { i ->
            val t = i / 12.0
            val x = start.first + toCupX * t
            val y = start.second + toCupY * t
            val center = GreenTerrain.effectiveSlopeAt(settings, x, y)
            val left = GreenTerrain.effectiveSlopeAt(settings, x - perpX * .12, y - perpY * .12)
            val right = GreenTerrain.effectiveSlopeAt(settings, x + perpX * .12, y + perpY * .12)
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
        val missCm = trace.result.distanceToCupM * 100.0
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
            solverMissCm = missCm,
            solverReliable = trace.result.holed || missCm <= RELIABLE_MISS_CM,
            predictedTrail = trace.trail
        )
    }

    private fun candidate(
        settings: GreenSettings,
        angle: Double,
        speed: Double,
        flatSpeed: Double,
        targetCupSpeedMps: Double
    ): Candidate {
        val evidence = simulatePaceEvidence(settings, speed, angle)
        val start = V26BallStartRuntime.current(settings)
        val direct = Math.toDegrees(kotlin.math.atan2(-start.first, settings.holeDistanceM - start.second))
        val regularizer = abs(angle - direct) * .00010 + abs(speed - flatSpeed) * .00012
        val paceError = abs(evidence.cupSpeedMps - targetCupSpeedMps)
        val crossingPenalty = if (evidence.crossedCupPlane) 0.0 else .45
        val objective = evidence.closestDistanceM + paceError * .11 + crossingPenalty + regularizer
        return Candidate(angle, speed, evidence.cupSpeedMps, objective)
    }

    private fun simulatePaceEvidence(settings: GreenSettings, speed: Double, angle: Double): PaceEvidence {
        val start = V26BallStartRuntime.current(settings)
        val state = physics.launch(shot(speed, angle), settings, start.first, start.second)
        val cupY = settings.holeDistanceM
        var closest = hypot(state.x, state.y - cupY)
        var speedAtClosest = hypot(state.vx, state.vy)
        var crossed = false
        for (step in 0 until 900) {
            val beforeY = state.y
            val completed = physics.step(state, settings, .025, cupEnabled = false)
            val distance = hypot(state.x, state.y - cupY)
            if (distance < closest) { closest = distance; speedAtClosest = hypot(state.vx, state.vy) }
            val crossedNow = (beforeY - cupY) * (state.y - cupY) <= 0.0 && abs(state.y - beforeY) > 1e-9
            if (crossedNow) { crossed = true; closest = minOf(closest, abs(state.x)); speedAtClosest = hypot(state.vx, state.vy) }
            if (completed != null) break
        }
        return PaceEvidence(closest, speedAtClosest, crossed)
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
        val start = V26BallStartRuntime.current(settings)
        val state = physics.launch(shot(speed, angle), settings, start.first, start.second)
        var result: SimResult? = null
        for (step in 0 until 900) {
            val completed = physics.step(state, settings, .025)
            if (completed != null) {
                result = completed
                break
            }
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
