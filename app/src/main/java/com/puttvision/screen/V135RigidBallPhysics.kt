package com.puttvision.screen

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V135 is a purpose-built six-DOF golf-ball solver.
 *
 * A general game rigid-body engine is intentionally not authoritative here: a single golf ball on
 * a calibrated putting surface benefits from a smaller deterministic solver where Stimp rolling
 * resistance, skid-to-roll transition, the regulation cup edge and cup-wall losses are explicit.
 * Rendering remains Filament; this class is the physical source of truth for the ball state.
 */
object V135RigidBallPhysics {
    const val G = 9.80665
    const val BALL_DIAMETER_M = 0.04267
    const val BALL_RADIUS_M = BALL_DIAMETER_M * 0.5
    const val BALL_MASS_KG = 0.04593
    const val BALL_INERTIA_KGM2 = 0.4 * BALL_MASS_KG * BALL_RADIUS_M * BALL_RADIUS_M
    const val CUP_DIAMETER_M = 0.108
    const val CUP_RADIUS_M = CUP_DIAMETER_M * 0.5
    const val CUP_DEPTH_M = 0.1016

    private const val STIMP_LAUNCH_MPS = 1.95072
    private const val MAX_SUBSTEP_SEC = 1.0 / 480.0
    private const val INITIAL_ROLL_FRACTION = 0.72
    private const val SKID_FRICTION_COEFF = 0.135
    private const val CUP_EDGE_RESTITUTION = 0.055
    private const val CUP_EDGE_FRICTION = 0.24
    private const val CUP_WALL_RESTITUTION = 0.035
    private const val CUP_WALL_FRICTION = 0.20
    private const val CUP_BOTTOM_RESTITUTION = 0.08
    private const val CUP_BOTTOM_FRICTION = 0.34
    private const val GROUND_RESTITUTION = 0.035
    private const val AIR_LINEAR_DAMPING = 0.018
    private const val SPIN_NORMAL_DAMPING = 5.5
    private const val STOP_SPEED_MPS = 0.008
    private const val STOP_SLIP_MPS = 0.012
    private const val TERRAIN_SAMPLE_M = 0.012

    data class V3(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = V3(x * k, y * k, z * k)
        operator fun unaryMinus() = V3(-x, -y, -z)
        fun dot(o: V3): Double = x * o.x + y * o.y + z * o.z
        fun cross(o: V3) = V3(
            y * o.z - z * o.y,
            z * o.x - x * o.z,
            x * o.y - y * o.x
        )
        fun mag2(): Double = dot(this)
        fun mag(): Double = sqrt(mag2())
        fun normalized(fallback: V3 = V3(0.0, 0.0, 1.0)): V3 {
            val m = mag()
            return if (m > 1e-12 && m.isFinite()) this * (1.0 / m) else fallback
        }
    }

    private data class SurfaceFrame(
        val height: Double,
        val dzdx: Double,
        val dzdy: Double,
        val normal: V3
    )

    fun initialize(state: SimState, settings: GreenSettings) {
        val surface = surfaceAt(settings, state.x, state.y)
        state.ballCenterZM = surface.height + BALL_RADIUS_M
        state.vz = surface.dzdx * state.vx + surface.dzdy * state.vy
        state.surfaceNormalX = surface.normal.x
        state.surfaceNormalY = surface.normal.y
        state.surfaceNormalZ = surface.normal.z

        // A putter-launched ball normally starts with some skid. Starting below pure-roll angular
        // velocity lets Coulomb contact friction physically converge it into true rolling.
        state.omegaXRadS = -INITIAL_ROLL_FRACTION * state.vy / BALL_RADIUS_M
        state.omegaYRadS = INITIAL_ROLL_FRACTION * state.vx / BALL_RADIUS_M
        state.omegaZRadS = 0.0
        state.orientationW = 1.0
        state.orientationX = 0.0
        state.orientationY = 0.0
        state.orientationZ = 0.0
        state.v135Airborne = false
        state.v135Initialized = true
        state.v135SlipSpeedMps = contactSlip(state, surface.normal).mag()
        state.cupVerticalOffsetM = 0.0
    }

