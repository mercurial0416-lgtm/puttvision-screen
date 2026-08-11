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
        if (::appUpdater.isInitialized) {
            appUpdater.resumePendingInstallIfPossible()
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
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Pv.inkDeep)
    }

    val stage = FrameLayout(this).apply {
        setBackgroundColor(Color.rgb(3, 5, 7))
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
        FrameLayout.LayoutParams(pvDp(if (compact) 174 else 220), pvDp(if (compact) 80 else 102), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(if (compact) 48 else 56)
            rightMargin = pvDp(if (compact) 10 else 14)
        }
    )

    replayView = ImpactReplayView(this)
    stage.addView(replayView, FrameLayout.LayoutParams(-1, -1))

    // Minimal top rail floats over the camera instead of stealing vertical space.
    val topRail = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Color.argb(222, 5, 8, 11), Pv.rLg, Color.argb(145, 52, 65, 74))
        setPadding(pvDp(if (compact) 12 else 16), pvDp(5), pvDp(if (compact) 8 else 10), pvDp(5))
        elevation = pvDp(10).toFloat()
    }
    val brand = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    brand.addView(TextView(this).apply {
        text = "PUTTVISION"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 11.5f else 13f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .04f
        includeFontPadding = false
    })
    brand.addView(TextView(this).apply {
        text = "  PRO"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 7f else 8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .16f
        includeFontPadding = false
    })
    topRail.addView(brand)
    topRail.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

    fun statusPill(initial: String, accent: Int, click: () -> Unit): TextView = TextView(this).apply {
        text = initial
        setTextColor(accent)
        textSize = pvSp(if (compact) 7.4f else 8.4f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setSingleLine(true)
        background = pvRounded(Color.rgb(13, 18, 23), 100f, Pv.line)
        setPadding(pvDp(if (compact) 9 else 11), pvDp(4), pvDp(if (compact) 9 else 11), pvDp(4))
        minHeight = pvDp(if (compact) 27 else 30)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    hfrStatus = statusPill("HFR CHECK", Pv.primary) { toast(lastHfrStatusMessage) }
    topRail.addView(hfrStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(4) })
    tvStatus = statusPill("○ TV", Pv.textMid) { toast(lastTvStatusMessage) }
    topRail.addView(tvStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(4) })

    modeButton = pvButton(engine.gameModes.status.mode.label, PvButtonStyle.GHOST, textSp = if (compact) 7.2f else 8.2f, radiusDp = 100f) {
        if (engine.state?.running == true) {
            toast("공이 굴러가는 동안에는 모드를 바꿀 수 없습니다")
        } else if (sessionActive) {
            pauseSessionForMenu()
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        } else {
            showHomeMenu()
        }
    }
    topRail.addView(modeButton, LinearLayout.LayoutParams(pvDp(if (compact) 72 else 82), pvDp(if (compact) 27 else 30)).apply { marginStart = pvDp(4) })

    autoButton = pvButton("AUTO", PvButtonStyle.PRIMARY, textSp = if (compact) 7.2f else 8.2f, radiusDp = 100f) {
        autoPlayEnabled = !autoPlayEnabled
        autoGeneration++
        updateAutoButton()
        if (autoPlayEnabled) maybeAutoStartAfterCalibration()
    }
    topRail.addView(autoButton, LinearLayout.LayoutParams(pvDp(if (compact) 58 else 66), pvDp(if (compact) 27 else 30)).apply { marginStart = pvDp(4) })

    stage.addView(topRail, FrameLayout.LayoutParams(-1, pvDp(if (compact) 38 else 42), Gravity.TOP).apply {
        leftMargin = pvDp(if (compact) 8 else 12)
        rightMargin = pvDp(if (compact) 8 else 12)
        topMargin = pvDp(if (compact) 7 else 10)
    })

    root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

    // Bottom command deck: one continuous instrument panel, not a wall of cards.
    val console = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvVGradient(Color.rgb(11, 15, 19), Color.rgb(7, 10, 13))
        setPadding(pvDp(if (compact) 10 else 14), pvDp(if (compact) 7 else 9), pvDp(if (compact) 10 else 14), pvDp(if (compact) 7 else 9))
    }

    fun metricColumn(label: String, key: String, unit: String, primary: Boolean = false): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pvDp(if (compact) 8 else 12), 0, pvDp(if (compact) 8 else 12), 0)
        }
        col.addView(TextView(this).apply {
            text = label
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 6.5f else 7.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
            includeFontPadding = false
            maxLines = 1
        })
        val valueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val value = TextView(this).apply {
            text = "--"
            setTextColor(if (primary) Pv.primary else Pv.textHi)
            textSize = pvSp(if (compact) 18f else 23f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        metricCards[key] = value
        valueRow.addView(value)
        valueRow.addView(TextView(this).apply {
            text = if (unit.isBlank()) "" else "  $unit"
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 6.2f else 7.4f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, 0, pvDp(2))
        })
        col.addView(valueRow)
        return col
    }

    console.addView(metricColumn("BALL SPEED", "ball", "m/s", true), LinearLayout.LayoutParams(0, -1, 1.05f))
    console.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) }, LinearLayout.LayoutParams(pvDp(1), -1).apply { topMargin = pvDp(8); bottomMargin = pvDp(8) })
    console.addView(metricColumn("START LINE", "launch", "°"), LinearLayout.LayoutParams(0, -1, .92f))
    console.addView(View(this).apply { setBackgroundColor(Pv.lineSoft) }, LinearLayout.LayoutParams(pvDp(1), -1).apply { topMargin = pvDp(8); bottomMargin = pvDp(8) })
    console.addView(metricColumn("HEAD SPEED", "head", "m/s"), LinearLayout.LayoutParams(0, -1, 1.0f))

    val detail = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(pvDp(if (compact) 9 else 12), 0, pvDp(if (compact) 9 else 12), 0)
    }
    val detailTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val detailBottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    fun diagnostic(label: String, key: String): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wrap.addView(TextView(this@MainActivity).apply {
            text = label
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 5.7f else 6.5f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        val v = TextView(this).apply {
            text = "--"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 8.1f else 9.4f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        metricCards[key] = v
        wrap.addView(v)
        return wrap
    }
    listOf("FACE" to "face", "PATH" to "path", "F→P" to "f2p").forEachIndexed { i, pair ->
        detailTop.addView(diagnostic(pair.first, pair.second), LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = pvDp(5) })
    }
    listOf("IMPACT" to "impact", "SMASH" to "smash", "TEMPO" to "tempo").forEachIndexed { i, pair ->
        detailBottom.addView(diagnostic(pair.first, pair.second), LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = pvDp(5) })
    }
    detail.addView(detailTop)
    detail.addView(detailBottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(4) })
    console.addView(detail, LinearLayout.LayoutParams(0, -1, 1.3f))

    val command = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(pvDp(if (compact) 8 else 12), 0, 0, 0)
    }
    shotPanelTitle = TextView(this).apply {
        text = "READY"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 6.8f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .10f
        includeFontPadding = false
    }
    metricText = TextView(this).apply {
        text = "공을 놓고 퍼팅하세요"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 7.4f else 8.5f)
        includeFontPadding = false
        maxLines = 2
    }
    command.addView(shotPanelTitle)
    command.addView(metricText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(2) })

    val commandButtons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    commandButtons.addView(pvButton("READY", PvButtonStyle.PRIMARY, textSp = if (compact) 7.6f else 8.8f, radiusDp = 100f) { armPrecision() }, LinearLayout.LayoutParams(0, pvDp(if (compact) 30 else 34), 1.15f).apply { marginEnd = pvDp(4) })
    commandButtons.addView(pvButton("CAL", PvButtonStyle.GHOST, textSp = if (compact) 7f else 8f, radiusDp = 100f) { beginAutoCalibration() }, LinearLayout.LayoutParams(0, pvDp(if (compact) 30 else 34), .72f).apply { marginEnd = pvDp(4) })
    settingsToggle = pvButton("MENU", PvButtonStyle.GHOST, textSp = if (compact) 7f else 8f, radiusDp = 100f) { showSettingsDialog() }
    commandButtons.addView(settingsToggle, LinearLayout.LayoutParams(0, pvDp(if (compact) 30 else 34), .8f))
    command.addView(commandButtons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(5) })

    settingSummary = TextView(this).apply {
        setTextColor(Pv.textLo)
        textSize = pvSp(if (compact) 5.8f else 6.8f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        maxLines = 1
        gravity = Gravity.END
    }
    command.addView(settingSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(3) })
    console.addView(command, LinearLayout.LayoutParams(0, -1, 1.32f))

    settingsPanel = LinearLayout(this).apply { visibility = View.GONE }
    speedLabel = settingLabel()
    distanceLabel = settingLabel()
    sideLabel = settingLabel()
    longLabel = settingLabel()
    updateSettingLabels()
    updateAutoButton()

    root.addView(console, LinearLayout.LayoutParams(-1, pvDp(if (compact) 116 else 138)))

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
    val root = FrameLayout(this).apply { setBackgroundColor(Pv.inkDeep) }
    root.addView(CommercialHomeBackdropView(this), FrameLayout.LayoutParams(-1, -1))

    val chrome = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            sdp(if (compact) 20 else 30),
            sdp(if (compact) 14 else 20),
            sdp(if (compact) 20 else 30),
            sdp(if (compact) 16 else 22)
        )
    }

    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val logo = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    val brandLine = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    brandLine.addView(TextView(this).apply {
        text = "PUTTVISION"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 17f else 21f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .035f
        includeFontPadding = false
    })
    brandLine.addView(TextView(this).apply {
        text = "  PRO"
        setTextColor(Pv.primary)
        textSize = scaledSp(if (compact) 8f else 9f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .16f
        includeFontPadding = false
    })
    logo.addView(brandLine)
    logo.addView(TextView(this).apply {
        text = "PERSONAL PUTTING STUDIO"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 6.8f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .18f
        includeFontPadding = false
    })
    top.addView(logo)
    top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

    fun topAction(symbol: String, label: String, click: () -> Unit): TextView = TextView(this).apply {
        text = "$symbol  $label"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 7.5f else 8.5f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = pvRounded(Color.argb(178, 10, 14, 17), 100f, Pv.line)
        setPadding(sdp(if (compact) 10 else 12), sdp(5), sdp(if (compact) 10 else 12), sdp(5))
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }
    top.addView(topAction("⚙", "환경") { showSettingsDialog() })
    top.addView(topAction("⇥", "종료") { finishAffinity() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(7) })
    chrome.addView(top)

    val center = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val message = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, sdp(if (compact) 20 else 34), 0)
    }
    message.addView(TextView(this).apply {
        text = "READ. ROLL. REPEAT."
        setTextColor(Pv.primary)
        textSize = scaledSp(if (compact) 7.8f else 9f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .18f
        includeFontPadding = false
    })
    message.addView(TextView(this).apply {
        text = "퍼팅을 감으로 치지 마세요.\n숫자로 확인하세요."
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 23f else 31f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(sdp(2).toFloat(), 1f)
        setPadding(0, sdp(5), 0, 0)
    })
    message.addView(TextView(this).apply {
        text = "240fps 고속 촬영 · 페이스/패스 분석 · TV 시뮬레이터"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 8f else 9.5f)
        includeFontPadding = false
        setPadding(0, sdp(8), 0, 0)
    })

    val readiness = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, sdp(if (compact) 13 else 18), 0, 0)
    }
    fun readyItem(dot: Int, title: String, sub: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(this@MainActivity).apply { background = pvRounded(dot, 100f) }, LinearLayout.LayoutParams(sdp(5), sdp(5)).apply { marginEnd = sdp(6) })
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 7f else 8f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textLo)
            textSize = scaledSp(if (compact) 5.8f else 6.8f)
            includeFontPadding = false
        })
        addView(copy)
    }
    readiness.addView(readyItem(Pv.primary, "PHONE", "CAMERA READY"))
    readiness.addView(readyItem(Pv.info, "TV", "HDMI / DeX"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(if (compact) 16 else 22) })
    readiness.addView(readyItem(Pv.amber, "MAT", "4 MARKERS"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(if (compact) 16 else 22) })
    message.addView(readiness)
    center.addView(message, LinearLayout.LayoutParams(0, -1, 1.05f))

    val launcher = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Color.argb(214, 7, 10, 13), Pv.rXl, Color.argb(145, 53, 67, 76))
        setPadding(sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16), sdp(if (compact) 14 else 18), sdp(if (compact) 12 else 16))
    }
    launcher.addView(TextView(this).apply {
        text = "START SESSION"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 6.8f else 7.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .14f
        includeFontPadding = false
    })

    fun sessionButton(kicker: String, title: String, sub: String, accent: Int, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceHi, Pv.rLg, Pv.line)
        setPadding(sdp(if (compact) 14 else 17), sdp(if (compact) 9 else 11), sdp(if (compact) 12 else 15), sdp(if (compact) 9 else 11))
        val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this@MainActivity).apply {
            text = kicker
            setTextColor(accent)
            textSize = scaledSp(if (compact) 6.5f else 7.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .10f
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(Pv.textHi)
            textSize = scaledSp(if (compact) 16f else 19f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })
        copy.addView(TextView(this@MainActivity).apply {
            text = sub
            setTextColor(Pv.textMid)
            textSize = scaledSp(if (compact) 6.8f else 7.8f)
            includeFontPadding = false
            maxLines = 1
        })
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "→"
            setTextColor(accent)
            textSize = scaledSp(if (compact) 20f else 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }, LinearLayout.LayoutParams(sdp(30), -1))
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    launcher.addView(sessionButton("PRACTICE LAB", "연습 시작", "거리 · 컵 · 경사 · 반복", Pv.primary) { showPracticeEntrance() }, LinearLayout.LayoutParams(-1, sdp(if (compact) 68 else 78)).apply { topMargin = sdp(if (compact) 10 else 13) })
    launcher.addView(sessionButton("MATCH PLAY", "게임 시작", "1–4인 · 9홀 · 18홀 · 챌린지", Pv.amber) { showGameEntrance() }, LinearLayout.LayoutParams(-1, sdp(if (compact) 68 else 78)).apply { topMargin = sdp(if (compact) 8 else 10) })
    center.addView(launcher, LinearLayout.LayoutParams(0, -1, .72f))

    chrome.addView(center, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = sdp(if (compact) 10 else 16) })

    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    footer.addView(TextView(this).apply {
        text = "PUTTVISION SYSTEM  ·  PERSONAL PUTTING ANALYSIS"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 5.8f else 6.8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .10f
        includeFontPadding = false
    })
    footer.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    footer.addView(TextView(this).apply {
        text = "240 FPS  •  AUTO CAL  •  TV OUT"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 5.8f else 6.8f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
    })
    chrome.addView(footer)

    root.addView(chrome, FrameLayout.LayoutParams(-1, -1))
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
            addView(View(this@MainActivity).apply {
                background = pvRounded(Pv.primary, 100f)
            }, LinearLayout.LayoutParams(sdp(4), sdp(20)).apply { marginEnd = sdp(9) })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = scaledSp(18f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Pv.textHi)
            })
            addView(TextView(this@MainActivity).apply {
                text = english
                textSize = scaledSp(9f)
                setTextColor(Pv.textLo)
                setPadding(sdp(6), sdp(6), 0, 0)
            })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            if (guide != null) {
                addView(roundMenuIcon("?", "", guide), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            }
            addView(roundMenuIcon("↩", "", back))
        }

    private fun showPracticeEntrance() {
        replaceMenuScreen(buildPracticeEntrance()) { showHomeMenu() }
    }

    private fun buildPracticeEntrance(): View {
    val compact = compactLandscape
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
        addView(CommercialModeVisualView(this@MainActivity, false), FrameLayout.LayoutParams(-1, -1))
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
                    2 -> "좌우 경사와 브레이크를\n반복해서 읽습니다."
                    else -> "거리별 볼 스피드와\n스트로크 크기를 잡습니다."
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
    right.addView(sectionLabel("GREEN SPEED"))
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
        max = 5
        progress = ((practiceGreenSpeed - 2.5) * 10).toInt().coerceIn(0, 5)
        progressTintList = ColorStateList.valueOf(Pv.primary)
        progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
        thumbTintList = ColorStateList.valueOf(Pv.primary)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                practiceGreenSpeed = 2.5 + progress * .1
                speedValue.text = "%.1f".format(practiceGreenSpeed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
    right.addView(speedSeek)
    middle.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = sdp(if (compact) 10 else 14) })

    panel.addView(middle, LinearLayout.LayoutParams(-1, 0, .35f).apply { topMargin = sdp(if (compact) 7 else 10) })

    panel.addView(sectionLabel(if (practiceEntranceMode == 2) "BREAK PATTERN" else if (practiceEntranceMode == 1) "CUP PRESET" else "DISTANCE PATTERN"))
    val patternRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val patterns = when (practiceEntranceMode) {
        1 -> listOf("3m", "5m", "7m", "10m")
        2 -> listOf("평지", "좌경사", "우경사", "랜덤")
        else -> listOf("고정", "랜덤", "증가", "감소")
    }
    patterns.forEachIndexed { i, label ->
        patternRow.addView(
            darkChoice(label, if (practiceEntranceMode == 1) practiceDistanceM == label.removeSuffix("m").toInt() else practicePatternIndex == i) {
                if (practiceEntranceMode == 1) practiceDistanceM = label.removeSuffix("m").toInt() else practicePatternIndex = i
                showPracticeEntrance()
            },
            LinearLayout.LayoutParams(0, sdp(if (compact) 36 else 42), 1f).apply { if (i > 0) marginStart = sdp(5) }
        )
    }
    panel.addView(patternRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(6) })

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
        }, LinearLayout.LayoutParams(0, sdp(if (compact) 35 else 41), 1f).apply { if (i > 0) marginStart = sdp(4) })
    }
    countWrap.addView(countRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = sdp(5) })
    bottom.addView(countWrap, LinearLayout.LayoutParams(0, -1, 1.35f).apply { marginEnd = sdp(if (compact) 10 else 14) })

    val start = pvButton("연습 시작  →", PvButtonStyle.PRIMARY, textSp = if (compact) 10f else 11.5f, radiusDp = Pv.rLg) {
        openPreStartOrMat(false)
    }
    bottom.addView(start, LinearLayout.LayoutParams(0, sdp(if (compact) 58 else 68), .75f))
    panel.addView(bottom, LinearLayout.LayoutParams(-1, 0, .31f).apply { topMargin = sdp(if (compact) 7 else 10) })

    body.addView(panel, LinearLayout.LayoutParams(0, -1, .58f))
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
        max = 5
        progress = ((practiceGreenSpeed - 2.5) * 10).toInt().coerceIn(0, 5)
        progressTintList = ColorStateList.valueOf(Pv.amber)
        progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
        thumbTintList = ColorStateList.valueOf(Pv.amber)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                practiceGreenSpeed = 2.5 + progress * .1
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
                engine.settings.holeDistanceM = base
                engine.settings.sideSlopePct = 0.0
                engine.settings.longSlopePct = 0.0
            }
            else -> {
                engine.settings.holeDistanceM = base
                when (practicePatternIndex) {
                    1 -> {
                        engine.settings.sideSlopePct = -2.0
                        engine.settings.longSlopePct = 0.0
                    }
                    2 -> {
                        engine.settings.sideSlopePct = 2.0
                        engine.settings.longSlopePct = 0.0
                    }
                    3 -> {
                        engine.settings.sideSlopePct = practiceRandom.nextDouble(-4.0, 4.0)
                        engine.settings.longSlopePct = practiceRandom.nextDouble(-2.5, 2.5)
                    }
                    else -> {
                        engine.settings.sideSlopePct = 0.0
                        engine.settings.longSlopePct = 0.0
                    }
                }
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
