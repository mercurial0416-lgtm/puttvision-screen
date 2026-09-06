package com.puttvision.screen

import org.json.JSONObject
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Optional transport from PuttVision's authoritative native pipeline into an embedded Unity
 * renderer. There is deliberately no compile-time Unity dependency.
 */
object UnityRendererBridge {
    const val SCHEMA_VERSION = 1
    const val RECEIVER_GAME_OBJECT = "PuttTelemetryReceiver"
    const val SHOT_METHOD = "OnTelemetryJson"
    const val FRAME_METHOD = "OnPhysicsFrameJson"
    const val SURFACE_METHOD = "OnSurfaceGridJson"
    const val RESET_METHOD = "OnRendererReset"

    @Volatile var enabled: Boolean = false
    private val reflectedMethod = AtomicReference<Method?>(null)
    private val runtimeProbeAttempted = AtomicBoolean(false)
    @Volatile private var senderOverride: ((String, String, String) -> Unit)? = null

    fun isUnityRuntimeAvailable(): Boolean = resolveUnitySendMessage() != null

    fun enableIfRuntimeAvailable(): Boolean {
        enabled = isUnityRuntimeAvailable()
        runtimeProbeAttempted.set(true)
        return enabled
    }

    fun publishShot(metrics: ShotMetrics, settings: GreenSettings, startX: Double = 0.0, startY: Double = 0.0): Boolean {
        if (!rendererActive()) return false
        return send(SHOT_METHOD, UnityRendererProtocol.shotJson(metrics, settings, startX, startY))
    }

    /**
     * Publishes the native terrain truth once per shot. The grid calls GreenTerrain.effectiveHeightAt
     * directly, therefore custom greens, built-in profiles and global slopes all render from the
     * exact same height source consumed by native physics.
     */
    fun publishSurfaceGrid(settings: GreenSettings, startX: Double = 0.0, startY: Double = 0.0): Boolean {
        if (!rendererActive()) return false
        return send(SURFACE_METHOD, UnityRendererProtocol.surfaceGridJson(settings, startX, startY))
    }

    fun publishPhysicsFrame(state: SimState?): Boolean {
        if (state == null || !rendererActive()) return false
        return send(FRAME_METHOD, UnityRendererProtocol.physicsFrameJson(state))
    }

    fun publishReset(): Boolean {
        if (!rendererActive()) return false
        return send(RESET_METHOD, "{}")
    }

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
        senderOverride?.let {
            it(RECEIVER_GAME_OBJECT, methodName, payload)
            return true
        }
        val method = resolveUnitySendMessage() ?: return false
        return runCatching { method.invoke(null, RECEIVER_GAME_OBJECT, methodName, payload) }.isSuccess
    }

    private fun resolveUnitySendMessage(): Method? {
        reflectedMethod.get()?.let { return it }
        val resolved = runCatching {
            Class.forName("com.unity3d.player.UnityPlayer").getMethod(
                "UnitySendMessage", String::class.java, String::class.java, String::class.java
            )
        }.getOrNull() ?: return null
        reflectedMethod.compareAndSet(null, resolved)
        return reflectedMethod.get() ?: resolved
    }
}

internal object UnityRendererProtocol {
    const val SURFACE_WIDTH = 33
    const val SURFACE_HEIGHT = 65

    fun shotJson(metrics: ShotMetrics, settings: GreenSettings, startX: Double, startY: Double): String {
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

    fun surfaceGridJson(settings: GreenSettings, startX: Double, startY: Double): String {
        val distance = max(2.0, settings.holeDistanceM)
        val halfWidth = max(max(2.5, distance * 0.55), abs(startX) + 1.5)
        val minX = -halfWidth
        val maxX = halfWidth
        val minY = min(-1.5, startY - 1.2)
        val maxY = max(distance + 2.0, startY + 2.0)
        val bytes = ByteBuffer.allocate(SURFACE_WIDTH * SURFACE_HEIGHT * 4).order(ByteOrder.LITTLE_ENDIAN)

        for (iy in 0 until SURFACE_HEIGHT) {
            val fy = iy.toDouble() / (SURFACE_HEIGHT - 1).toDouble()
            val y = minY + (maxY - minY) * fy
            for (ix in 0 until SURFACE_WIDTH) {
                val fx = ix.toDouble() / (SURFACE_WIDTH - 1).toDouble()
                val x = minX + (maxX - minX) * fx
                val height = GreenTerrain.effectiveHeightAt(settings, x, y)
                    .takeIf { it.isFinite() } ?: 0.0
                bytes.putFloat(height.toFloat())
            }
        }

        return JSONObject().apply {
            put("schemaVersion", UnityRendererBridge.SCHEMA_VERSION)
            put("width", SURFACE_WIDTH)
            put("height", SURFACE_HEIGHT)
            put("minXM", minX)
            put("maxXM", maxX)
            put("minYM", minY)
            put("maxYM", maxY)
            put("heightF32LeBase64", Base64.getEncoder().encodeToString(bytes.array()))
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
    private fun finiteOr(value: Double?, fallback: Double): Double = value?.takeIf { it.isFinite() } ?: fallback
}
