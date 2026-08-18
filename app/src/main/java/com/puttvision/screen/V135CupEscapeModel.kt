package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Physics guard for the uncatchable region of cup-entry phase space.
 *
 * Holmes' rim model, reiterated and unified with in-hole motion by Hogan & Antali (2025), gives
 * 1.626 m/s as the maximum rim-entry speed at which a regulation golf ball can be captured even
 * for the most favourable (centered) path. This is not a gameplay threshold: it is a mechanical
 * upper bound. Above it the ball must escape, so this integrator continues the actual unsupported
 * ballistic crossing rather than letting numerical rim friction drain enough energy to create an
 * impossible hole-out.
 *
 * Reference: Hogan, S. J. & Antali, M. (2025), Mechanics of the golf lip out,
 * Royal Society Open Science 12:250907, doi:10.1098/rsos.250907.
 */
object V135CupEscapeModel {
    const val MAX_PHYSICAL_CAPTURE_SPEED_MPS = 1.626
    private const val MAX_SUBSTEP_SEC = 1.0 / 960.0
    private const val AIR_LINEAR_DAMPING = 0.018

    fun isUncatchable(state: SimState): Boolean =
        state.cupEntrySpeedMps.isFinite() &&
            state.cupEntrySpeedMps > MAX_PHYSICAL_CAPTURE_SPEED_MPS &&
            state.cupEntrySpeedMps > 0.0

    /**
     * Continue a guaranteed-escape cup crossing with 960 Hz ballistic microsteps. Returns true if
     * the ball has re-established green support or the simulation became invalid.
     */
    fun stepEscape(state: SimState, settings: GreenSettings, dtRaw: Double): Boolean {
        val dt = dtRaw.coerceIn(0.001, 0.033)
        val count = ceil(dt / MAX_SUBSTEP_SEC).toInt().coerceIn(1, 40)
        val h = dt / count
        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val radius = V135RigidBallPhysics.BALL_RADIUS_M
        val cupRadius = V135RigidBallPhysics.CUP_RADIUS_M

        repeat(count) {
            val damping = exp(-AIR_LINEAR_DAMPING * h)
            state.vx *= damping
            state.vy *= damping
            state.vz = state.vz * damping - V135RigidBallPhysics.G * h
            state.x += state.vx * h
            state.y += state.vy * h
            state.ballCenterZM += state.vz * h
            state.elapsed += h
            state.cupPhaseElapsedSec += h

            val dx = state.x - cupX
            val dy = state.y - cupY
            val radial = hypot(dx, dy)
            val localSurface = GreenTerrain.effectiveHeightAt(settings, state.x, state.y)
            state.cupVerticalOffsetM = state.ballCenterZM - (localSurface + radius)
            state.cupPhase = V134CupPhase.DROP
            state.v135Airborne = true

            // Once the center has crossed the far opening boundary, the plane can support the
            // sphere again. Resolve the downward impact with a small turf restitution and keep
            // horizontal momentum; a fast putt therefore visibly bridges/skips the cup.
            val passedCupCenter = state.vx * dx + state.vy * dy > 0.0
            if (radial >= cupRadius && passedCupCenter) {
                val supportZ = localSurface + radius
                if (state.ballCenterZM <= supportZ) {
                    state.ballCenterZM = supportZ
                    if (state.vz < 0.0) state.vz = -state.vz * 0.035
                    if (abs(state.vz) < 0.04) state.vz = 0.0
                    state.v135Airborne = false
                    state.cupPhase = V134CupPhase.NONE
                    state.cupPhaseElapsedSec = 0.0
                    state.cupVerticalOffsetM = 0.0
                    state.bridgeCount++
                    state.cupContacts++
                    state.lipOut = true
                    state.lastCupContactSec = state.elapsed
                    state.v135CaptureForbidden = false
                    return true
                }
            }

            // Safety only. A guaranteed-escape trajectory must never be allowed to settle on the
            // cup bottom due to numerical drift.
            val cupSurface = GreenTerrain.effectiveHeightAt(settings, cupX, cupY)
            val bottom = cupSurface - V135RigidBallPhysics.CUP_DEPTH_M + radius
            if (state.ballCenterZM < bottom - radius || !finite(state)) {
                val speed = hypot(state.vx, state.vy).coerceAtLeast(0.15)
                val mag = hypot(dx, dy).coerceAtLeast(1e-6)
                state.x = cupX + dx / mag * (cupRadius + 0.002)
                state.y = cupY + dy / mag * (cupRadius + 0.002)
                state.ballCenterZM = GreenTerrain.effectiveHeightAt(settings, state.x, state.y) + radius
                state.vx = if (hypot(state.vx, state.vy) > 1e-6) state.vx else dx / mag * speed
                state.vy = if (hypot(state.vx, state.vy) > 1e-6) state.vy else dy / mag * speed
                state.vz = 0.0
                state.v135Airborne = false
                state.cupPhase = V134CupPhase.NONE
                state.cupVerticalOffsetM = 0.0
                state.bridgeCount++
                state.cupContacts++
                state.lipOut = true
                state.v135CaptureForbidden = false
                return true
            }
        }
        return false
    }

    private fun finite(state: SimState): Boolean =
        state.x.isFinite() && state.y.isFinite() && state.ballCenterZM.isFinite() &&
            state.vx.isFinite() && state.vy.isFinite() && state.vz.isFinite()
}
