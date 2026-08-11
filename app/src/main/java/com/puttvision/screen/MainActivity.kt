package com.puttvision.screen

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: PhoneOverlayView
    private lateinit var replayView: ImpactReplayView
    private lateinit var analysis: ImageAnalysis
    private lateinit var provider: ProcessCameraProvider

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var displayController: ExternalDisplayController
    private lateinit var statsRepository: StatsRepository
    private lateinit var appUpdater: AppUpdater

    private val engine = GameEngine()
    private val tracker = ShotTracker()
    private val hfrAnalyzer = HfrVideoAnalyzer()

    private var homography: Homography? = null
    private var calibrator: AutoCalibrator? = null
    private var hfrController: HighSpeedCaptureController? = null

    private lateinit var impactDetector: PreviewImpactDetector

    private val mainHandler = Handler(Looper.getMainLooper())
    private val simHandler = Handler(Looper.getMainLooper())

    private var hfrHardwareAvailable = false
    private var hfrProbeComplete = false
    private var cameraProviderOpening = false

    private var impactDetected = false
    private var impactPolling = false
    private var recordingStartedAtMs = 0L
    private var hfrRecordingGeneration = 0

    private var autoPlayEnabled = true
    private var simulationTicking = false
    private var lastSimulationNs = 0L
    private var autoGeneration = 0

    private lateinit var tvStatus: TextView
    private lateinit var hfrStatus: TextView
    private lateinit var metricText: TextView
    private lateinit var speedLabel: TextView
    private lateinit var distanceLabel: TextView
    private lateinit var sideLabel: TextView
    private lateinit var longLabel: TextView
    private lateinit var autoButton: Button
    private lateinit var modeButton: Button
    private lateinit var settingsPanel: LinearLayout
    private lateinit var settingSummary: TextView
    private lateinit var settingsToggle: Button
    private val metricCards = linkedMapOf<String, TextView>()
    private lateinit var shotPanelTitle: TextView
    private lateinit var menuOverlay: FrameLayout
    private var practiceEntranceMode = 0
    private var practiceCount = 10
    private var practiceDistanceM = 5
    private var practiceGreenSpeed = 2.8
    private var practicePatternIndex = 0
    private var practiceGreenPresetIndex = 2
    private var practiceShotsTaken = 0
    private var practicePatternShotIndex = 0
    private val practiceRandom = Random(20260811)

    private var gamePlayers = 2
    private var gameModeIndex = 0
    private var gameDistanceM = 3

    private var sessionActive = false
    private var measurementSuspended = false
    private var activeSessionIsGame = false
    private var menuBackAction: (() -> Unit)? = null
    private var settingsDialog: AlertDialog? = null
    private val uiPrefs by lazy { getSharedPreferences("puttvision_ui", Context.MODE_PRIVATE) }

    private val compactLandscape: Boolean
        get() = resources.configuration.screenHeightDp <= 420

    private var lastTvStatusMessage = "외부 디스플레이 상태 확인중"
    private var lastHfrStatusMessage = "HFR 확인중"

    private data class PracticeGreenPreset(
        val title: String,
        val subtitle: String,
        val sideSlopePct: Double,
        val longSlopePct: Double,
        val previewStyle: Int
    )

    private val practiceGreenPresets = listOf(
        PracticeGreenPreset("스트레이트", "0.5m / 20in", 0.0, 0.0, 0),
        PracticeGreenPreset("좌→우 미세", "0.5m / 20in", 1.3, 0.0, 1),
        PracticeGreenPreset("중앙 브레이크", "0.5m / 20in", -1.8, -0.6, 2),
        PracticeGreenPreset("오르막 스트레이트", "1.2m / 45in", 0.0, -2.2, 3),
        PracticeGreenPreset("오르막 브레이크", "1.2m / 45in", 2.8, -1.1, 4),
        PracticeGreenPreset("복합 경사", "1.2m / 45in", -3.4, -1.6, 5)
    )

    private val permission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openProvider()
            } else {
                toast("카메라 권한이 필요합니다")
                if (sessionActive) showHomeMenu()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraExecutor =
            Executors.newSingleThreadExecutor()

        statsRepository =
            StatsRepository(this)

        appUpdater = AppUpdater(this)

        engine.seedHistory(
            statsRepository.all()
        )

        engine.onRecordFinalized = { record ->
            statsRepository.add(record)
        }

        buildUi()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (menuOverlay.visibility == View.VISIBLE) {
                        val back = menuBackAction
                        if (back != null) back() else finish()
                    } else {
                        showHomeMenu()
                    }
                }
            }
        )

        impactDetector =
            PreviewImpactDetector(
                previewView
            )

        displayController =
            ExternalDisplayController(
                this,
                engine
            ) { connected, msg ->
                runOnUiThread {
                    setTvStatus(connected, msg)
                }
            }

        displayController.start()

        mainHandler.postDelayed({ appUpdater.check(silent = true) }, 1800L)

        probeHfr()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openProvider()
        } else {
            permission.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (::appUpdater.isInitialized) {
            appUpdater.resumePendingInstallIfPossible()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun probeHfr() {
        cameraExecutor.execute {
            val caps =
                HfrCapabilityProbe.queryBackCamera(
                    this
                )

            val preferred =
                HfrCapabilityProbe.preferred(
                    caps
                )

            runOnUiThread {
                hfrHardwareAvailable =
                    preferred != null

                hfrProbeComplete = true

                val detail = when {
                    preferred == null -> "HFR 미지원 · 일반 추적 모드"
                    preferred.fps >= 240 -> "HFR ${preferred.fps}fps 가능 · 240fps 우선"
                    else -> "HFR ${preferred.fps}fps 가능"
                }
                setHfrStatus(
                    short = if (preferred == null) "HFR 미지원" else "HFR ${preferred.fps}fps",
                    detail = detail
                )

                maybeAutoStartAfterCalibration()
            }
        }
    }

    private fun buildUi() {
    val compact = compactLandscape
    metricCards.clear()

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Pv.inkDeep)
    }

    val stage = FrameLayout(this).apply {
        setBackgroundColor(Color.rgb(2, 4, 6))
    }
    previewView = PreviewView(this).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    stage.addView(previewView, FrameLayout.LayoutParams(-1, -1))

    overlay = PhoneOverlayView(this)
    stage.addView(overlay, FrameLayout.LayoutParams(-1, -1))

    val impactPreview = CommercialImpactPreviewView(this)
    stage.addView(
        impactPreview,
        FrameLayout.LayoutParams(pvDp(if (compact) 168 else 214), pvDp(if (compact) 76 else 96), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(if (compact) 50 else 58)
            rightMargin = pvDp(if (compact) 10 else 14)
        }
    )

    replayView = ImpactReplayView(this)
    stage.addView(replayView, FrameLayout.LayoutParams(-1, -1))

    val topRail = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Color.argb(232, 5, 8, 11), 18f, Color.argb(125, 75, 91, 100))
        setPadding(pvDp(if (compact) 12 else 16), pvDp(5), pvDp(if (compact) 7 else 9), pvDp(5))
        elevation = pvDp(12).toFloat()
    }
    val brand = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    brand.addView(TextView(this).apply {
        text = "PUTTVISION"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 10.5f else 12f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .06f
        includeFontPadding = false
    })
    brand.addView(TextView(this).apply {
        text = "PRECISION PUTTING SYSTEM"
        setTextColor(Pv.textLo)
        textSize = pvSp(if (compact) 5.4f else 6.3f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
    })
    topRail.addView(brand)

    val sessionLabel = TextView(this).apply {
        text = "LIVE SESSION"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 6f else 7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
        gravity = Gravity.CENTER
        background = pvRounded(Pv.primaryDim, 100f, Pv.primaryLine)
        setPadding(pvDp(9), pvDp(4), pvDp(9), pvDp(4))
    }
    topRail.addView(sessionLabel, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(if (compact) 8 else 12) })
    topRail.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

    fun railPill(initial: String, accent: Int, click: () -> Unit): TextView = TextView(this).apply {
        text = initial
        setTextColor(accent)
        textSize = pvSp(if (compact) 6.8f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setSingleLine(true)
        background = pvRounded(Color.rgb(13, 18, 23), 100f, Pv.line)
        setPadding(pvDp(if (compact) 8 else 10), pvDp(4), pvDp(if (compact) 8 else 10), pvDp(4))
        minHeight = pvDp(if (compact) 27 else 30)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    hfrStatus = railPill("HFR", Pv.primary) { toast(lastHfrStatusMessage) }
    topRail.addView(hfrStatus)
    tvStatus = railPill("TV", Pv.textMid) { toast(lastTvStatusMessage) }
    topRail.addView(tvStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(4) })

    modeButton = pvButton("세션", PvButtonStyle.GHOST, textSp = if (compact) 7f else 8f, radiusDp = 100f) {
        if (engine.state?.running == true) {
            toast("공이 멈춘 뒤 세션을 변경하세요")
        } else if (sessionActive) {
            pauseSessionForMenu()
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        } else {
            showHomeMenu()
        }
    }
    topRail.addView(modeButton, LinearLayout.LayoutParams(pvDp(if (compact) 60 else 68), pvDp(if (compact) 27 else 30)).apply { marginStart = pvDp(4) })

    autoButton = pvButton("AUTO", PvButtonStyle.PRIMARY, textSp = if (compact) 7f else 8f, radiusDp = 100f) {
        autoPlayEnabled = !autoPlayEnabled
        autoGeneration++
        updateAutoButton()
        if (autoPlayEnabled) maybeAutoStartAfterCalibration()
    }
    topRail.addView(autoButton, LinearLayout.LayoutParams(pvDp(if (compact) 58 else 64), pvDp(if (compact) 27 else 30)).apply { marginStart = pvDp(4) })

    stage.addView(topRail, FrameLayout.LayoutParams(-1, pvDp(if (compact) 39 else 43), Gravity.TOP).apply {
        leftMargin = pvDp(if (compact) 8 else 12)
        rightMargin = pvDp(if (compact) 8 else 12)
        topMargin = pvDp(if (compact) 7 else 10)
    })

    root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

    val console = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvVGradient(Color.rgb(12, 16, 21), Color.rgb(6, 9, 12))
        setPadding(pvDp(if (compact) 10 else 14), pvDp(if (compact) 6 else 8), pvDp(if (compact) 10 else 14), pvDp(if (compact) 6 else 8))
    }

    val primaryRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun heroMetric(label: String, key: String, unit: String, accent: Boolean = false): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(pvDp(if (compact) 8 else 11), 0, pvDp(if (compact) 8 else 11), 0)
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 5.9f else 6.9f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .09f
            includeFontPadding = false
        })
        val line = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val value = TextView(this@MainActivity).apply {
            text = "--"
            setTextColor(if (accent) Pv.primary else Pv.textHi)
            textSize = pvSp(if (compact) 18.5f else 23f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        metricCards[key] = value
        line.addView(value)
        if (unit.isNotBlank()) {
            line.addView(TextView(this@MainActivity).apply {
                text = "  $unit"
                setTextColor(Pv.textLo)
                textSize = pvSp(if (compact) 5.8f else 6.8f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(0, 0, 0, pvDp(2))
            })
        }
        addView(line)
    }

    primaryRow.addView(heroMetric("BALL SPEED", "ball", "m/s", true), LinearLayout.LayoutParams(0, -1, .86f))
    primaryRow.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) }, LinearLayout.LayoutParams(pvDp(1), -1).apply { topMargin = pvDp(8); bottomMargin = pvDp(8) })
    primaryRow.addView(heroMetric("START LINE", "launch", "°"), LinearLayout.LayoutParams(0, -1, .86f))
    primaryRow.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) }, LinearLayout.LayoutParams(pvDp(1), -1).apply { topMargin = pvDp(8); bottomMargin = pvDp(8) })
    primaryRow.addView(heroMetric("HEAD SPEED", "head", "m/s"), LinearLayout.LayoutParams(0, -1, .90f))

    val command = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceLo, Pv.rLg, Pv.lineSoft)
        setPadding(pvDp(if (compact) 9 else 12), pvDp(5), pvDp(if (compact) 9 else 12), pvDp(5))
    }
    shotPanelTitle = TextView(this).apply {
        text = "READY"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 6.2f else 7.2f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .11f
        includeFontPadding = false
    }
    metricText = TextView(this).apply {
        text = "공을 놓고 퍼팅하세요"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 7.2f else 8.3f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        maxLines = 1
    }
    command.addView(shotPanelTitle)
    command.addView(metricText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(1) })

    val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    actions.addView(pvButton("측정 준비", PvButtonStyle.PRIMARY, textSp = if (compact) 6.8f else 7.8f, radiusDp = 100f) { armPrecision() }, LinearLayout.LayoutParams(0, pvDp(if (compact) 29 else 33), 1.15f).apply { marginEnd = pvDp(4) })
    actions.addView(pvButton("재보정", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, radiusDp = 100f) { beginAutoCalibration() }, LinearLayout.LayoutParams(0, pvDp(if (compact) 29 else 33), .78f).apply { marginEnd = pvDp(4) })
    settingsToggle = pvButton("설정", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, radiusDp = 100f) { showSettingsDialog() }
    actions.addView(settingsToggle, LinearLayout.LayoutParams(0, pvDp(if (compact) 29 else 33), .72f))
    command.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(4) })

    primaryRow.addView(command, LinearLayout.LayoutParams(0, -1, 1.34f).apply { marginStart = pvDp(if (compact) 7 else 10) })
    console.addView(primaryRow, LinearLayout.LayoutParams(-1, 0, .68f))

    val secondaryRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Color.rgb(8, 12, 16), Pv.rMd, Pv.lineSoft)
        setPadding(pvDp(if (compact) 8 else 10), pvDp(3), pvDp(if (compact) 8 else 10), pvDp(3))
    }

    fun diagnostic(label: String, key: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 5.3f else 6.2f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        val value = TextView(this@MainActivity).apply {
            text = "--"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 6.8f else 8f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(pvDp(4), 0, 0, 0)
            maxLines = 1
        }
        metricCards[key] = value
        addView(value)
    }

    listOf(
        "FACE" to "face",
        "PATH" to "path",
        "F→P" to "f2p",
        "IMPACT" to "impact",
        "SMASH" to "smash",
        "TEMPO" to "tempo"
    ).forEachIndexed { i, pair ->
        secondaryRow.addView(diagnostic(pair.first, pair.second), LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = pvDp(5) })
    }

    settingSummary = TextView(this).apply {
        setTextColor(Pv.textMid)
        textSize = pvSp(if (compact) 5.4f else 6.4f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        maxLines = 1
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    secondaryRow.addView(settingSummary, LinearLayout.LayoutParams(0, -1, 1.35f).apply { marginStart = pvDp(8) })
    console.addView(secondaryRow, LinearLayout.LayoutParams(-1, 0, .32f).apply { topMargin = pvDp(4) })

    settingsPanel = LinearLayout(this).apply { visibility = View.GONE }
    speedLabel = settingLabel()
    distanceLabel = settingLabel()
    sideLabel = settingLabel()
    longLabel = settingLabel()
    updateSettingLabels()
    updateAutoButton()

    root.addView(console, LinearLayout.LayoutParams(-1, pvDp(if (compact) 116 else 140)))

    val shell = FrameLayout(this)
    shell.addView(root, FrameLayout.LayoutParams(-1, -1))
    menuOverlay = buildSmartPuttMenu()
    shell.addView(menuOverlay, FrameLayout.LayoutParams(-1, -1))
    setContentView(shell)
}

    private fun buildSmartPuttMenu(): FrameLayout {
        menuBackAction = null
        return FrameLayout(this).apply {
            setBackgroundColor(Pv.inkDeep)
            isClickable = true
            isFocusable = true
            addView(buildVideoHomeScreen(), FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun replaceMenuScreen(view: View, backAction: (() -> Unit)? = null) {
        menuBackAction = backAction
        menuOverlay.removeAllViews()
        menuOverlay.addView(view, FrameLayout.LayoutParams(-1, -1))
        menuOverlay.isClickable = true
        menuOverlay.visibility = View.VISIBLE
    }

    private fun cyanButton(label: String, click: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = scaledSp(14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Pv.primaryInk)
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
            setPadding(sdp(8), sdp(10), sdp(8), sdp(10))
            background = pvRounded(Pv.primary, Pv.rLg)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun darkChoice(label: String, selected: Boolean, click: () -> Unit): Button =
        pvChip(label, selected, onClick = click)

    private fun roundMenuIcon(symbol: String, label: String, click: () -> Unit): LinearLayout =
        pvIconControl(symbol, label, click)

    private fun buildVideoHomeScreen(): View {
    val compact = compactLandscape
    val root = FrameLayout(this).apply {
        setBackgroundColor(Pv.inkDeep)
        addView(CommercialHomeBackdropView(this@MainActivity), FrameLayout.LayoutParams(-1, -1))
    }

    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(sdp(if (compact) 16 else 24), sdp(if (compact) 9 else 14), sdp(if (compact) 16 else 24), 0)
    }
    val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    brand.addView(TextView(this).apply {
        text = "PUTTVISION"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 13f else 15f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .08f
        includeFontPadding = false
    })
    brand.addView(TextView(this).apply {
        text = "PERSONAL PUTTING STUDIO"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 5.8f else 6.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .16f
        includeFontPadding = false
    })
    top.addView(brand)
    top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    top.addView(pvButton("환경", PvButtonStyle.GHOST, textSp = if (compact) 7f else 8f, scaled = true, radiusDp = 100f) { showSettingsDialog() }, LinearLayout.LayoutParams(sdp(if (compact) 58 else 66), sdp(if (compact) 31 else 35)))
    top.addView(pvButton("종료", PvButtonStyle.GHOST, textSp = if (compact) 7f else 8f, scaled = true, radiusDp = 100f) { finishAffinity() }, LinearLayout.LayoutParams(sdp(if (compact) 58 else 66), sdp(if (compact) 31 else 35)).apply { marginStart = sdp(6) })

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(sdp(if (compact) 24 else 36), sdp(if (compact) 50 else 64), sdp(if (compact) 24 else 36), sdp(if (compact) 17 else 24))
    }

    val hero = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(sdp(if (compact) 4 else 8), 0, sdp(if (compact) 18 else 28), 0)
    }
    hero.addView(TextView(this).apply {
        text = "240 FPS · CAMERA VISION · TV SIMULATOR"
        setTextColor(Pv.primary)
        textSize = scaledSp(if (compact) 6.6f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .14f
        includeFontPadding = false
    })
    hero.addView(TextView(this).apply {
        text = "퍼팅을 보고,\n숫자로 고칩니다."
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 28f else 37f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(sdp(1).toFloat(), .98f)
        setPadding(0, sdp(if (compact) 7 else 10), 0, 0)
    })
    hero.addView(TextView(this).apply {
        text = "볼 스피드 · 출발각 · 페이스 · 패스 · 임팩트 · 템포를\n한 번의 스트로크에서 측정하고 TV에서 바로 확인합니다."
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 7.6f else 9f)
        includeFontPadding = false
        setLineSpacing(sdp(2).toFloat(), 1.08f)
        setPadding(0, sdp(if (compact) 8 else 12), 0, 0)
    })

    val capabilityRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    fun capability(title: String, sub: String, accent: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Color.argb(210, 10, 15, 18), Pv.rMd, Pv.lineSoft)
        setPadding(sdp(if (compact) 9 else 12), sdp(if (compact) 7 else 9), sdp(if (compact) 9 else 12), sdp(if (compact) 7 else 9))
        addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(accent)
            textSize = scaledSp(if (compact) 7f else 8f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textLo)
            textSize = scaledSp(if (compact) 5.8f else 6.8f)
            includeFontPadding = false
            setPadding(0, sdp(2), 0, 0)
        })
    }
    capabilityRow.addView(capability("CAMERA", "240fps 우선", Pv.primary), LinearLayout.LayoutParams(0, -2, 1f))
    capabilityRow.addView(capability("CAL", "마커 4개 자동", Pv.info), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = sdp(6) })
    capabilityRow.addView(capability("SCREEN", "HDMI / DeX", Pv.amber), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = sdp(6) })
    hero.addView(capabilityRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(if (compact) 12 else 18) })
    body.addView(hero, LinearLayout.LayoutParams(0, -1, .56f))

    val launcher = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Color.argb(238, 11, 15, 19), Pv.rXl, Color.argb(150, 55, 68, 78))
        setPadding(sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16), sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16))
    }
    launcher.addView(TextView(this).apply {
        text = "SESSION"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 6.2f else 7.2f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .16f
        includeFontPadding = false
    })
    launcher.addView(TextView(this).apply {
        text = "오늘의 퍼팅"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 17f else 21f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setPadding(0, sdp(3), 0, 0)
    })
    launcher.addView(TextView(this).apply {
        text = "목적에 맞는 세션을 선택하세요."
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 6.8f else 8f)
        includeFontPadding = false
        setPadding(0, sdp(3), 0, 0)
    })

    fun sessionCard(number: String, title: String, sub: String, meta: String, accent: Int, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceHi, Pv.rLg, Pv.line)
        setPadding(sdp(if (compact) 10 else 13), sdp(if (compact) 8 else 10), sdp(if (compact) 11 else 14), sdp(if (compact) 8 else 10))
        addView(TextView(this@MainActivity).apply {
            text = number
            setTextColor(if (accent == Pv.amber) Pv.amberInk else Pv.primaryInk)
            setBackgroundColor(accent)
            textSize = scaledSp(if (compact) 8f else 9f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(sdp(if (compact) 31 else 36), sdp(if (compact) 31 else 36)))
        val copy = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(10), 0, 0, 0)
        }
        copy.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 13f else 15.5f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textMid)
            textSize = scaledSp(if (compact) 6.3f else 7.4f)
            includeFontPadding = false
            maxLines = 1
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = meta
            setTextColor(accent)
            textSize = scaledSp(if (compact) 5.6f else 6.6f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, sdp(2), 0, 0)
        })
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "→"
            setTextColor(accent)
            textSize = scaledSp(if (compact) 19f else 22f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(sdp(28), -1))
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    launcher.addView(sessionCard("01", "연습 세션", "거리 · 컵 · 그린 읽기 · 반복", "PRACTICE LAB", Pv.primary) { showPracticeEntrance() }, LinearLayout.LayoutParams(-1, sdp(if (compact) 74 else 86)).apply { topMargin = sdp(if (compact) 10 else 14) })
    launcher.addView(sessionCard("02", "게임 세션", "1–4인 · 9홀 · 18홀 · 챌린지", "MATCH PLAY", Pv.amber) { showGameEntrance() }, LinearLayout.LayoutParams(-1, sdp(if (compact) 74 else 86)).apply { topMargin = sdp(if (compact) 8 else 10) })

    launcher.addView(TextView(this).apply {
        text = "RECENT  ${engine.recentRecords.size} SHOTS  ·  DATA STORED LOCALLY"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 5.4f else 6.4f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, sdp(if (compact) 8 else 10), 0, 0)
    })

    body.addView(launcher, LinearLayout.LayoutParams(0, -1, .44f).apply { marginStart = sdp(if (compact) 12 else 18) })
    root.addView(body, FrameLayout.LayoutParams(-1, -1))
    root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    return root
}

    private fun sectionPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surface, Pv.rLg, Pv.lineSoft)
        setPadding(sdp(12), sdp(10), sdp(12), sdp(10))
    }

    private fun tinyCaption(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = scaledSp(9f)
        setTextColor(Pv.textMid)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.06f
        setPadding(sdp(2), 0, 0, sdp(6))
    }

    private fun buildEntranceHeader(title: String, english: String, guide: (() -> Unit)? = null, back: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val copy = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = scaledSp(if (compactLandscape) 15.5f else 18f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Pv.textHi)
                includeFontPadding = false
            })
            if (english.isNotBlank()) {
                addView(TextView(this@MainActivity).apply {
                    text = english
                    textSize = scaledSp(if (compactLandscape) 5.9f else 6.9f)
                    setTextColor(Pv.textLo)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = .14f
                    includeFontPadding = false
                    setPadding(0, sdp(2), 0, 0)
                })
            }
        }
        addView(copy)
        addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        if (guide != null) {
            addView(pvButton("가이드", PvButtonStyle.GHOST, textSp = if (compactLandscape) 6.8f else 7.8f, scaled = true, radiusDp = 100f) { guide() }, LinearLayout.LayoutParams(sdp(if (compactLandscape) 58 else 68), sdp(if (compactLandscape) 31 else 35)).apply { marginEnd = sdp(6) })
        }
        addView(pvButton("뒤로", PvButtonStyle.GHOST, textSp = if (compactLandscape) 6.8f else 7.8f, scaled = true, radiusDp = 100f) { back() }, LinearLayout.LayoutParams(sdp(if (compactLandscape) 54 else 64), sdp(if (compactLandscape) 31 else 35)))
    }

    private fun showPracticeEntrance() {
        replaceMenuScreen(buildPracticeEntrance()) { showHomeMenu() }
    }

    private fun showPracticeGreenPicker() {
        replaceMenuScreen(buildPracticeGreenPicker()) { showPracticeEntrance() }
    }

    private fun buildPracticeEntrance(): View {
        val compact = compactLandscape
        val selectedGreen = practiceGreenPresets[practiceGreenPresetIndex]
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14), sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14))
        }

        root.addView(
            buildEntranceHeader("연습 세션", "PRACTICE LAB", { showCupGuideScreen { showPracticeEntrance() } }) { showHomeMenu() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(if (compact) 8 else 12) }
        )

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val visual = FrameLayout(this).apply {
            background = pvRounded(Color.rgb(8, 12, 14), Pv.rXl, Pv.lineSoft)
            clipToOutline = true
            if (practiceEntranceMode == 2) {
                addView(PracticeGreenPreviewView(this@MainActivity).apply {
                    styleIndex = selectedGreen.previewStyle
                }, FrameLayout.LayoutParams(-1, -1))
                isClickable = true
                isFocusable = true
                setOnClickListener { showPracticeGreenPicker() }
            } else {
                addView(CommercialModeVisualView(this@MainActivity, false), FrameLayout.LayoutParams(-1, -1))
            }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(sdp(if (compact) 14 else 18), sdp(12), sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16))
                addView(TextView(this@MainActivity).apply {
                    text = when (practiceEntranceMode) {
                        1 -> "CUP CONTROL"
                        2 -> "GREEN READING"
                        else -> "DISTANCE CONTROL"
                    }
                    setTextColor(Pv.primary)
                    textSize = scaledSp(if (compact) 7f else 8f)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = .14f
                    includeFontPadding = false
                })
                addView(TextView(this@MainActivity).apply {
                    text = when (practiceEntranceMode) {
                        1 -> "같은 스트로크로\n컵 거리 감각을 맞춥니다."
                        2 -> "${selectedGreen.title}\n눌러서 그린을 변경하세요."
                        else -> "거리별 볼 스피드와\n스트로크 크기를 잡습니다."
                    }
                    setTextColor(Pv.textHi)
                    textSize = scaledSp(if (compact) 15.5f else 18.5f)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setLineSpacing(sdp(1).toFloat(), 1f)
                })
                if (practiceEntranceMode == 2) {
                    addView(TextView(this@MainActivity).apply {
                        text = "${selectedGreen.subtitle}  ·  BREAK ${if (selectedGreen.sideSlopePct >= 0) "+" else ""}${"%.1f".format(selectedGreen.sideSlopePct)}%  ·  GRADE ${if (selectedGreen.longSlopePct >= 0) "+" else ""}${"%.1f".format(selectedGreen.longSlopePct)}%"
                        setTextColor(Pv.textMid)
                        textSize = scaledSp(if (compact) 6.2f else 7.2f)
                        typeface = Typeface.MONOSPACE
                        includeFontPadding = false
                        setPadding(0, sdp(5), 0, 0)
                    })
                }
            }, FrameLayout.LayoutParams(-1, -1))
        }
        body.addView(visual, LinearLayout.LayoutParams(0, -1, .42f).apply { marginEnd = sdp(if (compact) 10 else 14) })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pvRounded(Pv.surface, Pv.rXl, Pv.lineSoft)
            setPadding(sdp(if (compact) 13 else 17), sdp(if (compact) 10 else 13), sdp(if (compact) 13 else 17), sdp(if (compact) 10 else 13))
        }

        fun sectionLabel(textValue: String): TextView = TextView(this).apply {
            text = textValue
            setTextColor(Pv.textLo)
            textSize = scaledSp(if (compact) 6.2f else 7.2f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
            includeFontPadding = false
        }

        panel.addView(sectionLabel("TRAINING TYPE"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("거리", "컵", "그린").forEachIndexed { i, label ->
            modeRow.addView(
                darkChoice(label, practiceEntranceMode == i) {
                    practiceEntranceMode = i
                    practicePatternIndex = 0
                    showPracticeEntrance()
                },
                LinearLayout.LayoutParams(0, sdp(if (compact) 38 else 44), 1f).apply { if (i > 0) marginStart = sdp(5) }
            )
        }
        panel.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(6) })

        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(sectionLabel("TARGET DISTANCE"))
        val distanceValue = TextView(this).apply {
            text = "${practiceDistanceM} m"
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 21f else 25f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, sdp(3), 0, 0)
        }
        left.addView(distanceValue)
        val distanceSeek = SeekBar(this).apply {
            max = 13
            progress = (practiceDistanceM - 2).coerceIn(0, 13)
            progressTintList = ColorStateList.valueOf(Pv.primary)
            progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
            thumbTintList = ColorStateList.valueOf(Pv.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    practiceDistanceM = progress + 2
                    distanceValue.text = "${practiceDistanceM} m"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        left.addView(distanceSeek)
        middle.addView(left, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = sdp(if (compact) 10 else 14) })

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(sectionLabel("GREEN SPEED  ·  2.4 — 3.6"))
        val speedValue = TextView(this).apply {
            text = "%.1f".format(practiceGreenSpeed)
            setTextColor(Pv.primary)
            textSize = scaledSp(if (compact) 21f else 25f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, sdp(3), 0, 0)
        }
        right.addView(speedValue)
        val speedSeek = SeekBar(this).apply {
            max = 12
            progress = ((practiceGreenSpeed - 2.4) * 10).toInt().coerceIn(0, 12)
            progressTintList = ColorStateList.valueOf(Pv.primary)
            progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
            thumbTintList = ColorStateList.valueOf(Pv.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    practiceGreenSpeed = 2.4 + progress * .1
                    speedValue.text = "%.1f".format(practiceGreenSpeed)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        right.addView(speedSeek)
        middle.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = sdp(if (compact) 10 else 14) })

        panel.addView(middle, LinearLayout.LayoutParams(-1, 0, .31f).apply { topMargin = sdp(if (compact) 7 else 10) })

        if (practiceEntranceMode == 2) {
            panel.addView(sectionLabel("GREEN PROFILE"))
            val greenSelect = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pvRounded(Pv.surfaceLo, Pv.rLg, Pv.lineSoft)
                setPadding(sdp(if (compact) 8 else 10), sdp(6), sdp(if (compact) 9 else 12), sdp(6))
                addView(PracticeGreenPreviewView(this@MainActivity).apply {
                    styleIndex = selectedGreen.previewStyle
                }, LinearLayout.LayoutParams(sdp(if (compact) 72 else 88), -1))
                val copy = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(sdp(9), 0, 0, 0)
                    addView(TextView(this@MainActivity).apply {
                        text = selectedGreen.title
                        setTextColor(Pv.textHi)
                        textSize = scaledSp(if (compact) 9f else 10.5f)
                        typeface = Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = selectedGreen.subtitle
                        setTextColor(Pv.textMid)
                        textSize = scaledSp(if (compact) 6.3f else 7.3f)
                        typeface = Typeface.MONOSPACE
                        includeFontPadding = false
                    })
                }
                addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "그린 선택  ›"
                    setTextColor(Pv.primary)
                    textSize = scaledSp(if (compact) 8f else 9f)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                })
                isClickable = true
                isFocusable = true
                setOnClickListener { showPracticeGreenPicker() }
            }
            panel.addView(greenSelect, LinearLayout.LayoutParams(-1, 0, .19f).apply { topMargin = sdp(5) })
        }

        panel.addView(sectionLabel(if (practiceEntranceMode == 1) "CUP PRESET" else "DISTANCE PATTERN"))
        val patternRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val patterns = if (practiceEntranceMode == 1) listOf("3m", "5m", "7m", "10m") else listOf("고정", "랜덤", "증가", "감소")
        patterns.forEachIndexed { i, label ->
            patternRow.addView(
                darkChoice(label, if (practiceEntranceMode == 1) practiceDistanceM == label.removeSuffix("m").toInt() else practicePatternIndex == i) {
                    if (practiceEntranceMode == 1) practiceDistanceM = label.removeSuffix("m").toInt() else practicePatternIndex = i
                    showPracticeEntrance()
                },
                LinearLayout.LayoutParams(0, sdp(if (compact) 34 else 40), 1f).apply { if (i > 0) marginStart = sdp(5) }
            )
        }
        panel.addView(patternRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(5) })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val countWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        countWrap.addView(sectionLabel("SHOTS"))
        val countRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5, 10, 15, 20).forEachIndexed { i, value ->
            countRow.addView(darkChoice(value.toString(), practiceCount == value) {
                practiceCount = value
                showPracticeEntrance()
            }, LinearLayout.LayoutParams(0, sdp(if (compact) 33 else 39), 1f).apply { if (i > 0) marginStart = sdp(4) })
        }
        countWrap.addView(countRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(4) })
        bottom.addView(countWrap, LinearLayout.LayoutParams(0, -1, 1.35f).apply { marginEnd = sdp(if (compact) 10 else 14) })

        val start = pvButton("연습 시작  →", PvButtonStyle.PRIMARY, textSp = if (compact) 10f else 11.5f, radiusDp = Pv.rLg) {
            openPreStartOrMat(false)
        }
        bottom.addView(start, LinearLayout.LayoutParams(0, sdp(if (compact) 54 else 64), .75f))
        panel.addView(bottom, LinearLayout.LayoutParams(-1, 0, if (practiceEntranceMode == 2) .22f else .30f).apply { topMargin = sdp(if (compact) 6 else 9) })

        body.addView(panel, LinearLayout.LayoutParams(0, -1, .58f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildPracticeGreenPicker(): View {
        val compact = compactLandscape
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14), sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14))
        }
        root.addView(
            buildEntranceHeader("그린 선택", "GREEN LIBRARY", null) { showPracticeEntrance() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(if (compact) 8 else 12) }
        )

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val selected = practiceGreenPresets[practiceGreenPresetIndex]

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pvRounded(Pv.surface, Pv.rXl, Pv.lineSoft)
            setPadding(sdp(if (compact) 12 else 16), sdp(if (compact) 10 else 14), sdp(if (compact) 12 else 16), sdp(if (compact) 10 else 14))
            addView(PracticeGreenPreviewView(this@MainActivity).apply {
                styleIndex = selected.previewStyle
            }, LinearLayout.LayoutParams(-1, 0, .60f))
            addView(TextView(this@MainActivity).apply {
                text = "퍼팅을 연습할\n그린을 선택하세요"
                setTextColor(Pv.textHi)
                textSize = scaledSp(if (compact) 15f else 18f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(0, sdp(10), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "현재 · ${selected.title}\n${selected.subtitle}  ·  BREAK ${if (selected.sideSlopePct >= 0) "+" else ""}${"%.1f".format(selected.sideSlopePct)}%  ·  GRADE ${if (selected.longSlopePct >= 0) "+" else ""}${"%.1f".format(selected.longSlopePct)}%"
                setTextColor(Pv.textMid)
                textSize = scaledSp(if (compact) 6.6f else 7.8f)
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setPadding(0, sdp(7), 0, 0)
            })
        }
        body.addView(info, LinearLayout.LayoutParams(0, -1, .25f).apply { marginEnd = sdp(if (compact) 8 else 12) })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        practiceGreenPresets.chunked(3).forEachIndexed { rowIndex, rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEachIndexed { colIndex, preset ->
                val presetIndex = practiceGreenPresets.indexOf(preset)
                val active = presetIndex == practiceGreenPresetIndex
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = pvRounded(if (active) Color.rgb(29, 58, 42) else Pv.surfaceHi, Pv.rLg, if (active) Pv.primary else Pv.lineSoft)
                    setPadding(sdp(if (compact) 8 else 10), sdp(if (compact) 6 else 8), sdp(if (compact) 8 else 10), sdp(if (compact) 6 else 8))
                    val header = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(this@MainActivity).apply {
                            text = preset.title
                            setTextColor(if (active) Pv.primary else Pv.textHi)
                            textSize = scaledSp(if (compact) 7.5f else 9f)
                            typeface = Typeface.DEFAULT_BOLD
                            includeFontPadding = false
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(TextView(this@MainActivity).apply {
                            text = preset.subtitle
                            setTextColor(Pv.textMid)
                            textSize = scaledSp(if (compact) 6f else 7f)
                            typeface = Typeface.MONOSPACE
                            includeFontPadding = false
                        })
                    }
                    addView(header)
                    addView(PracticeGreenPreviewView(this@MainActivity).apply {
                        styleIndex = preset.previewStyle
                    }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = sdp(5) })
                    addView(TextView(this@MainActivity).apply {
                        text = "BREAK ${if (preset.sideSlopePct >= 0) "+" else ""}${"%.1f".format(preset.sideSlopePct)}%   ·   GRADE ${if (preset.longSlopePct >= 0) "+" else ""}${"%.1f".format(preset.longSlopePct)}%"
                        setTextColor(Pv.textLo)
                        textSize = scaledSp(if (compact) 5.4f else 6.4f)
                        typeface = Typeface.MONOSPACE
                        includeFontPadding = false
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, sdp(4), 0, 0)
                    })
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        practiceGreenPresetIndex = presetIndex
                        showPracticeEntrance()
                    }
                }
                row.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply { if (colIndex > 0) marginStart = sdp(if (compact) 6 else 8) })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { if (rowIndex > 0) topMargin = sdp(if (compact) 6 else 8) })
        }
        body.addView(grid, LinearLayout.LayoutParams(0, -1, .75f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showGameEntrance() {
        replaceMenuScreen(buildGameEntrance()) { showHomeMenu() }
    }

    private fun buildGameEntrance(): View {
    val compact = compactLandscape
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Pv.inkDeep)
        setPadding(sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14), sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14))
    }

    root.addView(
        buildEntranceHeader("게임 세션", "MATCH PLAY", null) { showHomeMenu() },
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(if (compact) 8 else 12) }
    )

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val visual = FrameLayout(this).apply {
        background = pvRounded(Color.rgb(11, 10, 8), Pv.rXl, Pv.lineSoft)
        clipToOutline = true
        addView(CommercialModeVisualView(this@MainActivity, true), FrameLayout.LayoutParams(-1, -1))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(sdp(if (compact) 14 else 18), sdp(12), sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16))
            addView(TextView(this@MainActivity).apply {
                text = when (gameModeIndex) {
                    0 -> "9 HOLE"
                    1 -> "18 HOLE"
                    2 -> "DISTANCE MATCH"
                    else -> "RANDOM BREAK"
                }
                setTextColor(Pv.amber)
                textSize = scaledSp(if (compact) 7f else 8f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = .14f
                includeFontPadding = false
            })
            addView(TextView(this@MainActivity).apply {
                text = when (gameModeIndex) {
                    0 -> "짧고 빠르게\n9홀 승부"
                    1 -> "정식 라운드 느낌의\n18홀 승부"
                    2 -> "목표 거리에 가장\n가깝게 붙이는 게임"
                    else -> "매 샷 달라지는 경사를\n읽고 대응하는 게임"
                }
                setTextColor(Pv.textHi)
                textSize = scaledSp(if (compact) 15.5f else 18.5f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setLineSpacing(sdp(1).toFloat(), 1f)
            })
        }, FrameLayout.LayoutParams(-1, -1))
    }
    body.addView(visual, LinearLayout.LayoutParams(0, -1, .42f).apply { marginEnd = sdp(if (compact) 10 else 14) })

    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surface, Pv.rXl, Pv.lineSoft)
        setPadding(sdp(if (compact) 13 else 17), sdp(if (compact) 10 else 13), sdp(if (compact) 13 else 17), sdp(if (compact) 10 else 13))
    }

    fun label(textValue: String): TextView = TextView(this).apply {
        text = textValue
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 6.2f else 7.2f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
    }

    panel.addView(label("GAME MODE"))
    val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("9홀", "18홀", "거리 맞추기", "랜덤 경사").forEachIndexed { i, text ->
        modeRow.addView(
            darkChoice(text, gameModeIndex == i) {
                gameModeIndex = i
                showGameEntrance()
            },
            LinearLayout.LayoutParams(0, sdp(if (compact) 40 else 46), 1f).apply { if (i > 0) marginStart = sdp(5) }
        )
    }
    panel.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(6) })

    val mid = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val players = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    players.addView(label("PLAYERS"))
    val playerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    (1..4).forEachIndexed { i, value ->
        playerRow.addView(darkChoice(value.toString(), gamePlayers == value) {
            gamePlayers = value
            showGameEntrance()
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 36 else 42), 1f).apply { if (i > 0) marginStart = sdp(4) })
    }
    players.addView(playerRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(6) })
    mid.addView(players, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = sdp(if (compact) 10 else 14) })

    val speed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    speed.addView(label("GREEN SPEED"))
    val speedValue = TextView(this).apply {
        text = "%.1f".format(practiceGreenSpeed)
        setTextColor(Pv.amber)
        textSize = scaledSp(if (compact) 20f else 24f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        includeFontPadding = false
        setPadding(0, sdp(3), 0, 0)
    }
    speed.addView(speedValue)
    speed.addView(SeekBar(this).apply {
        max = 12
        progress = ((practiceGreenSpeed - 2.4) * 10).toInt().coerceIn(0, 12)
        progressTintList = ColorStateList.valueOf(Pv.amber)
        progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
        thumbTintList = ColorStateList.valueOf(Pv.amber)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                practiceGreenSpeed = 2.4 + progress * .1
                speedValue.text = "%.1f".format(practiceGreenSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    })
    mid.addView(speed, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = sdp(if (compact) 10 else 14) })
    panel.addView(mid, LinearLayout.LayoutParams(-1, 0, .32f).apply { topMargin = sdp(if (compact) 8 else 11) })

    val option = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surfaceLo, Pv.rLg, Pv.lineSoft)
        setPadding(sdp(if (compact) 11 else 14), sdp(if (compact) 8 else 10), sdp(if (compact) 11 else 14), sdp(if (compact) 8 else 10))
    }
    if (gameModeIndex == 2) {
        option.addView(label("TARGET DISTANCE"))
        val distanceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(3, 5, 7, 10).forEachIndexed { i, distance ->
            distanceRow.addView(darkChoice("${distance}m", gameDistanceM == distance) {
                gameDistanceM = distance
                showGameEntrance()
            }, LinearLayout.LayoutParams(0, sdp(if (compact) 36 else 42), 1f).apply { if (i > 0) marginStart = sdp(5) })
        }
        option.addView(distanceRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(6) })
    } else {
        option.addView(label("SESSION FORMAT"))
        option.addView(TextView(this).apply {
            text = when (gameModeIndex) {
                0 -> "9개 홀 · 홀마다 거리와 경사 자동 구성 · 빠른 대결"
                1 -> "18개 홀 · 풀 라운드 구성 · 누적 스코어 경쟁"
                else -> "매 샷 거리와 경사가 랜덤으로 바뀌는 읽기 테스트"
            }
            setTextColor(Pv.textMid)
            textSize = scaledSp(if (compact) 8f else 9f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    panel.addView(option, LinearLayout.LayoutParams(-1, 0, .27f).apply { topMargin = sdp(if (compact) 7 else 10) })

    val bottom = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    bottom.addView(TextView(this).apply {
        text = "${gamePlayers} PLAYER  ·  ${when (gameModeIndex) { 0 -> "9 HOLE"; 1 -> "18 HOLE"; 2 -> "DISTANCE"; else -> "RANDOM" }}"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 7f else 8.2f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
    }, LinearLayout.LayoutParams(0, -2, 1f))
    bottom.addView(pvButton("게임 시작  →", PvButtonStyle.AMBER, textSp = if (compact) 10f else 11.5f, radiusDp = Pv.rLg) {
        openPreStartOrMat(true)
    }, LinearLayout.LayoutParams(0, sdp(if (compact) 58 else 68), .72f))
    panel.addView(bottom, LinearLayout.LayoutParams(-1, 0, .25f).apply { topMargin = sdp(if (compact) 7 else 10) })

    body.addView(panel, LinearLayout.LayoutParams(0, -1, .58f))
    root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    return root
}

    private fun showCupGuideScreen(backAction: () -> Unit = { showPracticeEntrance() }) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(18), sdp(14), sdp(18), sdp(12))
        }
        root.addView(buildEntranceHeader("컵 가이드", "", null, backAction), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })
        val panels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chart = sectionPanel().apply {
            gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = "③  ─────────────●\n\n②  ────────────●\n\n①  ───────────●\n\n     Ready Line"
                textSize = scaledSp(15f)
                typeface = Typeface.MONOSPACE
                setTextColor(Pv.textHi)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -1))
        }
        panels.addView(chart, LinearLayout.LayoutParams(0, -1, 0.57f).apply { marginEnd = dp(8) })
        val table = sectionPanel()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val quadrantColors = listOf(Pv.danger, Pv.info, Pv.amber)
        val quadrantInk = listOf(Pv.textHi, Pv.textHi, Pv.amberInk)
        listOf("", "①", "②", "③").forEachIndexed { i, t ->
            header.addView(TextView(this@MainActivity).apply {
                text = t; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                if (i > 0) {
                    setTextColor(quadrantInk[i - 1])
                    background = pvRounded(quadrantColors[i - 1], Pv.rSm)
                } else {
                    setTextColor(Pv.textMid)
                }
            }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { if (i > 0) marginStart = dp(3) })
        }
        table.addView(header)
        val rows = listOf(
            listOf("3m", "1컵반", "3컵", "4컵반"),
            listOf("5m", "2컵반", "5컵", "7컵반"),
            listOf("7m", "3컵반", "7컵", "10컵반"),
            listOf("10m", "5컵", "10컵", "16컵반")
        )
        rows.forEach { rr ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rr.forEachIndexed { i, t ->
                r.addView(TextView(this@MainActivity).apply {
                    text = t; gravity = Gravity.CENTER; textSize = scaledSp(10f); typeface = Typeface.DEFAULT_BOLD
                    if (i > 0) {
                        setTextColor(quadrantInk[i - 1])
                        background = pvRounded(quadrantColors[i - 1], Pv.rSm)
                    } else {
                        setTextColor(Pv.textHi)
                    }
                }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (i > 0) marginStart = dp(3) })
            }
            table.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }
        table.addView(TextView(this).apply {
            text = "※ 1클럽 = 6컵"; gravity = Gravity.CENTER; textSize = scaledSp(11.5f); typeface = Typeface.DEFAULT_BOLD; setTextColor(Pv.textLo); setPadding(0, dp(8), 0, 0)
        })
        panels.addView(table, LinearLayout.LayoutParams(0, -1, 0.43f))
        root.addView(panels, LinearLayout.LayoutParams(-1, 0, 1f))
        replaceMenuScreen(root, backAction)
    }

    private fun openPreStartOrMat(game: Boolean) {
        if (uiPrefs.getString("skip_prestart_date", null) == LocalDate.now().toString()) {
            showMatPrep(game)
        } else {
            showPreStartGuide(game)
        }
    }

    private fun skipPreStartGuideToday() {
        uiPrefs.edit().putString("skip_prestart_date", LocalDate.now().toString()).apply()
    }

    private fun showPreStartGuide(game: Boolean) {
    val compact = compactLandscape
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Pv.inkDeep)
        setPadding(sdp(if (compact) 18 else 26), sdp(if (compact) 13 else 18), sdp(if (compact) 18 else 26), sdp(if (compact) 12 else 16))
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    copy.addView(TextView(this@MainActivity).apply {
        text = "세션 준비"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 18f else 22f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    copy.addView(TextView(this@MainActivity).apply {
        text = "SETUP CHECK  ·  10초면 끝납니다"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 6.8f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
    })
    header.addView(copy)
    header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    header.addView(pvButton("← 뒤로", PvButtonStyle.GHOST, textSp = if (compact) 7.5f else 8.5f, radiusDp = 100f) {
        if (game) showGameEntrance() else showPracticeEntrance()
    }, LinearLayout.LayoutParams(sdp(if (compact) 76 else 88), sdp(if (compact) 32 else 36)))
    root.addView(header)

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val diagram = FrameLayout(this).apply {
        background = pvRounded(Pv.surface, Pv.rXl, Pv.lineSoft)
        setPadding(sdp(if (compact) 10 else 14), sdp(if (compact) 10 else 14), sdp(if (compact) 10 else 14), sdp(if (compact) 10 else 14))
        addView(CommercialSetupDiagramView(this@MainActivity), FrameLayout.LayoutParams(-1, -1))
    }
    body.addView(diagram, LinearLayout.LayoutParams(0, -1, .55f).apply { marginEnd = sdp(if (compact) 12 else 18) })

    val checklist = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    checklist.addView(TextView(this).apply {
        text = "측정 전에 이것만 확인하세요"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 15f else 18f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })

    fun check(number: String, title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surface, Pv.rLg, Pv.lineSoft)
        setPadding(sdp(if (compact) 10 else 13), sdp(if (compact) 7 else 9), sdp(if (compact) 10 else 13), sdp(if (compact) 7 else 9))
        addView(TextView(this@MainActivity).apply {
            text = number
            setTextColor(Pv.primaryInk)
            setBackgroundColor(Pv.primary)
            textSize = scaledSp(if (compact) 8f else 9f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(sdp(if (compact) 28 else 32), sdp(if (compact) 28 else 32)))
        val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(sdp(9), 0, 0, 0) }
        c.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 9f else 10f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        c.addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textMid)
            textSize = scaledSp(if (compact) 6.8f else 7.8f)
            includeFontPadding = false
            maxLines = 1
        })
        addView(c, LinearLayout.LayoutParams(0, -2, 1f))
    }

    checklist.addView(check("01", "마커 4개가 모두 보이게", "프레임 모서리에서 마커가 잘리지 않게 맞춥니다."), LinearLayout.LayoutParams(-1, sdp(if (compact) 53 else 61)).apply { topMargin = sdp(if (compact) 10 else 14) })
    checklist.addView(check("02", "공과 퍼터가 충분히 밝게", "역광보다 실내 조명을 켜는 편이 인식이 안정적입니다."), LinearLayout.LayoutParams(-1, sdp(if (compact) 53 else 61)).apply { topMargin = sdp(7) })
    checklist.addView(check("03", "폰은 세션 중 움직이지 않게", "자동 캘리브레이션 후 위치가 바뀌면 다시 CAL을 실행합니다."), LinearLayout.LayoutParams(-1, sdp(if (compact) 53 else 61)).apply { topMargin = sdp(7) })

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    actions.addView(pvButton("오늘 다시 보지 않기", PvButtonStyle.GHOST, textSp = if (compact) 7.6f else 8.8f, radiusDp = Pv.rLg) {
        skipPreStartGuideToday()
        showMatPrep(game)
    }, LinearLayout.LayoutParams(0, sdp(if (compact) 42 else 48), 1f).apply { marginEnd = sdp(5) })
    actions.addView(pvButton("다음  →", PvButtonStyle.PRIMARY, textSp = if (compact) 8.5f else 9.8f, radiusDp = Pv.rLg) {
        showMatPrep(game)
    }, LinearLayout.LayoutParams(0, sdp(if (compact) 42 else 48), 1f).apply { marginStart = sdp(5) })
    checklist.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(if (compact) 10 else 14) })

    body.addView(checklist, LinearLayout.LayoutParams(0, -1, .45f))
    root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = sdp(if (compact) 10 else 14) })
    replaceMenuScreen(root) { if (game) showGameEntrance() else showPracticeEntrance() }
}

    private fun showMatPrep(game: Boolean) {
    val compact = compactLandscape
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Pv.inkDeep)
        setPadding(sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14), sdp(if (compact) 14 else 20), sdp(if (compact) 10 else 14))
    }

    root.addView(
        buildEntranceHeader("카메라 맞추기", "CAMERA ALIGNMENT", null) { if (game) showGameEntrance() else showPracticeEntrance() },
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(if (compact) 8 else 12) }
    )

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val previewCard = FrameLayout(this).apply {
        background = pvRounded(Color.rgb(5, 8, 10), Pv.rXl, Pv.line)
        clipToOutline = true
    }
    previewCard.addView(CommercialSetupDiagramView(this), FrameLayout.LayoutParams(-1, -1))
    previewCard.addView(TextView(this).apply {
        text = "LIVE CAMERA ALIGNMENT"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 7f else 8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
        background = pvRounded(Color.argb(205, 6, 9, 12), 100f, Pv.line)
        setPadding(sdp(10), sdp(4), sdp(10), sdp(4))
    }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { leftMargin = sdp(10); topMargin = sdp(10) })
    body.addView(previewCard, LinearLayout.LayoutParams(0, -1, .62f).apply { marginEnd = sdp(if (compact) 10 else 14) })

    val right = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surface, Pv.rXl, Pv.lineSoft)
        setPadding(sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16), sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16))
    }
    right.addView(TextView(this).apply {
        text = "프레임 안에 4개 마커와 공이\n모두 들어오면 준비 완료입니다."
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 13.5f else 16f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(sdp(1).toFloat(), 1f)
    })

    fun status(dot: Int, title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(this@MainActivity).apply { background = pvRounded(dot, 100f) }, LinearLayout.LayoutParams(sdp(6), sdp(6)).apply { marginEnd = sdp(8) })
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 8.2f else 9.2f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textLo)
            textSize = scaledSp(if (compact) 6.2f else 7.2f)
            includeFontPadding = false
            maxLines = 1
        })
        addView(copy)
    }

    right.addView(status(Pv.primary, "MARKERS", "4개 자동 인식 후 캘리브레이션"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(if (compact) 14 else 18) })
    right.addView(status(Pv.info, "CAMERA", "HFR 가능 시 240fps 우선"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(9) })
    right.addView(status(Pv.amber, "TV", "연결되어 있으면 자동으로 시뮬레이터 출력"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(9) })

    right.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) }, LinearLayout.LayoutParams(-1, sdp(1)).apply { topMargin = sdp(if (compact) 13 else 17); bottomMargin = sdp(if (compact) 12 else 16) })

    right.addView(TextView(this).apply {
        text = if (game) "MATCH PLAY READY" else "PRACTICE READY"
        setTextColor(if (game) Pv.amber else Pv.primary)
        textSize = scaledSp(if (compact) 7f else 8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .14f
        includeFontPadding = false
    })
    right.addView(TextView(this).apply {
        text = if (game) "${gamePlayers}인 · ${when (gameModeIndex) { 0 -> "9홀"; 1 -> "18홀"; 2 -> "거리 맞추기"; else -> "랜덤 경사" }}" else "${practiceCount}구 · ${practiceDistanceM}m · Green ${"%.1f".format(practiceGreenSpeed)}"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 8f else 9f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        setPadding(0, sdp(4), 0, 0)
    })

    right.addView(pvButton("측정 시작  →", if (game) PvButtonStyle.AMBER else PvButtonStyle.PRIMARY, textSp = if (compact) 10f else 11.5f, radiusDp = Pv.rLg) {
        startConfiguredSession(game)
    }, LinearLayout.LayoutParams(-1, sdp(if (compact) 54 else 62)).apply { topMargin = sdp(if (compact) 14 else 18) })

    body.addView(right, LinearLayout.LayoutParams(0, -1, .38f))
    root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    replaceMenuScreen(root) { if (game) showGameEntrance() else showPracticeEntrance() }
}

    private fun startConfiguredSession(game: Boolean) {
        activeSessionIsGame = game
        sessionActive = true
        measurementSuspended = false
        practiceShotsTaken = 0
        practicePatternShotIndex = 0

        engine.settings.holeDistanceM = if (game) gameDistanceM.toDouble() else practiceDistanceM.toDouble()
        engine.settings.stimpMeters = practiceGreenSpeed
        engine.settings.terrainProfileId = if (!game && practiceEntranceMode == 2) practiceGreenPresets[practiceGreenPresetIndex].previewStyle else -1
        engine.gameModes.configurePlayers(if (game) gamePlayers else 1)
        if (game) {
            val mode = when (gameModeIndex) {
                0 -> PracticeMode.NINE_HOLE
                1 -> PracticeMode.EIGHTEEN_HOLE
                2 -> PracticeMode.DISTANCE
                else -> PracticeMode.RANDOM_SLOPE
            }
            engine.gameModes.setMode(mode)
        } else {
            engine.gameModes.setMode(PracticeMode.PRACTICE)
        }

        modeButton.text = engine.gameModes.status.mode.label
        metricText.text = "${engine.gameModes.status.mode.label} · READY"
        updateSettingLabels()
        menuBackAction = null
        menuOverlay.isClickable = false
        menuOverlay.visibility = View.GONE

        // Measurement/recording starts only after explicit entry. If camera permission/provider
        // is not ready yet, request/initialize it instead of leaving a dead measurement screen.
        if (!::provider.isInitialized) {
            metricText.text = "카메라 준비 중 · 권한을 확인하세요"
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) openProvider() else permission.launch(Manifest.permission.CAMERA)
            return
        }
        if (homography != null) {
            mainHandler.post { armPrecision() }
        } else {
            beginAutoCalibration()
        }
    }

    private fun suspendMeasurementForOverlay() {
        measurementSuspended = true
        cancelPendingAuto()
        stopSimulation()
        impactPolling = false
        tracker.cancel()
        stopHfrRecordingOnly()
    }

    private fun pauseSessionForMenu() {
        sessionActive = false
        suspendMeasurementForOverlay()
    }

    private fun showHomeMenu() {
        if (sessionActive) pauseSessionForMenu()
        replaceMenuScreen(buildVideoHomeScreen(), null)
    }

    private fun applyPracticeTargetForNextShot() {
        if (activeSessionIsGame || engine.gameModes.status.mode != PracticeMode.PRACTICE) return

        engine.settings.stimpMeters = practiceGreenSpeed
        val base = practiceDistanceM.toDouble()
        when (practiceEntranceMode) {
            0 -> {
                engine.settings.terrainProfileId = -1
                engine.settings.sideSlopePct = 0.0
                engine.settings.longSlopePct = 0.0
                engine.settings.holeDistanceM = when (practicePatternIndex) {
                    1 -> practiceRandom.nextInt(20, 151) / 10.0
                    2 -> (base + practicePatternShotIndex).coerceIn(2.0, 15.0)
                    3 -> (base - practicePatternShotIndex).coerceIn(2.0, 15.0)
                    else -> base
                }
            }
            1 -> {
                engine.settings.terrainProfileId = -1
                engine.settings.holeDistanceM = base
                engine.settings.sideSlopePct = 0.0
                engine.settings.longSlopePct = 0.0
            }
            else -> {
                val preset = practiceGreenPresets[practiceGreenPresetIndex]
                engine.settings.terrainProfileId = preset.previewStyle
                engine.settings.holeDistanceM = when (practicePatternIndex) {
                    1 -> practiceRandom.nextInt(20, 151) / 10.0
                    2 -> (base + practicePatternShotIndex).coerceIn(2.0, 15.0)
                    3 -> (base - practicePatternShotIndex).coerceIn(2.0, 15.0)
                    else -> base
                }
                engine.settings.sideSlopePct = preset.sideSlopePct
                engine.settings.longSlopePct = preset.longSlopePct
            }
        }
    }

    private fun onSessionShotFinished() {
        if (!sessionActive || activeSessionIsGame) return
        practiceShotsTaken++
        practicePatternShotIndex++
        if (practiceShotsTaken >= practiceCount) {
            cancelPendingAuto()
            overlay.status = "연습 완료 · ${practiceShotsTaken}/${practiceCount}"
            overlay.invalidate()
        }
    }

    private fun shouldContinueAutoAfterResult(): Boolean {
        if (!sessionActive) return false
        return if (activeSessionIsGame) {
            !engine.gameModes.status.completed
        } else {
            practiceShotsTaken < practiceCount
        }
    }

    private fun showSettingsDialog() {
    if (settingsDialog?.isShowing == true) return
    if (engine.state?.running == true) {
        toast("공이 멈춘 뒤 설정을 열어주세요")
        return
    }

    val wasActiveSession = sessionActive
    if (wasActiveSession) suspendMeasurementForOverlay()
    var resumeOnDismiss = wasActiveSession

    fun closeThen(resumeAfter: Boolean = false, block: () -> Unit) {
        resumeOnDismiss = resumeAfter
        settingsDialog?.dismiss()
        settingsDialog = null
        block()
    }

    val compact = compactLandscape
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pvDp(if (compact) 4 else 8), 0, pvDp(if (compact) 4 else 8), 0)
    }

    shell.addView(TextView(this).apply {
        text = "SESSION CONTROL"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 7f else 8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .14f
        includeFontPadding = false
    })
    shell.addView(TextView(this).apply {
        text = "그린 환경과 시스템 도구"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 15f else 18f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setPadding(0, pvDp(3), 0, pvDp(if (compact) 8 else 11))
    })

    val columns = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
    }

    fun fieldLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 8f else 9f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    fun sliderRow(labelView: TextView, seekBar: SeekBar): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.lineSoft)
        setPadding(pvDp(if (compact) 10 else 12), pvDp(if (compact) 7 else 9), pvDp(if (compact) 10 else 12), pvDp(if (compact) 4 else 6))
        addView(labelView)
        addView(seekBar, LinearLayout.LayoutParams(-1, pvDp(if (compact) 28 else 34)))
    }

    fun seek(maxV: Int, progressV: Int, accent: Int = Pv.primary, onChange: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        max = maxV
        progress = progressV
        progressTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
        thumbTintList = ColorStateList.valueOf(accent)
        setOnSeekBarChangeListener(simpleSeek(onChange))
    }

    val env = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    env.addView(TextView(this).apply {
        text = "GREEN CONDITIONS"
        setTextColor(Pv.textLo)
        textSize = pvSp(if (compact) 6f else 7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
    })

    val speed = fieldLabel("그린 스피드   ${"%.1f".format(engine.settings.stimpMeters)}")
    env.addView(sliderRow(speed, seek(20, ((engine.settings.stimpMeters - 2.0) * 10).toInt().coerceIn(0, 20)) {
        engine.settings.stimpMeters = 2.0 + it / 10.0
        speed.text = "그린 스피드   ${"%.1f".format(engine.settings.stimpMeters)}"
        updateSettingLabels()
    }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })

    val distance = fieldLabel("홀 거리   ${"%.1f".format(engine.settings.holeDistanceM)} m")
    env.addView(sliderRow(distance, seek(140, ((engine.settings.holeDistanceM - 1.0) * 10).toInt().coerceIn(0, 140)) {
        engine.settings.holeDistanceM = 1.0 + it / 10.0
        distance.text = "홀 거리   ${"%.1f".format(engine.settings.holeDistanceM)} m"
        updateSettingLabels()
    }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })

    val side = fieldLabel("좌우 경사   ${"%+.1f".format(engine.settings.sideSlopePct)} %")
    env.addView(sliderRow(side, seek(100, (engine.settings.sideSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
        engine.settings.sideSlopePct = (it - 50) / 10.0
        side.text = "좌우 경사   ${"%+.1f".format(engine.settings.sideSlopePct)} %"
        updateSettingLabels()
    }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })

    val grade = fieldLabel("오르막 / 내리막   ${"%+.1f".format(engine.settings.longSlopePct)} %")
    env.addView(sliderRow(grade, seek(100, (engine.settings.longSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
        engine.settings.longSlopePct = (it - 50) / 10.0
        grade.text = "오르막 / 내리막   ${"%+.1f".format(engine.settings.longSlopePct)} %"
        updateSettingLabels()
    }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })

    columns.addView(env, LinearLayout.LayoutParams(0, -2, 1.22f).apply { marginEnd = pvDp(if (compact) 8 else 12) })

    val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    tools.addView(TextView(this).apply {
        text = "SYSTEM"
        setTextColor(Pv.textLo)
        textSize = pvSp(if (compact) 6f else 7f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .12f
        includeFontPadding = false
    })

    fun tool(kicker: String, title: String, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
        setPadding(pvDp(if (compact) 10 else 12), pvDp(if (compact) 6 else 8), pvDp(if (compact) 8 else 10), pvDp(if (compact) 6 else 8))
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this@MainActivity).apply {
            text = kicker
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 5.6f else 6.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 8f else 9.2f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "›"
            setTextColor(Pv.primary)
            textSize = pvSp(if (compact) 18f else 21f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        })
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    tools.addView(tool("NAVIGATION", "메인 메뉴") { closeThen { showHomeMenu() } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("ANALYTICS", "샷 기록 / STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DISPLAY", "TV 다시 연결") { displayController.refresh() }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("TRAINING", "컵 가이드") {
        closeThen {
            if (sessionActive) {
                suspendMeasurementForOverlay()
                showCupGuideScreen {
                    menuBackAction = null
                    menuOverlay.isClickable = false
                    menuOverlay.visibility = View.GONE
                    mainHandler.post { armPrecision() }
                }
            } else {
                showCupGuideScreen { showHomeMenu() }
            }
        }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("SOFTWARE", "업데이트 확인") {
        closeThen {
            if (wasActiveSession) showHomeMenu()
            appUpdater.check(silent = false)
        }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DEVELOPER", "ZIP 배포") {
        closeThen {
            if (wasActiveSession) showHomeMenu()
            startActivity(Intent(this, DeployActivity::class.java))
        }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })

    columns.addView(tools, LinearLayout.LayoutParams(0, -2, .78f))
    shell.addView(columns)

    val dialog = pvDialog(title = "설정", content = shell, dismissLabel = "완료")
    settingsDialog = dialog
    dialog.setOnDismissListener {
        if (settingsDialog === dialog) settingsDialog = null
        if (resumeOnDismiss && sessionActive && menuOverlay.visibility != View.VISIBLE) {
            mainHandler.post { armPrecision() }
        }
    }
    dialog.show()
}

    private fun updateMetricCards(m: ShotMetrics) {
        fun set(key: String, value: String) { metricCards[key]?.text = value }
        set("ball", m.ballSpeedMps?.let { "%.2f m/s".format(it) } ?: "--")
        set("launch", "%+.2f°".format(m.launchAngleDeg))
        set("head", m.headSpeedMps?.let { "%.2f m/s".format(it) } ?: "--")
        set("face", m.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--")
        set("path", m.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--")
        set("f2p", m.faceToPathDeg?.let { "%+.2f°".format(it) } ?: "--")
        set("impact", m.impactOffsetMm?.let { "%+.1f mm".format(it) } ?: "--")
        set("smash", m.smash?.let { "%.2f".format(it) } ?: "--")
        set("tempo", m.tempoRatio?.let { "%.2f:1".format(it) } ?: "--")
    }

    private fun uiScale(): Float {
        val dm = resources.displayMetrics
        val shortestDp = min(dm.widthPixels / dm.density, dm.heightPixels / dm.density)
        return when {
            shortestDp < 360f -> 0.82f
            shortestDp < 420f -> 0.90f
            shortestDp < 520f -> 0.96f
            else -> 1.0f
        }
    }

    private fun scaledSp(value: Float): Float = value * uiScale()

    private fun sdp(value: Int): Int =
        (value * uiScale() * resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun setTvStatus(connected: Boolean, detail: String) {
        lastTvStatusMessage = detail
        if (!::tvStatus.isInitialized) return
        tvStatus.text = if (connected) "● TV 연결" else "○ TV 미연결"
        tvStatus.setTextColor(if (connected) Pv.primary else Pv.textMid)
    }

    private fun compactHfrText(detail: String): String {
        val fps = Regex("(\\d{2,3})fps").find(detail)?.groupValues?.getOrNull(1)
        return when {
            detail.contains("RECORDING", ignoreCase = true) -> if (fps != null) "● ${fps}fps REC" else "● HFR REC"
            detail.contains("분석") -> if (fps != null) "HFR ${fps}fps 분석" else "HFR 분석중"
            detail.contains("실패") || detail.contains("ERROR", ignoreCase = true) -> "HFR 오류"
            detail.contains("미지원") -> "HFR 미지원"
            detail.contains("준비") || detail.contains("READY", ignoreCase = true) -> if (fps != null) "HFR ${fps}fps" else "HFR 준비"
            detail.startsWith("✓") -> if (fps != null) "✓ ${fps}fps" else "✓ HFR"
            else -> detail.take(18)
        }
    }

    private fun setHfrStatus(short: String, detail: String = short) {
        lastHfrStatusMessage = detail
        if (!::hfrStatus.isInitialized) return
        hfrStatus.text = short
    }

    private fun updateAutoButton() {
        if (!::autoButton.isInitialized) {
            return
        }

        autoButton.text =
            if (autoPlayEnabled) {
                "AUTO ON"
            } else {
                "AUTO OFF"
            }

        autoButton.background = pvRounded(
            if (autoPlayEnabled) Pv.primary else Pv.surfaceHi,
            100f,
            if (autoPlayEnabled) Color.TRANSPARENT else Pv.line
        )
        autoButton.setTextColor(
            if (autoPlayEnabled) Pv.primaryInk else Pv.textMid
        )
    }

    private fun settingLabel(): TextView =
        TextView(this).apply {
            setTextColor(Pv.textHi)
            textSize = 11.5f
        }

    private fun simpleSeek(
        onChange: (Int) -> Unit
    ) =
        object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                onChange(
                    progress
                )
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?
            ) {
            }

            override fun onStopTrackingTouch(
                seekBar: SeekBar?
            ) {
            }
        }

    private fun updateSettingLabels() {
        if (::speedLabel.isInitialized) {
            speedLabel.text = "그린스피드 ${"%.1f".format(engine.settings.stimpMeters)}m"
        }
        if (::distanceLabel.isInitialized) {
            distanceLabel.text = "홀 거리 ${"%.1f".format(engine.settings.holeDistanceM)}m"
        }
        if (::sideLabel.isInitialized) {
            sideLabel.text = "좌우경사 ${"%+.1f".format(engine.settings.sideSlopePct)}%"
        }
        if (::longLabel.isInitialized) {
            longLabel.text = "종경사 ${"%+.1f".format(engine.settings.longSlopePct)}%"
        }
        if (::settingSummary.isInitialized) {
            settingSummary.text =
                "${engine.gameModes.status.mode.label}  ·  ${"%.1f".format(engine.settings.holeDistanceM)}m  ·  GREEN ${"%.1f".format(engine.settings.stimpMeters)}  ·  LR ${"%+.1f".format(engine.settings.sideSlopePct)}%  FB ${"%+.1f".format(engine.settings.longSlopePct)}%"
        }
    }

    private fun openProvider() {
        if (cameraProviderOpening || ::provider.isInitialized) {
            if (::provider.isInitialized) beginAutoCalibration()
            return
        }
        cameraProviderOpening = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                cameraProviderOpening = false
                runCatching { future.get() }
                    .onSuccess { cameraProvider ->
                        provider = cameraProvider
                        beginAutoCalibration()
                    }
                    .onFailure { error ->
                        toast("카메라 초기화 실패: ${error.message ?: "알 수 없는 오류"}")
                        if (sessionActive) showHomeMenu()
                    }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun beginAutoCalibration() {
        if (!::provider.isInitialized) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                toast("카메라 준비 중")
                openProvider()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
            return
        }

        cancelPendingAuto()

        stopSimulation()
        stopHfrRecordingOnly()

        provider.unbindAll()

        homography = null
        tracker.cancel()
        engine.resetSimulation()

        val preview =
            Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }

        analysis =
            ImageAnalysis.Builder()
                .setTargetResolution(
                    Size(
                        640,
                        480
                    )
                )
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

        calibrator?.close()

        calibrator =
            AutoCalibrator(
                onStatus = { msg ->
                    runOnUiThread {
                        overlay.status =
                            msg

                        overlay.invalidate()
                    }
                },
                onCalibrated = { result ->
                    runOnUiThread {
                        homography =
                            result.homography

                        overlay.calibrationImagePoints =
                            result.imagePoints

                        overlay.status =
                            "AUTO CAL OK · AUTO READY"

                        metricText.text =
                            "자동캘 완료 · 폰/마커 안 움직이면 이후 손댈 거 없음"

                        overlay.invalidate()

                        installNormalAnalyzer(
                            result.homography
                        )

                        maybeAutoStartAfterCalibration()
                    }
                }
            )

        analysis.setAnalyzer(
            cameraExecutor,
            calibrator!!
        )

        try {
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

            overlay.status =
                "QR 마커 4개 자동 인식중"

            overlay.invalidate()
        } catch (t: Throwable) {
            toast(
                "카메라 실패: ${t.message}"
            )
        }
    }

    private fun installNormalAnalyzer(
        h: Homography
    ) {
        analysis.clearAnalyzer()

        calibrator?.close()
        calibrator = null

        val analyzer =
            ShotVisionAnalyzer(
                homography = h,
                tracker = tracker,
                onOverlay = { visual ->
                    runOnUiThread {
                        if (!sessionActive || measurementSuspended) return@runOnUiThread
                        overlay.lastOverlay = visual
                        overlay.invalidate()
                    }
                },
                onShotReady = { metrics ->
                    runOnUiThread {
                        if (!sessionActive || measurementSuspended) return@runOnUiThread
                        handleMeasuredShot(
                            metrics = metrics,
                            replay = null,
                            source = "NORMAL"
                        )
                    }
                }
            )

        analysis.setAnalyzer(
            cameraExecutor,
            analyzer
        )
    }

    private fun maybeAutoStartAfterCalibration() {
        if (
            !sessionActive ||
            measurementSuspended ||
            !autoPlayEnabled ||
            homography == null ||
            !::provider.isInitialized
        ) {
            return
        }

        val generation =
            ++autoGeneration

        fun waitProbe(
            attempts: Int
        ) {
            if (
                !autoPlayEnabled ||
                measurementSuspended ||
                generation != autoGeneration
            ) {
                return
            }

            if (
                hfrProbeComplete ||
                attempts <= 0
            ) {
                mainHandler.postDelayed(
                    {
                        if (
                            autoPlayEnabled &&
                            !measurementSuspended &&
                            generation == autoGeneration
                        ) {
                            armPrecision()
                        }
                    },
                    650L
                )
            } else {
                mainHandler.postDelayed(
                    {
                        waitProbe(
                            attempts - 1
                        )
                    },
                    200L
                )
            }
        }

        waitProbe(
            10
        )
    }

    private fun armPrecision() {
        if (!sessionActive) {
            toast("먼저 연습장/게임장에 입장하세요")
            return
        }
        if (!activeSessionIsGame && practiceShotsTaken >= practiceCount) {
            toast("${practiceCount}구 연습 완료 · 메뉴에서 새 세션을 시작하세요")
            return
        }
        if (
            !::provider.isInitialized ||
            homography == null
        ) {
            toast(
                "자동 캘리브레이션 먼저"
            )

            return
        }

        measurementSuspended = false
        stopSimulation()

        engine.gameModes
            .prepareNextIfNeeded()

        if (activeSessionIsGame && gameModeIndex == 2) {
            engine.settings.holeDistanceM = gameDistanceM.toDouble()
            engine.settings.sideSlopePct = 0.0
        }
        applyPracticeTargetForNextShot()
        updateSettingLabels()

        engine.resetSimulation()
        tracker.cancel()

        impactDetected = false
        impactDetector.reset()

        if (!hfrHardwareAvailable) {
            tracker.arm()

            overlay.status =
                "NORMAL AUTO READY · HFR 미지원"

            metricText.text =
                "HFR 미지원이라 일반 추적. 공 놓고 그냥 치면 됨."

            overlay.invalidate()

            return
        }

        overlay.status =
            "240/120fps PRECISION 준비중"

        metricText.text =
            "240fps 우선 → 120fps fallback · 자동 녹화 준비"

        overlay.invalidate()

        if (::analysis.isInitialized) {
            analysis.clearAnalyzer()
        }

        calibrator?.close()
        calibrator = null

        val existing =
            hfrController

        if (
            existing != null &&
            existing.fps() >= 120
        ) {
            startHfrRecording()
            return
        }

        existing?.close()

        val controller =
            HighSpeedCaptureController(
                context = this,
                lifecycleOwner = this,
                provider = provider,
                previewView = previewView,
                callbackExecutor =
                    ContextCompat.getMainExecutor(
                        this
                    ),
                status = { msg ->
                    runOnUiThread {
                        setHfrStatus(compactHfrText(msg), msg)
                        overlay.status = msg
                        overlay.invalidate()
                    }
                }
            )

        hfrController =
            controller

        val session =
            controller.bindBest()

        if (session == null) {
            hfrHardwareAvailable = false

            setHfrStatus("HFR 오류", "HFR 바인딩 실패 · NORMAL fallback")

            beginAutoCalibration()

            return
        }

        setHfrStatus("HFR ${session.fps}fps", "PRECISION ${session.fps}fps 준비")

        mainHandler.postDelayed(
            {
                startHfrRecording()
            },
            420L
        )
    }

    private fun startHfrRecording() {
        if (!sessionActive || measurementSuspended) return
        val controller =
            hfrController ?: return

        if (
            controller.isRecording()
        ) {
            return
        }

        impactDetected = false
        impactPolling = false

        impactDetector.reset()
        val recordingGeneration = ++hfrRecordingGeneration

        controller.start(
            onStart = { _, fps ->
                runOnUiThread {
                    if (recordingGeneration != hfrRecordingGeneration || !sessionActive || measurementSuspended) {
                        controller.stop()
                        return@runOnUiThread
                    }
                    recordingStartedAtMs =
                        System.currentTimeMillis()

                    overlay.status =
                        "● ${fps}fps AUTO READY · 퍼팅하셈"

                    metricText.text =
                        "공/퍼터 자동감지 · 임팩트 후 자동 종료/분석"

                    overlay.invalidate()

                    impactPolling = true
                    pollImpact()
                }
            },
            onFinalize = { file, fps, error ->
                runOnUiThread {
                    if (recordingGeneration != hfrRecordingGeneration) {
                        runCatching { file?.delete() }
                        return@runOnUiThread
                    }
                    impactPolling = false

                    if (!sessionActive || measurementSuspended) {
                        runCatching { file?.delete() }
                        return@runOnUiThread
                    }

                    if (
                        error != null ||
                        file == null
                    ) {
                        setHfrStatus("HFR 오류", "HFR 저장 실패")

                        metricText.text =
                            error?.message
                                ?: "고속영상 파일 없음"

                        overlay.status =
                            "HFR ERROR"

                        overlay.invalidate()

                        scheduleAutoRetry(
                            700L
                        )

                        return@runOnUiThread
                    }

                    if (!impactDetected) {
                        try {
                            file.delete()
                        } catch (_: Throwable) {
                        }

                        overlay.status =
                            "공 대기 · AUTO 재시작"

                        overlay.invalidate()

                        scheduleAutoRetry(
                            350L
                        )

                        return@runOnUiThread
                    }

                    overlay.status =
                        "HFR ${fps}fps 정밀분석"

                    metricText.text =
                        "임팩트/템포/매트마찰/리플레이 계산중..."

                    overlay.invalidate()

                    analyzeHfr(
                        file,
                        fps
                    )
                }
            }
        )
    }

    private fun pollImpact() {
        if (!sessionActive || measurementSuspended) return
        val controller =
            hfrController ?: return

        if (
            !impactPolling ||
            !controller.isRecording()
        ) {
            return
        }

        val elapsed =
            System.currentTimeMillis() -
                recordingStartedAtMs

        if (
            !impactDetected &&
            elapsed > 250
        ) {
            if (
                impactDetector.sampleMoved()
            ) {
                impactDetected = true

                overlay.status =
                    "IMPACT · +700ms HFR 캡처"

                overlay.invalidate()

                mainHandler.postDelayed(
                    {
                        hfrController?.stop()
                    },
                    700L
                )

                return
            }
        }

        if (
            elapsed >= 8000
        ) {
            overlay.status =
                "공 대기 TIMEOUT · 자동 재시작"

            metricText.text =
                "8초 동안 퍼팅 없음 · 다시 감시"

            overlay.invalidate()

            controller.stop()

            return
        }

        mainHandler.postDelayed(
            {
                pollImpact()
            },
            26L
        )
    }

    private fun analyzeHfr(
        file: File,
        fps: Int
    ) {
        cameraExecutor.execute {
            val result =
                hfrAnalyzer.analyze(
                    file = file,
                    requestedFps = fps,
                    onProgress = { progress ->
                        runOnUiThread {
                            setHfrStatus(compactHfrText(progress), progress)
                        }
                    }
                )

            val replay =
                if (result != null) {
                    ImpactReplayExtractor.extract(
                        file = file,
                        impactFrame =
                            result.impactFrame,
                        captureFps =
                            result.fps
                    )
                } else {
                    null
                }

            runOnUiThread {
                if (!sessionActive || measurementSuspended) {
                    replay?.frames?.forEach { if (!it.isRecycled) it.recycle() }
                    runCatching { file.delete() }
                    return@runOnUiThread
                }
                if (result == null) {
                    overlay.status =
                        "HFR 분석 실패 · QR/공/헤드마커 확인"

                    metricText.text =
                        "영상에 QR4개 + 흰 공 + 주황/파랑 헤드마커가 보여야 함."

                    setHfrStatus("HFR 분석 실패", "PRECISION 분석 실패")

                    overlay.invalidate()

                    scheduleAutoRetry(
                        900L
                    )
                } else {
                    handleMeasuredShot(
                        metrics =
                            result.metrics,
                        replay = replay,
                        source =
                            "PRECISION ${result.fps}fps"
                    )

                    setHfrStatus(
                        "✓ ${result.fps}fps",
                        "✓ ${result.fps}fps · ${result.analyzedFrames} frames · F${result.impactFrame}"
                    )
                }

                try {
                    file.delete()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun handleMeasuredShot(
        metrics: ShotMetrics,
        replay: ImpactReplay?,
        source: String
    ) {
        if (!sessionActive || measurementSuspended) {
            replay?.frames?.forEach { if (!it.isRecycled) it.recycle() }
            return
        }
        updateMetricCards(metrics)

        engine.launch(
            metrics
        )

        startSimulationTicker()

        replay?.let {
            replayView.play(
                it,
                metrics
            )
        }

        val preScore =
            engine.strokeScore

        val coach =
            engine.coachFeedback

        overlay.status =
            "$source · TV SHOT"

        metricText.text =
            buildString {
                append(
                    formatMetrics(
                        metrics
                    )
                )

                preScore?.let {
                    append(
                        "\nPerfect ${it.total}"
                    )
                }

                coach?.let {
                    append(
                        " · ${it.headline}"
                    )
                }

                metrics.estimatedMatStimpM?.let {
                    append(
                        "\nMAT AUTO-CAL ${"%.2f".format(it)}m"
                    )
                }
            }

        overlay.invalidate()
    }

    private fun startSimulationTicker() {
        simulationTicking = true
        lastSimulationNs =
            System.nanoTime()

        val tick =
            object : Runnable {
                override fun run() {
                    if (!simulationTicking) {
                        return
                    }

                    val now =
                        System.nanoTime()

                    val dt =
                        (
                            now -
                                lastSimulationNs
                            ) /
                            1_000_000_000.0

                    lastSimulationNs =
                        now

                    val result =
                        engine.step(
                            dt
                        )

                    if (result != null) {
                        simulationTicking = false

                        onSessionShotFinished()
                        showFinalShotSummary(result)

                        if (autoPlayEnabled && shouldContinueAutoAfterResult()) {
                            scheduleAutoNext()
                        }

                        return
                    }

                    simHandler.postDelayed(
                        this,
                        16L
                    )
                }
            }

        simHandler.post(
            tick
        )
    }

    private fun showFinalShotSummary(
        result: SimResult
    ) {
        val score =
            engine.strokeScore

        val coach =
            engine.coachFeedback

        val game =
            engine.gameModes.status

        metricText.text =
            buildString {
                append(
                    if (result.holed) {
                        "HOLE IN"
                    } else {
                        "컵까지 ${"%.0f".format(result.distanceToCupM * 100)}cm"
                    }
                )

                score?.let {
                    append(
                        " · PERFECT ${it.total}"
                    )
                }

                append(
                    "\n${game.mode.label}"
                )

                if (
                    game.totalHoles > 0
                ) {
                    append(
                        " ${game.hole}/${game.totalHoles}H"
                    )
                }

                append(
                    " · GAME ${game.gameScore}"
                )
                if (activeSessionIsGame && game.playerCount > 1) {
                    append(" · P${game.activePlayer}/${game.playerCount}")
                }

                if (game.completed) {
                    append(" · COMPLETE")
                }

                if (!activeSessionIsGame) {
                    append(" · ${practiceShotsTaken}/${practiceCount}구")
                    if (practiceShotsTaken >= practiceCount) append(" · COMPLETE")
                }

                coach?.let {
                    append(
                        "\nCOACH: ${it.headline} — ${it.detail}"
                    )
                }
            }
    }

    private fun scheduleAutoNext() {
        if (!sessionActive || measurementSuspended) return
        val generation =
            ++autoGeneration

        mainHandler.postDelayed(
            {
                if (
                    autoPlayEnabled &&
                    !measurementSuspended &&
                    generation ==
                    autoGeneration
                ) {
                    armPrecision()
                }
            },
            1200L
        )
    }

    private fun scheduleAutoRetry(
        delayMs: Long
    ) {
        if (!sessionActive || measurementSuspended || !autoPlayEnabled) {
            return
        }

        val generation =
            ++autoGeneration

        mainHandler.postDelayed(
            {
                if (
                    autoPlayEnabled &&
                    !measurementSuspended &&
                    generation ==
                    autoGeneration
                ) {
                    armPrecision()
                }
            },
            delayMs
        )
    }

    private fun cancelPendingAuto() {
        autoGeneration++
    }

    private fun stopHfrRecordingOnly() {
        hfrRecordingGeneration++
        impactPolling = false

        hfrController?.close()
        hfrController = null
    }

    private fun stopSimulation() {
        simulationTicking = false
        simHandler.removeCallbacksAndMessages(null)
        lastSimulationNs = 0L
    }

    private fun showStats(resumeAfter: Boolean = false) {
        val summary = statsRepository.summary()
        val recent = statsRepository.recent(10)

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(pvEyebrow("누적 기록 · CAREER TOTALS"))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun statRow(vararg cells: Pair<String, String>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            cells.forEachIndexed { i, (label, value) ->
                row.addView(pvStatTile(label, value, if (i == 0) Pv.primary else Pv.textHi),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = pvDp(6) })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })
        }
        statRow(
            "누적 샷" to "${summary.shots}구",
            "홀인" to "${summary.made} · ${"%.0f".format(summary.makePct)}%"
        )
        statRow(
            "Perfect 평균" to "%.1f".format(summary.avgScore),
            "출발각 평균" to "${"%+.2f".format(summary.avgLaunch)}° (±${"%.2f".format(summary.launchStd)})"
        )
        if (summary.avgFace != null || summary.avgPath != null || summary.avgDistanceErrorCm != null) {
            statRow(
                "Face 평균" to (summary.avgFace?.let { "${"%+.2f".format(it)}°" } ?: "--"),
                "Path 평균" to (summary.avgPath?.let { "${"%+.2f".format(it)}°" } ?: "--")
            )
            summary.avgDistanceErrorCm?.let {
                statRow("평균 컵 오차" to "${"%.0f".format(it)}cm")
            }
        }
        box.addView(grid)

        if (recent.isNotEmpty()) {
            box.addView(pvEyebrow("최근 ${recent.size}구 · PERFECT SCORE"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(14) })
            val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            recent.forEachIndexed { i, r ->
                chipRow.addView(TextView(this@MainActivity).apply {
                    text = r.strokeScore.total.toString()
                    setTextColor(Pv.primaryInk)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    textSize = Pv.body
                    gravity = Gravity.CENTER
                    background = pvRounded(Pv.primary, Pv.rSm)
                    setPadding(pvDp(4), pvDp(6), pvDp(4), pvDp(6))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = pvDp(4) })
            }
            box.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })
        }

        val dialog = pvDialog(
            title = "STATS",
            content = box,
            dismissLabel = "닫기",
            extraActions = listOf("기록 초기화" to {
                statsRepository.clear()
                engine.seedHistory(emptyList())
                toast("기록 초기화함")
            })
        )
        dialog.setOnDismissListener {
            if (resumeAfter && sessionActive && menuOverlay.visibility != View.VISIBLE) {
                mainHandler.post { armPrecision() }
            }
        }
        dialog.show()
    }

    private fun formatMetrics(
        m: ShotMetrics
    ): String {
        fun n(
            v: Double?
        ): String =
            v?.let {
                "%.2f".format(
                    it
                )
            } ?: "--"

        return buildString {
            append(
                "BALL ${n(m.ballSpeedMps)}m/s"
            )

            append(
                "  LAUNCH ${"%+.2f".format(m.launchAngleDeg)}°"
            )

            append(
                "\nHEAD ${n(m.headSpeedMps)}m/s"
            )

            append(
                "  FACE ${m.faceAngleDeg?.let { "%+.2f".format(it) } ?: "--"}°"
            )

            append(
                "  PATH ${m.pathAngleDeg?.let { "%+.2f".format(it) } ?: "--"}°"
            )

            append(
                "\nTEMPO ${m.tempoRatio?.let { "%.2f:1".format(it) } ?: "--"}"
            )

            append(
                "  BS ${m.backswingLengthCm?.let { "%.1fcm".format(it) } ?: "--"}"
            )

            append(
                "  IMP ${m.impactOffsetMm?.let { "%+.1fmm".format(it) } ?: "--"}"
            )

            m.confidence?.let {
                append(
                    "  Q ${"%.0f".format(it * 100)}%"
                )
            }
        }
    }

    private fun toast(
        text: String
    ) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        autoPlayEnabled = false
        cancelPendingAuto()

        stopSimulation()

        impactPolling = false
        mainHandler.removeCallbacksAndMessages(
            null
        )

        stopHfrRecordingOnly()

        settingsDialog?.dismiss()
        settingsDialog = null

        if (::displayController.isInitialized) displayController.stop()

        calibrator?.close()
        calibrator = null

        if (::appUpdater.isInitialized) appUpdater.close()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdownNow()
        super.onDestroy()
    }
}
