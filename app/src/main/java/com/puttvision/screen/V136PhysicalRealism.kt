package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Additional physical effects layered around the V135 six-DOF core without weakening it. */
object V136PhysicalRealism {
    private const val FLAGSTICK_RADIUS_M = 0.0065
    private const val FLAGSTICK_RESTITUTION = 0.16
    private const val FLAGSTICK_TANGENT_RETENTION = 0.72
    private const val SLOW_FLAGSTICK_CAPTURE_MPS = 1.15

    /**
     * Converts rolling-speed and user surface-condition controls into the instantaneous effective
     * Stimp seen by the six-DOF core.
     *
     * V137 first applies a Stimp-preserving speed-dependent rolling resistance. Directional grain,
     * moisture and firmness then modify that baseline. With neutral environmental controls the
     * selected Stimp still represents the calibrated total roll distance at the standard Stimp
     * launch speed even though instantaneous deceleration now changes during the putt.
     */
    fun effectiveSettings(settings: GreenSettings, state: SimState): GreenSettings {
        if (state.v135Airborne || !state.running) return settings
        val speed = hypot(state.vx, state.vy)
        if (speed < 1e-5) return settings

        val speedDependentStimp = V137RollingResistance.effectiveStimp(settings.stimpMeters, speed)
        val grainStrength = settings.grainStrength01.coerceIn(0.0, 1.0)
        val moisture = settings.moisture01.coerceIn(0.0, 1.0)
        val firmness = settings.firmness01.coerceIn(0.0, 1.0)

        val travel = atan2(state.vy, state.vx)
        val grain = Math.toRadians(settings.grainDirectionDeg)
        val along = cos(travel - grain)
        val grainFactor = 1.0 + 0.115 * grainStrength * along
        val moistureFactor = 1.0 - 0.16 * (moisture - 0.5)
        val firmnessFactor = 1.0 + 0.07 * (firmness - 0.5)
        val environmentFactor = (grainFactor * moistureFactor * firmnessFactor).coerceIn(0.74, 1.28)
        return settings.copy(stimpMeters = (speedDependentStimp * environmentFactor).coerceIn(1.0, 6.2))
    }

    /**
     * Adds a tiny deterministic lateral component for imperfect trueness. This represents local
     * grass/footprint deviation below the macro GreenTerrain mesh; it is zero for trueness=1.
     */
    fun applyTrueness(state: SimState, settings: GreenSettings, dtRaw: Double) {
        if (!state.running || state.v135Airborne) return
        val defect = (1.0 - settings.trueness01.coerceIn(0.0, 1.0))
        if (defect <= 1e-6) return
        val speed = hypot(state.vx, state.vy)
        if (speed < 0.03) return
        val dt = dtRaw.coerceIn(0.001, 0.033)
        val nx = -state.vy / speed
        val ny = state.vx / speed
        val micro = sin(state.x * 47.0 + state.y * 31.0) * cos(state.x * 23.0 - state.y * 61.0)
        val lateralAccel = micro * defect * 0.22
        state.vx += nx * lateralAccel * dt
        state.vy += ny * lateralAccel * dt
    }

    /**
     * Continuous swept sphere-vs-cylinder flagstick collision. Segment CCD prevents the ball from
     * tunnelling through a thin pole even when the display frame is much slower than V135 physics.
     */
    fun resolveFlagstickSweep(
        state: SimState,
        settings: GreenSettings,
        fromX: Double,
        fromY: Double,
        fromZ: Double,
        toX: Double = state.x,
        toY: Double = state.y,
        toZ: Double = state.ballCenterZM
    ): Boolean {
        if (!settings.flagstickIn || !state.running || state.holed) return false
        if (!fromX.isFinite() || !fromY.isFinite() || !fromZ.isFinite() || !toZ.isFinite()) return false

        val cx = 0.0
        val cy = settings.holeDistanceM
        val sx = toX - fromX
        val sy = toY - fromY
        val len2 = sx * sx + sy * sy
        val t = if (len2 > 1e-12) {
            (((cx - fromX) * sx + (cy - fromY) * sy) / len2).coerceIn(0.0, 1.0)
        } else 0.0
        val px = fromX + sx * t
        val py = fromY + sy * t
        val pz = fromZ + (toZ - fromZ) * t
        val dx = px - cx
        val dy = py - cy
        val dist = hypot(dx, dy)
        val contactRadius = V135RigidBallPhysics.BALL_RADIUS_M + FLAGSTICK_RADIUS_M
        val cupSurface = GreenTerrain.effectiveHeightAt(settings, cx, cy)
        val poleBottom = cupSurface - V135RigidBallPhysics.CUP_DEPTH_M
        val poleTop = cupSurface + 1.05
        if (dist >= contactRadius || pz + V135RigidBallPhysics.BALL_RADIUS_M < poleBottom || pz - V135RigidBallPhysics.BALL_RADIUS_M > poleTop) {
            return false
        }

        var nx = dx
        var ny = dy
        var nmag = hypot(nx, ny)
        if (nmag < 1e-8) {
            val vmag = hypot(state.vx, state.vy)
            if (vmag > 1e-8) {
                nx = -state.vx / vmag
                ny = -state.vy / vmag
            } else {
                nx = 1.0
                ny = 0.0
            }
            nmag = 1.0
        }
        nx /= nmag
        ny /= nmag

        val horizontalSpeed = hypot(state.vx, state.vy)
        val vn = state.vx * nx + state.vy * ny
        val tx = -ny
        val ty = nx
        val vt = state.vx * tx + state.vy * ty

        // Rewind to the physical contact shell before applying impulse.
        state.x = cx + nx * (contactRadius + 1e-5)
        state.y = cy + ny * (contactRadius + 1e-5)
        state.ballCenterZM = pz

        if (horizontalSpeed <= SLOW_FLAGSTICK_CAPTURE_MPS && hypot(state.x - cx, state.y - cy) < V135RigidBallPhysics.CUP_RADIUS_M) {
            // A dying putt striking an in-hole stick sheds most horizontal energy and remains
            // unsupported, letting the V135 gravity/cup solver decide wall/bottom settlement.
            state.vx *= 0.18
            state.vy *= 0.18
            state.vz = min(state.vz, -0.16)
            state.v135Airborne = true
            state.cupPhase = V134CupPhase.DROP
        } else if (vn < 0.0) {
            val newVn = -vn * FLAGSTICK_RESTITUTION
            val newVt = vt * FLAGSTICK_TANGENT_RETENTION
            state.vx = nx * newVn + tx * newVt
            state.vy = ny * newVn + ty * newVt
            state.lipOut = true
        }

        // Pole contact also bleeds spin around axes transverse to the stick.
        state.omegaXRadS *= 0.78
        state.omegaYRadS *= 0.78
        state.omegaZRadS *= 0.90
        state.flagstickContacts++
        state.cupContacts++
        state.lastCupContactSec = state.elapsed
        return true
    }
}
