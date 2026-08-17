package com.puttvision.screen

/**
 * Thread boundary between the mutable physics integrator and presentation threads.
 *
 * GreenPhysics deliberately mutates SimState in-place for deterministic integration. Android's
 * GLSurfaceView renders on a separate GL thread, so publishing that same mutable instance through a
 * volatile reference is not sufficient: inner x/y/vx/vy writes can be observed inconsistently.
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
            lastCupContactSec = source.lastCupContactSec
        )
    }
}