    /** Returns true once the shot is physically finished. */
    fun step(state: SimState, settings: GreenSettings, dtRaw: Double, cupEnabled: Boolean): Boolean {
        if (!state.running) return true
        if (!state.v135Initialized || !state.ballCenterZM.isFinite()) initialize(state, settings)

        val dt = dtRaw.coerceIn(0.001, 0.033)
        val count = ceil(dt / MAX_SUBSTEP_SEC).toInt().coerceIn(1, 20)
        val h = dt / count

        repeat(count) {
            if (!state.running) return true
            if (state.v135Airborne) {
                stepFree(state, settings, h, cupEnabled)
            } else {
                stepSurface(state, settings, h, cupEnabled)
            }
            state.elapsed += h
            appendTrail(state)

            if (
                !state.x.isFinite() || !state.y.isFinite() || !state.ballCenterZM.isFinite() ||
                state.elapsed > 20.0 || state.y > settings.holeDistanceM + 8.0 || abs(state.x) > 8.0
            ) {
                state.running = false
                return true
            }
        }
        return !state.running
    }

    private fun stepSurface(state: SimState, settings: GreenSettings, dt: Double, cupEnabled: Boolean) {
        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val cupDistance = hypot(state.x - cupX, state.y - cupY)
        if (cupEnabled && cupDistance < CUP_RADIUS_M) {
            val surface = surfaceAt(settings, state.x, state.y)
            state.ballCenterZM = max(state.ballCenterZM, surface.height + BALL_RADIUS_M)
            state.vz = surface.dzdx * state.vx + surface.dzdy * state.vy
            state.v135Airborne = true
            state.cupPhase = V134CupPhase.DROP
            state.cupPhaseElapsedSec = 0.0
            state.cupEntrySpeedMps = hypot(state.vx, state.vy)
            stepFree(state, settings, dt, cupEnabled)
            return
        }

        val surface = surfaceAt(settings, state.x, state.y)
        state.surfaceNormalX = surface.normal.x
        state.surfaceNormalY = surface.normal.y
        state.surfaceNormalZ = surface.normal.z
        state.ballCenterZM = surface.height + BALL_RADIUS_M
        state.cupVerticalOffsetM = 0.0
        state.cupPhase = V134CupPhase.NONE
        state.cupPhaseElapsedSec = 0.0

        val normal = surface.normal
        val gravity = V3(0.0, 0.0, -G)
        val projectedGravity = gravity - normal * gravity.dot(normal)
        val rollingGravity = projectedGravity * (5.0 / 7.0)
        val drive = V3(rollingGravity.x, rollingGravity.y, 0.0)
        val driveMag = hypot(drive.x, drive.y)
        val rollingDecel = rollingDeceleration(settings)
        val speed = hypot(state.vx, state.vy)

        if (speed < 0.003 && driveMag <= rollingDecel) {
            state.vx = 0.0
            state.vy = 0.0
            state.vz = 0.0
            state.omegaXRadS = 0.0
            state.omegaYRadS = 0.0
            state.v135SlipSpeedMps = 0.0
            state.running = false
            return
        }

        var accel = drive
        if (speed > 0.003) {
            accel += V3(-state.vx / speed * rollingDecel, -state.vy / speed * rollingDecel, 0.0)
        } else if (driveMag > rollingDecel) {
            accel = drive * ((driveMag - rollingDecel) / driveMag)
        }

        val tangentVz = surface.dzdx * state.vx + surface.dzdy * state.vy
        val velocity = V3(state.vx, state.vy, tangentVz)
        var omega = V3(state.omegaXRadS, state.omegaYRadS, state.omegaZRadS)
        val contactArm = normal * -BALL_RADIUS_M
        val contactVelocity = velocity + omega.cross(contactArm)
        val slip = contactVelocity - normal * contactVelocity.dot(normal)
        val slipMag = slip.mag()
        state.v135SlipSpeedMps = slipMag

        if (slipMag > 0.0008) {
            // For a solid sphere, a tangential friction impulse changes contact-point slip 3.5x
            // faster than COM speed. Clamp by that effective mass so a 480 Hz step never overshoots.
            val maxFrictionAccel = SKID_FRICTION_COEFF * G
            val frictionAccelMag = min(maxFrictionAccel, slipMag / (3.5 * dt))
            val frictionAccel = slip * (-frictionAccelMag / slipMag)
            accel += frictionAccel
            val alpha = contactArm.cross(frictionAccel) * (1.0 / (0.4 * BALL_RADIUS_M * BALL_RADIUS_M))
            omega += alpha * dt
        }

        state.vx += accel.x * dt
        state.vy += accel.y * dt
        state.x += state.vx * dt
        state.y += state.vy * dt

        val nextSurface = surfaceAt(settings, state.x, state.y)
        state.ballCenterZM = nextSurface.height + BALL_RADIUS_M
        state.vz = nextSurface.dzdx * state.vx + nextSurface.dzdy * state.vy
        val velocityAfter = V3(state.vx, state.vy, state.vz)
        val nextNormal = nextSurface.normal

        // Once skid is small, enforce the physically correct no-slip angular velocity while keeping
        // any spin about the surface normal. This prevents numerical creep from accumulating.
        val spinNormal = omega.dot(nextNormal) * exp(-SPIN_NORMAL_DAMPING * dt)
        if (slipMag < 0.045) {
            val rollingOmega = nextNormal.cross(velocityAfter) * (1.0 / BALL_RADIUS_M) + nextNormal * spinNormal
            val blend = (dt * 34.0).coerceIn(0.0, 1.0)
            omega = omega * (1.0 - blend) + rollingOmega * blend
        } else {
            val tangentOmega = omega - nextNormal * omega.dot(nextNormal)
            omega = tangentOmega + nextNormal * spinNormal
        }
        writeOmega(state, omega)
        integrateOrientation(state, omega, dt)

        val newSpeed = hypot(state.vx, state.vy)
        state.v135SlipSpeedMps = contactSlip(state, nextNormal).mag()
        if (newSpeed < STOP_SPEED_MPS && state.v135SlipSpeedMps < STOP_SLIP_MPS && driveMag <= rollingDecel) {
            state.vx = 0.0
            state.vy = 0.0
            state.vz = 0.0
            state.omegaXRadS = 0.0
            state.omegaYRadS = 0.0
            state.running = false
            return
        }

        val newCupDistance = hypot(state.x - cupX, state.y - cupY)
        if (cupEnabled && newCupDistance < CUP_RADIUS_M) {
            state.v135Airborne = true
            state.cupPhase = V134CupPhase.DROP
            state.cupPhaseElapsedSec = 0.0
            state.cupEntrySpeedMps = newSpeed
        }
    }

