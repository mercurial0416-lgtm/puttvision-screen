package com.puttvision.screen

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
                    tvStatus.text =
                        if (connected) {
                            "● $msg"
                        } else {
                            "○ $msg"
                        }

                    tvStatus.setTextColor(
                        if (connected) {
                            Color.rgb(
                                137,
                                247,
                                176
                            )
                        } else {
                            Color.LTGRAY
                        }
                    )
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

                hfrStatus.text =
                    when {
                        preferred == null ->
                            "HFR 미지원 · NORMAL"

                        preferred.fps >= 240 ->
                            "HFR ${preferred.fps}fps 가능 · 240 우선"

                        else ->
                            "HFR ${preferred.fps}fps 가능"
                    }

                maybeAutoStartAfterCalibration()
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 10, 14))
            setPadding(dp(8), dp(6), dp(8), dp(7))
        }

        fun compactButton(label: String, accent: Boolean = false, click: () -> Unit): Button =
            Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(10), 0, dp(10), 0)
                setTextColor(if (accent) Color.WHITE else Color.rgb(225, 232, 239))
                backgroundTintList = ColorStateList.valueOf(
                    if (accent) Color.rgb(89, 58, 204) else Color.rgb(22, 31, 40)
                )
                setOnClickListener { click() }
            }

        // Header follows the original concept preview: brand + precision status + compact controls.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), 0)
        }

        val brandBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandBlock.addView(TextView(this).apply {
            text = "PuttVision Screen"
            setTextColor(Color.WHITE)
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
        })
        brandBlock.addView(TextView(this).apply {
            text = "스마트폰 = 런치모니터  ·  TV = 스크린 퍼팅 시뮬레이터"
            setTextColor(Color.rgb(145, 159, 173))
            textSize = 8.5f
        })
        header.addView(brandBlock, LinearLayout.LayoutParams(0, -2, 1.25f))

        hfrStatus = TextView(this).apply {
            text = "HFR 확인중"
            setTextColor(Color.rgb(146, 255, 177))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(Color.rgb(14, 24, 31), 10f, Color.rgb(51, 74, 91))
            setPadding(dp(9), dp(4), dp(9), dp(4))
        }
        header.addView(hfrStatus, LinearLayout.LayoutParams(0, -2, 0.72f).apply {
            marginStart = dp(5)
        })

        tvStatus = TextView(this).apply {
            text = "○ TV"
            setTextColor(Color.LTGRAY)
            textSize = 9.5f
            gravity = Gravity.CENTER
            setPadding(dp(7), 0, dp(7), 0)
        }
        header.addView(tvStatus)

        modeButton = compactButton(engine.gameModes.status.mode.label) {
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
        header.addView(modeButton, LinearLayout.LayoutParams(dp(84), dp(34)).apply { marginStart = dp(5) })

        autoButton = compactButton("AUTO ON", true) {
            autoPlayEnabled = !autoPlayEnabled
            autoGeneration++
            updateAutoButton()
            if (autoPlayEnabled) maybeAutoStartAfterCalibration()
        }
        header.addView(autoButton, LinearLayout.LayoutParams(dp(78), dp(34)).apply { marginStart = dp(5) })

        root.addView(header, LinearLayout.LayoutParams(-1, dp(45)))

        // Camera is now the hero panel, exactly like the concept preview.
        val cameraFrame = FrameLayout(this).apply {
            background = roundedBg(Color.rgb(9, 14, 17), 18f, Color.rgb(46, 61, 72))
            setPadding(dp(2), dp(2), dp(2), dp(2))
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

        root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1.55f).apply {
            setMargins(0, dp(3), 0, dp(5))
        })

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = roundedBg(Color.rgb(11, 17, 23), 18f, Color.rgb(29, 43, 54))
        }

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val metricsSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        metricsSection.addView(TextView(this).apply {
            text = "실시간 측정"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), 0, 0, dp(4))
        })

        fun metricCard(title: String, key: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(2), dp(4), dp(2))
                background = roundedBg(Color.rgb(20, 29, 38), 9f, Color.rgb(34, 47, 59))
            }
            card.addView(TextView(this).apply {
                text = title
                setTextColor(Color.rgb(151, 164, 178))
                textSize = 7.8f
                gravity = Gravity.CENTER
            })
            val value = TextView(this).apply {
                text = "--"
                setTextColor(Color.WHITE)
                textSize = 12.4f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            metricCards[key] = value
            card.addView(value)
            return card
        }

        val metricDefs = listOf(
            listOf("볼 스피드" to "ball", "출발 각도" to "launch", "헤드 스피드" to "head"),
            listOf("페이스 각도" to "face", "패스 각도" to "path", "Face to Path" to "f2p"),
            listOf("임팩트 위치" to "impact", "스매시 팩터" to "smash", "템포" to "tempo")
        )
        metricDefs.forEachIndexed { rowIndex, rowDefs ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowDefs.forEachIndexed { i, (title, key) ->
                row.addView(metricCard(title, key), LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (i > 0) marginStart = dp(3)
                })
            }
            metricsSection.addView(row, LinearLayout.LayoutParams(-1, dp(40)).apply {
                if (rowIndex > 0) topMargin = dp(3)
            })
        }

        contentRow.addView(metricsSection, LinearLayout.LayoutParams(0, -1, 2.25f).apply {
            marginEnd = dp(7)
        })

        val shotSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = roundedBg(Color.rgb(15, 23, 30), 12f, Color.rgb(38, 52, 64))
        }
        shotPanelTitle = TextView(this).apply {
            text = "샷 결과 예측"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        shotSection.addView(shotPanelTitle)

        metricText = TextView(this).apply {
            text = "READY\n공을 놓고 퍼팅하세요"
            setTextColor(Color.rgb(225, 235, 241))
            textSize = 10.2f
            setPadding(0, dp(5), 0, 0)
            maxLines = 5
        }
        shotSection.addView(metricText, LinearLayout.LayoutParams(-1, 0, 1f))

        settingSummary = TextView(this).apply {
            setTextColor(Color.rgb(132, 247, 174))
            textSize = 8.2f
            maxLines = 2
        }
        shotSection.addView(settingSummary)

        contentRow.addView(shotSection, LinearLayout.LayoutParams(0, -1, 0.9f))
        dashboard.addView(contentRow, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        fun actionButton(label: String, purple: Boolean = false, click: () -> Unit): Button =
            Button(this).apply {
                text = label
                isAllCaps = false
                textSize = if (purple) 11.5f else 10.2f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(
                    if (purple) Color.rgb(96, 57, 211) else Color.rgb(20, 31, 40)
                )
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { click() }
            }

        actions.addView(actionButton("재캘리브레이션") { beginAutoCalibration() }, LinearLayout.LayoutParams(0, dp(42), 0.9f).apply {
            marginEnd = dp(5)
        })
        actions.addView(actionButton("PRECISION READY", true) { armPrecision() }, LinearLayout.LayoutParams(0, dp(42), 1.25f).apply {
            marginStart = dp(2); marginEnd = dp(2)
        })
        settingsToggle = actionButton("설정 / 환경") { showSettingsDialog() }
        actions.addView(settingsToggle, LinearLayout.LayoutParams(0, dp(42), 0.9f).apply {
            marginStart = dp(5)
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

        root.addView(dashboard, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showSettingsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        scroll.addView(box)

        fun title(text: String): TextView = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(225, 232, 238))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        fun seek(maxV: Int, progressV: Int, onChange: (Int) -> Unit): SeekBar = SeekBar(this).apply {
            max = maxV
            progress = progressV
            progressTintList = ColorStateList.valueOf(Color.rgb(111, 89, 232))
            thumbTintList = ColorStateList.valueOf(Color.rgb(138, 118, 255))
            setOnSeekBarChangeListener(simpleSeek(onChange))
        }
        fun util(label: String, click: () -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(32, 42, 51))
            setOnClickListener { click() }
        }

        val speed = title("그린 스피드  ${"%.1f".format(engine.settings.stimpMeters)}m")
        box.addView(speed)
        box.addView(seek(20, ((engine.settings.stimpMeters - 2.0) * 10).toInt().coerceIn(0, 20)) {
            engine.settings.stimpMeters = 2.0 + it / 10.0
            speed.text = "그린 스피드  ${"%.1f".format(engine.settings.stimpMeters)}m"
            updateSettingLabels()
        })

        val distance = title("홀 거리  ${"%.1f".format(engine.settings.holeDistanceM)}m")
        box.addView(distance)
        box.addView(seek(140, ((engine.settings.holeDistanceM - 1.0) * 10).toInt().coerceIn(0, 140)) {
            engine.settings.holeDistanceM = 1.0 + it / 10.0
            distance.text = "홀 거리  ${"%.1f".format(engine.settings.holeDistanceM)}m"
            updateSettingLabels()
        })

        val side = title("좌우 경사  ${"%+.1f".format(engine.settings.sideSlopePct)}%")
        box.addView(side)
        box.addView(seek(100, (engine.settings.sideSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
            engine.settings.sideSlopePct = (it - 50) / 10.0
            side.text = "좌우 경사  ${"%+.1f".format(engine.settings.sideSlopePct)}%"
            updateSettingLabels()
        })

        val longSlope = title("오르막 / 내리막  ${"%+.1f".format(engine.settings.longSlopePct)}%")
        box.addView(longSlope)
        box.addView(seek(100, (engine.settings.longSlopePct * 10 + 50).toInt().coerceIn(0, 100)) {
            engine.settings.longSlopePct = (it - 50) / 10.0
            longSlope.text = "오르막 / 내리막  ${"%+.1f".format(engine.settings.longSlopePct)}%"
            updateSettingLabels()
        })

        val utility = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        utility.addView(util("STATS") { showStats() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
        utility.addView(util("TV 재연결") { displayController.refresh() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        utility.addView(util("업데이트") { appUpdater.check(silent = false) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        box.addView(utility)

        AlertDialog.Builder(this)
            .setTitle("PuttVision 설정 / 환경")
            .setView(scroll)
            .setPositiveButton("닫기", null)
            .show()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedBg(color: Int, radiusDp: Float, strokeColor: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
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

        autoButton.backgroundTintList = ColorStateList.valueOf(
            if (autoPlayEnabled) Color.rgb(126, 238, 174) else Color.rgb(58, 66, 62)
        )
        autoButton.setTextColor(
            if (autoPlayEnabled) Color.rgb(4, 24, 14) else Color.WHITE
        )
    }

    private fun settingLabel(): TextView =
        TextView(this).apply {
            setTextColor(
                Color.rgb(
                    215,
                    225,
                    219
                )
            )

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
        val summary =
            statsRepository.summary()

        val recent =
            statsRepository.recent(
                10
            )

        val text =
            buildString {
                append(
                    "누적 ${summary.shots}구"
                )

                append(
                    "\n홀인 ${summary.made} · ${"%.1f".format(summary.makePct)}%"
                )

                append(
                    "\nPerfect 평균 ${"%.1f".format(summary.avgScore)}"
                )

                append(
                    "\n출발각 평균 ${"%+.2f".format(summary.avgLaunch)}°"
                )

                append(
                    " · 편차 ${"%.2f".format(summary.launchStd)}°"
                )

                summary.avgFace?.let {
                    append(
                        "\nFace 평균 ${"%+.2f".format(it)}°"
                    )
                }

                summary.avgPath?.let {
                    append(
                        " · Path ${"%+.2f".format(it)}°"
                    )
                }

                summary.avgDistanceErrorCm?.let {
                    append(
                        "\n평균 컵 오차 ${"%.0f".format(it)}cm"
                    )
                }

                if (recent.isNotEmpty()) {
                    append(
                        "\n\n최근 ${recent.size}구 Perfect: "
                    )

                    append(
                        recent.joinToString(
                            ", "
                        ) {
                            it.strokeScore.total.toString()
                        }
                    )
                }
            }

        AlertDialog.Builder(this)
            .setTitle(
                "PuttVision Stats"
            )
            .setMessage(
                text
            )
            .setPositiveButton(
                "닫기",
                null
            )
            .setNeutralButton(
                "기록 초기화"
            ) { _, _ ->
                statsRepository.clear()
                engine.seedHistory(
                    emptyList()
                )

                toast(
                    "기록 초기화함"
                )
            }
            .show()
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
