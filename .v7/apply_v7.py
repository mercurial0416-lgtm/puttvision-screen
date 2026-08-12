from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing target: {label}")
    return text.replace(old, new, 1)


# ---- MainActivity ---------------------------------------------------------
p = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
s = p.read_text()

s = replace_once(
    s,
    '''    private lateinit var statsRepository: StatsRepository
    private lateinit var appUpdater: AppUpdater

    private val engine = GameEngine()''',
    '''    private lateinit var statsRepository: StatsRepository
    private lateinit var appUpdater: AppUpdater
    private lateinit var putterProfileStore: PutterProfileStore
    private lateinit var matCalibrationManager: MatCalibrationManager
    private lateinit var voiceCoach: HandsFreeVoiceCoach
    private val cameraStability = CameraStabilityController()

    private val engine = GameEngine()''',
    "product system fields",
)

s = replace_once(
    s,
    '''        statsRepository =
            StatsRepository(this)

        appUpdater = AppUpdater(this)''',
    '''        statsRepository =
            StatsRepository(this)

        putterProfileStore = PutterProfileStore(this)
        matCalibrationManager = MatCalibrationManager(this)
        voiceCoach = HandsFreeVoiceCoach(this)
        appUpdater = AppUpdater(this)''',
    "product system init",
)

s = replace_once(
    s,
    '''    private fun replaceMenuScreen(view: View, backAction: (() -> Unit)? = null) {
        menuBackAction = backAction
        menuOverlay.removeAllViews()
        menuOverlay.addView(view, FrameLayout.LayoutParams(-1, -1))
        menuOverlay.isClickable = true
        menuOverlay.visibility = View.VISIBLE
    }''',
    '''    private fun replaceMenuScreen(view: View, backAction: (() -> Unit)? = null) {
        menuBackAction = backAction
        menuOverlay.animate().cancel()
        menuOverlay.removeAllViews()
        menuOverlay.addView(view, FrameLayout.LayoutParams(-1, -1))
        menuOverlay.isClickable = true
        menuOverlay.visibility = View.VISIBLE
        view.animateProductEnter()
    }''',
    "menu transition",
)

s = replace_once(
    s,
    '''            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun darkChoice''',
    '''            isClickable = true
            isFocusable = true
            installProductPressFeedback()
            setOnClickListener { click() }
        }

    private fun darkChoice''',
    "cyan button feedback",
)

s = replace_once(
    s,
    '''        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    actionPanel.addView(launchButton''',
    '''        isClickable = true
        isFocusable = true
        installProductPressFeedback()
        setOnClickListener { click() }
    }

    actionPanel.addView(launchButton''',
    "home launch feedback",
)

s = replace_once(
    s,
    '''        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    tools.addView(tool("NAVIGATION"''',
    '''        isClickable = true
        isFocusable = true
        installProductPressFeedback()
        setOnClickListener { click() }
    }

    tools.addView(tool("NAVIGATION"''',
    "settings tool feedback",
)

s = replace_once(
    s,
    '''    tools.addView(tool("ANALYTICS", "샷 기록 / STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DISPLAY", "TV 다시 연결") { displayController.refresh() }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })''',
    '''    tools.addView(tool("ANALYTICS", "샷 기록 / STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("PRODUCT", "장비 · 매트 · 핸즈프리") {
        showProductSetupDialog(this, putterProfileStore, matCalibrationManager, voiceCoach)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DISPLAY", "TV 다시 연결") { displayController.refresh() }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })''',
    "product setup settings row",
)

s = replace_once(
    s,
    '''        provider.unbindAll()

        homography = null''',
    '''        cameraStability.release()
        provider.unbindAll()

        homography = null''',
    "release camera lock before rebind",
)

s = replace_once(
    s,
    '''                        val quality = CalibrationQuality.evaluate(result)''',
    '''                        val quality = ProductCalibrationQuality.evaluate(result)''',
    "calibration quality V2",
)

s = replace_once(
    s,
    '''                            metricText.text = "${quality.score}점 · ${quality.hint}"
                            overlay.invalidate()
                            return@runOnUiThread''',
    '''                            metricText.text = "${quality.score}점 · ${quality.hint}"
                            overlay.invalidate()
                            if (::voiceCoach.isInitialized) voiceCoach.speakCalibrationProblem(quality.hint)
                            return@runOnUiThread''',
    "calibration voice failure",
)

s = replace_once(
    s,
    '''        try {
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

            overlay.status =''',
    '''        try {
            val camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            cameraStability.stabilize(camera, previewView)

            overlay.status =''',
    "normal camera focus exposure stabilization",
)

s = replace_once(
    s,
    '''        applyPracticeTargetForNextShot()
        updateSettingLabels()

        engine.resetSimulation()''',
    '''        applyPracticeTargetForNextShot()
        updateSettingLabels()

        if (::voiceCoach.isInitialized) {
            voiceCoach.speakReady(GreenReadAdvisor.read(engine.settings))
        }
        if (::previewView.isInitialized) previewView.productHaptic()

        engine.resetSimulation()''',
    "handsfree ready cue",
)

