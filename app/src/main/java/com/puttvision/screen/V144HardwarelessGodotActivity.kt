package com.puttvision.screen

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import kotlin.math.max

/**
 * V144 no-hardware simulator.
 *
 * This is deliberately a real V143 Godot host, not an Android imitation of the TV. The only
 * synthetic component is shot input. Every shot enters GameEngine.launch(), advances through the
 * production ball/cup physics, and is published through the exact same V143GodotRenderBridge
 * consumed by the HDMI/DeX renderer.
 *
 * The manifest puts this Activity in :hardwareless so its Godot singleton cannot fight the main
 * process / external-display Godot host for ownership of the native renderer.
 */
class V144HardwarelessGodotActivity : GodotActivity() {
    private val engine = GameEngine()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var panel: ScrollView

    private val distances = doubleArrayOf(3.0, 5.0, 7.0, 10.0)
    private var distanceIndex = 1
    private var profile = 8
    private var lastTickNs = 0L
    private var lastRunning = false

    private enum class Scenario(
        val label: String,
        val ballSpeed: Double,
        val directionDeg: Double,
        val headSpeed: Double
    ) {
        CENTER("CENTER", 1.45, 0.0, 0.95),
        PUSH("PUSH +2°", 1.45, 2.0, 0.95),
        PULL("PULL -2°", 1.45, -2.0, 0.95),
        SHORT("SHORT", 0.92, 0.0, 0.62),
        LONG("LONG", 2.05, 0.0, 1.30),
        BREAK("BREAK", 1.48, 0.0, 0.96)
    }

    private val pump = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dt = if (lastTickNs == 0L) 1.0 / 60.0 else
                ((now - lastTickNs) / 1_000_000_000.0).coerceIn(1.0 / 240.0, 0.05)
            lastTickNs = now

            val runningBefore = engine.state?.running == true
            if (runningBefore) engine.step(dt)
            V143GodotRenderBridge.publish(engine)

            val runningNow = engine.state?.running == true
            if (lastRunning && !runningNow) renderResultStatus()
            lastRunning = runningNow
            handler.postDelayed(this, 16L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        configureLabEngine()
        V143GodotRenderBridge.publish(engine)
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        addContentView(buildLabOverlay(), FrameLayout.LayoutParams(-1, -1))
        lastTickNs = System.nanoTime()
        handler.post(pump)
    }

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> =
        setOf(V143GodotPlugin(godot))

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        runOnUiThread {
            updateStatus("V143 GODOT READY · ${distanceLabel()} · GREEN ${greenLabel()}")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun configureLabEngine() {
        engine.gameModes.setMode(PracticeMode.PRACTICE)
        engine.settings.stimpMeters = 2.8
        engine.settings.holeDistanceM = distances[distanceIndex]
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = profile
        engine.resetSimulation()
    }

    private fun buildLabOverlay(): FrameLayout {
        val overlay = FrameLayout(this)

        panel = ScrollView(this).apply {
            setBackgroundColor(Color.argb(218, 5, 10, 13))
            isFillViewport = true
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        controls.addView(label("NO HARDWARE · REAL V143", 12f, Color.rgb(105, 239, 176), true))
        controls.addView(label("합성 샷만 사용 · 렌더/그린/볼/컵 물리는 실제 TV와 동일", 9f, Color.LTGRAY, false).apply {
            setPadding(0, dp(3), 0, dp(8))
        })
        status = label("GODOT STARTING · 5m · GREEN 08", 10f, Color.WHITE, true).apply {
            setBackgroundColor(Color.argb(210, 21, 29, 34))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        controls.addView(status, LinearLayout.LayoutParams(-1, -2))

        controls.addView(label("SHOT", 9f, Color.GRAY, true).apply { setPadding(0, dp(10), 0, dp(3)) })
        Scenario.entries.forEach { scenario ->
            controls.addView(labButton(scenario.label) { inject(scenario) }, buttonLp())
        }

        val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        targetRow.addView(labButton("거리") { cycleDistance() }, LinearLayout.LayoutParams(0, dp(40), 1f))
        targetRow.addView(labButton("그린 +1") { cycleGreen() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            marginStart = dp(5)
        })
        controls.addView(targetRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        controls.addView(labButton("RESET") {
            engine.resetSimulation()
            V143GodotRenderBridge.publish(engine)
            updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}")
        }, buttonLp())
        controls.addView(labButton("닫기") { finish() }, buttonLp())
        panel.addView(controls, FrameLayout.LayoutParams(-1, -2))

        overlay.addView(panel, FrameLayout.LayoutParams(dp(250), -1, Gravity.END).apply {
            topMargin = dp(48)
            bottomMargin = dp(8)
            marginEnd = dp(8)
        })

        overlay.addView(labButton("LAB") {
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, FrameLayout.LayoutParams(dp(62), dp(36), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8)
            marginEnd = dp(52)
        })
        overlay.addView(labButton("×") { finish() }, FrameLayout.LayoutParams(dp(40), dp(36), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8)
            marginEnd = dp(8)
        })
        return overlay
    }

    private fun inject(scenario: Scenario) {
        if (engine.state?.running == true) {
            updateStatus("ROLLING · 끝난 뒤 다음 샷")
            return
        }
        if (scenario == Scenario.BREAK && profile == 0) profile = 8
        applyTarget(reset = true)

        val metrics = ShotMetrics(
            ballSpeedMps = scenario.ballSpeed,
            launchAngleDeg = scenario.directionDeg,
            headSpeedMps = scenario.headSpeed,
            faceAngleDeg = scenario.directionDeg * 0.65,
            pathAngleDeg = scenario.directionDeg * 0.35,
            faceToPathDeg = scenario.directionDeg * 0.30,
            smash = scenario.ballSpeed / max(0.10, scenario.headSpeed),
            impactOffsetMm = when (scenario) {
                Scenario.PUSH -> 2.0
                Scenario.PULL -> -2.0
                else -> 0.0
            },
            measuredAtNs = SystemClock.elapsedRealtimeNanos(),
            confidence = .98,
            uncertainty = MeasurementUncertaintyEstimator.synthetic()
        )
        engine.launch(metrics)
        V143GodotRenderBridge.publish(engine)
        lastRunning = engine.state?.running == true
        updateStatus("${scenario.label} · REAL PHYSICS · ${distanceLabel()} · G${greenLabel()}")
    }

    private fun cycleDistance() {
        if (engine.state?.running == true) return
        distanceIndex = (distanceIndex + 1) % distances.size
        applyTarget(reset = true)
        updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}")
    }

    private fun cycleGreen() {
        if (engine.state?.running == true) return
        profile = (profile + 1) % 24
        applyTarget(reset = true)
        updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}")
    }

    private fun applyTarget(reset: Boolean) {
        engine.settings.holeDistanceM = distances[distanceIndex]
        engine.settings.stimpMeters = 2.8
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = profile
        if (reset) engine.resetSimulation()
        V143GodotRenderBridge.publish(engine)
    }

    private fun renderResultStatus() {
        val result = engine.lastResult
        updateStatus(if (result == null) {
            "READY · ${distanceLabel()} · GREEN ${greenLabel()}"
        } else {
            "SHOT COMPLETE · ${distanceLabel()} · GREEN ${greenLabel()}"
        })
    }

    private fun updateStatus(text: String) {
        if (::status.isInitialized) status.text = text
    }

    private fun distanceLabel(): String = "${distances[distanceIndex].toInt()}m"
    private fun greenLabel(): String = "%02d".format(profile)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = sp
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun labButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 10f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(220, 24, 34, 39))
        setOnClickListener { action() }
    }

    private fun buttonLp() = LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
