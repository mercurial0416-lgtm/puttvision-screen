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
    private val onChanged: (Boolean, String) -> Unit
) : DisplayManager.DisplayListener {
    private val dm = context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: GamePresentation? = null
    private var unityDisplayId: Int? = null
    private var unityFailedForDisplayId: Int? = null
    private var godotDisplayId: Int? = null
    private var started = false

    private val snapshotPump = object : Runnable {
        override fun run() {
            if (!started) return
            // Godot remains warm as a rollback renderer. Unity's dynamic 6DOF frames are published
            // directly at the V126 physics snapshot boundary rather than duplicated by this pump.
            V143GodotRenderBridge.publish(engine)
            handler.postDelayed(this, 16L)
        }
    }

    fun start() {
        if (started) return
        started = true
        V143GodotRenderBridge.publish(engine)
        handler.post(snapshotPump)
        dm.registerDisplayListener(this, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        try { dm.unregisterDisplayListener(this) } catch (_: Throwable) { }
        stopUnity()
        stopGodot()
        presentation?.dismiss()
        presentation = null
        unityFailedForDisplayId = null
    }

    fun refresh() {
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val display = displays.firstOrNull()
        if (display == null) {
            stopUnity()
            stopGodot()
            presentation?.dismiss()
            presentation = null
            unityFailedForDisplayId = null
            onChanged(false, "외부 TV 미검출 · HDMI/DeX 연결 확인")
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
                onChanged(true, "TV 연결됨 · ${display.name} · $state")
                return
            }

            stopGodot()
            presentation?.dismiss()
            presentation = null
            launchUnity(display)
            return
        }

        if (godotDisplayId == display.displayId) {
            val state = if (V143GodotRuntime.setupComplete) "GODOT READY" else "GODOT STARTING"
            onChanged(true, "TV 연결됨 · ${display.name} · $state")
            return
        }

        stopUnity()
        presentation?.dismiss()
        presentation = null
        launchGodot(display)
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
        onChanged(true, "TV 연결됨 · ${display.name} · PUTTVISION UNITY")

        // ActivityManager launch success is not enough; the scene calls UnityTvRuntime.onUnityReady
        // after it has actually loaded. If that callback never arrives, roll back automatically.
        handler.postDelayed({
            if (!started || unityDisplayId != display.displayId) return@postDelayed
            if (!UnityTvRuntime.isReadyOn(display.displayId)) {
                val reason = UnityTvRuntime.lastFailure ?: "Unity 초기화 타임아웃"
                unityFailedForDisplayId = display.displayId
                stopUnity()
                launchGodot(display, reason)
            } else {
                onChanged(true, "TV 연결됨 · ${display.name} · UNITY READY")
            }
        }, 9000L)
    }

    private fun launchGodot(display: Display, unityReason: String? = null) {
        V143GodotRuntime.setupComplete = false
        V143GodotRuntime.lastFailure = null
        V143GodotRenderBridge.publish(engine)
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
            onChanged(true, "TV 연결됨 · ${display.name} · ${prefix}GODOT")

            // A launch can succeed at ActivityManager level but fail during native engine setup.
            handler.postDelayed({
                if (!started || godotDisplayId != display.displayId) return@postDelayed
                if (!V143GodotTvActivity.isActiveOn(display.displayId) || !V143GodotRuntime.setupComplete) {
                    showFallback(display, "Godot 초기화 실패")
                }
            }, 7000L)
        } catch (t: Throwable) {
            V143GodotRuntime.lastFailure = t.message ?: t.javaClass.simpleName
            showFallback(display, "Godot 실행 실패")
        }
    }

    private fun stopUnity() {
        UnityTvRuntime.finishCurrent()
        unityDisplayId = null
    }

    private fun stopGodot() {
        V143GodotTvActivity.finishCurrent()
        godotDisplayId = null
    }

    private fun showFallback(display: Display, reason: String) {
        stopUnity()
        stopGodot()
        presentation?.dismiss()
        presentation = GamePresentation(context, display, engine).also {
            try {
                it.show()
                onChanged(true, "TV 연결됨 · ${display.name} · FILAMENT FALLBACK · $reason")
            } catch (e: Throwable) {
                presentation = null
                onChanged(false, "TV 화면 열기 실패 · ${e.message}")
            }
        }
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()
}
