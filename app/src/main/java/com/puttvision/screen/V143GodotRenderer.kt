package com.puttvision.screen

import android.os.Bundle
import android.view.WindowManager
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot

/**
 * V143 bridge between PuttVision's native Android measurement/physics stack and the embedded
 * Godot TV renderer.  Rendering is intentionally a consumer of GameEngine snapshots only:
 * V135-V137 remain authoritative for every ball/cup outcome.
 */
object V143GodotRenderBridge {
    private val snapshot = AtomicReference(defaultSnapshot())

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

        val result = engine.lastResult
        val json = JSONObject()
            .put("version", 143)
            .put("holeDistance", settings.holeDistanceM)
            .put("stimp", settings.stimpMeters)
            .put("sideSlope", settings.sideSlopePct)
            .put("longSlope", settings.longSlopePct)
            .put("terrainProfile", settings.terrainProfileId)
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
            .toString()
        snapshot.set(json)
    }

    fun snapshotJson(): String = snapshot.get()

    private fun defaultSnapshot(): String = JSONObject()
        .put("version", 143)
        .put("holeDistance", 5.0)
        .put("stimp", 2.8)
        .put("sideSlope", 0.0)
        .put("longSlope", 0.0)
        .put("terrainProfile", -1)
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
    fun rendererVersion(): String = "V143-Godot-4.7.1"
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
