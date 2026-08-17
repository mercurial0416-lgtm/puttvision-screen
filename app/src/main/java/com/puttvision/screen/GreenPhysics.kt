package com.puttvision.screen

import kotlin.math.*

data class GreenSettings(
    var stimpMeters: Double = 2.8,
    var holeDistanceM: Double = 5.0,
    var sideSlopePct: Double = 0.0,   // + = right side is lower, ball breaks right
    var longSlopePct: Double = 0.0,   // + = downhill toward the hole
    var terrainProfileId: Int = -1    // -1 = uniform plane, 0..23 = practice terrain profile
)

enum class V134CupPhase { NONE, RIM, DROP, SETTLED }

data class SimState(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var running: Boolean = false,
    var holed: Boolean = false,
    var elapsed: Double = 0.0,
    val trail: MutableList<Pair<Double, Double>> = mutableListOf(),
    var cupContacts: Int = 0,
    var lipOut: Boolean = false,
    var lastCupContactSec: Double = -10.0,
    var cupPhase: V134CupPhase = V134CupPhase.NONE,
    var cupPhaseElapsedSec: Double = 0.0,
    var cupVerticalOffsetM: Double = 0.0,
    var cupEntrySpeedMps: Double = 0.0,
    var cupRimAngleRad: Double = 0.0,
    var cupRimRadiusM: Double = 0.0,
    var cupRimAngularVelocityRadS: Double = 0.0,
    var cupRimWillDrop: Boolean = false,
    var cupRimDurationSec: Double = 0.0,
    var cupRimReleaseSpeedMps: Double = 0.0,
    var cupDropDurationSec: Double = 0.0
)

data class SimResult(
    val holed: Boolean,
    val finishX: Double,
    val finishY: Double,
    val distanceToCupM: Double,
    val elapsedSec: Double,
    val lipOut: Boolean = false,
    val cupContacts: Int = 0
)

class GreenPhysics {
    companion object {
        private const val G = 9.80665
        private const val STIMP_LAUNCH_MPS = 1.95072 // 6.4 ft/s from USGA educational material.
        private const val ROLLING_GRAVITY_FACTOR = 5.0 / 7.0
        private const val CUP_RADIUS_M = 0.054
        private const val BALL_RADIUS_M = 0.02135

        // Center-path radius that lets the ball lose support over the opening.
        private const val CAPTURE_CENTER_RADIUS_M = CUP_RADIUS_M - BALL_RADIUS_M * 0.24
        private const val RIM_CONTACT_RADIUS_M = CUP_RADIUS_M + BALL_RADIUS_M * 0.80

        // V134 cup interaction is deliberately time-resolved. The old engine snapped a captured
        // ball to the center and ended the shot in one physics tick, which could never look like a
        // real screen-golf cup. These values keep the rolling model 2D while exposing a physical
        // vertical presentation coordinate for the Filament renderer.
        private const val RIM_TRACK_RADIUS_M = 0.0575
        private const val DROP_ENTRY_RADIUS_M = 0.0305
        private const val CUP_DROP_DEPTH_M = 0.108
        private const val MIN_DROP_SEC = 0.32
        private const val MAX_DROP_SEC = 0.50
    }

    fun launch(
        metrics: ShotMetrics,
        settings: GreenSettings,
        startX: Double = 0.0,
        startY: Double = 0.0
    ): SimState {
        val a = Math.toRadians(metrics.launchAngleDeg)
        val speed = metrics.ballSpeedMps.coerceIn(0.05, 5.0)
        return SimState(
            x = startX,
            y = startY,
            vx = speed * sin(a),
            vy = speed * cos(a),
            running = true,
            trail = mutableListOf(startX to startY)
        )
    }

    fun step(
        state: SimState,
        settings: GreenSettings,
        dtRaw: Double,
        cupEnabled: Boolean = true
    ): SimResult? {
        if (!state.running) return result(state, settings)

        val dt = dtRaw.coerceIn(0.001, 0.025)
        if (cupEnabled) {
            when (state.cupPhase) {
                V134CupPhase.RIM -> return stepRim(state, settings, dt)
                V134CupPhase.DROP -> return stepDrop(state, settings, dt)
                V134CupPhase.SETTLED -> {
                    state.running = false
                    state.holed = true
                    return result(state, settings)
                }
                V134CupPhase.NONE -> Unit
            }
        }

        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)

        // A Stimpmeter launches at a fixed speed; if it stops after S meters under
        // constant deceleration, a = v^2 / (2S).
        val frictionDecel = (STIMP_LAUNCH_MPS * STIMP_LAUNCH_MPS) / (2.0 * stimp)

