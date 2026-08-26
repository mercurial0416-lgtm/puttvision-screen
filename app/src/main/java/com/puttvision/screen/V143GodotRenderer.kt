package com.puttvision.screen

import android.os.Bundle
import android.view.WindowManager
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

/**
 * V143 bridge between PuttVision's native Android measurement/physics stack and the embedded
 * Godot TV renderer. Rendering is intentionally a consumer of GameEngine snapshots only:
 * V135-V137 remain authoritative for every ball/cup outcome.
 *
 * V166 exposes a cached spatial terrain field and the existing GreenReadAdvisor inverse-physics
 * result. V171 additionally exposes the authoritative Green/Fringe/Rough zone so presentation and
 * replay can report the same surface resistance the ball actually receives.
 */
object V143GodotRenderBridge {
    private val snapshot = AtomicReference(defaultSnapshot())
    private val terrainField = AtomicReference("{}")
    @Volatile private var lastTerrainKey = ""

    fun publish(engine: GameEngine) {
        val settings = engine.settings.copy()
        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val bx = state?.x?.takeIf(Double::isFinite) ?: start.first
        val by = state?.y?.takeIf(Double::isFinite) ?: start.second
        val terrainZ = runCatching {
            GreenTerrain.effectiveHeightAt(settings, bx, by) + V135RigidBallPhysics.BALL_RADIUS_M
        }.getOrDefault(V135RigidBallPhysics.BALL_RADIUS_M)
        val bz = state?.ballCenterZM?.takeIf(Double::isFinite) ?: terrainZ
        val cupZ = runCatching {
            GreenTerrain.effectiveHeightAt(settings, 0.0, settings.holeDistanceM) + .020
        }.getOrDefault(.020)
        val terrainKey = ensureTerrainField(settings)
        val surfaceZone = V170SurfaceZones.zoneAt(settings, bx, by).name

        // The advisor is already the app's exact inverse solver and delegates to GreenPhysics,
        // which in turn owns the V135-V137 path. Never block the renderer waiting for it.
        val read = GreenReadRuntime.peekOrSchedule(settings)
        val result = engine.lastResult
        val json = JSONObject()
            .put("version", 171)
            .put("holeDistance", settings.holeDistanceM)
            .put("stimp", settings.stimpMeters)
            .put("sideSlope", settings.sideSlopePct)
            .put("longSlope", settings.longSlopePct)
            .put("terrainProfile", settings.terrainProfileId)
            .put("terrainKey", terrainKey)
            .put("surfaceZone", surfaceZone)
            .put("flagstickPhysical", settings.flagstickIn)
            .put("startX", start.first)
            .put("startY", start.second)
            .put("ballX", bx)
            .put("ballY", by)
            .put("ballZ", bz)
            .put("vx", state?.vx ?: 0.0)
            .put("vy", state?.vy ?: 0.0)
            .put("vz", state?.vz ?: 0.0)
            .put("speed", if (state == null) 0.0 else hypot(state.vx, state.vy))
            .put("running", state?.running == true)
            .put("holed", state?.holed == true || result?.holed == true)
            .put("lipOut", state?.lipOut == true || result?.lipOut == true)
            .put("cupPhase", state?.cupPhase?.name ?: V134CupPhase.NONE.name)
            .put("cupZ", cupZ)
            .put("qw", state?.orientationW ?: 1.0)
            .put("qx", state?.orientationX ?: 0.0)
            .put("qy", state?.orientationY ?: 0.0)
            .put("qz", state?.orientationZ ?: 0.0)
            .put("elapsed", state?.elapsed ?: 0.0)
            .put("distanceToCup", result?.distanceToCupM ?: hypot(bx, settings.holeDistanceM - by))
            .put("actualTrail", trailJson(state?.trail ?: emptyList()))
            .put("readPending", read == null && GreenReadRuntime.isPending(settings))

        if (read != null) {
            json.put("recommendedAimOffsetM", read.aimOffsetCm / 100.0)
                .put("recommendedBallSpeedMps", read.recommendedBallSpeedMps)
                .put("recommendedLaunchAngleDeg", read.recommendedLaunchAngleDeg)
                .put("solverMissCm", read.solverMissCm)
                .put("solverReliable", read.solverReliable)
                .put("predictedTrail", trailJson(read.predictedTrail))

            predictedXAtY(read.predictedTrail, by)?.let { predictedX ->
                json.put("currentLineDeltaCm", (bx - predictedX) * 100.0)
            }
            if (result != null) {
                predictedXAtY(read.predictedTrail, result.finishY)?.let { predictedX ->
                    json.put("readLineDeltaCm", (result.finishX - predictedX) * 100.0)
                }
                json.put("paceDeltaCm", (result.finishY - settings.holeDistanceM) * 100.0)
            }
        } else {
            json.put("predictedTrail", JSONArray())
        }

        snapshot.set(json.toString())
    }

    fun snapshotJson(): String = snapshot.get()
    fun terrainFieldJson(): String = terrainField.get()

    private fun ensureTerrainField(settings: GreenSettings): String {
        val key = terrainKey(settings)
        if (key == lastTerrainKey) return key
        synchronized(this) {
            if (key != lastTerrainKey) {
                terrainField.set(buildTerrainField(settings, key))
                lastTerrainKey = key
            }
        }
        return key
    }

