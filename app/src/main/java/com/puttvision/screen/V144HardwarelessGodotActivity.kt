package com.puttvision.screen

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * V152 full no-hardware simulator presentation.
 *
 * Android/GameEngine remains the only physics authority.  The synthetic buttons now generate a
 * distance/Stimp-calibrated launch instead of the old fixed 1.45 m/s shot, so CENTER is a genuine
 * regulation-cup attempt at every selectable distance rather than stopping metres short.
 */
class V148HardwarelessFullActivity : GodotActivity() {
    private lateinit var engine: GameEngine
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var panel: ScrollView

    private val distances = doubleArrayOf(3.0, 5.0, 7.0, 10.0)
    private var distanceIndex = 1
    private var profile = 8
    private var lastTickNs = 0L
    private var lastRunning = false
    private var godotReady = false
    private var uiReady = false
    private var resumed = false
    private var pumpStarted = false
    private var fullStable = false

    private enum class Scenario(
        val label: String,
        val icon: String,
        val speedScale: Double,
        val directionDeg: Double,
        val headScale: Double
    ) {
        CENTER("CENTER", "◎", 1.00, 0.0, 0.66),
        PUSH("PUSH +2°", "↑", 1.00, 2.0, 0.66),
        PULL("PULL -2°", "↓", 1.00, -2.0, 0.66),
        SHORT("SHORT", "◌", 0.72, 0.0, 0.62),
        LONG("LONG", "+", 1.20, 0.0, 0.70),
        BREAK("BREAK", "↝", 1.00, 0.0, 0.66)
    }