        val effectiveSlope = GreenTerrain.effectiveSlopeAt(settings, state.x, state.y)
        val effectiveSideSlopePct = effectiveSlope.sidePct
        val effectiveLongSlopePct = effectiveSlope.longPct

        val slopeAx = ROLLING_GRAVITY_FACTOR * G * (effectiveSideSlopePct / 100.0)
        val slopeAy = ROLLING_GRAVITY_FACTOR * G * (effectiveLongSlopePct / 100.0)

        val speed = hypot(state.vx, state.vy)
        var ax = slopeAx
        var ay = slopeAy

        if (speed > 0.003) {
            ax += -frictionDecel * state.vx / speed
            ay += -frictionDecel * state.vy / speed
        }

        val nvx = state.vx + ax * dt
        val nvy = state.vy + ay * dt

        val wouldReverse = state.vx * nvx + state.vy * nvy < 0.0
        val slopeMag = hypot(slopeAx, slopeAy)
        if (wouldReverse && slopeMag < frictionDecel) {
            state.vx = 0.0
            state.vy = 0.0
            state.running = false
            return result(state, settings)
        }

        val oldX = state.x
        val oldY = state.y
        state.vx = nvx
        state.vy = nvy
        state.x += state.vx * dt
        state.y += state.vy * dt
        state.elapsed += dt
        state.cupVerticalOffsetM = 0.0

        appendTrail(state)

        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val nowSpeed = hypot(state.vx, state.vy)
        val closest = closestPointOnSegment(oldX, oldY, state.x, state.y, cupX, cupY)
        val closestDx = closest.first - cupX
        val closestDy = closest.second - cupY
        val closestDist = hypot(closestDx, closestDy)
        val normalizedOffset = (closestDist / CAPTURE_CENTER_RADIUS_M).coerceIn(0.0, 1.4)

        // Off-centre balls need a slower lip speed to fall. A dead-centre ball can carry more
        // pace, while a fast skim is explicitly allowed to bridge the opening.
        val captureSpeed = (1.20 - normalizedOffset * 0.48).coerceIn(0.52, 1.20)

        if (cupEnabled && closestDist <= CAPTURE_CENTER_RADIUS_M && nowSpeed <= captureSpeed) {
            state.x = closest.first
            state.y = closest.second
            val centered = normalizedOffset <= 0.30 && nowSpeed <= captureSpeed * 0.94
            if (centered) {
                beginDrop(state, nowSpeed)
            } else {
                beginRim(
                    state = state,
                    cupX = cupX,
                    cupY = cupY,
                    contactX = closest.first,
                    contactY = closest.second,
                    entrySpeed = nowSpeed,
                    willDrop = true
                )
            }
            return null
        }

        val oldCupDistance = hypot(oldX - cupX, oldY - cupY)
        val newCupDistance = hypot(state.x - cupX, state.y - cupY)
        val crossedCupPlane =
            (oldY - cupY) * (state.y - cupY) <= 0.0 && abs(state.y - oldY) > 1e-9
        val reachedClosestApproach = newCupDistance >= oldCupDistance || crossedCupPlane

        if (
            cupEnabled &&
            closestDist <= RIM_CONTACT_RADIUS_M &&
            reachedClosestApproach &&
            state.elapsed - state.lastCupContactSec > 0.075
        ) {
            // Very fast near-centre putts can bridge the cup. Edge contact below this speed gets
            // a real time-resolved rim phase instead of an instantaneous velocity reflection.
            val edgeContact = closestDist > CAPTURE_CENTER_RADIUS_M * 0.48
            if (edgeContact && closestDist > 1e-5 && nowSpeed <= 1.90) {
                val edgeNorm = ((closestDist - CAPTURE_CENTER_RADIUS_M) /
                    (RIM_CONTACT_RADIUS_M - CAPTURE_CENTER_RADIUS_M)).coerceIn(0.0, 1.0)
                val rimCaptureSpeed = (0.96 - edgeNorm * 0.30).coerceIn(0.58, 0.96)
                val willDrop = nowSpeed <= rimCaptureSpeed && closestDist <= CUP_RADIUS_M + BALL_RADIUS_M * 0.42
                beginRim(
                    state = state,
                    cupX = cupX,
                    cupY = cupY,
                    contactX = closest.first,
                    contactY = closest.second,
                    entrySpeed = nowSpeed,
                    willDrop = willDrop
                )
                return null
            }
        }

        val dx = state.x - cupX
        val dy = state.y - cupY
        val toCup = hypot(dx, dy)

