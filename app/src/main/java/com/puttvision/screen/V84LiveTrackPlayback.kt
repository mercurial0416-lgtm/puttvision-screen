package com.puttvision.screen

import kotlin.math.abs

enum class V84ReplaySpeed(val multiplier: Double) {
    QUARTER(0.25), HALF(0.5), NORMAL(1.0)
}

data class V84ReplayState(
    val playheadMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val speed: V84ReplaySpeed,
    val playing: Boolean,
    val impactHoldRemainingMs: Double,
    val finished: Boolean
)

/**
 * Deterministic playback state machine for the live-track HFR overlay.
 * UI clocks feed elapsed wall time; this model owns speed, scrub and impact hold semantics.
 */
object V84LiveTrackPlayback {
    const val IMPACT_HOLD_MS = 260.0

    fun initial(overlay: V81LiveTrackOverlay): V84ReplayState? {
        val times = V82LiveTrackReplay.timeline(overlay)
        if (times.isEmpty() || times.any { !it.isFinite() }) return null
        return V84ReplayState(
            playheadMs = times.first(),
            minMs = times.first(),
            maxMs = times.last(),
            speed = V84ReplaySpeed.HALF,
            playing = false,
            impactHoldRemainingMs = 0.0,
            finished = false
        )
    }

    fun setPlaying(state: V84ReplayState, playing: Boolean): V84ReplayState =
        if (state.finished && playing) state.copy(playheadMs = state.minMs, playing = true, finished = false, impactHoldRemainingMs = 0.0)
        else state.copy(playing = playing)

    fun setSpeed(state: V84ReplayState, speed: V84ReplaySpeed) = state.copy(speed = speed)

    fun scrub(state: V84ReplayState, playheadMs: Double): V84ReplayState {
        if (!playheadMs.isFinite()) return state
        val clamped = playheadMs.coerceIn(state.minMs, state.maxMs)
        return state.copy(playheadMs = clamped, impactHoldRemainingMs = 0.0, finished = clamped >= state.maxMs)
    }

    fun advance(state: V84ReplayState, elapsedWallMs: Double, impactMs: Double = 0.0): V84ReplayState {
        if (!state.playing || state.finished || !elapsedWallMs.isFinite() || elapsedWallMs <= 0.0) return state
        if (!impactMs.isFinite()) return state.copy(playing = false)

        var remainingWall = elapsedWallMs.coerceAtMost(2_000.0)
        var current = state

        if (current.impactHoldRemainingMs > 0.0) {
            val consumed = minOf(remainingWall, current.impactHoldRemainingMs)
            remainingWall -= consumed
            current = current.copy(impactHoldRemainingMs = current.impactHoldRemainingMs - consumed)
            if (remainingWall <= 0.0) return current
        }

        val deltaTimeline = remainingWall * current.speed.multiplier
        val old = current.playheadMs
        var next = (old + deltaTimeline).coerceAtMost(current.maxMs)
        var hold = current.impactHoldRemainingMs

        val crossesImpact = old < impactMs && next >= impactMs && impactMs in current.minMs..current.maxMs
        if (crossesImpact) {
            next = impactMs
            hold = IMPACT_HOLD_MS
        }

        val finished = next >= current.maxMs && hold <= 0.0
        return current.copy(
            playheadMs = next,
            impactHoldRemainingMs = hold,
            finished = finished,
            playing = current.playing && !finished
        )
    }

    fun progress01(state: V84ReplayState): Double {
        val span = state.maxMs - state.minMs
        if (!span.isFinite() || span <= 0.0) return 0.0
        return ((state.playheadMs - state.minMs) / span).coerceIn(0.0, 1.0)
    }
}

data class V84PlaybackSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V84HardwarelessPlaybackSuite {
    fun verify(): V84PlaybackSuiteResult {
        val overlay = V81LiveTrackOverlay(
            ball = listOf(
                V81LiveTrackPoint(8, -20.0, .4, .6),
                V81LiveTrackPoint(10, 0.0, .5, .5),
                V81LiveTrackPoint(12, 20.0, .6, .4)
            ),
            putter = listOf(
                V81LivePutterPose(8, -20.0, .4, .7, -1.0),
                V81LivePutterPose(10, 0.0, .5, .7, 0.0),
                V81LivePutterPose(12, 20.0, .6, .7, 1.0)
            ),
            impactFrame = 10,
            fps = 240,
            sourceWidthPx = 1920,
            sourceHeightPx = 1080,
            ready = true,
            reason = "fixture"
        )
        val initial = V84LiveTrackPlayback.initial(overlay)
        val playing = initial?.let { V84LiveTrackPlayback.setPlaying(it, true) }
        val quarter = playing?.let { V84LiveTrackPlayback.setSpeed(it, V84ReplaySpeed.QUARTER) }
        val qAdvanced = quarter?.let { V84LiveTrackPlayback.advance(it, 40.0, impactMs = 0.0) }
        val crossing = playing?.let { V84LiveTrackPlayback.scrub(it, -5.0) }?.let { V84LiveTrackPlayback.advance(it, 20.0, impactMs = 0.0) }
        val afterHold = crossing?.let { V84LiveTrackPlayback.advance(it, V84LiveTrackPlayback.IMPACT_HOLD_MS + 20.0, impactMs = 0.0) }
        val scrubLow = initial?.let { V84LiveTrackPlayback.scrub(it, -999.0) }
        val scrubHigh = initial?.let { V84LiveTrackPlayback.scrub(it, 999.0) }
        val badTick = playing?.let { V84LiveTrackPlayback.advance(it, Double.NaN) }
        val checks = listOf(
            "timeline initializes at first sample" to (initial?.playheadMs == -20.0),
            "default replay is paused half speed" to (initial?.speed == V84ReplaySpeed.HALF && initial.playing.not()),
            "quarter speed advances one quarter wall time" to (qAdvanced != null && abs(qAdvanced.playheadMs - (-10.0)) < 1e-9),
            "crossing impact snaps to impact and starts hold" to (crossing?.playheadMs == 0.0 && crossing.impactHoldRemainingMs == V84LiveTrackPlayback.IMPACT_HOLD_MS),
            "impact hold expires before replay resumes" to (afterHold != null && afterHold.impactHoldRemainingMs == 0.0 && afterHold.playheadMs > 0.0),
            "scrub clamps to timeline bounds" to (scrubLow?.playheadMs == -20.0 && scrubHigh?.playheadMs == 20.0),
            "non-finite elapsed time is ignored" to (badTick == playing),
            "progress stays normalized" to (initial != null && V84LiveTrackPlayback.progress01(initial) == 0.0 && scrubHigh != null && V84LiveTrackPlayback.progress01(scrubHigh) == 1.0)
        )
        val count = checks.count { it.second }
        return V84PlaybackSuiteResult(
            passed = count == checks.size,
            checksPassed = count,
            checksTotal = checks.size,
            reason = checks.firstOrNull { !it.second }?.first ?: "speed, scrub and impact-hold playback verified"
        )
    }
}

object V84HardwarelessPlaybackRuntime {
    @Volatile private var latest: V84PlaybackSuiteResult? = null
    fun run(): V84PlaybackSuiteResult = V84HardwarelessPlaybackSuite.verify().also { latest = it }
    fun snapshot(): V84PlaybackSuiteResult? = latest
    fun clear() { latest = null }
}