s = replace_once(
    s,
    '''        val confidence = metrics.confidence
        if (confidence != null && confidence < 0.65) {''',
    '''        val processedMetrics = if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.applyFallback(metrics)
        } else metrics
        val confidence = processedMetrics.confidence
        val rejectThreshold = if (source.startsWith("PRECISION")) 0.65 else 0.38
        if (confidence != null && confidence < rejectThreshold) {''',
    "measurement quality threshold and mat fallback",
)

s = replace_once(
    s,
    '''            setHfrStatus("재측정", "측정 신뢰도 ${pct}% · 자동 폐기")
            scheduleAutoRetry(850L)''',
    '''            setHfrStatus("재측정", "측정 신뢰도 ${pct}% · 자동 폐기")
            if (::voiceCoach.isInitialized) voiceCoach.speakRetry(pct)
            scheduleAutoRetry(850L)''',
    "low quality voice retry",
)

s = replace_once(
    s,
    '''        updateMetricCards(metrics)

        engine.launch(
            metrics
        )''',
    '''        updateMetricCards(processedMetrics)
        if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.observe(processedMetrics)
        }

        engine.launch(
            processedMetrics
        )''',
    "accepted metric processing",
)

s = replace_once(
    s,
    '''            replayView.play(
                it,
                metrics
            )''',
    '''            replayView.play(
                it,
                processedMetrics
            )''',
    "replay processed metrics",
)

s = replace_once(
    s,
    '''                coach?.let {
                    append(
                        "\\nCOACH: ${it.headline} — ${it.detail}"
                    )
                }
            }
    }

    private fun scheduleAutoNext()''',
    '''                coach?.let {
                    append(
                        "\\nCOACH: ${it.headline} — ${it.detail}"
                    )
                }
            }

        if (::voiceCoach.isInitialized) {
            voiceCoach.speakResult(result, engine.currentShot?.launchAngleDeg)
        }
        if (::previewView.isInitialized) previewView.productHaptic()
    }

    private fun scheduleAutoNext()''',
    "handsfree result cue",
)

s = replace_once(
    s,
    '''        val summary = statsRepository.summary()
        val recent = statsRepository.recent(10)

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }''',
    '''        val summary = statsRepository.summary()
        val recent = statsRepository.recent(10)
        val nowMs = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = LocalDate.now().minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val todaySummary = statsRepository.summary(statsRepository.between(todayStart, nowMs + 1))
        val weekSummary = statsRepository.summary(statsRepository.between(weekStart, nowMs + 1))
        val monthSummary = statsRepository.summary(statsRepository.between(monthStart, nowMs + 1))
        val currentPutterName = if (::putterProfileStore.isInitialized) putterProfileStore.current().name else ProductRuntime.putterProfileName
        val putterSummary = statsRepository.summary(statsRepository.all().filter { it.putterProfileName == currentPutterName })

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }''',
    "time and putter stats summaries",
)

s = replace_once(
    s,
    '''        statRow(
            "누적 샷" to "${summary.shots}구",
            "홀인" to "${summary.made} · ${"%.0f".format(summary.makePct)}%"
        )
        statRow(
            "Perfect 평균" to "%.1f".format(summary.avgScore),''',
    '''        statRow(
            "누적 샷" to "${summary.shots}구",
            "홀인" to "${summary.made} · ${"%.0f".format(summary.makePct)}%"
        )
        statRow(
            "오늘" to "${todaySummary.shots}구 · ${"%.0f".format(todaySummary.makePct)}%",
            "최근 7일" to "${weekSummary.shots}구 · ${"%.0f".format(weekSummary.makePct)}%"
        )
        statRow(
            "최근 30일" to "${monthSummary.shots}구 · ${"%.0f".format(monthSummary.makePct)}%",
            currentPutterName to "${putterSummary.shots}구 · ${"%.1f".format(putterSummary.avgScore)}"
        )
        statRow(
            "Perfect 평균" to "%.1f".format(summary.avgScore),''',
    "stats trend tiles",
)

s = replace_once(
    s,
    '''        calibrator?.close()
        calibrator = null

        if (::appUpdater.isInitialized) appUpdater.close()''',
    '''        calibrator?.close()
        calibrator = null
        cameraStability.release()
        if (::voiceCoach.isInitialized) voiceCoach.shutdown()

        if (::appUpdater.isInitialized) appUpdater.close()''',
    "product system destroy",
)

p.write_text(s)


# ---- DesignSystem ---------------------------------------------------------
p = Path("app/src/main/java/com/puttvision/screen/DesignSystem.kt")
s = p.read_text()
needle = '''    setOnClickListener { onClick() }'''
count = s.count(needle)
if count < 2:
    raise SystemExit(f"unexpected DesignSystem click target count: {count}")
s = s.replace(
    needle,
    '''    installProductPressFeedback()
    setOnClickListener { onClick() }'''
)
p.write_text(s)
