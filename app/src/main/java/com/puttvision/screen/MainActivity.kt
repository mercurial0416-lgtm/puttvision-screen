package com.puttvision.screen

import android.Manifest
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

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

    private var impactDetected = false
    private var impactPolling = false
    private var recordingStartedAtMs = 0L

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
    private var gamePlayers = 2
    private var gameModeIndex = 0
    private var gameDistanceM = 3

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
                toast("카메라 권한 없으면 측정 못함")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    lastTvStatusMessage = msg
                    tvStatus.text = if (connected) "● TV 연결" else "○ TV 미연결"
                    tvStatus.setTextColor(if (connected) Pv.primary else Pv.textMid)
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

                lastHfrStatusMessage =
                    when {
                        preferred == null -> "HFR 미지원 · 일반 추적 모드"
                        preferred.fps >= 240 -> "HFR ${preferred.fps}fps 가능 · 240fps 우선"
                        else -> "HFR ${preferred.fps}fps 가능"
                    }

                // Header pills must stay short on landscape phones. Tap the pill for detail.
                hfrStatus.text =
                    when {
                        preferred == null -> "HFR 미지원"
                        else -> "HFR ${preferred.fps}fps"
                    }

                maybeAutoStartAfterCalibration()
            }
        }
    }

    private fun buildUi() {
        val compact = compactLandscape
        val outerH = if (compact) 6 else 10
        val outerTop = if (compact) 4 else 8
        val outerBottom = if (compact) 5 else 9
        val headerHeight = if (compact) 40 else 46
        val headerGap = if (compact) 4 else 6

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.ink)
            setPadding(pvDp(outerH), pvDp(outerTop), pvDp(outerH), pvDp(outerBottom))
        }

        // Compact landscape header: never let verbose hardware strings steal the viewport.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pvDp(if (compact) 2 else 4), 0, pvDp(2), 0)
        }

        header.addView(View(this).apply {
            background = pvRounded(Pv.primary, 100f)
        }, LinearLayout.LayoutParams(pvDp(if (compact) 7 else 8), pvDp(if (compact) 7 else 8)).apply {
            marginEnd = pvDp(if (compact) 7 else 9)
        })

        val brandBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandBlock.addView(TextView(this).apply {
            text = "PuttVision Screen"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 14f else 16f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        if (!compact) {
            brandBlock.addView(TextView(this).apply {
                text = "스마트폰 = 런치모니터   ·   TV = 스크린 퍼팅 시뮬레이터"
                setTextColor(Pv.textLo)
                textSize = pvSp(8.5f)
                setPadding(0, pvDp(1), 0, 0)
                maxLines = 1
            })
        }
        header.addView(brandBlock, LinearLayout.LayoutParams(0, -2, 1f))

        hfrStatus = TextView(this).apply {
            text = "HFR 확인중"
            setTextColor(Pv.primary)
            textSize = pvSp(if (compact) 8.8f else 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            background = pvRounded(Pv.surfaceLo, 100f, Pv.line)
            setPadding(pvDp(if (compact) 8 else 11), pvDp(if (compact) 5 else 6), pvDp(if (compact) 8 else 11), pvDp(if (compact) 5 else 6))
            setOnClickListener { toast(lastHfrStatusMessage) }
        }
        header.addView(hfrStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(headerGap) })

        tvStatus = TextView(this).apply {
            text = "○ TV"
            setTextColor(Pv.textMid)
            textSize = pvSp(if (compact) 8.8f else 9.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            background = pvRounded(Pv.surfaceLo, 100f, Pv.line)
            setPadding(pvDp(if (compact) 8 else 10), pvDp(if (compact) 5 else 6), pvDp(if (compact) 8 else 10), pvDp(if (compact) 5 else 6))
            setOnClickListener { toast(lastTvStatusMessage) }
        }
        header.addView(tvStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(headerGap) })

        modeButton = pvButton(engine.gameModes.status.mode.label, PvButtonStyle.SECONDARY, textSp = if (compact) 9.4f else 10.5f, radiusDp = 100f) {
            if (engine.state?.running == true) {
                toast("공 굴러가는 중엔 모드 변경 막아놨음")
            } else {
                val mode = engine.gameModes.nextMode()
                modeButton.text = mode.label
                engine.resetSimulation()
                metricText.text = "${mode.label} · READY"
                updateSettingLabels()
            }
        }
        header.addView(modeButton, LinearLayout.LayoutParams(pvDp(if (compact) 82 else 88), pvDp(if (compact) 31 else 34)).apply {
            marginStart = pvDp(headerGap)
        })

        autoButton = pvButton("AUTO ON", PvButtonStyle.PRIMARY, textSp = if (compact) 9.4f else 10.5f, radiusDp = 100f) {
            autoPlayEnabled = !autoPlayEnabled
            autoGeneration++
            updateAutoButton()
            if (autoPlayEnabled) maybeAutoStartAfterCalibration()
        }
        header.addView(autoButton, LinearLayout.LayoutParams(pvDp(if (compact) 70 else 80), pvDp(if (compact) 31 else 34)).apply {
            marginStart = pvDp(headerGap)
        })

        root.addView(header, LinearLayout.LayoutParams(-1, pvDp(headerHeight)))

        // Camera consumes whatever height remains after a guaranteed-size dashboard.
        // This prevents the dashboard from being squeezed/cropped on 18:9~22:9 phones.
        val cameraFrame = FrameLayout(this).apply {
            background = pvRounded(Pv.surfaceLo, Pv.rLg, Pv.line)
            setPadding(pvDp(if (compact) 2 else 3), pvDp(if (compact) 2 else 3), pvDp(if (compact) 2 else 3), pvDp(if (compact) 2 else 3))
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        overlay = PhoneOverlayView(this)
        cameraFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        replayView = ImpactReplayView(this)
        cameraFrame.addView(replayView, FrameLayout.LayoutParams(-1, -1))

        root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            val m = pvDp(if (compact) 5 else 8)
            setMargins(0, m, 0, m)
        })

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val hPad = pvDp(if (compact) 7 else 10)
            val vPad = pvDp(if (compact) 6 else 10)
            setPadding(hPad, vPad, hPad, vPad)
            background = pvRounded(Pv.surface, Pv.rLg, Pv.lineSoft)
        }

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val metricsSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        metricsSection.addView(pvEyebrow("실시간 측정 · LIVE METRICS").apply {
            if (compact) {
                textSize = pvSp(7.5f)
                setPadding(pvDp(1), 0, 0, pvDp(3))
            }
        })

        fun metricCard(title: String, key: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(pvDp(if (compact) 3 else 5), pvDp(if (compact) 2 else 3), pvDp(if (compact) 3 else 5), pvDp(if (compact) 2 else 3))
                background = pvRounded(Pv.surfaceHi, Pv.rSm, Pv.lineSoft)
            }
            card.addView(TextView(this).apply {
                text = title
                setTextColor(Pv.textMid)
                textSize = pvSp(if (compact) 6.7f else 7.6f)
                gravity = Gravity.CENTER
                maxLines = 1
            })
            val value = TextView(this).apply {
                text = "--"
                setTextColor(Pv.textHi)
                textSize = pvSp(if (compact) 10.6f else 12.4f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, pvDp(if (compact) 0 else 1), 0, 0)
            }
            metricCards[key] = value
            card.addView(value)
            return card
        }

        val metricDefs = listOf(
            "볼 스피드" to "ball",
            "출발 각도" to "launch",
            "헤드 스피드" to "head",
            "페이스 각도" to "face",
            "패스 각도" to "path",
            "Face to Path" to "f2p",
            "임팩트 위치" to "impact",
            "스매시 팩터" to "smash",
            "템포" to "tempo"
        )
        val columns = if (compact) 5 else 3
        val cardH = pvDp(if (compact) 32 else 40)
        val cardGap = pvDp(if (compact) 4 else 5)
        metricDefs.chunked(columns).forEachIndexed { rowIndex, rowDefs ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowDefs.forEachIndexed { i, (title, key) ->
                row.addView(metricCard(title, key), LinearLayout.LayoutParams(0, cardH, 1f).apply {
                    if (i > 0) marginStart = cardGap
                })
            }
            // Keep the second compact row aligned to the same 5-column grid.
            if (compact && rowDefs.size < columns) {
                repeat(columns - rowDefs.size) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, cardH, 1f).apply { marginStart = cardGap })
                }
            }
            metricsSection.addView(row, LinearLayout.LayoutParams(-1, cardH).apply {
                if (rowIndex > 0) topMargin = cardGap
            })
        }

        contentRow.addView(metricsSection, LinearLayout.LayoutParams(0, -1, if (compact) 2.15f else 2.25f).apply {
            marginEnd = pvDp(if (compact) 7 else 10)
        })

        val shotSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pvDp(if (compact) 8 else 11), pvDp(if (compact) 6 else 9), pvDp(if (compact) 8 else 11), pvDp(if (compact) 6 else 9))
            background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.lineSoft)
        }
        shotPanelTitle = TextView(this).apply {
            text = "샷 결과 예측"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 9.2f else 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        shotSection.addView(shotPanelTitle)

        metricText = TextView(this).apply {
            text = "READY\n공을 놓고 퍼팅하세요"
            setTextColor(Pv.textHi)
            textSize = pvSp(if (compact) 9.0f else 10.5f)
            setLineSpacing(pvDp(if (compact) 1 else 2).toFloat(), 1f)
            setPadding(0, pvDp(if (compact) 3 else 6), 0, 0)
            maxLines = if (compact) 4 else 5
        }
        shotSection.addView(metricText, LinearLayout.LayoutParams(-1, 0, 1f))

        settingSummary = TextView(this).apply {
            setTextColor(Pv.primary)
            textSize = pvSp(if (compact) 7.2f else 8.2f)
            typeface = Typeface.MONOSPACE
            maxLines = if (compact) 1 else 2
        }
        shotSection.addView(settingSummary)

        contentRow.addView(shotSection, LinearLayout.LayoutParams(0, -1, if (compact) 1.05f else 0.9f))
        dashboard.addView(contentRow, LinearLayout.LayoutParams(-1, pvDp(if (compact) 82 else 145)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, pvDp(if (compact) 6 else 10), 0, 0)
        }

        val actionH = pvDp(if (compact) 36 else 44)
        actions.addView(
            pvButton("재캘리브레이션", PvButtonStyle.SECONDARY, textSp = if (compact) 9.2f else 10.2f) { beginAutoCalibration() },
            LinearLayout.LayoutParams(0, actionH, 0.9f).apply { marginEnd = pvDp(if (compact) 4 else 6) }
        )
        actions.addView(
            pvButton("PRECISION READY", PvButtonStyle.PRIMARY, textSp = if (compact) 10.0f else 11.5f) { armPrecision() },
            LinearLayout.LayoutParams(0, actionH, 1.25f).apply {
                marginStart = pvDp(2)
                marginEnd = pvDp(2)
            }
        )
        settingsToggle = pvButton("설정 / 환경", PvButtonStyle.SECONDARY, textSp = if (compact) 9.2f else 10.2f) { showSettingsDialog() }
        actions.addView(settingsToggle, LinearLayout.LayoutParams(0, actionH, 0.9f).apply {
            marginStart = pvDp(if (compact) 4 else 6)
        })
        dashboard.addView(actions)

        // Keep these initialized for existing settings/update logic; actual controls live in the dialog.
        settingsPanel = LinearLayout(this).apply { visibility = View.GONE }
        speedLabel = settingLabel()
        distanceLabel = settingLabel()
        sideLabel = settingLabel()
        longLabel = settingLabel()

        updateSettingLabels()
        updateAutoButton()

        // Wrap content instead of weight=1: the old weighted dashboard got squeezed on short
        // landscape devices, which clipped metric rows and the shot panel while buttons overlapped.
        root.addView(dashboard, LinearLayout.LayoutParams(-1, -2))

        val shell = FrameLayout(this)
        shell.addView(root, FrameLayout.LayoutParams(-1, -1))
        menuOverlay = buildSmartPuttMenu()
        shell.addView(menuOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(shell)
    }

    private fun buildSmartPuttMenu(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Pv.inkDeep)
            addView(buildVideoHomeScreen(), FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun replaceMenuScreen(view: View) {
        menuOverlay.removeAllViews()
        menuOverlay.addView(view, FrameLayout.LayoutParams(-1, -1))
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
            setOnClickListener { click() }
        }

    private fun darkChoice(label: String, selected: Boolean, click: () -> Unit): Button =
        pvChip(label, selected, onClick = click)

    private fun roundMenuIcon(symbol: String, label: String, click: () -> Unit): LinearLayout =
        pvIconControl(symbol, label, click)

    private fun buildVideoHomeScreen(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Pv.inkDeep)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), 0)
        }
        top.addView(roundMenuIcon("◎", "언어") { toast("한국어") })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(roundMenuIcon("⚙", "환경") { showSettingsDialog() })
        top.addView(roundMenuIcon("⇥", "종료") { finishAffinity() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(20))
        }

        // Course visual: a lush green gradient panel that anchors the brand.
        val visual = FrameLayout(this).apply {
            background = pvVGradient(Color.rgb(104, 170, 84), Color.rgb(44, 94, 52), Pv.rXl)
        }
        visual.addView(TextView(this).apply {
            text = "⛳"
            textSize = 92f
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
        body.addView(visual, LinearLayout.LayoutParams(0, -1, 0.42f).apply { marginEnd = dp(18) })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(this).apply {
            text = "Putt"
            textSize = 46f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Pv.primary)
        })
        title.addView(TextView(this).apply {
            text = "Vision"
            textSize = 46f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Pv.textHi)
        })
        right.addView(title)
        right.addView(TextView(this).apply {
            text = "SCREEN PUTTING SIMULATOR"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.22f
            setTextColor(Pv.textLo)
            setPadding(dp(3), dp(2), 0, 0)
        })

        fun homeTile(titleText: String, accent: Int, click: () -> Unit): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = pvRounded(Pv.surfaceHi, Pv.rLg, Pv.line)
                setPadding(dp(24), dp(14), dp(18), dp(14))
                addView(TextView(this@MainActivity).apply {
                    text = titleText
                    textSize = 23f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Pv.textHi)
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(View(this@MainActivity).apply {
                    background = pvRounded(accent, 100f)
                }, LinearLayout.LayoutParams(dp(48), dp(4)).apply { topMargin = dp(8) })
                setOnClickListener { click() }
            }

        right.addView(homeTile("⛳  연습장", Pv.primary) { showPracticeEntrance() }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(20) })
        right.addView(homeTile("♟  게임장", Pv.amber) { showGameEntrance() }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(12) })
        body.addView(right, LinearLayout.LayoutParams(0, -1, 0.58f))
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
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
        replaceMenuScreen(buildPracticeEntrance())
    }

    private fun buildPracticeEntrance(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(14), sdp(14), sdp(14), sdp(12))
        }
        root.addView(buildEntranceHeader("연습장 입구", "Practice Mode Entrance", { showCupGuideScreen() }) { showHomeMenu() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val upper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modePanel = sectionPanel()
        modePanel.addView(tinyCaption("모드"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("거리", "컵", "그린").forEachIndexed { i, label ->
            modeRow.addView(darkChoice(label, practiceEntranceMode == i) { practiceEntranceMode = i; showPracticeEntrance() }, LinearLayout.LayoutParams(0, sdp(48), 1f).apply { if (i > 0) marginStart = dp(3) })
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
                row.addView(darkChoice(v.toString(), practiceCount == v) { practiceCount = v; showPracticeEntrance() }, LinearLayout.LayoutParams(0, sdp(36), 1f).apply { if (c > 0) marginStart = dp(3) })
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
        lower.addView(tinyCaption(if (practiceEntranceMode == 1) "컵" else "거리"))
        val lowerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (practiceEntranceMode == 1) {
            listOf(3, 5, 7, 10).forEachIndexed { i, v ->
                lowerRow.addView(darkChoice("${v}m", practiceDistanceM == v) { practiceDistanceM = v; showPracticeEntrance() }, LinearLayout.LayoutParams(0, sdp(48), 1f).apply { if (i > 0) marginStart = dp(4) })
            }
        } else {
            listOf("고정", "랜덤", "증가", "감소").forEachIndexed { i, label ->
                lowerRow.addView(darkChoice(label, i == 0) { toast("$label 모드") }, LinearLayout.LayoutParams(0, sdp(48), 1f).apply { if (i > 0) marginStart = dp(4) })
            }
        }
        lower.addView(lowerRow)
        val distanceLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        distanceLine.addView(TextView(this).apply { text = "2m"; setTextColor(Pv.textMid); textSize = 11f; typeface = Typeface.MONOSPACE })
        val dSeek = SeekBar(this).apply {
            max = 13
            progress = (practiceDistanceM - 2).coerceIn(0, 13)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { practiceDistanceM = progress + 2 }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { showPracticeEntrance() }
            })
        }
        distanceLine.addView(dSeek, LinearLayout.LayoutParams(0, sdp(36), 1f).apply { marginStart = sdp(10); marginEnd = sdp(10) })
        distanceLine.addView(TextView(this).apply { text = "15m"; setTextColor(Pv.textMid); textSize = 11f; typeface = Typeface.MONOSPACE })
        lower.addView(distanceLine)
        left.addView(lower, LinearLayout.LayoutParams(-1, 0, 0.48f).apply { topMargin = dp(6) })

        content.addView(left, LinearLayout.LayoutParams(0, -1, 0.81f).apply { marginEnd = sdp(10) })
        content.addView(cyanButton("▶\n입장") { showPreStartGuide(false) }, LinearLayout.LayoutParams(0, -1, 0.19f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showGameEntrance() {
        replaceMenuScreen(buildGameEntrance())
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
                row.addView(darkChoice(v.toString(), gamePlayers == v) { gamePlayers = v; showGameEntrance() }, LinearLayout.LayoutParams(0, sdp(36), 1f).apply { if (c > 0) marginStart = dp(3) })
            }
            pGrid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dp(3) })
        }
        players.addView(pGrid)
        upper.addView(players, LinearLayout.LayoutParams(0, -1, 0.18f).apply { marginEnd = dp(5) })

        val modes = sectionPanel(); modes.addView(tinyCaption("게임 방식"))
        val mRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("스트로크", "매치\n플레이", "빙고", "투어").forEachIndexed { i, label ->
            mRow.addView(darkChoice(label, gameModeIndex == i) { gameModeIndex = i; showGameEntrance() }, LinearLayout.LayoutParams(0, sdp(54), 1f).apply { if (i > 0) marginStart = dp(3) })
        }
        modes.addView(mRow)
        upper.addView(modes, LinearLayout.LayoutParams(0, -1, 0.47f).apply { marginEnd = dp(5) })

        val speed = sectionPanel(); speed.addView(tinyCaption("그린스피드"))
        speed.addView(TextView(this).apply {
            text = "2.8\n약간빠름"; gravity = Gravity.CENTER; textSize = scaledSp(18f); typeface = Typeface.DEFAULT_BOLD; setTextColor(Pv.textHi)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        upper.addView(speed, LinearLayout.LayoutParams(0, -1, 0.35f))
        left.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.52f))

        val lower = sectionPanel(); lower.addView(tinyCaption(if (gameModeIndex == 3) "노선" else "거리"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = if (gameModeIndex == 3) listOf("수도권", "충청권", "영남권") else listOf("3m", "5m", "7m", "10m")
        labels.forEachIndexed { i, label ->
            row.addView(darkChoice(label, if (gameModeIndex == 3) i == 0 else gameDistanceM == label.removeSuffix("m").toInt()) {
                if (gameModeIndex != 3) gameDistanceM = label.removeSuffix("m").toInt()
                showGameEntrance()
            }, LinearLayout.LayoutParams(0, sdp(54), 1f).apply { if (i > 0) marginStart = dp(6) })
        }
        lower.addView(row)
        left.addView(lower, LinearLayout.LayoutParams(-1, 0, 0.48f).apply { topMargin = dp(6) })

        content.addView(left, LinearLayout.LayoutParams(0, -1, 0.81f).apply { marginEnd = sdp(10) })
        content.addView(cyanButton("▶\n입장") { showPreStartGuide(true) }, LinearLayout.LayoutParams(0, -1, 0.19f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun showCupGuideScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pv.inkDeep)
            setPadding(sdp(18), sdp(14), sdp(18), sdp(12))
        }
        root.addView(buildEntranceHeader("컵 가이드", "", null) { showPracticeEntrance() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = sdp(8) })
        val panels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chart = sectionPanel().apply {
            gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = "③  ─────────────●\n\n②  ────────────●\n\n①  ───────────●\n\n     Ready Line"
                textSize = 17f
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
                    text = t; gravity = Gravity.CENTER; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
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
            text = "※ 1클럽 = 6컵"; gravity = Gravity.CENTER; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Pv.textLo); setPadding(0, dp(8), 0, 0)
        })
        panels.addView(table, LinearLayout.LayoutParams(0, -1, 0.43f))
        root.addView(panels, LinearLayout.LayoutParams(-1, 0, 1f))
        replaceMenuScreen(root)
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
            text = textValue; gravity = Gravity.CENTER; textSize = 15f; typeface = Typeface.MONOSPACE; setTextColor(Pv.textMid); background = pvRounded(Pv.surface, Pv.rMd, Pv.lineSoft)
        }
        pics.addView(diagram("카메라\n   │\n   ▼\n▰  매트  ⚪"), LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(6) })
        pics.addView(diagram("휴대폰 위치\n↘\n┌────────┐\n│  매트  │\n└────────┘"), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(6) })
        root.addView(pics, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "충분히 밝은 곳에서 카메라와 매트를 정확히 맞춰주세요."; gravity = Gravity.CENTER; textSize = 11f; setTextColor(Pv.textLo); setPadding(0, dp(10), 0, dp(10))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(darkChoice("오늘 다시 보지 않기", false) { showMatPrep(game) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        buttons.addView(cyanButton("시작하기 ▶") { showMatPrep(game) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        root.addView(buttons)
        replaceMenuScreen(root)
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
                text = "카메라 프리뷰\n\n공과 마커 4개가 모두 보이게 맞춰주세요"; gravity = Gravity.CENTER; setTextColor(Pv.textMid); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            }, FrameLayout.LayoutParams(-1, -1))
        }
        body.addView(preview, LinearLayout.LayoutParams(0, -1, 0.63f).apply { marginEnd = dp(8) })
        val guide = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        guide.addView(TextView(this@MainActivity).apply {
            text = "PuttVision\n┌────────────┐\n│ □        □ │\n│     ⚪      │\n│ □        □ │\n└────────────┘\n녹색 박스가 생겼나요?"
            gravity = Gravity.CENTER; textSize = 13f; typeface = Typeface.MONOSPACE; setTextColor(Pv.primary); background = pvRounded(Pv.primaryDim, Pv.rMd, Pv.primaryLine)
        }, LinearLayout.LayoutParams(-1, 0, 0.65f))
        guide.addView(cyanButton("측정 시작") { startConfiguredSession(game) }, LinearLayout.LayoutParams(-1, 0, 0.35f).apply { topMargin = dp(8) })
        body.addView(guide, LinearLayout.LayoutParams(0, -1, 0.37f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        replaceMenuScreen(root)
    }

    private fun startConfiguredSession(game: Boolean) {
        engine.settings.holeDistanceM = if (game) gameDistanceM.toDouble() else practiceDistanceM.toDouble()
        engine.settings.stimpMeters = practiceGreenSpeed
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
        menuOverlay.visibility = View.GONE
    }

    private fun showHomeMenu() {
        replaceMenuScreen(buildVideoHomeScreen())
    }

    private fun showSettingsDialog() {
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
        utility.addView(util("메뉴") { showHomeMenu() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        utility.addView(util("STATS") { showStats() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        utility.addView(util("업데이트") { appUpdater.check(silent = false) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(utility, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pvDp(8) })

        val utility2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utility2.addView(util("TV 재연결") { displayController.refresh() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        utility2.addView(util("컵 가이드") { showCupGuideScreen() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(utility2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pvDp(8) })

        val deployRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        deployRow.addView(
            util("ZIP 배포") {
                startActivity(Intent(this, DeployActivity::class.java))
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        box.addView(deployRow)

        pvDialog(title = "설정 / 환경", content = box, dismissLabel = "닫기").show()
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
        val future =
            ProcessCameraProvider.getInstance(
                this
            )

        future.addListener(
            {
                provider =
                    future.get()

                beginAutoCalibration()
            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    private fun beginAutoCalibration() {
        if (!::provider.isInitialized) {
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
                        overlay.lastOverlay =
                            visual

                        overlay.invalidate()
                    }
                },
                onShotReady = { metrics ->
                    runOnUiThread {
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
        if (
            !::provider.isInitialized ||
            homography == null
        ) {
            toast(
                "자동 캘리브레이션 먼저"
            )

            return
        }

        stopSimulation()

        engine.gameModes
            .prepareNextIfNeeded()

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
                        hfrStatus.text =
                            msg

                        overlay.status =
                            msg

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

            hfrStatus.text =
                "HFR 바인딩 실패 · NORMAL fallback"

            beginAutoCalibration()

            return
        }

        hfrStatus.text =
            "PRECISION ${session.fps}fps"

        mainHandler.postDelayed(
            {
                startHfrRecording()
            },
            420L
        )
    }

    private fun startHfrRecording() {
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

        controller.start(
            onStart = { _, fps ->
                runOnUiThread {
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
                    impactPolling = false

                    if (
                        error != null ||
                        file == null
                    ) {
                        hfrStatus.text =
                            "HFR 저장 실패"

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
                            hfrStatus.text =
                                progress
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
                if (result == null) {
                    overlay.status =
                        "HFR 분석 실패 · QR/공/헤드마커 확인"

                    metricText.text =
                        "영상에 QR4개 + 흰 공 + 주황/파랑 헤드마커가 보여야 함."

                    hfrStatus.text =
                        "PRECISION 분석 실패"

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

                    hfrStatus.text =
                        "✓ ${result.fps}fps · ${result.analyzedFrames} frames · F${result.impactFrame}"
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

                        showFinalShotSummary(
                            result
                        )

                        if (
                            autoPlayEnabled &&
                            !engine.gameModes.status.completed
                        ) {
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

                if (game.completed) {
                    append(" · COMPLETE")
                }

                coach?.let {
                    append(
                        "\nCOACH: ${it.headline} — ${it.detail}"
                    )
                }
            }
    }

    private fun scheduleAutoNext() {
        val generation =
            ++autoGeneration

        mainHandler.postDelayed(
            {
                if (
                    autoPlayEnabled &&
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
        if (!autoPlayEnabled) {
            return
        }

        val generation =
            ++autoGeneration

        mainHandler.postDelayed(
            {
                if (
                    autoPlayEnabled &&
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
        impactPolling = false

        hfrController?.close()
        hfrController = null
    }

    private fun stopSimulation() {
        simulationTicking = false
        simHandler.removeCallbacksAndMessages(null)
        lastSimulationNs = 0L
    }

    private fun showStats() {
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

        pvDialog(
            title = "STATS",
            content = box,
            dismissLabel = "닫기",
            extraActions = listOf("기록 초기화" to {
                statsRepository.clear()
                engine.seedHistory(emptyList())
                toast("기록 초기화함")
            })
        ).show()
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
        super.onDestroy()

        autoPlayEnabled = false
        cancelPendingAuto()

        stopSimulation()

        impactPolling = false
        mainHandler.removeCallbacksAndMessages(
            null
        )

        hfrController?.close()
        hfrController = null

        displayController.stop()

        calibrator?.close()

        cameraExecutor.shutdown()
    }
}