    private fun stepFree(state: SimState, settings: GreenSettings, dt: Double, cupEnabled: Boolean) {
        if (!cupEnabled) {
            state.v135Airborne = false
            stepSurface(state, settings, dt, false)
            return
        }

        state.cupPhaseElapsedSec += dt
        val damping = exp(-AIR_LINEAR_DAMPING * dt)
        state.vx *= damping
        state.vy *= damping
        state.vz = state.vz * damping - G * dt
        state.x += state.vx * dt
        state.y += state.vy * dt
        state.ballCenterZM += state.vz * dt

        val omega = V3(state.omegaXRadS, state.omegaYRadS, state.omegaZRadS)
        integrateOrientation(state, omega, dt)

        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val cupSurfaceZ = GreenTerrain.effectiveHeightAt(settings, cupX, cupY)
        var dx = state.x - cupX
        var dy = state.y - cupY
        var radial = hypot(dx, dy)
        var rimHit = false

        // Regulation sharp cup edge, represented as a circular line. The ball is a true sphere;
        // collision only begins after gravity has lowered the center enough to touch that edge.
        if (radial > 1e-8) {
            val ex = cupX + CUP_RADIUS_M * dx / radial
            val ey = cupY + CUP_RADIUS_M * dy / radial
            val edgeToBall = V3(state.x - ex, state.y - ey, state.ballCenterZM - cupSurfaceZ)
            val distance = edgeToBall.mag()
            if (distance < BALL_RADIUS_M && state.ballCenterZM > cupSurfaceZ - CUP_DEPTH_M) {
                val n = edgeToBall.normalized(V3(dx / radial, dy / radial, 0.0))
                resolveContact(state, n, BALL_RADIUS_M - distance, CUP_EDGE_RESTITUTION, CUP_EDGE_FRICTION)
                markCupContact(state)
                state.cupPhase = V134CupPhase.RIM
                state.cupRimRadiusM = radial
                state.cupRimAngleRad = kotlin.math.atan2(dy, dx)
                rimHit = true
            }
        }

        dx = state.x - cupX
        dy = state.y - cupY
        radial = hypot(dx, dy)

        // Once below the lip, the sphere collides with the cylindrical cup/liner wall rather than
        // being magnetically steered to the center.
        val maxCenterRadius = CUP_RADIUS_M - BALL_RADIUS_M
        if (
            radial > maxCenterRadius && radial > 1e-8 &&
            state.ballCenterZM < cupSurfaceZ + BALL_RADIUS_M * 0.18 &&
            state.ballCenterZM > cupSurfaceZ - CUP_DEPTH_M
        ) {
            val n = V3(-dx / radial, -dy / radial, 0.0)
            resolveContact(
                state,
                n,
                radial - maxCenterRadius,
                CUP_WALL_RESTITUTION,
                CUP_WALL_FRICTION
            )
            state.cupWallContacts++
            markCupContact(state)
            state.cupPhase = V134CupPhase.RIM
        }

        val bottomCenterZ = cupSurfaceZ - CUP_DEPTH_M + BALL_RADIUS_M
        if (state.ballCenterZM < bottomCenterZ) {
            resolveContact(
                state,
                V3(0.0, 0.0, 1.0),
                bottomCenterZ - state.ballCenterZM,
                CUP_BOTTOM_RESTITUTION,
                CUP_BOTTOM_FRICTION
            )
            state.cupBottomContacts++
            val speed3 = sqrt(state.vx * state.vx + state.vy * state.vy + state.vz * state.vz)
            if (speed3 < 0.075 && abs(state.vz) < 0.055 && radial <= maxCenterRadius + 0.004) {
                state.ballCenterZM = bottomCenterZ
                state.vx = 0.0
                state.vy = 0.0
                state.vz = 0.0
                state.omegaXRadS *= 0.18
                state.omegaYRadS *= 0.18
                state.omegaZRadS *= 0.18
                state.cupPhase = V134CupPhase.SETTLED
                state.cupVerticalOffsetM = state.ballCenterZM - (cupSurfaceZ + BALL_RADIUS_M)
                state.v135Airborne = false
                state.running = false
                state.holed = true
                state.lipOut = false
                return
            }
        }

        dx = state.x - cupX
        dy = state.y - cupY
        radial = hypot(dx, dy)

        // A fast putt may bridge the opening: after a short free fall it can strike/reach the far
        // green again. That outcome is produced by time of flight, not a speed threshold.
        if (radial >= CUP_RADIUS_M) {
            val surface = surfaceAt(settings, state.x, state.y)
            val supportZ = surface.height + BALL_RADIUS_M
            if (state.ballCenterZM <= supportZ) {
                resolveContact(
                    state,
                    surface.normal,
                    max(0.0, supportZ - state.ballCenterZM),
                    GROUND_RESTITUTION,
                    0.28
                )
                state.ballCenterZM = supportZ
                state.vz = surface.dzdx * state.vx + surface.dzdy * state.vy
                state.v135Airborne = false
                if (state.cupPhase != V134CupPhase.NONE || state.cupContacts > 0) state.lipOut = true
                if (!rimHit && state.cupContacts == 0) state.bridgeCount++
                state.cupPhase = V134CupPhase.NONE
                state.cupPhaseElapsedSec = 0.0
                state.lastCupContactSec = state.elapsed
            }
        }

        val localSurface = GreenTerrain.effectiveHeightAt(settings, state.x, state.y)
        state.cupVerticalOffsetM = state.ballCenterZM - (localSurface + BALL_RADIUS_M)
        if (state.v135Airborne && !rimHit && state.cupPhase == V134CupPhase.RIM && state.elapsed - state.lastCupContactSec > 0.035) {
            state.cupPhase = V134CupPhase.DROP
        }
    }