    private val pump = object : Runnable {
        override fun run() {
            if (!pumpStarted || !godotReady || !uiReady || !resumed || !::engine.isInitialized || isFinishing || isDestroyed) {
                pumpStarted = false
                return
            }

            val now = System.nanoTime()
            val dt = if (lastTickNs == 0L) {
                1.0 / 60.0
            } else {
                ((now - lastTickNs) / 1_000_000_000.0).coerceIn(1.0 / 240.0, 0.05)
            }
            lastTickNs = now

            runCatching {
                val runningBefore = engine.state?.running == true
                if (runningBefore) engine.step(dt)
                V143GodotRenderBridge.publish(engine)

                val runningNow = engine.state?.running == true
                if (lastRunning && !runningNow) renderResultStatus()
                lastRunning = runningNow
            }.onFailure { error ->
                markStage("pump-error:${error.javaClass.simpleName}")
                updateStatus("SIM LAB ERROR · ${error.javaClass.simpleName}", StatusTone.ERROR)
                stopPump()
                return
            }

            if (pumpStarted) handler.postDelayed(this, 16L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        markStage("onCreate-before-godot")
        super.onCreate(savedInstanceState)
        markStage("onCreate-after-godot")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        addContentView(buildLabOverlay(), FrameLayout.LayoutParams(-1, -1))
        uiReady = true
        markStage("ui-ready")

        installHardwarelessRuntimes()
        markStage("runtimes-ready")

        engine = GameEngine()
        markStage("engine-created")
        configureLabEngine()
        markStage("engine-configured")
        V143GodotRenderBridge.publish(engine)
        markStage("first-snapshot")
        maybeStartPump()
    }

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> =
        setOf(V143GodotPlugin(godot))

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        godotReady = true
        V143GodotRuntime.setupComplete = true
        V143GodotRuntime.lastFailure = null
        markStage("godot-ready")
        handler.post {
            updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}", StatusTone.NEUTRAL)
            maybeStartPump()
        }
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && godotReady && uiReady && ::engine.isInitialized) {
                fullStable = true
                markStage("full-stable")
            }
        }, 4000L)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        markStage("resumed")
        maybeStartPump()
    }

    override fun onPause() {
        resumed = false
        stopPump()
        markStage("paused")
        super.onPause()
    }

    override fun onDestroy() {
        resumed = false
        godotReady = false
        uiReady = false
        V143GodotRuntime.setupComplete = false
        stopPump()
        handler.removeCallbacksAndMessages(null)
        if (fullStable) markStage("full-finished")
        super.onDestroy()
    }

    private fun installHardwarelessRuntimes() {
        val app = applicationContext
        installRuntime("green-read") { GreenReadRuntime.install(app) }
        installRuntime("device-cal") { V16DeviceAutoCalibrationRuntime.install(app) }
        installRuntime("product-prefs") { V20ProductPreferences.install(app) }
        installRuntime("custom-green") { V22CustomGreenRuntime.install(app) }
        installRuntime("audio") { V22AudioRuntime.install(app) }
        installRuntime("tv-quality") { V24TvQualityRuntime.install(app) }
        installRuntime("ball-start") { V26BallStartRuntime.install(app) }
        installRuntime("green-visual") { V26GreenVisualRuntime.install(app) }
        installRuntime("report-prefs") { V26ReportPreferences.install(app) }
        installRuntime("cup-pace") { V27CupPaceRuntime.install(app) }
        installRuntime("training-session") { V31TrainingSessionRuntime.install(app) }
    }

    private inline fun installRuntime(name: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.w("PuttVisionV152", "runtime $name init failed", error)
            markStage("runtime-warning:$name:${error.javaClass.simpleName}")
        }
    }

    private fun maybeStartPump() {
        if (!::engine.isInitialized || !godotReady || !uiReady || !resumed || pumpStarted || isFinishing || isDestroyed) return
        lastTickNs = System.nanoTime()
        pumpStarted = true
        markStage("pump-started")
        handler.post(pump)
    }

    private fun stopPump() {
        pumpStarted = false
        lastTickNs = 0L
        handler.removeCallbacks(pump)
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
            background = roundedDrawable(
                color = Color.argb(232, 5, 13, 16),
                radiusDp = 12,
                strokeColor = Color.argb(90, 92, 125, 130),
                strokeDp = 1
            )
            isFillViewport = true
            clipToOutline = true
            elevation = dp(6).toFloat()
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        controls.addView(label("NO HARDWARE · REAL V143 · FULL", 12.5f, Color.rgb(105, 233, 170), true))
        controls.addView(label("합성 샷만 사용 · 렌더/그린/볼/컵 물리는 실제 TV와 동일", 8.7f, Color.rgb(206, 215, 216), false).apply {
            setPadding(0, dp(4), 0, dp(10))
        })

        status = label("GODOT STARTING · FULL · 5m · GREEN 08", 10.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(10), 0)
            background = roundedDrawable(
                Color.argb(220, 24, 34, 37),
                8,
                Color.argb(65, 255, 255, 255),
                1
            )
        }
        controls.addView(status, LinearLayout.LayoutParams(-1, dp(48)))

        controls.addView(label("SHOT", 9f, Color.rgb(164, 174, 175), true).apply {
            setPadding(0, dp(12), 0, dp(4))
        })

        listOf(Scenario.CENTER, Scenario.PUSH, Scenario.PULL, Scenario.SHORT).forEach { scenario ->
            controls.addView(primaryShotButton(scenario) { inject(scenario) }, primaryButtonLp())
        }

        // Preserve power/break diagnostics without letting secondary controls dominate the approved UI.
        val diagnostics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        diagnostics.addView(compactButton("LONG") { inject(Scenario.LONG) }, LinearLayout.LayoutParams(0, dp(34), 1f))
        diagnostics.addView(compactButton("BREAK") { inject(Scenario.BREAK) }, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            marginStart = dp(6)
        })
        controls.addView(diagnostics, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })

        val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        targetRow.addView(compactButton("거리 ${distanceLabel()}") { cycleDistance() }, LinearLayout.LayoutParams(0, dp(34), 1f))
        targetRow.addView(compactButton("그린 +1") { cycleGreen() }, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
            marginStart = dp(6)
        })
        controls.addView(targetRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })

        controls.addView(compactButton("RESET") {
            if (!::engine.isInitialized) return@compactButton
            engine.resetSimulation()
            if (godotReady) V143GodotRenderBridge.publish(engine)
            updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}", StatusTone.NEUTRAL)
        }, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(7) })

        panel.addView(controls, FrameLayout.LayoutParams(-1, -2))

        overlay.addView(panel, FrameLayout.LayoutParams(dp(276), -1, Gravity.END).apply {
            topMargin = dp(50)
            bottomMargin = dp(8)
            marginEnd = dp(8)
        })

        overlay.addView(topButton("LAB") {
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, FrameLayout.LayoutParams(dp(72), dp(40), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(7)
            marginEnd = dp(56)
        })
        overlay.addView(topButton("×") {
            markStage("full-user-close")
            finish()
        }, FrameLayout.LayoutParams(dp(44), dp(40), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(7)
            marginEnd = dp(7)
        })
        return overlay
    }

    private fun inject(scenario: Scenario) {
        if (!godotReady || !::engine.isInitialized) {
            updateStatus("GODOT STARTING · 잠시 후 다시", StatusTone.NEUTRAL)
            return
        }
        if (engine.state?.running == true) {
            updateStatus("ROLLING · 끝난 뒤 다음 샷", StatusTone.ROLLING)
            return
        }
        if (scenario == Scenario.BREAK && profile == 0) profile = 8
        applyTarget(reset = true)

        val ballSpeed = scenarioBallSpeed(scenario)
        val headSpeed = max(0.20, ballSpeed * scenario.headScale)
        val metrics = ShotMetrics(
            ballSpeedMps = ballSpeed,
            launchAngleDeg = scenario.directionDeg,
            headSpeedMps = headSpeed,
            faceAngleDeg = scenario.directionDeg * 0.65,
            pathAngleDeg = scenario.directionDeg * 0.35,
            faceToPathDeg = scenario.directionDeg * 0.30,
            smash = ballSpeed / headSpeed,
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
        updateStatus("${scenario.label} · ROLLING · ${"%.2f".format(ballSpeed)} m/s", StatusTone.ROLLING)
        maybeStartPump()
    }

    /**
     * Stimp is defined from a 1.95072 m/s launch.  Solve v² = u² + 2as backwards from a target
     * cup speed, then compensate the solver's intentional initial 72% roll fraction.  This keeps
     * synthetic examples realistic while allowing the real 6DOF cup solver to decide make/lip-out.
     */
    private fun scenarioBallSpeed(scenario: Scenario): Double {
        val start = V26BallStartRuntime.current(engine.settings)
        val distance = hypot(start.first, engine.settings.holeDistanceM - start.second).coerceAtLeast(0.25)
        val stimp = engine.settings.stimpMeters.coerceIn(1.5, 5.0)
        val stimpLaunch = 1.95072
        val rollingDecel = stimpLaunch * stimpLaunch / (2.0 * stimp)
        val desiredCupSpeed = V27CupPaceRuntime.targetCupSpeedMps.coerceIn(0.25, 0.85)
        val pureRollLaunch = sqrt(desiredCupSpeed * desiredCupSpeed + 2.0 * rollingDecel * distance)
        val skidCompensated = pureRollLaunch / 0.92
        return (skidCompensated * scenario.speedScale).coerceIn(0.15, 4.75)
    }

    private fun cycleDistance() {
        if (!::engine.isInitialized || engine.state?.running == true) return
        distanceIndex = (distanceIndex + 1) % distances.size
        applyTarget(reset = true)
        updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}", StatusTone.NEUTRAL)
    }

    private fun cycleGreen() {
        if (!::engine.isInitialized || engine.state?.running == true) return
        profile = (profile + 1) % 24
        applyTarget(reset = true)
        updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}", StatusTone.NEUTRAL)
    }

    private fun applyTarget(reset: Boolean) {
        if (!::engine.isInitialized) return
        engine.settings.holeDistanceM = distances[distanceIndex]
        engine.settings.stimpMeters = 2.8
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = profile
        if (reset) engine.resetSimulation()
        if (godotReady) V143GodotRenderBridge.publish(engine)
    }

    private fun renderResultStatus() {
        if (!::engine.isInitialized) return
        val result = engine.lastResult
        when {
            result == null -> updateStatus("READY · ${distanceLabel()} · GREEN ${greenLabel()}", StatusTone.NEUTRAL)
            result.holed -> updateStatus("✓  SHOT COMPLETE · HOLED", StatusTone.HOLED)
            result.lipOut -> updateStatus("SHOT COMPLETE · LIP OUT", StatusTone.MISS)
            result.distanceToCupM <= 0.30 -> updateStatus(
                "SHOT COMPLETE · ${formatLeave(result.distanceToCupM)}",
                StatusTone.NEAR
            )
            else -> updateStatus("SHOT COMPLETE · ${formatLeave(result.distanceToCupM)}", StatusTone.MISS)
        }
    }

    private fun formatLeave(meters: Double): String = if (meters < 1.0) {
        "${(meters * 100.0).toInt()} cm"
    } else {
        "${"%.2f".format(meters)} m"
    }

    private fun markStage(stage: String) {
        Log.i("PuttVisionV152", stage)
        V148GodotCrashJournal.write(this, "full", stage)
    }

    private enum class StatusTone { NEUTRAL, ROLLING, HOLED, NEAR, MISS, ERROR }

    private fun updateStatus(text: String, tone: StatusTone = StatusTone.NEUTRAL) {
        if (!::status.isInitialized) return
        status.text = text
        val (bg, fg, stroke) = when (tone) {
            StatusTone.HOLED -> Triple(Color.argb(235, 18, 50, 39), Color.rgb(127, 239, 177), Color.rgb(78, 190, 129))
            StatusTone.ROLLING -> Triple(Color.argb(225, 28, 41, 45), Color.rgb(238, 241, 239), Color.argb(100, 106, 225, 168))
            StatusTone.NEAR -> Triple(Color.argb(225, 45, 43, 24), Color.rgb(244, 220, 130), Color.rgb(165, 142, 69))
            StatusTone.MISS -> Triple(Color.argb(225, 46, 30, 30), Color.rgb(240, 205, 205), Color.rgb(142, 79, 79))
            StatusTone.ERROR -> Triple(Color.argb(235, 65, 22, 22), Color.WHITE, Color.rgb(202, 77, 77))
            StatusTone.NEUTRAL -> Triple(Color.argb(220, 24, 34, 37), Color.WHITE, Color.argb(65, 255, 255, 255))
        }
        status.setTextColor(fg)
        status.background = roundedDrawable(bg, 8, stroke, 1)
    }

    private fun distanceLabel(): String = "${distances[distanceIndex].toInt()}m"
    private fun greenLabel(): String = "%02d".format(profile)

    private fun label(text: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = sp
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun primaryShotButton(scenario: Scenario, action: () -> Unit) = Button(this).apply {
        text = "${scenario.icon}     ${scenario.label}"
        textSize = 11.5f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isAllCaps = false
        includeFontPadding = false
        setPadding(dp(16), 0, dp(12), 0)
        setTextColor(Color.rgb(239, 243, 242))
        setTypeface(typeface, Typeface.NORMAL)
        stateListAnimator = null
        background = roundedDrawable(
            Color.argb(222, 24, 34, 37),
            9,
            Color.argb(60, 175, 198, 196),
            1
        )
        setOnClickListener { action() }
    }

    private fun compactButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 9f
        gravity = Gravity.CENTER
        isAllCaps = false
        includeFontPadding = false
        setTextColor(Color.rgb(212, 220, 219))
        stateListAnimator = null
        background = roundedDrawable(Color.argb(205, 20, 29, 32), 8, Color.argb(45, 255, 255, 255), 1)
        setOnClickListener { action() }
    }

    private fun topButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = if (text == "LAB") 13f else 18f
        gravity = Gravity.CENTER
        isAllCaps = false
        includeFontPadding = false
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null
        background = roundedDrawable(Color.argb(232, 8, 20, 25), 9, Color.argb(55, 255, 255, 255), 1)
        setOnClickListener { action() }
    }

    private fun roundedDrawable(color: Int, radiusDp: Int, strokeColor: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun primaryButtonLp() = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
