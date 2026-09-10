package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos

data class GreenSettings(
    var stimpMeters: Double = 2.8,
    var holeDistanceM: Double = 5.0,
    var sideSlopePct: Double = 0.0,
    var longSlopePct: Double = 0.0,
    var terrainProfileId: Int = -1,
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

class GreenPhysics {
    fun launch(
        metrics: ShotMetrics,
        settings: GreenSettings,
        startX: Double = 0.0,
        startY: Double = 0.0
    ): SimState {
        if (
            !metrics.ballSpeedMps.isFinite() || !metrics.launchAngleDeg.isFinite() ||
            !startX.isFinite() || !startY.isFinite()
        ) {
            val safeX = startX.takeIf { it.isFinite() } ?: 0.0
            val safeY = startY.takeIf { it.isFinite() } ?: 0.0
            return SimState(
                x = safeX,
                y = safeY,
                running = false,
                trail = mutableListOf(safeX to safeY)
            )
        }

        val a = Math.toRadians(metrics.launchAngleDeg)
        val speed = metrics.ballSpeedMps.coerceIn(0.05, 5.0)
        UnityRendererBridge.publishShot(metrics, settings, startX, startY)
        UnityRendererBridge.publishSurfaceGrid(settings, startX, startY)
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
        if (!dtRaw.isFinite() || dtRaw <= 0.0) return null

        if (cupEnabled && !settings.flagstickIn && state.v135CaptureForbidden && state.v135Airborne) {
            V135CupEscapeModel.stepEscape(state, settings, dtRaw)
            return if (!state.running) result(state, settings) else null
        }

        val beforeX = state.x
        val beforeY = state.y
        val beforeZ = state.ballCenterZM
        val physicalSettings = V136PhysicalRealism.effectiveSettings(settings, state)
        val finished = V135RigidBallPhysics.step(state, physicalSettings, dtRaw, cupEnabled)
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