    private fun resolveContact(
        state: SimState,
        normalRaw: V3,
        penetration: Double,
        restitution: Double,
        friction: Double
    ) {
        val normal = normalRaw.normalized()
        val correction = normal * (penetration.coerceAtLeast(0.0) + 1e-5)
        state.x += correction.x
        state.y += correction.y
        state.ballCenterZM += correction.z

        var velocity = V3(state.vx, state.vy, state.vz)
        var omega = V3(state.omegaXRadS, state.omegaYRadS, state.omegaZRadS)
        val arm = normal * -BALL_RADIUS_M
        val contactVelocity = velocity + omega.cross(arm)
        val vn = contactVelocity.dot(normal)
        if (vn < 0.0) {
            val normalDeltaSpeed = -(1.0 + restitution) * vn
            velocity += normal * normalDeltaSpeed

            val afterNormalContactVelocity = velocity + omega.cross(arm)
            val tangent = afterNormalContactVelocity - normal * afterNormalContactVelocity.dot(normal)
            val tangentSpeed = tangent.mag()
            if (tangentSpeed > 1e-7) {
                val maxComDelta = friction * normalDeltaSpeed
                val neededComDelta = tangentSpeed / 3.5
                val delta = min(maxComDelta, neededComDelta)
                val dv = tangent * (-delta / tangentSpeed)
                velocity += dv
                val alphaImpulse = arm.cross(dv) * (1.0 / (0.4 * BALL_RADIUS_M * BALL_RADIUS_M))
                omega += alphaImpulse
            }
        }

        state.vx = velocity.x
        state.vy = velocity.y
        state.vz = velocity.z
        writeOmega(state, omega)
    }

