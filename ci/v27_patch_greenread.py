from pathlib import Path
p=Path('app/src/main/java/com/puttvision/screen/GreenReadAdvisor.kt')
t=p.read_text(encoding='utf-8')
if 'val pace100: Int' not in t:
    t=t.replace('''    val startX100: Int,
    val startY100: Int
)''','''    val startX100: Int,
    val startY100: Int,
    val pace100: Int
)''',1)
    t=t.replace('''    private data class Candidate(
        val angleDeg: Double,
        val speed: Double,
        val result: SimResult,
        val objective: Double
    )
    private data class Trace(val result: SimResult, val trail: List<Pair<Double, Double>>)''','''    private data class Candidate(
        val angleDeg: Double,
        val speed: Double,
        val cupSpeedMps: Double,
        val objective: Double
    )
    private data class Trace(val result: SimResult, val trail: List<Pair<Double, Double>>)
    private data class PaceEvidence(val closestDistanceM: Double, val cupSpeedMps: Double, val crossedCupPlane: Boolean)''',1)
    t=t.replace('fun key(settings: GreenSettings): GreenReadKey {','''fun key(
        settings: GreenSettings,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): GreenReadKey {''',1)
    t=t.replace('''            (start.first * 100.0).toInt(),
            (start.second * 100.0).toInt()''','''            (start.first * 100.0).toInt(),
            (start.second * 100.0).toInt(),
            (targetCupSpeedMps * 100.0).toInt()''',1)
    t=t.replace('''fun read(settings: GreenSettings): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = key(settings)''','''fun read(
        settings: GreenSettings,
        targetCupSpeedMps: Double = V27CupPaceRuntime.targetCupSpeedMps
    ): GreenRead {
        val putterWidth = ProductRuntime.putterHeadWidthCm.coerceIn(8.0, 15.0)
        val key = key(settings, targetCupSpeedMps)''',1)
    t=t.replace('val solved = solve(settings.copy(), putterWidth)','val solved = solve(settings.copy(), putterWidth, targetCupSpeedMps)',1)
    t=t.replace('private fun solve(settings: GreenSettings, putterWidth: Double): GreenRead {','private fun solve(settings: GreenSettings, putterWidth: Double, targetCupSpeedMps: Double): GreenRead {',1)
    t=t.replace('candidate(settings, angle, speed, flatSpeed)','candidate(settings, angle, speed, flatSpeed, targetCupSpeedMps)')
    t=t.replace('candidate(settings, 0.0, flatSpeed, flatSpeed)','candidate(settings, 0.0, flatSpeed, flatSpeed, targetCupSpeedMps)')
    old='''    private fun candidate(settings: GreenSettings, angle: Double, speed: Double, flatSpeed: Double): Candidate {
        val result = simulate(settings, speed, angle)
        val start = V26BallStartRuntime.current(settings)
        val direct = Math.toDegrees(kotlin.math.atan2(-start.first, settings.holeDistanceM - start.second))
        val regularizer = abs(angle - direct) * .00015 + abs(speed - flatSpeed) * .00025
        val objective = if (result.holed) -1.0 + regularizer else result.distanceToCupM + regularizer
        return Candidate(angle, speed, result, objective)
    }

    private fun shot(speed: Double, angle: Double) = ShotMetrics('''
    new='''    private fun candidate(
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

    private fun shot(speed: Double, angle: Double) = ShotMetrics('''
    if old not in t: raise SystemExit('candidate marker')
    t=t.replace(old,new,1)
    p.write_text(t,encoding='utf-8'); print('GreenReadAdvisor: V27 patched')
else:
    print('GreenReadAdvisor: current')
