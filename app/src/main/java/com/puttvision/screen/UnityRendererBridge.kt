package com.puttvision.screen

import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Optional transport from PuttVision's authoritative native pipeline into an embedded Unity
 * renderer. This file intentionally has no compile-time Unity dependency, so the existing Android
 * app keeps building before a Unity `unityLibrary` export is checked in/generated.
 *
 * The native camera, shot measurement and V135/V136 rigid-ball physics remain authoritative.
 * Unity is a presentation consumer only.
 */
object UnityRendererBridge {
    const val SCHEMA_VERSION = 1
    const val RECEIVER_GAME_OBJECT = "PuttTelemetryReceiver"
    const val SHOT_METHOD = "OnTelemetryJson"
    const val FRAME_METHOD = "OnPhysicsFrameJson"
    const val RESET_METHOD = "OnRendererReset"

    /** May be forced off/on by diagnostics. Runtime probing happens only once per process. */
    @Volatile
    var enabled: Boolean = false

    private val reflectedMethod = AtomicReference<Method?>(null)
    private val runtimeProbeAttempted = AtomicBoolean(false)

    @Volatile
    private var senderOverride: ((String, String, String) -> Unit)? = null

    fun isUnityRuntimeAvailable(): Boolean = resolveUnitySendMessage() != null

    fun enableIfRuntimeAvailable(): Boolean {
        enabled = isUnityRuntimeAvailable()
        runtimeProbeAttempted.set(true)
        return enabled
    }

    fun publishShot(
        metrics: ShotMetrics,
        settings: GreenSettings,
        startX: Double = 0.0,
        startY: Double = 0.0,
    ): Boolean {
        if (!rendererActive()) return false
        return send(SHOT_METHOD, UnityRendererProtocol.shotJson(metrics, settings, startX, startY))
    }

    fun publishPhysicsFrame(state: SimState?): Boolean {
        if (state == null || !rendererActive()) return false
        return send(FRAME_METHOD, UnityRendererProtocol.physicsFrameJson(state))
    }

    fun publishReset(): Boolean {
        if (!rendererActive()) return false
        return send(RESET_METHOD, "{}")
    }

    /** Test seam; never install this from product code. */
    internal fun installSenderForTests(sender: ((String, String, String) -> Unit)?) {
        senderOverride = sender
        if (sender != null) enabled = true
    }

    private fun rendererActive(): Boolean {
        if (enabled || senderOverride != null) return true
        if (!runtimeProbeAttempted.compareAndSet(false, true)) return false
        enabled = isUnityRuntimeAvailable()
        return enabled
    }

    private fun send(methodName: String, payload: String): Boolean {
        senderOverride?.let { sender ->
            sender(RECEIVER_GAME_OBJECT, methodName, payload)
            return true
        }

        val method = resolveUnitySendMessage() ?: return false
        return runCatching {
            method.invoke(null, RECEIVER_GAME_OBJECT, methodName, payload)
        }.isSuccess
    }

    private fun resolveUnitySendMessage(): Method? {
        reflectedMethod.get()?.let { return it }
        val resolved = runCatching {
            Class.forName("com.unity3d.player.UnityPlayer").getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java,
            )
        }.getOrNull() ?: return null
        reflectedMethod.compareAndSet(null, resolved)
        return reflectedMethod.get() ?: resolved
    }
}

/** Pure JSON protocol functions kept separate so Android JVM tests do not need a Unity runtime. */
internal object UnityRendererProtocol {
    fun shotJson(
        metrics: ShotMetrics,
        settings: GreenSettings,
        startX: Double,
        startY: Double,
    ): String {
        var flags = 0
        if (metrics.faceAngleDeg != null) flags = flags or 1
        if (metrics.pathAngleDeg != null) flags = flags or 2
        if (metrics.impactOffsetMm != null) flags = flags or 4
        if (metrics.confidence != null) flags = flags or 8

        return JSONObject().apply {
            put("schemaVersion", UnityRendererBridge.SCHEMA_VERSION)
            put("shotId", metrics.measuredAtNs.toString())
            put("timestampNs", metrics.measuredAtNs)
            put("ballSpeedMps", finiteOrZero(metrics.ballSpeedMps))
            put("launchDirectionDeg", finiteOrZero(metrics.launchAngleDeg))
            put("faceAngleDeg", finiteOrZero(metrics.faceAngleDeg))
            put("pathAngleDeg", finiteOrZero(metrics.pathAngleDeg))
            put("impactOffsetMm", finiteOrZero(metrics.impactOffsetMm))
            put("confidence", finiteOrZero(metrics.confidence))
            put("validityFlags", flags)

            // Shot-static green context. Dynamic ball pose is delivered separately from SimState.
            put("startXM", finiteOrZero(startX))
            put("startYM", finiteOrZero(startY))
            put("holeDistanceM", finiteOrZero(settings.holeDistanceM))
            put("stimpMeters", finiteOrZero(settings.stimpMeters))
            put("sideSlopePct", finiteOrZero(settings.sideSlopePct))
            put("longSlopePct", finiteOrZero(settings.longSlopePct))
            put("terrainProfileId", settings.terrainProfileId)
            put("flagstickIn", settings.flagstickIn)
            put("grainDirectionDeg", finiteOrZero(settings.grainDirectionDeg))
            put("grainStrength01", finiteOrZero(settings.grainStrength01))
            put("moisture01", finiteOrZero(settings.moisture01))
            put("firmness01", finiteOrZero(settings.firmness01))
            put("trueness01", finiteOrZero(settings.trueness01))
        }.toString()
    }

    fun physicsFrameJson(state: SimState): String = JSONObject().apply {
        put("schemaVersion", UnityRendererBridge.SCHEMA_VERSION)
        put("elapsedSec", finiteOrZero(state.elapsed))
        put("xM", finiteOrZero(state.x))
        put("yM", finiteOrZero(state.y))
        put("centerZM", finiteOrZero(state.ballCenterZM))
        put("vxMps", finiteOrZero(state.vx))
        put("vyMps", finiteOrZero(state.vy))
        put("vzMps", finiteOrZero(state.vz))

        put("orientationW", finiteOr(state.orientationW, 1.0))
        put("orientationX", finiteOrZero(state.orientationX))
        put("orientationY", finiteOrZero(state.orientationY))
        put("orientationZ", finiteOrZero(state.orientationZ))

        put("surfaceNormalX", finiteOrZero(state.surfaceNormalX))
        put("surfaceNormalY", finiteOrZero(state.surfaceNormalY))
        put("surfaceNormalZ", finiteOr(state.surfaceNormalZ, 1.0))
        put("slipSpeedMps", finiteOrZero(state.v135SlipSpeedMps))
        put("airborne", state.v135Airborne)

        put("running", state.running)
        put("holed", state.holed)
        put("lipOut", state.lipOut)
        put("cupPhase", state.cupPhase.name)
        put("cupContacts", state.cupContacts)
        put("cupWallContacts", state.cupWallContacts)
        put("cupBottomContacts", state.cupBottomContacts)
        put("bridgeCount", state.bridgeCount)
        put("flagstickContacts", state.flagstickContacts)
    }.toString()

    private fun finiteOrZero(value: Double?): Double = finiteOr(value, 0.0)

    private fun finiteOr(value: Double?, fallback: Double): Double =
        value?.takeIf { it.isFinite() } ?: fallback
}
