package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos

data class GreenSettings(
    var stimpMeters: Double = 2.8,
    var holeDistanceM: Double = 5.0,
    var sideSlopePct: Double = 0.0,   // + = right side is lower, ball breaks right
    var longSlopePct: Double = 0.0,   // + = downhill toward the hole
    var terrainProfileId: Int = -1,   // -1 = uniform plane, 0..23 = practice terrain profile

    // V136 surface/obstacle realism. Neutral defaults preserve existing Stimp calibration while
    // allowing real-green anisotropy and imperfections to be enabled without a second physics path.
    var flagstickIn: Boolean = false,
    var grainDirectionDeg: Double = 0.0,
    var grainStrength01: Double = 0.0,
    var moisture01: Double = 0.5,
    var firmness01: Double = 0.5,
    var trueness01: Double = 1.0
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

    // V134 compatibility. V135 no longer scripts these phases; they describe the true 3D contact
    // state produced by the rigid-ball solver so UI/replay code can keep the same public contract.
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
    var cupDropDurationSec: Double = 0.0,

    // V135 physical state: center-of-mass height, 3D velocity, angular velocity and orientation.
    // These are kept directly on SimState so replay, renderer and diagnostics can observe the exact
    // physics frame instead of reconstructing motion from an animation.
    var ballCenterZM: Double = Double.NaN,
    var vz: Double = 0.0,
    var omegaXRadS: Double = 0.0,
    var omegaYRadS: Double = 0.0,
    var omegaZRadS: Double = 0.0,
    var orientationW: Double = 1.0,
    var orientationX: Double = 0.0,
    var orientationY: Double = 0.0,
    var orientationZ: Double = 0.0,
    var ballRotationRadians: Double = 0.0,
    var surfaceNormalX: Double = 0.0,
    var surfaceNormalY: Double = 0.0,
    var surfaceNormalZ: Double = 1.0,
    var v135SlipSpeedMps: Double = 0.0,
    var v135Airborne: Boolean = false,
    var v135Initialized: Boolean = false,
    var v135CaptureForbidden: Boolean = false,
    var cupWallContacts: Int = 0,
    var cupBottomContacts: Int = 0,
    var bridgeCount: Int = 0,

    // V136 obstacle telemetry.
    var flagstickContacts: Int = 0
)

data class SimResult(
    val holed: Boolean,
    val finishX: Double,
    val finishY: Double,
    val distanceToCupM: Double,
    val elapsedSec: Double,
    val lipOut: Boolean = false,
    val cupContacts: Int = 0,
    val bridgeCount: Int = 0,
    val flagstickContacts: Int = 0
)

/**
 * Stable facade retained for all existing callers.
 *
 * V135 delegates every ordinary physical step to [V135RigidBallPhysics], which runs fixed
 * microsteps at up to 480 Hz and owns translational, rotational and cup-contact dynamics. V136
 * layers anisotropic surface conditions and swept flagstick contact around that core. For a cup
 * without the flagstick, the analytically uncatchable region above the published regulation-cup
 * capture limit is still forced through [V135CupEscapeModel] so numerical contact damping cannot
 * create a physically impossible high-speed hole-out.
 */
class GreenPhysics {
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
        ).also { V135RigidBallPhysics.initialize(it, settings) }
    }

    fun step(
        state: SimState,
        settings: GreenSettings,
        dtRaw: Double,
        cupEnabled: Boolean = true
    ): SimResult? {
        if (!state.running) return result(state, settings)

        // The 1.626 m/s bare-cup capture limit does not apply when a physical flagstick can absorb
        // or redirect the ball, so only use the guaranteed-escape integrator with the stick out.
        if (cupEnabled && !settings.flagstickIn && state.v135CaptureForbidden && state.v135Airborne) {
            V135CupEscapeModel.stepEscape(state, settings, dtRaw)
            return if (!state.running) result(state, settings) else null
        }

        val beforeX = state.x
        val beforeY = state.y
        val beforeZ = state.ballCenterZM
        val physicalSettings = V136PhysicalRealism.effectiveSettings(settings, state)
        val finished = V135RigidBallPhysics.step(state, physicalSettings, dtRaw, cupEnabled)

        // Sub-grid green trueness is intentionally applied after the deterministic 6DOF core so
        // macro terrain normals and Stimp calibration remain authoritative and reproducible.
        V136PhysicalRealism.applyTrueness(state, settings, dtRaw)

        if (cupEnabled && settings.flagstickIn && state.running) {
            V136PhysicalRealism.resolveFlagstickSweep(
                state = state,
                settings = settings,
                fromX = beforeX,
                fromY = beforeY,
                fromZ = beforeZ
            )
        }

        // Detect entry into the rigorously uncatchable part of bare-cup rim phase space before later
        // frames can dissipate it into a false positive. With the stick in, the stick itself remains
        // free to change the outcome, so this mechanical bound is intentionally bypassed.
        if (
            cupEnabled && !settings.flagstickIn && state.v135Airborne && !state.holed &&
            V135CupEscapeModel.isUncatchable(state)
        ) {
            state.v135CaptureForbidden = true
            return null
        }

        return if (finished || !state.running) result(state, settings) else null
    }

    private fun result(state: SimState, settings: GreenSettings): SimResult {
        val dx = state.x
        val dy = state.y - settings.holeDistanceM
        val bridgeBoundaryContact = state.bridgeCount > 0 && state.cupContacts == 0
        return SimResult(
            holed = state.holed,
            finishX = state.x,
            finishY = state.y,
            distanceToCupM = hypot(dx, dy),
            elapsedSec = state.elapsed,
            lipOut = (state.lipOut || state.bridgeCount > 0) && !state.holed,
            cupContacts = state.cupContacts + if (bridgeBoundaryContact) 1 else 0,
            bridgeCount = state.bridgeCount,
            flagstickContacts = state.flagstickContacts
        )
    }
}