        if (state.elapsed > 20.0 || state.y > settings.holeDistanceM + 8.0 || abs(state.x) > 8.0) {
            state.running = false
            return result(state, settings)
        }

        if (nowSpeed < 0.012 && slopeMag < frictionDecel) {
            state.running = false
            return result(state, settings)
        }

        if (!toCup.isFinite()) {
            state.running = false
            return result(state, settings)
        }

        return null
    }

    private fun beginRim(
        state: SimState,
        cupX: Double,
        cupY: Double,
        contactX: Double,
        contactY: Double,
        entrySpeed: Double,
        willDrop: Boolean
    ) {
        val dx = contactX - cupX
        val dy = contactY - cupY
        val distance = hypot(dx, dy).coerceAtLeast(1e-6)
        val nx = dx / distance
        val ny = dy / distance
        val tx = -ny
        val ty = nx
        val tangentSpeed = state.vx * tx + state.vy * ty
        val cross = nx * state.vy - ny * state.vx
        val direction = when {
            abs(tangentSpeed) > 0.015 -> sign(tangentSpeed)
            abs(cross) > 0.015 -> sign(cross)
            state.vx < 0.0 -> -1.0
            else -> 1.0
        }
        var omega = tangentSpeed / RIM_TRACK_RADIUS_M
        if (abs(omega) < 1.55) omega = direction * 1.55
        omega = omega.coerceIn(-15.0, 15.0)

        state.cupContacts++
        state.lastCupContactSec = state.elapsed
        state.cupPhase = V134CupPhase.RIM
        state.cupPhaseElapsedSec = 0.0
        state.cupVerticalOffsetM = 0.0
        state.cupEntrySpeedMps = entrySpeed.coerceAtLeast(0.0)
        state.cupRimAngleRad = atan2(dy, dx)
        state.cupRimRadiusM = RIM_TRACK_RADIUS_M
        state.cupRimAngularVelocityRadS = omega
        state.cupRimWillDrop = willDrop
        state.cupRimDurationSec = if (willDrop) {
            (0.62 - entrySpeed * 0.19 + min(0.09, abs(omega) * 0.006)).coerceIn(0.34, 0.68)
        } else {
            (0.18 + (1.35 - entrySpeed).coerceIn(0.0, 1.0) * 0.12).coerceIn(0.18, 0.31)
        }
        state.cupRimReleaseSpeedMps = (entrySpeed * 0.62).coerceIn(0.16, 1.12)
        state.x = cupX + cos(state.cupRimAngleRad) * RIM_TRACK_RADIUS_M
        state.y = cupY + sin(state.cupRimAngleRad) * RIM_TRACK_RADIUS_M
        state.vx = -sin(state.cupRimAngleRad) * omega * RIM_TRACK_RADIUS_M
        state.vy = cos(state.cupRimAngleRad) * omega * RIM_TRACK_RADIUS_M
        // We mark contact now. If the ball ultimately falls, SimResult suppresses lipOut.
        state.lipOut = true
        appendTrail(state)
    }

    private fun stepRim(state: SimState, settings: GreenSettings, dt: Double): SimResult? {
        val cupX = 0.0
        val cupY = settings.holeDistanceM
        state.elapsed += dt
        state.cupPhaseElapsedSec += dt

        val duration = state.cupRimDurationSec.coerceAtLeast(0.16)
        val raw = (state.cupPhaseElapsedSec / duration).coerceIn(0.0, 1.0)
        val p = smoothStep(raw)
        val oldX = state.x
        val oldY = state.y

        val damping = if (state.cupRimWillDrop) 2.15 else 0.82
        state.cupRimAngularVelocityRadS *= exp(-damping * dt)
        state.cupRimAngleRad += state.cupRimAngularVelocityRadS * dt

        state.cupRimRadiusM = if (state.cupRimWillDrop) {
            lerp(RIM_TRACK_RADIUS_M, DROP_ENTRY_RADIUS_M, p)
        } else {
            lerp(RIM_TRACK_RADIUS_M, RIM_CONTACT_RADIUS_M + 0.004, p)
        }
        state.x = cupX + cos(state.cupRimAngleRad) * state.cupRimRadiusM
        state.y = cupY + sin(state.cupRimAngleRad) * state.cupRimRadiusM
        state.vx = (state.x - oldX) / dt
        state.vy = (state.y - oldY) / dt

        // A tiny vertical dip makes the ball visibly ride the edge instead of sliding on a flat
        // decal. It is intentionally only millimetres until support is genuinely lost.
        state.cupVerticalOffsetM = if (state.cupRimWillDrop) {
            -0.0025 * p - 0.0030 * sin(PI * p)
        } else {
            -0.0015 * sin(PI * p)
        }
        appendTrail(state)

        if (raw < 1.0) return null

        if (state.cupRimWillDrop) {
            beginDrop(state, state.cupEntrySpeedMps)
            return null
        }

        // Lip-out release: preserve the orbit direction, add a modest outward component and put
        // the ball beyond the contact shell so it cannot instantly collide with the same lip again.
        val angle = state.cupRimAngleRad
        val nx = cos(angle)
        val ny = sin(angle)
        val tx = -ny
        val ty = nx
        val tangentSign = if (state.cupRimAngularVelocityRadS < 0.0) -1.0 else 1.0
        val release = state.cupRimReleaseSpeedMps.coerceAtLeast(0.14)
        state.x = cupX + nx * (RIM_CONTACT_RADIUS_M + 0.007)
        state.y = cupY + ny * (RIM_CONTACT_RADIUS_M + 0.007)
        state.vx = tx * tangentSign * release * 0.78 + nx * release * 0.46
        state.vy = ty * tangentSign * release * 0.78 + ny * release * 0.46
        state.cupPhase = V134CupPhase.NONE
        state.cupPhaseElapsedSec = 0.0
        state.cupVerticalOffsetM = 0.0
        state.lastCupContactSec = state.elapsed
        appendTrail(state)
        return null
    }

    private fun beginDrop(state: SimState, entrySpeed: Double) {
        state.cupPhase = V134CupPhase.DROP
        state.cupPhaseElapsedSec = 0.0
        state.cupEntrySpeedMps = entrySpeed.coerceAtLeast(0.0)
        state.cupDropDurationSec = (0.46 - entrySpeed * 0.08).coerceIn(MIN_DROP_SEC, MAX_DROP_SEC)
        state.cupVerticalOffsetM = min(state.cupVerticalOffsetM, -0.001)
        state.running = true
        state.holed = false
    }

    private fun stepDrop(state: SimState, settings: GreenSettings, dt: Double): SimResult? {
        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val oldX = state.x
        val oldY = state.y
        state.elapsed += dt
        state.cupPhaseElapsedSec += dt

        val duration = state.cupDropDurationSec.coerceIn(MIN_DROP_SEC, MAX_DROP_SEC)
        val raw = (state.cupPhaseElapsedSec / duration).coerceIn(0.0, 1.0)
        val horizontal = 1.0 - exp(-7.5 * dt)
        state.x += (cupX - state.x) * horizontal
        state.y += (cupY - state.y) * horizontal
        state.vx = (state.x - oldX) / dt
        state.vy = (state.y - oldY) / dt

        // Hold the ball on the lip for a few tens of milliseconds, then accelerate downward. This
        // is a presentation coordinate only; x/y outcome physics remains deterministic.
        val dropRaw = ((state.cupPhaseElapsedSec - 0.035) / (duration - 0.035)).coerceIn(0.0, 1.0)
        val dropP = smoothStep(dropRaw).pow(1.18)
        state.cupVerticalOffsetM = -CUP_DROP_DEPTH_M * dropP

        if (raw < 1.0) return null

        state.x = cupX
        state.y = cupY
        state.vx = 0.0
        state.vy = 0.0
        state.cupVerticalOffsetM = -CUP_DROP_DEPTH_M
        state.cupPhase = V134CupPhase.SETTLED
        state.running = false
        state.holed = true
        return result(state, settings)
    }

    private fun appendTrail(state: SimState) {
        if (state.trail.isEmpty() ||
            hypot(
                state.x - state.trail.last().first,
                state.y - state.trail.last().second
            ) > 0.035
        ) {
            state.trail += state.x to state.y
            if (state.trail.size > 500) state.trail.removeAt(0)
        }
    }

    private fun closestPointOnSegment(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        px: Double,
        py: Double
    ): Pair<Double, Double> {
        val dx = bx - ax
        val dy = by - ay
        val denom = dx * dx + dy * dy
        if (denom <= 1e-12) return ax to ay
        val t = (((px - ax) * dx + (py - ay) * dy) / denom).coerceIn(0.0, 1.0)
        return (ax + dx * t) to (ay + dy * t)
    }

    private fun smoothStep(x: Double): Double {
        val t = x.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)

    private fun result(state: SimState, settings: GreenSettings): SimResult {
        val dx = state.x
        val dy = state.y - settings.holeDistanceM
        return SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(dx, dy),
            elapsedSec = state.elapsed,
            lipOut = state.lipOut && !state.holed,
            cupContacts = state.cupContacts
        )
    }
}
