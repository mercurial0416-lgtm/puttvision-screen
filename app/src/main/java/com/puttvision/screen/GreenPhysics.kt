package com.puttvision.screen

import kotlin.math.*

data class GreenSettings(
    var stimpMeters: Double = 2.8,
    var holeDistanceM: Double = 5.0,
    var sideSlopePct: Double = 0.0,   // + = right side is lower, ball breaks right
    var longSlopePct: Double = 0.0,   // + = downhill toward the hole
    var terrainProfileId: Int = -1    // -1 = uniform plane, 0..23 = practice terrain profile
)

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
    var lastCupContactSec: Double = -10.0
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
        // Center-path radius that lets the ball fall rather than ride the rim.
        private const val CAPTURE_CENTER_RADIUS_M = CUP_RADIUS_M - BALL_RADIUS_M * 0.24
        private const val RIM_CONTACT_RADIUS_M = CUP_RADIUS_M + BALL_RADIUS_M * 0.80
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

        if (state.trail.isEmpty() ||
            hypot(
                state.x - state.trail.last().first,
                state.y - state.trail.last().second
            ) > 0.035
        ) {
            state.trail += state.x to state.y
            if (state.trail.size > 500) state.trail.removeAt(0)
        }

        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val nowSpeed = hypot(state.vx, state.vy)
        val closest = closestPointOnSegment(oldX, oldY, state.x, state.y, cupX, cupY)
        val closestDx = closest.first - cupX
        val closestDy = closest.second - cupY
        val closestDist = hypot(closestDx, closestDy)
        val normalizedOffset = (closestDist / CAPTURE_CENTER_RADIUS_M).coerceIn(0.0, 1.4)
        // Off-centre balls need a slower lip speed to fall. A dead-centre ball
        // can carry more speed, while a fast skim is explicitly a lip-out.
        val captureSpeed = (1.20 - normalizedOffset * 0.48).coerceIn(0.52, 1.20)

        // Capture is evaluated while the ball is still approaching the opening.
        // Do this before rim contact so a centered, properly paced putt is not
        // incorrectly bounced off an imaginary vertical wall in front of the cup.
        if (cupEnabled && closestDist <= CAPTURE_CENTER_RADIUS_M && nowSpeed <= captureSpeed) {
            state.x = cupX
            state.y = cupY
            state.vx = 0.0
            state.vy = 0.0
            state.running = false
            state.holed = true
            return result(state, settings)
        }

        val oldCupDistance = hypot(oldX - cupX, oldY - cupY)
        val newCupDistance = hypot(state.x - cupX, state.y - cupY)
        val crossedCupPlane =
            (oldY - cupY) * (state.y - cupY) <= 0.0 && abs(state.y - oldY) > 1e-9
        val reachedClosestApproach = newCupDistance >= oldCupDistance || crossedCupPlane

        // A rim collision is only possible once the trajectory has reached/passed
        // its closest approach. Entering the outer rim radius while still moving
        // toward the hole is not itself a collision.
        if (
            cupEnabled &&
            closestDist <= RIM_CONTACT_RADIUS_M &&
            reachedClosestApproach &&
            state.elapsed - state.lastCupContactSec > 0.075
        ) {
            state.lipOut = true
            state.cupContacts++
            state.lastCupContactSec = state.elapsed

            // A true edge contact gets a damped radial rebound. A very fast
            // centre-line pass is allowed to bridge the cup rather than receiving
            // an artificial sideways kick.
            if (closestDist > CAPTURE_CENTER_RADIUS_M * 0.56 && closestDist > 1e-5 && nowSpeed <= 1.85) {
                val nx = closestDx / closestDist
                val ny = closestDy / closestDist
                val vn = state.vx * nx + state.vy * ny
                if (vn < 0.0) {
                    val tx = state.vx - vn * nx
                    val ty = state.vy - vn * ny
                    val rebound = -vn * 0.34
                    state.vx = tx * 0.78 + rebound * nx
                    state.vy = ty * 0.78 + rebound * ny
                    // Keep the ball just outside the effective rim after contact.
                    val push = RIM_CONTACT_RADIUS_M + 0.002
                    state.x = cupX + nx * push
                    state.y = cupY + ny * push
                }
            }
        }

        val dx = state.x - cupX
        val dy = state.y - cupY
        val toCup = hypot(dx, dy)

        if (state.elapsed > 20.0 || state.y > settings.holeDistanceM + 8.0 ||
            abs(state.x) > 8.0
        ) {
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