    private fun terrainKey(settings: GreenSettings): String {
        val customHash = runCatching { V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile) }.getOrDefault(0)
        return listOf(
            settings.terrainProfileId,
            q(settings.holeDistanceM, 1000.0),
            q(settings.sideSlopePct, 1000.0),
            q(settings.longSlopePct, 1000.0),
            customHash
        ).joinToString(":")
    }

    /**
     * Physical field shared by ball physics and TV rendering. 25x49 samples are dense enough to
     * retain bowls/crowns/ridges while remaining a one-off, compact payload on Android/TV hardware.
     * samples is flat [heightM, sidePct, longPct, ...] in row-major Y/X order.
     */
    private fun buildTerrainField(settings: GreenSettings, key: String): String {
        val cols = 25
        val rows = 49
        val xMin = -8.6
        val xMax = 8.6
        val yMin = -3.0
        val yMax = 31.5
        val samples = JSONArray()
        for (row in 0 until rows) {
            val fy = if (rows == 1) 0.0 else row.toDouble() / (rows - 1).toDouble()
            val y = yMin + (yMax - yMin) * fy
            for (col in 0 until cols) {
                val fx = if (cols == 1) 0.0 else col.toDouble() / (cols - 1).toDouble()
                val x = xMin + (xMax - xMin) * fx
                val h = GreenTerrain.effectiveHeightAt(settings, x, y)
                val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
                samples.put(q(h, 100000.0))
                samples.put(q(slope.sidePct, 1000.0))
                samples.put(q(slope.longPct, 1000.0))
            }
        }
        return JSONObject()
            .put("key", key)
            .put("cols", cols)
            .put("rows", rows)
            .put("xMin", xMin)
            .put("xMax", xMax)
            .put("yMin", yMin)
            .put("yMax", yMax)
            .put("samples", samples)
            .toString()
    }

    private fun trailJson(points: List<Pair<Double, Double>>, maxPoints: Int = 72): JSONArray {
        val out = JSONArray()
        if (points.isEmpty()) return out
        val count = minOf(points.size, maxPoints)
        for (i in 0 until count) {
            val index = if (count <= 1) 0 else round(i.toDouble() * (points.size - 1) / (count - 1)).toInt()
            val point = points[index]
            out.put(JSONArray().put(q(point.first, 10000.0)).put(q(point.second, 10000.0)))
        }
        return out
    }

    private fun predictedXAtY(points: List<Pair<Double, Double>>, y: Double): Double? {
        if (points.isEmpty()) return null
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val dy = b.second - a.second
            if ((y - a.second) * (y - b.second) <= 0.0 && abs(dy) > 1e-9) {
                val t = ((y - a.second) / dy).coerceIn(0.0, 1.0)
                return a.first + (b.first - a.first) * t
            }
        }
        return points.minByOrNull { abs(it.second - y) }?.first
    }

    private fun q(value: Double, scale: Double): Double = round(value * scale) / scale

    private fun defaultSnapshot(): String = JSONObject()
        .put("version", 171)
        .put("holeDistance", 5.0)
        .put("stimp", 2.8)
        .put("sideSlope", 0.0)
        .put("longSlope", 0.0)
        .put("terrainProfile", -1)
        .put("terrainKey", "default")
        .put("surfaceZone", V170SurfaceZone.GREEN.name)
        .put("flagstickPhysical", false)
        .put("startX", 0.0)
        .put("startY", 0.0)
        .put("ballX", 0.0)
        .put("ballY", 0.0)
        .put("ballZ", V135RigidBallPhysics.BALL_RADIUS_M)
        .put("vx", 0.0)
        .put("vy", 0.0)
        .put("vz", 0.0)
        .put("speed", 0.0)
        .put("running", false)
        .put("holed", false)
        .put("lipOut", false)
        .put("cupPhase", V134CupPhase.NONE.name)
        .put("cupZ", .020)
        .put("qw", 1.0)
        .put("qx", 0.0)
        .put("qy", 0.0)
        .put("qz", 0.0)
        .put("elapsed", 0.0)
        .put("distanceToCup", 5.0)
        .put("actualTrail", JSONArray())
        .put("predictedTrail", JSONArray())
        .put("readPending", false)
        .toString()
}

object V143GodotRuntime {
    @Volatile var setupComplete = false
    @Volatile var lastFailure: String? = null
    @Volatile var displayId: Int = -1
}

class V143GodotPlugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName(): String = "PuttVisionBridge"

    @UsedByGodot
    fun snapshotJson(): String = V143GodotRenderBridge.snapshotJson()

    @UsedByGodot
    fun terrainFieldJson(): String = V143GodotRenderBridge.terrainFieldJson()

    @UsedByGodot
    fun rendererVersion(): String = "V171-Godot-4.7.1"
}

/** Dedicated full-screen Godot host launched directly onto the HDMI/DeX presentation display. */
class V143GodotTvActivity : GodotActivity() {
    companion object {
        @Volatile private var current = WeakReference<V143GodotTvActivity>(null)

        fun finishCurrent() {
            current.get()?.let { activity ->
                activity.runOnUiThread { runCatching { activity.finish() } }
            }
        }

        fun isActiveOn(displayId: Int): Boolean {
            val activity = current.get() ?: return false
            return !activity.isFinishing && activity.display?.displayId == displayId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        current = WeakReference(this)
        V143GodotRuntime.displayId = display?.displayId ?: -1
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> =
        setOf(V143GodotPlugin(godot))

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        V143GodotRuntime.setupComplete = true
        V143GodotRuntime.lastFailure = null
    }

    override fun onDestroy() {
        V143GodotRuntime.setupComplete = false
        if (current.get() === this) current = WeakReference(null)
        super.onDestroy()
    }
}
