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
        setPadding(pvDp(if (compact) 8 else 12), pvDp(if (compact) 5 else 8), pvDp(if (compact) 8 else 12), pvDp(if (compact) 7 else 10))
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val brand = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val brandLine = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    brandLine.addView(TextView(this).apply {
        text = "PuttVision"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 15.5f else 18f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    brandLine.addView(TextView(this).apply {
        text = "  SCREEN"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 9f else 10f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.14f
        includeFontPadding = false
    })
    brand.addView(brandLine)
    brand.addView(TextView(this).apply {
        text = "HFR PRECISION · PUTTING LAUNCH MONITOR"
        setTextColor(Pv.textLo)
        textSize = pvSp(if (compact) 6.8f else 7.8f)
        letterSpacing = 0.08f
        includeFontPadding = false
        maxLines = 1
    })
    header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

    fun statusChip(initial: String, accent: Int, click: () -> Unit): TextView = TextView(this).apply {
        text = initial
        setTextColor(accent)
        textSize = pvSp(if (compact) 8.2f else 9.2f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setSingleLine(true)
        includeFontPadding = false
        minHeight = pvDp(if (compact) 30 else 34)
        background = pvRounded(Pv.surfaceLo, 100f, Pv.line)
        setPadding(pvDp(if (compact) 9 else 12), pvDp(5), pvDp(if (compact) 9 else 12), pvDp(5))
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    hfrStatus = statusChip("HFR 확인중", Pv.primary) { toast(lastHfrStatusMessage) }
    header.addView(hfrStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(5) })

    tvStatus = statusChip("○ TV", Pv.textMid) { toast(lastTvStatusMessage) }
    header.addView(tvStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(5) })

    modeButton = pvButton(engine.gameModes.status.mode.label, PvButtonStyle.SECONDARY, textSp = if (compact) 8.5f else 9.5f, radiusDp = 100f) {
        if (engine.state?.running == true) {
            toast("공이 굴러가는 동안에는 모드를 바꿀 수 없습니다")
        } else if (sessionActive) {
            pauseSessionForMenu()
            if (activeSessionIsGame) showGameEntrance() else showPracticeEntrance()
        } else {
            showHomeMenu()
        }
    }
    header.addView(modeButton, LinearLayout.LayoutParams(pvDp(if (compact) 78 else 88), pvDp(if (compact) 30 else 34)).apply { marginStart = pvDp(5) })

    autoButton = pvButton("AUTO ON", PvButtonStyle.PRIMARY, textSp = if (compact) 8.5f else 9.5f, radiusDp = 100f) {
        autoPlayEnabled = !autoPlayEnabled
        autoGeneration++
        updateAutoButton()
        if (autoPlayEnabled) maybeAutoStartAfterCalibration()
    }
    header.addView(autoButton, LinearLayout.LayoutParams(pvDp(if (compact) 70 else 78), pvDp(if (compact) 30 else 34)).apply { marginStart = pvDp(5) })

    root.addView(header, LinearLayout.LayoutParams(-1, pvDp(if (compact) 39 else 45)))

    val cameraFrame = FrameLayout(this).apply {
        background = pvRounded(Color.rgb(4, 7, 9), Pv.rLg, Pv.line)
        setPadding(pvDp(2), pvDp(2), pvDp(2), pvDp(2))
    }
    previewView = PreviewView(this).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    cameraFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))

    overlay = PhoneOverlayView(this)
    cameraFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))

    val impactPreview = ReferenceImpactPreviewView(this)
    cameraFrame.addView(
        impactPreview,
        FrameLayout.LayoutParams(pvDp(if (compact) 176 else 220), pvDp(if (compact) 88 else 108), Gravity.TOP or Gravity.END).apply {
            topMargin = pvDp(if (compact) 8 else 12)
            rightMargin = pvDp(if (compact) 8 else 12)
        }
    )

    replayView = ImpactReplayView(this)
    cameraFrame.addView(replayView, FrameLayout.LayoutParams(-1, -1))

    root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
        topMargin = pvDp(if (compact) 5 else 8)
        bottomMargin = pvDp(if (compact) 5 else 8)
    })

    val dashboard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = pvRounded(Pv.surface, Pv.rLg, Pv.lineSoft)
        setPadding(pvDp(if (compact) 7 else 10), pvDp(if (compact) 6 else 9), pvDp(if (compact) 7 else 10), pvDp(if (compact) 6 else 9))
    }

    fun heroMetric(title: String, key: String, unit: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.lineSoft)
            setPadding(pvDp(if (compact) 10 else 14), pvDp(5), pvDp(if (compact) 10 else 14), pvDp(5))
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Pv.textMid)
            textSize = pvSp(if (compact) 7.2f else 8.2f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 1
        })
        val valueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val value = TextView(this).apply {
            text = "--"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 17f else 21f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        metricCards[key] = value
        valueRow.addView(value)
        if (unit.isNotEmpty()) valueRow.addView(TextView(this).apply {
            text = "  $unit"
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 7.5f else 8.5f)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, 0, pvDp(2))
        })
        card.addView(valueRow, LinearLayout.LayoutParams(-1, 0, 1f))
        return card
    }

    val mainMetrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val mainH = pvDp(if (compact) 48 else 60)
    listOf(
        Triple("볼 스피드", "ball", "m/s"),
        Triple("출발 각도", "launch", "deg"),
        Triple("헤드 스피드", "head", "m/s")
    ).forEachIndexed { i, (title, key, unit) ->
        mainMetrics.addView(heroMetric(title, key, unit), LinearLayout.LayoutParams(0, mainH, 1f).apply {
            if (i > 0) marginStart = pvDp(if (compact) 5 else 7)
        })
    }
    dashboard.addView(mainMetrics)

    fun miniMetric(title: String, key: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = pvRounded(Pv.surfaceLo, Pv.rSm, Pv.lineSoft)
            setPadding(pvDp(3), pvDp(2), pvDp(3), pvDp(2))
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Pv.textLo)
            textSize = pvSp(if (compact) 5.9f else 6.8f)
            includeFontPadding = false
            maxLines = 1
        })
        val value = TextView(this).apply {
            text = "--"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 8.6f else 10.2f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }
        metricCards[key] = value
        card.addView(value)
        return card
    }

    val miniRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val miniDefs = listOf(
        "FACE" to "face", "PATH" to "path", "F→P" to "f2p",
        "IMPACT" to "impact", "SMASH" to "smash", "TEMPO" to "tempo"
    )
    miniDefs.forEachIndexed { i, (title, key) ->
        miniRow.addView(miniMetric(title, key), LinearLayout.LayoutParams(0, pvDp(if (compact) 31 else 38), 1f).apply {
            if (i > 0) marginStart = pvDp(4)
        })
    }
    dashboard.addView(miniRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(if (compact) 4 else 6) })

    val statusRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.lineSoft)
        setPadding(pvDp(if (compact) 9 else 12), pvDp(4), pvDp(if (compact) 9 else 12), pvDp(4))
    }
    shotPanelTitle = TextView(this).apply {
        text = "SHOT"
        setTextColor(Pv.primary)
        textSize = pvSp(if (compact) 7f else 8f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f
        includeFontPadding = false
    }
    statusRow.addView(shotPanelTitle)
    metricText = TextView(this).apply {
        text = "  READY · 공을 놓고 퍼팅하세요"
        setTextColor(Pv.textHi)
        textSize = pvSp(if (compact) 7.7f else 9f)
        includeFontPadding = false
        maxLines = 2
    }
    statusRow.addView(metricText, LinearLayout.LayoutParams(0, -2, 1f))
    settingSummary = TextView(this).apply {
        setTextColor(Pv.textMid)
        textSize = pvSp(if (compact) 6.5f else 7.5f)
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        maxLines = 1
        gravity = Gravity.END
    }
    statusRow.addView(settingSummary, LinearLayout.LayoutParams(0, -2, 0.65f))
    dashboard.addView(statusRow, LinearLayout.LayoutParams(-1, pvDp(if (compact) 29 else 34)).apply { topMargin = pvDp(if (compact) 4 else 6) })

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val actionH = pvDp(if (compact) 34 else 40)
    actions.addView(pvButton("재캘리브레이션", PvButtonStyle.SECONDARY, textSp = if (compact) 8.4f else 9.4f) { beginAutoCalibration() }, LinearLayout.LayoutParams(0, actionH, 0.95f).apply { marginEnd = pvDp(5) })
    actions.addView(pvButton("PRECISION READY", PvButtonStyle.PRIMARY, textSp = if (compact) 9.2f else 10.6f) { armPrecision() }, LinearLayout.LayoutParams(0, actionH, 1.25f).apply { marginStart = pvDp(2); marginEnd = pvDp(2) })
    settingsToggle = pvButton("설정 / 환경", PvButtonStyle.SECONDARY, textSp = if (compact) 8.4f else 9.4f) { showSettingsDialog() }
    actions.addView(settingsToggle, LinearLayout.LayoutParams(0, actionH, 0.95f).apply { marginStart = pvDp(5) })
    dashboard.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(if (compact) 4 else 6) })

    settingsPanel = LinearLayout(this).apply { visibility = View.GONE }
    speedLabel = settingLabel()
    distanceLabel = settingLabel()
    sideLabel = settingLabel()
    longLabel = settingLabel()
    updateSettingLabels()
    updateAutoButton()

    root.addView(dashboard, LinearLayout.LayoutParams(-1, -2))

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

    val body = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            sdp(if (compact) 18 else 28),
            sdp(if (compact) 48 else 58),
            sdp(if (compact) 18 else 28),
            sdp(if (compact) 14 else 22)
        )
    }

    val hero = FrameLayout(this).apply {
        background = pvRounded(Color.rgb(7, 12, 13), Pv.rXl, Pv.line)
        clipToOutline = true
    }
    hero.addView(ReferenceHeroView(this), FrameLayout.LayoutParams(-1, -1))
    val heroCopy = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(sdp(if (compact) 15 else 22), sdp(12), sdp(if (compact) 15 else 22), sdp(if (compact) 14 else 20))
    }
    heroCopy.addView(TextView(this).apply {
        text = "PRECISION PUTTING"
        setTextColor(Pv.primary)
        textSize = scaledSp(if (compact) 8f else 9.5f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.15f
        includeFontPadding = false
    })
    heroCopy.addView(TextView(this).apply {
        text = "스마트폰이\n런치모니터가 됩니다"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 21f else 28f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(sdp(2).toFloat(), 1f)
    })
    heroCopy.addView(TextView(this).apply {
        text = "240fps · 자동 캘리브레이션 · TV 스크린 시뮬레이터"
        setTextColor(Pv.textMid)
        textSize = scaledSp(if (compact) 7.5f else 9f)
        includeFontPadding = false
        setPadding(0, sdp(5), 0, 0)
    })
    hero.addView(heroCopy, FrameLayout.LayoutParams(-1, -1))
    body.addView(hero, LinearLayout.LayoutParams(0, -1, 0.51f).apply { marginEnd = sdp(if (compact) 12 else 18) })

    val right = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val logo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    logo.addView(TextView(this).apply {
        text = "Putt"
        setTextColor(Pv.primary)
        textSize = scaledSp(if (compact) 29f else 36f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    logo.addView(TextView(this).apply {
        text = "Vision"
        setTextColor(Pv.textHi)
        textSize = scaledSp(if (compact) 29f else 36f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    right.addView(logo)
    right.addView(TextView(this).apply {
        text = "SCREEN PUTTING SIMULATOR"
        setTextColor(Pv.textLo)
        textSize = scaledSp(if (compact) 7.5f else 9f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.18f
        includeFontPadding = false
    })

    fun homeTile(kicker: String, title: String, sub: String, accent: Int, click: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pvRounded(Pv.surfaceHi, Pv.rLg, Pv.line)
            setPadding(sdp(if (compact) 14 else 18), sdp(8), sdp(if (compact) 12 else 16), sdp(8))
            val copy = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(TextView(this@MainActivity).apply {
                text = kicker
                setTextColor(accent)
                textSize = scaledSp(if (compact) 7f else 8f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.10f
                includeFontPadding = false
            })
            copy.addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Pv.textHi)
                textSize = scaledSp(if (compact) 17f else 20f)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
            copy.addView(TextView(this@MainActivity).apply {
                text = sub
                setTextColor(Pv.textMid)
                textSize = scaledSp(if (compact) 7f else 8f)
                includeFontPadding = false
                maxLines = 1
            })
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "›"
                setTextColor(accent)
                textSize = scaledSp(if (compact) 30f else 36f)
                gravity = Gravity.CENTER
                includeFontPadding = false
            }, LinearLayout.LayoutParams(sdp(28), -1))
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    val tileH = sdp(if (compact) 65 else 76)
    right.addView(homeTile("PRACTICE", "연습장", "거리 · 컵 · 경사 · 반복 연습", Pv.primary) { showPracticeEntrance() }, LinearLayout.LayoutParams(-1, tileH).apply { topMargin = sdp(if (compact) 11 else 17) })
    right.addView(homeTile("GAME ZONE", "게임장", "1~4인 · 9홀 · 18홀 · 랜덤 경사", Pv.amber) { showGameEntrance() }, LinearLayout.LayoutParams(-1, tileH).apply { topMargin = sdp(if (compact) 8 else 10) })

    val connection = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.lineSoft)
        setPadding(sdp(10), sdp(6), sdp(10), sdp(6))
    }
    connection.addView(TextView(this).apply {
        text = "PHONE"
        setTextColor(Pv.primary)
        textSize = scaledSp(7f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    connection.addView(TextView(this).apply {
        text = "  →  HDMI / DeX  →  TV SCREEN"
        setTextColor(Pv.textMid)
        textSize = scaledSp(7f)
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    })
    right.addView(connection, LinearLayout.LayoutParams(-1, sdp(if (compact) 29 else 34)).apply { topMargin = sdp(if (compact) 8 else 10) })
    body.addView(right, LinearLayout.LayoutParams(0, -1, 0.49f))

    root.addView(body, FrameLayout.LayoutParams(-1, -1))

    val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(sdp(if (compact) 12 else 18), sdp(if (compact) 6 else 10), sdp(if (compact) 12 else 18), 0)
        elevation = pvDp(14).toFloat()
    }
    top.addView(roundMenuIcon("◎", "언어") { toast("현재 한국어") })
    top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    top.addView(roundMenuIcon("⚙", "환경") { showSettingsDialog() })
    top.addView(roundMenuIcon("⇥", "종료") { finishAffinity() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(8) })
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(14), sdp(14), sdp(14), sdp(12))
        }
        root.addView(buildEntranceHeader("연습장 입구", "Practice Mode Entrance", { showCupGuideScreen { showPracticeEntrance() } }) { showHomeMenu() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val upper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modePanel = sectionPanel()
        modePanel.addView(tinyCaption("모드"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("거리", "컵", "그린").forEachIndexed { i, label ->
            modeRow.addView(darkChoice(label, practiceEntranceMode == i) { practiceEntranceMode = i; showPracticeEntrance() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(3) })
        }
        modePanel.addView(modeRow)
        upper.addView(modePanel, LinearLayout.LayoutParams(0, -1, 0.39f).apply { marginEnd = dp(5) })

        val countPanel = sectionPanel()
        countPanel.addView(tinyCaption("횟수"))
        val countGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val counts = listOf(5, 10, 15, 20)
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val v = counts[r * 2 + c]
                row.addView(darkChoice(v.toString(), practiceCount == v) { practiceCount = v; showPracticeEntrance() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (c > 0) marginStart = dp(3) })
            }
            countGrid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dp(3) })
        }
        countPanel.addView(countGrid)
        upper.addView(countPanel, LinearLayout.LayoutParams(0, -1, 0.22f).apply { marginEnd = dp(5) })

        val speedPanel = sectionPanel()
        speedPanel.addView(tinyCaption("그린스피드"))
        speedPanel.addView(TextView(this).apply {
            text = "%.1f".format(practiceGreenSpeed)
            textSize = scaledSp(24f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Pv.primary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val speedSeek = SeekBar(this).apply {
            max = 5
            progress = ((practiceGreenSpeed - 2.5) * 10).toInt().coerceIn(0, 5)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    practiceGreenSpeed = 2.5 + progress * 0.1
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { showPracticeEntrance() }
            })
        }
        speedPanel.addView(speedSeek)
        upper.addView(speedPanel, LinearLayout.LayoutParams(0, -1, 0.39f))
        left.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.52f))

        val lower = sectionPanel()
        val lowerCaption = when (practiceEntranceMode) {
            1 -> "컵 거리"
            2 -> "그린 변화"
            else -> "거리 변화"
        }
        lower.addView(tinyCaption(lowerCaption))
        val lowerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        when (practiceEntranceMode) {
            1 -> {
                listOf(3, 5, 7, 10).forEachIndexed { i, v ->
                    lowerRow.addView(
                        darkChoice("${v}m", practiceDistanceM == v) {
                            practiceDistanceM = v
                            showPracticeEntrance()
                        },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(4) }
                    )
                }
            }
            2 -> {
                listOf("평지", "좌경사", "우경사", "랜덤").forEachIndexed { i, label ->
                    lowerRow.addView(
                        darkChoice(label, practicePatternIndex == i) {
                            practicePatternIndex = i
                            showPracticeEntrance()
                        },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(4) }
                    )
                }
            }
            else -> {
                listOf("고정", "랜덤", "증가", "감소").forEachIndexed { i, label ->
                    lowerRow.addView(
                        darkChoice(label, practicePatternIndex == i) {
                            practicePatternIndex = i
                            showPracticeEntrance()
                        },
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(4) }
                    )
                }
            }
        }
        lower.addView(lowerRow)
        val distanceLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        distanceLine.addView(TextView(this).apply { text = "2m"; setTextColor(Pv.textMid); textSize = scaledSp(10f); typeface = Typeface.MONOSPACE })
        val dSeek = SeekBar(this).apply {
            max = 13
            progress = (practiceDistanceM - 2).coerceIn(0, 13)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { practiceDistanceM = progress + 2 }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { showPracticeEntrance() }
            })
        }
        distanceLine.addView(dSeek, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = sdp(10); marginEnd = sdp(10) })
        distanceLine.addView(TextView(this).apply { text = "15m"; setTextColor(Pv.textMid); textSize = scaledSp(10f); typeface = Typeface.MONOSPACE })
        lower.addView(distanceLine)
        left.addView(lower, LinearLayout.LayoutParams(-1, 0, 0.48f).apply { topMargin = dp(6) })

        content.addView(left, LinearLayout.LayoutParams(0, -1, 0.81f).apply { marginEnd = sdp(10) })
        content.addView(cyanButton("▶\n입장") { openPreStartOrMat(false) }, LinearLayout.LayoutParams(0, -1, 0.19f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showGameEntrance() {
        replaceMenuScreen(buildGameEntrance()) { showHomeMenu() }
    }

    private fun buildGameEntrance(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(14), sdp(14), sdp(14), sdp(12))
        }
        root.addView(buildEntranceHeader("게임장 입구", "Game Zone Entrance", null) { showHomeMenu() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })
        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val upper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val players = sectionPanel(); players.addView(tinyCaption("플레이어 수"))
        val pGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0..1) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0..1) {
                val v = r * 2 + c + 1
                row.addView(darkChoice(v.toString(), gamePlayers == v) { gamePlayers = v; showGameEntrance() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (c > 0) marginStart = dp(3) })
            }
            pGrid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dp(3) })
        }
        players.addView(pGrid)
        upper.addView(players, LinearLayout.LayoutParams(0, -1, 0.18f).apply { marginEnd = dp(5) })

        val modes = sectionPanel(); modes.addView(tinyCaption("게임 방식"))
        val mRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("9홀", "18홀", "거리\n맞추기", "랜덤\n경사").forEachIndexed { i, label ->
            mRow.addView(darkChoice(label, gameModeIndex == i) { gameModeIndex = i; showGameEntrance() }, LinearLayout.LayoutParams(0, sdp(54), 1f).apply { if (i > 0) marginStart = dp(3) })
        }
        modes.addView(mRow)
        upper.addView(modes, LinearLayout.LayoutParams(0, -1, 0.47f).apply { marginEnd = dp(5) })

        val speed = sectionPanel(); speed.addView(tinyCaption("그린스피드"))
        val gameSpeedValue = TextView(this).apply {
            text = "%.1f".format(practiceGreenSpeed)
            gravity = Gravity.CENTER
            textSize = scaledSp(18f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Pv.primary)
        }
        speed.addView(gameSpeedValue, LinearLayout.LayoutParams(-1, 0, 1f))
        speed.addView(SeekBar(this).apply {
            max = 5
            progress = ((practiceGreenSpeed - 2.5) * 10).toInt().coerceIn(0, 5)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    practiceGreenSpeed = 2.5 + progress * 0.1
                    gameSpeedValue.text = "%.1f".format(practiceGreenSpeed)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        upper.addView(speed, LinearLayout.LayoutParams(0, -1, 0.35f))
        left.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.52f))

        val lower = sectionPanel()
        if (gameModeIndex == 2) {
            lower.addView(tinyCaption("목표 거리"))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("3m", "5m", "7m", "10m").forEachIndexed { i, label ->
                val distance = label.removeSuffix("m").toInt()
                row.addView(darkChoice(label, gameDistanceM == distance) {
                    gameDistanceM = distance
                    showGameEntrance()
                }, LinearLayout.LayoutParams(0, sdp(54), 1f).apply { if (i > 0) marginStart = dp(6) })
            }
            lower.addView(row)
        } else {
            lower.addView(tinyCaption("코스 설정"))
            lower.addView(TextView(this).apply {
                text = when (gameModeIndex) {
                    0 -> "9홀 · 홀마다 거리/경사 자동 코스"
                    1 -> "18홀 · 홀마다 거리/경사 자동 코스"
                    else -> "매 샷 거리/좌우/종경사 자동 랜덤"
                }
                gravity = Gravity.CENTER
                textSize = scaledSp(11f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Pv.textMid)
                background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.lineSoft)
                setPadding(sdp(8), sdp(8), sdp(8), sdp(8))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        left.addView(lower, LinearLayout.LayoutParams(-1, 0, 0.48f).apply { topMargin = dp(6) })

        content.addView(left, LinearLayout.LayoutParams(0, -1, 0.81f).apply { marginEnd = sdp(10) })
        content.addView(cyanButton("▶\n입장") { openPreStartOrMat(true) }, LinearLayout.LayoutParams(0, -1, 0.19f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(24), sdp(18), sdp(24), sdp(14))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "시작하기 전에"; textSize = scaledSp(22f); typeface = Typeface.DEFAULT_BOLD; setTextColor(Pv.textHi); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(58)))
        val pics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun diagram(textValue: String): TextView = TextView(this).apply {
            text = textValue; gravity = Gravity.CENTER; textSize = scaledSp(13f); typeface = Typeface.MONOSPACE; setTextColor(Pv.textMid); background = pvRounded(Pv.surface, Pv.rMd, Pv.lineSoft)
        }
        pics.addView(diagram("카메라\n   │\n   ▼\n▰  매트  ⚪"), LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(6) })
        pics.addView(diagram("휴대폰 위치\n↘\n┌────────┐\n│  매트  │\n└────────┘"), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(6) })
        root.addView(pics, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "충분히 밝은 곳에서 카메라와 매트를 정확히 맞춰주세요."; gravity = Gravity.CENTER; textSize = scaledSp(10f); setTextColor(Pv.textLo); setPadding(0, sdp(8), 0, sdp(8))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(darkChoice("오늘 다시 보지 않기", false) {
            skipPreStartGuideToday()
            showMatPrep(game)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = sdp(6) })
        buttons.addView(cyanButton("시작하기 ▶") { showMatPrep(game) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = sdp(6) })
        root.addView(buttons)
        replaceMenuScreen(root) { if (game) showGameEntrance() else showPracticeEntrance() }
    }

    private fun showMatPrep(game: Boolean) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(14), sdp(14), sdp(14), sdp(12))
        }
        root.addView(buildEntranceHeader("매트 준비", "", null) { if (game) showGameEntrance() else showPracticeEntrance() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val preview = FrameLayout(this).apply {
            background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.line)
            addView(TextView(this@MainActivity).apply {
                text = "카메라 프리뷰\n\n공과 마커 4개가 모두 보이게 맞춰주세요"; gravity = Gravity.CENTER; setTextColor(Pv.textMid); textSize = scaledSp(12f); typeface = Typeface.DEFAULT_BOLD
            }, FrameLayout.LayoutParams(-1, -1))
        }
        body.addView(preview, LinearLayout.LayoutParams(0, -1, 0.63f).apply { marginEnd = dp(8) })
        val guide = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        guide.addView(TextView(this@MainActivity).apply {
            text = "PuttVision\n┌────────────┐\n│ □        □ │\n│     ⚪      │\n│ □        □ │\n└────────────┘\n녹색 박스가 생겼나요?"
            gravity = Gravity.CENTER; textSize = scaledSp(11.5f); typeface = Typeface.MONOSPACE; setTextColor(Pv.primary); background = pvRounded(Pv.primaryDim, Pv.rMd, Pv.primaryLine)
        }, LinearLayout.LayoutParams(-1, 0, 0.65f))
        guide.addView(cyanButton("측정 시작") { startConfiguredSession(game) }, LinearLayout.LayoutParams(-1, 0, 0.35f).apply { topMargin = dp(8) })
        body.addView(guide, LinearLayout.LayoutParams(0, -1, 0.37f))
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

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        box.addView(pvEyebrow("그린 컨디션 · GREEN CONDITIONS"))

        fun row(field: TextView, seekBar: SeekBar) {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = pvRounded(Pv.surfaceHi, Pv.rMd, Pv.lineSoft)
                setPadding(pvDp(12), pvDp(8), pvDp(12), pvDp(4))
            }
            wrap.addView(field)
            wrap.addView(seekBar)
            box.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pvDp(8) })
        }

        fun fieldLabel(text: String): TextView = TextView(this).apply {
            this.text = text
            setTextColor(Pv.textHi)
            textSize = Pv.body
            typeface = Typeface.DEFAULT_BOLD
        }
        fun seek(maxV: Int, progressV: Int, onChange: (Int) -> Unit): SeekBar = SeekBar(this).apply {
            max = maxV
            progress = progressV
            progressTintList = ColorStateList.valueOf(Pv.primary)
            progressBackgroundTintList = ColorStateList.valueOf(Pv.line)
            thumbTintList = ColorStateList.valueOf(Pv.primary)
            setOnSeekBarChangeListener(simpleSeek(onChange))
        }
        fun util(label: String, click: () -> Unit): Button =
            pvButton(label, PvButtonStyle.SECONDARY, textSp = Pv.label, onClick = click)

        val speed = fieldLabel("그린 스피드  ${"%.1f".format(engine.settings.stimpMeters)}m")
        row(speed, seek(20, ((engine.settings.stimpMeters - 2.0) * 10).toInt().coerceIn(0, 20)) {
            engine.settings.stimpMeters = 2.0 + it / 10.0
            speed.text = "그린 스피드  ${"%.1f".format(engine.settings.stimpMeters)}m"
            updateSettingLabels()
        })

        val distance = fieldLabel("홀 거리  ${"%.1f".format(engine.settings.holeDistanceM)}m")
        row(distance, seek(140, ((engine.settings.holeDistanceM - 1.0) * 10).toInt().coerceIn(0, 140)) {
            engine.settings.holeDistanceM = 1.0 + it / 10.0
            distance.text = "홀 거리  ${"%.1f".format(engine.settings.holeDistanceM)}m"
            updateSettingLabels()
        })

        val side = fieldLabel("좌우 경사  ${"%+.1f".format(engine.settings.sideSlopePct)}%")
        row(side, seek(100, (engine.settings.sideSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
            engine.settings.sideSlopePct = (it - 50) / 10.0
            side.text = "좌우 경사  ${"%+.1f".format(engine.settings.sideSlopePct)}%"
            updateSettingLabels()
        })

        val longSlope = fieldLabel("오르막 / 내리막  ${"%+.1f".format(engine.settings.longSlopePct)}%")
        row(longSlope, seek(100, (engine.settings.longSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
            engine.settings.longSlopePct = (it - 50) / 10.0
            longSlope.text = "오르막 / 내리막  ${"%+.1f".format(engine.settings.longSlopePct)}%"
            updateSettingLabels()
        })

        box.addView(pvEyebrow("도구 · TOOLS"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(6) })

        val utility = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utility.addView(util("메뉴") { closeThen { showHomeMenu() } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        utility.addView(util("STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        utility.addView(util("업데이트") {
            closeThen {
                // An updater dialog/installer must never leave a live camera session running behind it.
                if (wasActiveSession) showHomeMenu()
                appUpdater.check(silent = false)
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(utility, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pvDp(8) })

        val utility2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utility2.addView(util("TV 재연결") { displayController.refresh() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        utility2.addView(util("컵 가이드") {
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
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(utility2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pvDp(8) })

        val deployRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        deployRow.addView(
            util("ZIP 배포") {
                closeThen {
                    // DeployActivity is a separate screen. Exit the current measurement session
                    // first so returning from deploy never exposes a suspended/dead HUD.
                    if (wasActiveSession) showHomeMenu()
                    startActivity(Intent(this, DeployActivity::class.java))
                }
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        box.addView(deployRow)

        val dialog = pvDialog(title = "설정 / 환경", content = box, dismissLabel = "닫기")
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
