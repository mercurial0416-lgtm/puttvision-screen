package com.puttvision.screen

import kotlin.math.*

data class GreenSettings(
    var stimpMeters: Double = 2.8,
    var holeDistanceM: Double = 5.0,
    var sideSlopePct: Double = 0.0,   // + = right side is lower, ball breaks right
    var longSlopePct: Double = 0.0    // + = downhill toward the hole
)

data class SimState(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var running: Boolean = false,
    var holed: Boolean = false,
    var elapsed: Double = 0.0,
    val trail: MutableList<Pair<Double, Double>> = mutableListOf()
)

data class SimResult(
    val holed: Boolean,
    val finishX: Double,
    val finishY: Double,
    val distanceToCupM: Double,
    val elapsedSec: Double
)

class GreenPhysics {
    companion object {
        private const val G = 9.80665
        private const val STIMP_LAUNCH_MPS = 1.95072 // 6.4 ft/s from USGA educational material.
        private const val ROLLING_GRAVITY_FACTOR = 5.0 / 7.0
        private const val CUP_RADIUS_M = 0.054
    }

    fun launch(metrics: ShotMetrics, settings: GreenSettings): SimState {
        val a = Math.toRadians(metrics.launchAngleDeg)
        val speed = metrics.ballSpeedMps.coerceIn(0.05, 5.0)
        return SimState(
            vx = speed * sin(a),
            vy = speed * cos(a),
            running = true,
            trail = mutableListOf(0.0 to 0.0)
        )
    }

    fun step(state: SimState, settings: GreenSettings, dtRaw: Double): SimResult? {
        if (!state.running) return result(state, settings)

        val dt = dtRaw.coerceIn(0.001, 0.025)
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)

        // A Stimpmeter launches at a fixed speed; if it stops after S meters under
        // constant deceleration, a = v^2 / (2S).
        val frictionDecel = (STIMP_LAUNCH_MPS * STIMP_LAUNCH_MPS) / (2.0 * stimp)

        val slopeAx = ROLLING_GRAVITY_FACTOR * G * (settings.sideSlopePct / 100.0)
        val slopeAy = ROLLING_GRAVITY_FACTOR * G * (settings.longSlopePct / 100.0)

        val speed = hypot(state.vx, state.vy)
        var ax = slopeAx
        var ay = slopeAy

        if (speed > 0.003) {
            ax += -frictionDecel * state.vx / speed
            ay += -frictionDecel * state.vy / speed
        }

        val nvx = state.vx + ax * dt
        val nvy = state.vy + ay * dt

        // Prevent friction from numerically reversing a near-stopped ball unless
        // gravity down the slope is strong enough to overcome rolling resistance.
        val wouldReverse = state.vx * nvx + state.vy * nvy < 0.0
        val slopeMag = hypot(slopeAx, slopeAy)
        if (wouldReverse && slopeMag < frictionDecel) {
            state.vx = 0.0
            state.vy = 0.0
            state.running = false
            return result(state, settings)
        }

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

        val dx = state.x
        val dy = state.y - settings.holeDistanceM
        val toCup = hypot(dx, dy)
        val nowSpeed = hypot(state.vx, state.vy)

        // Approximate capture: center enters cup radius with a reasonable lip speed.
        if (toCup <= CUP_RADIUS_M && nowSpeed <= 1.15) {
            state.x = 0.0
            state.y = settings.holeDistanceM
            state.vx = 0.0
            state.vy = 0.0
            state.running = false
            state.holed = true
            return result(state, settings)
        }

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

        return null
    }

    private fun result(state: SimState, settings: GreenSettings): SimResult {
        val dx = state.x
        val dy = state.y - settings.holeDistanceM
        return SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(dx, dy),
            elapsedSec = state.elapsed
        )
    }
}
