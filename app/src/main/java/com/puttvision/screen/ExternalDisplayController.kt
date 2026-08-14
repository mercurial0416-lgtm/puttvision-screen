package com.puttvision.screen

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout

class GamePresentation(
    context: Context,
    display: Display,
    private val engine: GameEngine
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        root.addView(V18SimulatorFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(V27PaceLineOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        root.addView(V36OnlineMatchOverlay(context), FrameLayout.LayoutParams(-1, -1))
        root.addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}

class ExternalDisplayController(
    private val context: Context,
    private val engine: GameEngine,
    private val onChanged: (Boolean, String) -> Unit
) : DisplayManager.DisplayListener {
    private val dm = context.getSystemService(DisplayManager::class.java)
    private var presentation: GamePresentation? = null
    fun start() { dm.registerDisplayListener(this, null); refresh() }
    fun stop() {
        try { dm.unregisterDisplayListener(this) } catch (_: Throwable) { }
        presentation?.dismiss(); presentation = null
    }
    fun refresh() {
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val display = displays.firstOrNull()
        if (display == null) {
            presentation?.dismiss(); presentation = null
            onChanged(false, "외부 TV 미검출 · HDMI/DeX 연결 확인"); return
        }
        if (presentation?.display?.displayId == display.displayId) {
            onChanged(true, "TV 연결됨 · ${display.name} · V18 3D SIM"); return
        }
        presentation?.dismiss()
        presentation = GamePresentation(context, display, engine).also {
            try { it.show(); onChanged(true, "TV 연결됨 · ${display.name} · V18 3D SIM") }
            catch (e: Throwable) { presentation = null; onChanged(false, "TV 화면 열기 실패 · ${e.message}") }
        }
    }
    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()
}
