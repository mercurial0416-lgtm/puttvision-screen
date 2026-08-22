package com.puttvision.screen

/**
 * Thread boundary between the mutable physics integrator and presentation threads.
 *
 * GreenPhysics deliberately mutates SimState in-place for deterministic integration. Android's
 * GLSurfaceView/Filament renderer runs on a separate thread, so publishing that same mutable
 * instance through a volatile reference is not sufficient: inner writes can be observed
 * inconsistently.
 *
 * Every physics tick is therefore deep-copied before it becomes visible to TV/phone renderers.
 */
object V126PhysicsFrameBridge {
    fun snapshot(source: SimState?): SimState? {
        source ?: return null
        return SimState(
            x = source.x,
            y = source.y,
            vx = source.vx,
            vy = source.vy,
            running = source.running,
            holed = source.holed,
            elapsed = source.elapsed,
            trail = source.trail.toMutableList(),
            cupContacts = source.cupContacts,
            lipOut = source.lipOut,
            lastCupContactSec = source.lastCupContactSec,
            cupPhase = source.cupPhase,
            cupPhaseElapsedSec = source.cupPhaseElapsedSec,
            cupVerticalOffsetM = source.cupVerticalOffsetM,
            cupEntrySpeedMps = source.cupEntrySpeedMps,
            cupRimAngleRad = source.cupRimAngleRad,
            cupRimRadiusM = source.cupRimRadiusM,
            cupRimAngularVelocityRadS = source.cupRimAngularVelocityRadS,
            cupRimWillDrop = source.cupRimWillDrop,
            cupRimDurationSec = source.cupRimDurationSec,
            cupRimReleaseSpeedMps = source.cupRimReleaseSpeedMps,
            cupDropDurationSec = source.cupDropDurationSec,
            ballCenterZM = source.ballCenterZM,
            vz = source.vz,
            omegaXRadS = source.omegaXRadS,
            omegaYRadS = source.omegaYRadS,
            omegaZRadS = source.omegaZRadS,
            orientationW = source.orientationW,
            orientationX = source.orientationX,
            orientationY = source.orientationY,
            orientationZ = source.orientationZ,
            ballRotationRadians = source.ballRotationRadians,
            surfaceNormalX = source.surfaceNormalX,
            surfaceNormalY = source.surfaceNormalY,
            surfaceNormalZ = source.surfaceNormalZ,
            v135SlipSpeedMps = source.v135SlipSpeedMps,
            v135Airborne = source.v135Airborne,
            v135Initialized = source.v135Initialized,
            v135CaptureForbidden = source.v135CaptureForbidden,
            cupWallContacts = source.cupWallContacts,
            cupBottomContacts = source.cupBottomContacts,
            bridgeCount = source.bridgeCount,
            flagstickContacts = source.flagstickContacts
        )
    }
}
