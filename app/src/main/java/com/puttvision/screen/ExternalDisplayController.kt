package com.puttvision.screen

import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout

/** Filament presentation retained as the final safe fallback if embedded engines cannot launch. */
class GamePresentation(
    context: Context,
    display: Display,
    private val engine: GameEngine
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        root.addView(V57ProductTvSurface.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}

class ExternalDisplayController(
    private val context: Context,
    private val engine: GameEngine,
    onChanged: (Boolean, String) -> Unit
) : DisplayManager.DisplayListener {
    companion object {
        private const val SNAPSHOT_ACTIVE_INTERVAL_MS = 16L
        private const val SNAPSHOT_IDLE_INTERVAL_MS = 1000L
    }

    private val dm = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val statusReporter = DistinctDisplayStatusReporter(onChanged)
    private var presentation: GamePresentation? = null
    private var unityDisplayId: Int? = null
    private var unityFailedForDisplayId: Int? = null
    private var godotDisplayId: Int? = null
    private var godotFailedForDisplayId: Int? = null
    private var unityLaunchGeneration = 0
    private var godotLaunchGeneration = 0
    private var started = false
    private var hasPresentationDisplay = false

    private val snapshotPump = object : Runnable {
        override fun run() {
            if (!started) return
            // Godot only needs warm rollback snapshots while an external presentation display is
            // actually attached. Avoid bridge snapshot work every 16 ms during normal phone-only use.
            if (hasPresentationDisplay) {
                V143GodotRenderBridge.publish(engine)
            }
            handler.postDelayed(
                this,
                if (hasPresentationDisplay) SNAPSHOT_ACTIVE_INTERVAL_MS else SNAPSHOT_IDLE_INTERVAL_MS
            )
        }
    }

    fun start() {
        if (started) return
        started = true
        statusReporter.reset()
        V143GodotRenderBridge.publish(engine)
        handler.post(snapshotPump)
        dm.registerDisplayListener(this, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        hasPresentationDisplay = false
        handler.removeCallbacksAndMessages(null)
        try { dm.unregisterDisplayListener(this) } catch (_: Throwable) { }
        stopUnity()
        stopGodot()
        dismissPresentationSafely()
        unityFailedForDisplayId = null
        godotFailedForDisplayId = null
        statusReporter.reset()
    }

    fun refresh() {
        // DisplayManager callbacks already queued on the main looper can still arrive after stop().
        // Never let a stale add/remove/change event relaunch Unity/Godot after the controller has
        // relinquished ownership of the external display lifecycle.
        if (!started) return

        // During HDMI/DeX handoff Android can briefly keep a removed presentation Display in this
        // category either invalid or valid-but-STATE_OFF. Never launch Unity/Godot on that stale
        // surface: a failed launch would latch the display id and unnecessarily force the healthy
        // replacement session down to a fallback renderer until another reconnect cycle.
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.isValid && it.state != Display.STATE_OFF }
        // Android does not guarantee a stable ordering for presentation displays. Keep the renderer
        // on the display it already owns when another HDMI/DeX presentation display is added or
        // changes, instead of tearing down a healthy TV session just because firstOrNull() moved.
        val activeDisplayId = unityDisplayId ?: godotDisplayId ?: presentation?.display?.displayId
        val display = activeDisplayId?.let { id -> displays.firstOrNull { it.displayId == id } }
            ?: displays.firstOrNull()
        val hadPresentationDisplay = hasPresentationDisplay
        hasPresentationDisplay = display != null
        if (!hadPresentationDisplay && hasPresentationDisplay) {
            // The phone-only pump deliberately sleeps at a low cadence. Wake it immediately when
            // HDMI/DeX appears so the TV renderer never waits for that idle interval for snapshots.
            handler.removeCallbacks(snapshotPump)
            handler.post(snapshotPump)
        }
        if (display == null) {
            stopUnity()
            stopGodot()
            dismissPresentationSafely()
            unityFailedForDisplayId = null
            godotFailedForDisplayId = null
            statusReporter.report(false, "외부 TV 미검출 · HDMI/DeX 연결 확인")
            return
        }

        val unityEligible = UnityTvRuntime.isAvailable() && unityFailedForDisplayId != display.displayId
        if (unityEligible) {
            if (unityDisplayId == display.displayId) {
                val state = if (UnityTvRuntime.isReadyOn(display.displayId)) {
                    "UNITY READY"
                } else {
                    "UNITY STARTING"
                }
                statusReporter.report(true, "TV 연결됨 · ${display.name} · $state")
                return
            }

            stopGodot()
            dismissPresentationSafely()
            launchUnity(display)
            return
        }

        val godotEligible = godotFailedForDisplayId != display.displayId
        if (godotEligible) {
            if (godotDisplayId == display.displayId) {
                val state = if (V143GodotRuntime.setupComplete) "GODOT READY" else "GODOT STARTING"
                statusReporter.report(true, "TV 연결됨 · ${display.name} · $state")
                return
            }

            stopUnity()
            dismissPresentationSafely()
            launchGodot(display)
            return
        }

        // Samsung/DeX can emit repeated display-changed callbacks while the same physical TV stays
        // attached. If both embedded renderers already failed for this display and the Filament
        // fallback is healthy, keep that Presentation instead of dismissing/recreating it on every
        // callback. Reconnect still clears the failure latches via the no-display path above.
        if (presentation?.display?.displayId == display.displayId) {
            statusReporter.report(true, "TV 연결됨 · ${display.name} · FILAMENT FALLBACK · embedded renderer unavailable")
            return
        }

        showFallback(display, "Godot 이전 초기화 실패")
    }

    private fun launchUnity(display: Display) {
        stopUnity()
        UnityRendererBridge.enabled = false

        if (!UnityTvRuntime.launch(context, display)) {
            unityFailedForDisplayId = display.displayId
            launchGodot(display)
            return
        }

        unityDisplayId = display.displayId
        val launchGeneration = ++unityLaunchGeneration
        statusReporter.report(true, "TV 연결됨 · ${display.name} · PUTTVISION UNITY")

        // ActivityManager launch success is not enough; the scene calls UnityTvRuntime.onUnityReady
        // after it has actually loaded. If that callback never arrives, roll back automatically.
        // A generation guard also prevents an old timeout from killing a freshly reconnected session
        // when Android reuses the same displayId after HDMI/DeX disconnect + reconnect.
        handler.postDelayed({
            if (!started || unityLaunchGeneration != launchGeneration || unityDisplayId != display.displayId) {
                return@postDelayed
            }
            if (!UnityTvRuntime.isReadyOn(display.displayId)) {
                val reason = UnityTvRuntime.lastFailure ?: "Unity 초기화 타임아웃"
                unityFailedForDisplayId = display.displayId
                stopUnity()
                launchGodot(display, reason)
            } else {
                statusReporter.report(true, "TV 연결됨 · ${display.name} · UNITY READY")
            }
        }, 9000L)
    }

    private fun launchGodot(display: Display, unityReason: String? = null) {
        // A display handoff can reach here while an older Godot TV Activity is still alive on a
        // removed HDMI/DeX display. Tear it down before launching the replacement so we never keep
        // duplicate native renderers or let the old Activity retain ownership of the render bridge.
        stopGodot()
        V143GodotRuntime.setupComplete = false
        V143GodotRuntime.lastFailure = null
        V143GodotRenderBridge.publish(engine)
        val launchGeneration = ++godotLaunchGeneration
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            val intent = Intent(context, V143GodotTvActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("pv_display_id", display.displayId)
            }
            context.startActivity(intent, options.toBundle())
            godotDisplayId = display.displayId
            val prefix = unityReason?.let { "UNITY FALLBACK · $it · " } ?: ""
            statusReporter.report(true, "TV 연결됨 · ${display.name} · ${prefix}GODOT")

            // A launch can succeed at ActivityManager level but fail during native engine setup.
            // Ignore stale watchdogs from an older launch even if Android reused the displayId.
            handler.postDelayed({
                if (!started || godotLaunchGeneration != launchGeneration || godotDisplayId != display.displayId) {
                    return@postDelayed
                }
                if (!V143GodotTvActivity.isActiveOn(display.displayId) || !V143GodotRuntime.setupComplete) {
                    godotFailedForDisplayId = display.displayId
                    showFallback(display, "Godot 초기화 실패")
                }
            }, 7000L)
        } catch (t: Throwable) {
            V143GodotRuntime.lastFailure = t.message ?: t.javaClass.simpleName
            godotFailedForDisplayId = display.displayId
            showFallback(display, "Godot 실행 실패")
        }
    }

    private fun stopUnity() {
        ++unityLaunchGeneration
        UnityTvRuntime.finishCurrent()
        unityDisplayId = null
    }

    private fun stopGodot() {
        ++godotLaunchGeneration
        V143GodotTvActivity.finishCurrent()
        godotDisplayId = null
    }

    private fun dismissPresentationSafely() {
        val current = presentation ?: return
        // HDMI/DeX removal can invalidate the Presentation window between DisplayManager callbacks.
        // Clear ownership before dismissing so a WindowManager failure cannot leave a stale fallback
        // that refresh() later mistakes for a healthy renderer on a recycled display id.
        presentation = null
        try { current.dismiss() } catch (_: Throwable) { }
    }

    private fun showFallback(display: Display, reason: String) {
        stopUnity()
        stopGodot()
        dismissPresentationSafely()

        val candidate = GamePresentation(context, display, engine)
        try {
            candidate.show()
            presentation = candidate
            statusReporter.report(true, "TV 연결됨 · ${display.name} · FILAMENT FALLBACK · $reason")
        } catch (e: Throwable) {
            // Keep the retained fallback null when show() fails. Assigning through `also` here can
            // accidentally restore the failed object after the catch and make refresh() treat a
            // presentation that was never shown as healthy on subsequent display callbacks.
            try { candidate.dismiss() } catch (_: Throwable) { }
            statusReporter.report(false, "TV 화면 열기 실패 · ${e.message}")
        }
    }

    override fun onDisplayAdded(displayId: Int) = refresh()

    override fun onDisplayRemoved(displayId: Int) {
        // Samsung/DeX can queue remove+add before this callback is handled and reuse the same
        // displayId. Reset state from the explicit removal event first; otherwise refresh() can see
        // the replacement display with the old id and incorrectly keep a stale renderer/fallback.
        if (unityDisplayId == displayId) stopUnity()
        if (godotDisplayId == displayId) stopGodot()
        if (presentation?.display?.displayId == displayId) dismissPresentationSafely()
        if (unityFailedForDisplayId == displayId) unityFailedForDisplayId = null
        if (godotFailedForDisplayId == displayId) godotFailedForDisplayId = null
        refresh()
    }

    override fun onDisplayChanged(displayId: Int) = refresh()
}