    private fun markCupContact(state: SimState) {
        if (state.elapsed - state.lastCupContactSec > 0.028) {
            state.cupContacts++
            state.lastCupContactSec = state.elapsed
        }
        state.lipOut = true
    }

    private fun rollingDeceleration(settings: GreenSettings): Double {
        val stimp = settings.stimpMeters.coerceIn(1.5, 5.0)
        return STIMP_LAUNCH_MPS * STIMP_LAUNCH_MPS / (2.0 * stimp)
    }

    private fun surfaceAt(settings: GreenSettings, x: Double, y: Double): SurfaceFrame {
        val h = TERRAIN_SAMPLE_M
        val center = GreenTerrain.effectiveHeightAt(settings, x, y)
        val dzdx = (
            GreenTerrain.effectiveHeightAt(settings, x + h, y) -
                GreenTerrain.effectiveHeightAt(settings, x - h, y)
            ) / (2.0 * h)
        val dzdy = (
            GreenTerrain.effectiveHeightAt(settings, x, y + h) -
                GreenTerrain.effectiveHeightAt(settings, x, y - h)
            ) / (2.0 * h)
        val normal = V3(-dzdx, -dzdy, 1.0).normalized()
        return SurfaceFrame(center, dzdx, dzdy, normal)
    }

    private fun contactSlip(state: SimState, normal: V3): V3 {
        val velocity = V3(state.vx, state.vy, state.vz)
        val omega = V3(state.omegaXRadS, state.omegaYRadS, state.omegaZRadS)
        val contact = velocity + omega.cross(normal * -BALL_RADIUS_M)
        return contact - normal * contact.dot(normal)
    }

    private fun integrateOrientation(state: SimState, omega: V3, dt: Double) {
        val speed = omega.mag()
        if (speed < 1e-9 || !speed.isFinite()) return
        val half = 0.5 * speed * dt
        val s = sin(half) / speed
        val dw = cos(half)
        val dx = omega.x * s
        val dy = omega.y * s
        val dz = omega.z * s

        // World-space incremental rotation: dq * q.
        val qw = state.orientationW
        val qx = state.orientationX
        val qy = state.orientationY
        val qz = state.orientationZ
        var nw = dw * qw - dx * qx - dy * qy - dz * qz
        var nx = dw * qx + dx * qw + dy * qz - dz * qy
        var ny = dw * qy - dx * qz + dy * qw + dz * qx
        var nz = dw * qz + dx * qy - dy * qx + dz * qw
        val m = sqrt(nw * nw + nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12)
        nw /= m; nx /= m; ny /= m; nz /= m
        state.orientationW = nw
        state.orientationX = nx
        state.orientationY = ny
        state.orientationZ = nz
        state.ballRotationRadians += speed * dt
        if (state.ballRotationRadians > 2.0 * PI) state.ballRotationRadians %= 2.0 * PI
    }

    private fun writeOmega(state: SimState, omega: V3) {
        state.omegaXRadS = omega.x
        state.omegaYRadS = omega.y
        state.omegaZRadS = omega.z
    }

    private fun appendTrail(state: SimState) {
        if (
            state.trail.isEmpty() ||
            hypot(state.x - state.trail.last().first, state.y - state.trail.last().second) > 0.035
        ) {
            state.trail += state.x to state.y
            if (state.trail.size > 500) state.trail.removeAt(0)
        }
    }
}
