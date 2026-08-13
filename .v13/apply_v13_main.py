from pathlib import Path

p = Path('app/src/main/java/com/puttvision/screen/MainActivity.kt')
s = p.read_text(encoding='utf-8')

def r(old, new, label):
    global s
    if old not in s:
        raise RuntimeError(f'MainActivity missing anchor: {label}')
    s = s.replace(old, new, 1)

r(
'''    private var hfrRetryAfterMs = 0L\n\n    private var autoPlayEnabled = true''',
'''    private var hfrRetryAfterMs = 0L\n    private var offlineTestMode = false\n    private var calibrationShotsSinceCheck = 0\n    private var calibrationDriftRecovering = false\n    private var hardwarelessSequence = 0\n\n    private var autoPlayEnabled = true''',
'V13 fields')

r(
'''            } else {\n                toast("카메라 권한이 필요합니다")\n                if (sessionActive) showHomeMenu()\n            }''',
'''            } else {\n                toast("카메라 권한 없음 · 메인 화면의 ‘장비 없이 테스트’는 사용 가능")\n                if (sessionActive && !offlineTestMode) showHomeMenu()\n            }''',
'permission fallback')

r(
'''        statsRepository = StatsRepository(this)\n\n        putterProfileStore = PutterProfileStore(this)''',
'''        statsRepository = StatsRepository(this)\n        GreenReadRuntime.install(this)\n\n        putterProfileStore = PutterProfileStore(this)''',
'green read disk install')

r(
'''        engine.onRecordFinalized = { record ->\n            statsRepository.add(record)\n        }''',
'''        engine.onRecordFinalized = { record ->\n            if (!offlineTestMode) statsRepository.add(record)\n        }''',
'test stats isolation')

r(
'''        maxLines = 1\n    }\n    stateBlock.addView(shotPanelTitle)''',
'''        maxLines = 2\n    }\n    stateBlock.addView(shotPanelTitle)''',
'metric two lines')

r(
'''    utility.addView(pvButton("샷 기록", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { showStats() }, LinearLayout.LayoutParams(0, sdp(if (compact) 30 else 34), 1f))\n    utility.addView(pvButton("업데이트", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { appUpdater.check(silent = false) }, LinearLayout.LayoutParams(0, sdp(if (compact) 30 else 34), 1f).apply { marginStart = sdp(6) })''',
'''    utility.addView(pvButton("샷 기록", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { showStats() }, LinearLayout.LayoutParams(0, sdp(if (compact) 30 else 34), 1f))\n    utility.addView(pvButton("장비 없이 테스트", PvButtonStyle.GHOST, textSp = if (compact) 6.2f else 7.2f, scaled = true, radiusDp = 100f) { showHardwarelessTestLab() }, LinearLayout.LayoutParams(0, sdp(if (compact) 30 else 34), 1.25f).apply { marginStart = sdp(6) })\n    utility.addView(pvButton("업데이트", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { appUpdater.check(silent = false) }, LinearLayout.LayoutParams(0, sdp(if (compact) 30 else 34), 1f).apply { marginStart = sdp(6) })''',
'home hardwareless button')

r(
'''    tools.addView(tool("TV PREVIEW", "TV 화면을 이 기기에서 미리보기") {\n        closeThen { showTvPreview() }\n    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })''',
'''    tools.addView(tool("TV PREVIEW", "TV 화면을 이 기기에서 미리보기") {\n        closeThen { showTvPreview() }\n    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })\n    tools.addView(tool("SIM LAB", "카메라 · TV 없이 전체 장비 테스트") {\n        if (wasActiveSession) toast("실제 세션을 끝낸 뒤 SIM LAB을 실행하세요") else closeThen { showHardwarelessTestLab() }\n    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })''',
'settings sim lab')

r(
'''        val tv = GreenView(this, engine)\n        root.addView(tv, FrameLayout.LayoutParams(-1, -1))\n\n        val close = pvButton''',
'''        val tv = GreenView(this, engine)\n        root.addView(tv, FrameLayout.LayoutParams(-1, -1))\n        root.addView(TvImpactReplayView(this), FrameLayout.LayoutParams(-1, -1))\n\n        val close = pvButton''',
'tv preview replay overlay')

r(
'''                        homography = result.homography\n                        overlay.status = "CAL ${quality.score} · ${quality.grade}"''',
'''                        homography = result.homography\n                        calibrationShotsSinceCheck = 0\n                        calibrationDriftRecovering = false\n                        overlay.status = "CAL ${quality.score} · ${quality.grade}"''',
'calibration reset counters')

r(
'''                        installNormalAnalyzer(result.homography)''',
'''                        installNormalAnalyzer(result.homography, result.imagePoints)''',
'install analyzer marker baseline')

r(
'''    private fun installNormalAnalyzer(\n        h: Homography\n    ) {''',
'''    private fun installNormalAnalyzer(\n        h: Homography,\n        markerPoints: List<android.graphics.PointF> = emptyList()\n    ) {''',
'analyzer signature')

r(
'''                onQuality = { quality ->\n                    runOnUiThread {\n                        liveQualitySnapshot = quality\n                        updateLiveQualityStatus(quality)\n                    }\n                },\n                onShotReady = { metrics ->''',
'''                onQuality = { quality ->\n                    runOnUiThread {\n                        liveQualitySnapshot = quality\n                        updateLiveQualityStatus(quality)\n                    }\n                },\n                baselineMarkerPoints = markerPoints,\n                onCalibrationDrift = { drift ->\n                    runOnUiThread { handleCalibrationDrift(drift) }\n                },\n                onShotReady = { metrics ->''',
'analyzer drift callback')

r(
'''    private fun maybeShowFirstRunWizard(force: Boolean = false) {''',
'''    private fun handleCalibrationDrift(snapshot: CalibrationDriftSnapshot) {\n        if (offlineTestMode || !sessionActive || measurementSuspended || calibrationDriftRecovering) return\n        if (!snapshot.blocked) {\n            if (snapshot.driftPx >= 5.0) {\n                overlay.status = snapshot.detail\n                overlay.invalidate()\n            }\n            return\n        }\n        calibrationDriftRecovering = true\n        calibrationShotsSinceCheck = 0\n        cancelPendingAuto()\n        impactPolling = false\n        tracker.cancel()\n        stopHfrRecordingOnly()\n        homography = null\n        measurementSuspended = true\n        overlay.status = "CAL DRIFT · RECALIBRATE"\n        shotPanelTitle.text = "CAMERA MOVED"\n        shotPanelTitle.setTextColor(Pv.amber)\n        metricText.text = snapshot.detail\n        overlay.invalidate()\n        mainHandler.postDelayed({\n            if (!sessionActive || offlineTestMode) {\n                calibrationDriftRecovering = false\n                return@postDelayed\n            }\n            measurementSuspended = false\n            beginAutoCalibration()\n        }, 350L)\n    }\n\n    private fun maybeShowFirstRunWizard(force: Boolean = false) {''',
'drift handler insertion')

r(
'''        if (::offlineLicenseManager.isInitialized) {''',
'''        if (!offlineTestMode && calibrationShotsSinceCheck >= 6) {\n            calibrationShotsSinceCheck = 0\n            shotPanelTitle.text = "CAL CHECK"\n            shotPanelTitle.setTextColor(Pv.primary)\n            metricText.text = "6샷 주기 카메라 위치 재검증"\n            overlay.status = "CAL WATCH · QUICK CHECK"\n            overlay.invalidate()\n            beginAutoCalibration()\n            return\n        }\n\n        if (::offlineLicenseManager.isInitialized) {''',
'periodic HFR calibration check')

r(
'''        val baseMetrics = if (::matCalibrationManager.isInitialized) {\n            matCalibrationManager.applyFallback(metrics)\n        } else metrics''',
'''        val baseMetrics = if (offlineTestMode) {\n            metrics\n        } else if (::matCalibrationManager.isInitialized) {\n            matCalibrationManager.applyFallback(metrics)\n        } else metrics''',
'sim bypass mat correction')

r(
'''        if (::matCalibrationManager.isInitialized) {\n            matCalibrationManager.observe(baseMetrics)\n        }\n        if (::accuracyValidationLab.isInitialized) {\n            accuracyValidationLab.capture(baseMetrics)\n        }\n        if (::accuracyAutoTuner.isInitialized && ::accuracyValidationLab.isInitialized) {\n            accuracyAutoTuner.refresh(accuracyValidationLab.matched())\n        }\n        val processedMetrics = if (::accuracyAutoTuner.isInitialized) accuracyAutoTuner.apply(baseMetrics) else baseMetrics\n        updateMetricCards(processedMetrics)''',
'''        if (!offlineTestMode && ::matCalibrationManager.isInitialized) {\n            matCalibrationManager.observe(baseMetrics)\n        }\n        if (!offlineTestMode && ::accuracyValidationLab.isInitialized) {\n            accuracyValidationLab.capture(baseMetrics)\n        }\n        if (!offlineTestMode && ::accuracyAutoTuner.isInitialized && ::accuracyValidationLab.isInitialized) {\n            accuracyAutoTuner.refresh(accuracyValidationLab.matched())\n        }\n        val processedMetrics = if (!offlineTestMode && ::accuracyAutoTuner.isInitialized) accuracyAutoTuner.apply(baseMetrics) else baseMetrics\n        if (!offlineTestMode) calibrationShotsSinceCheck++\n        updateMetricCards(processedMetrics)''',
'sim isolation and calibration counter')

r(
'''        replay?.let {\n            replayView.play(\n                it,\n                processedMetrics,\n                bestReferenceMetrics()\n            )\n        }''',
'''        replay?.let {\n            TvImpactReplayBus.publish(it, processedMetrics, synthetic = offlineTestMode || source.contains("SIM"))\n            replayView.play(\n                it,\n                processedMetrics,\n                bestReferenceMetrics()\n            )\n        }''',
'tv replay publish')

r(
'''      coach?.let { append(" · ${it.headline}") }\n  }''',
'''      coach?.let { append(" · ${it.headline}") }\n      processedMetrics.uncertainty?.let { append("\\n${it.compact()}") }\n  }''',
'live uncertainty text')

r(
'''                    if (result.holed) {\n                        "HOLE IN"\n                    } else {\n                        "컵까지 ${"%.0f".format(result.distanceToCupM * 100)}cm"\n                    }''',
'''                    if (result.holed) {\n                        "HOLE IN"\n                    } else if (result.lipOut) {\n                        "LIP OUT · 컵까지 ${"%.0f".format(result.distanceToCupM * 100)}cm"\n                    } else {\n                        "컵까지 ${"%.0f".format(result.distanceToCupM * 100)}cm"\n                    }''',
'final lip out')

r(
'''        if (::voiceCoach.isInitialized) {\n            voiceCoach.speakResult(result, engine.currentShot?.launchAngleDeg)\n        }\n        if (::previewView.isInitialized) previewView.productHaptic()''',
'''        if (!offlineTestMode && ::voiceCoach.isInitialized) {\n            voiceCoach.speakResult(result, engine.currentShot?.launchAngleDeg)\n        }\n        if (!offlineTestMode && ::previewView.isInitialized) previewView.productHaptic()''',
'sim silent result')

# Do not persist a synthetic session if the app backgrounds while SIM LAB is open.
r(
'''    private fun saveSessionRecovery() {''',
'''    private fun saveSessionRecovery() {\n        if (offlineTestMode) return''',
'sim recovery isolation')

lab = r'''    private fun showHardwarelessTestLab() {
        if (sessionActive) {
            toast("실제 세션을 끝낸 뒤 장비 없이 테스트를 실행하세요")
            return
        }

        val oldSettings = engine.settings.copy()
        val oldMode = engine.gameModes.snapshot()
        val oldAuto = autoPlayEnabled
        val oldPracticeCount = practiceCount
        val oldPracticeShots = practiceShotsTaken
        val oldPracticePatternShot = practicePatternShotIndex
        val oldSessionStarted = sessionStartedAtMs
        val oldActiveGame = activeSessionIsGame
        val oldSuspended = measurementSuspended
        var profile = 8
        val distances = intArrayOf(3, 5, 7, 10)
        var distanceIndex = 1
        var suiteToken = 0

        offlineTestMode = true
        autoPlayEnabled = false
        updateAutoButton()
        cancelPendingAuto()
        stopSimulation()
        stopHfrRecordingOnly()
        tracker.cancel()
        if (::analysis.isInitialized) analysis.clearAnalyzer()
        sessionActive = true
        measurementSuspended = false
        activeSessionIsGame = false
        sessionStartedAtMs = System.currentTimeMillis()
        practiceCount = 999
        practiceShotsTaken = 0
        practicePatternShotIndex = 0
        engine.gameModes.setMode(PracticeMode.PRACTICE)
        engine.settings.stimpMeters = 2.8
        engine.settings.holeDistanceM = 5.0
        engine.settings.sideSlopePct = 0.0
        engine.settings.longSlopePct = 0.0
        engine.settings.terrainProfileId = profile
        engine.resetSimulation()
        GreenReadRuntime.prefetch(engine.settings)

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(GreenView(this, engine), FrameLayout.LayoutParams(-1, -1))
        root.addView(TvImpactReplayView(this), FrameLayout.LayoutParams(-1, -1))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pvRounded(Color.argb(238, 4, 8, 10), Pv.rLg, Pv.line)
            setPadding(pvDp(12), pvDp(10), pvDp(12), pvDp(10))
        }
        controls.addView(TextView(this).apply {
            text = "NO HARDWARE LAB"
            setTextColor(Pv.primary)
            textSize = pvSp(8f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
            includeFontPadding = false
        })
        controls.addView(TextView(this).apply {
            text = "CAMERA MOCK ●  ·  TV LOCAL ●\n실제 측정 파이프라인에 합성 샷을 주입합니다."
            setTextColor(Pv.textMid)
            textSize = pvSp(7f)
            setPadding(0, pvDp(3), 0, pvDp(8))
        })
        val status = TextView(this).apply {
            text = "READY · 5m · GREEN 08"
            setTextColor(Pv.textHi)
            textSize = pvSp(8f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            background = pvRounded(Pv.surfaceLo, Pv.rMd, Pv.lineSoft)
            setPadding(pvDp(8), pvDp(7), pvDp(8), pvDp(7))
        }
        controls.addView(status)

        fun applyLabTarget() {
            engine.settings.holeDistanceM = distances[distanceIndex].toDouble()
            engine.settings.stimpMeters = 2.8
            engine.settings.sideSlopePct = 0.0
            engine.settings.longSlopePct = 0.0
            engine.settings.terrainProfileId = profile
            engine.resetSimulation()
            GreenReadRuntime.prefetch(engine.settings)
            status.text = "READY · ${distances[distanceIndex]}m · GREEN ${"%02d".format(profile)}"
        }

        fun inject(scenario: HardwarelessScenario): Boolean {
            if (!offlineTestMode || engine.state?.running == true) return false
            hardwarelessSequence++
            engine.resetSimulation()
            val metrics = HardwarelessShotFactory.metrics(scenario, engine.settings, hardwarelessSequence)
            val replay = HardwarelessShotFactory.replay(metrics)
            val accepted = handleMeasuredShot(metrics, replay, "PRECISION SIM 240fps")
            status.text = if (accepted) {
                "${scenario.label} · ACCEPTED · 물리/TV 실행중"
            } else {
                "${scenario.label} · REJECTED · 품질 게이트 정상"
            }
            return accepted
        }

        controls.addView(TextView(this).apply {
            text = "SHOT INJECTION"
            setTextColor(Pv.textLo)
            textSize = pvSp(6f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .12f
            setPadding(0, pvDp(10), 0, pvDp(4))
        })
        HardwarelessScenario.entries.forEach { scenario ->
            controls.addView(
                pvButton(scenario.label, if (scenario == HardwarelessScenario.CENTER) PvButtonStyle.PRIMARY else PvButtonStyle.GHOST, textSp = 7f) {
                    inject(scenario)
                },
                LinearLayout.LayoutParams(-1, pvDp(36)).apply { topMargin = pvDp(4) }
            )
        }

        val targetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        targetRow.addView(pvButton("거리 변경", PvButtonStyle.GHOST, textSp = 6.8f) {
            if (engine.state?.running == true) return@pvButton
            distanceIndex = (distanceIndex + 1) % distances.size
            applyLabTarget()
        }, LinearLayout.LayoutParams(0, pvDp(36), 1f))
        targetRow.addView(pvButton("그린 +1", PvButtonStyle.GHOST, textSp = 6.8f) {
            if (engine.state?.running == true) return@pvButton
            profile = (profile + 1) % 24
            applyLabTarget()
        }, LinearLayout.LayoutParams(0, pvDp(36), 1f).apply { marginStart = pvDp(5) })
        controls.addView(targetRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pvDp(7) })

        controls.addView(pvButton("자동 시나리오 전체검사", PvButtonStyle.PRIMARY, textSp = 7f) {
            val token = ++suiteToken
            val cases = listOf(
                HardwarelessScenario.LOW_QUALITY,
                HardwarelessScenario.CENTER,
                HardwarelessScenario.PUSH,
                HardwarelessScenario.PULL,
                HardwarelessScenario.SHORT,
                HardwarelessScenario.LONG,
                HardwarelessScenario.BREAK_TEST
            )
            val profiles = intArrayOf(0, 6, 8, 12, 14, 19, 23)
            var index = 0
            var passed = 0
            fun advance() {
                if (!offlineTestMode || token != suiteToken) return
                if (engine.state?.running == true) {
                    mainHandler.postDelayed({ advance() }, 120L)
                    return
                }
                if (index >= cases.size) {
                    status.text = "AUTO SUITE · $passed/${cases.size} PASS"
                    status.setTextColor(if (passed == cases.size) Pv.primary else Pv.amber)
                    return
                }
                profile = profiles[index]
                distanceIndex = index % distances.size
                applyLabTarget()
                val scenario = cases[index]
                val accepted = inject(scenario)
                val expected = scenario != HardwarelessScenario.LOW_QUALITY
                if (accepted == expected) passed++
                index++
                mainHandler.postDelayed({ advance() }, if (accepted) 160L else 350L)
            }
            advance()
        }, LinearLayout.LayoutParams(-1, pvDp(40)).apply { topMargin = pvDp(8) })

        controls.addView(pvButton("24 GREEN 엔진 스모크", PvButtonStyle.GHOST, textSp = 7f) {
            status.text = "24 GREEN · 계산중"
            cameraExecutor.execute {
                var completed = 0
                for (id in 0..23) {
                    val settings = GreenSettings(2.8, 5.0, 0.0, 0.0, id)
                    val metrics = ShotMetrics(
                        ballSpeedMps = 1.45,
                        launchAngleDeg = 0.0,
                        headSpeedMps = 0.95,
                        faceAngleDeg = 0.0,
                        pathAngleDeg = 0.0,
                        faceToPathDeg = 0.0,
                        smash = 1.52,
                        impactOffsetMm = 0.0,
                        measuredAtNs = 0L,
                        confidence = .95,
                        uncertainty = MeasurementUncertaintyEstimator.synthetic()
                    )
                    if (HardwarelessEngineSmoke.run(settings, metrics).completed) completed++
                }
                runOnUiThread {
                    if (offlineTestMode) {
                        status.text = "24 GREEN ENGINE · $completed/24 PASS"
                        status.setTextColor(if (completed == 24) Pv.primary else Pv.amber)
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, pvDp(38)).apply { topMargin = pvDp(6) })

        controls.addView(pvButton("닫기", PvButtonStyle.GHOST, textSp = 7f) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, pvDp(38)).apply { topMargin = pvDp(8) })
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(controls, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(pvDp(if (compactLandscape) 250 else 300), -1, Gravity.END).apply {
            topMargin = pvDp(8)
            bottomMargin = pvDp(8)
            marginEnd = pvDp(8)
        })

        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        dialog.setOnDismissListener {
            suiteToken++
            TvImpactReplayBus.clear()
            stopSimulation()
            tracker.cancel()
            engine.resetSimulation()
            engine.settings.stimpMeters = oldSettings.stimpMeters
            engine.settings.holeDistanceM = oldSettings.holeDistanceM
            engine.settings.sideSlopePct = oldSettings.sideSlopePct
            engine.settings.longSlopePct = oldSettings.longSlopePct
            engine.settings.terrainProfileId = oldSettings.terrainProfileId
            engine.gameModes.restore(oldMode)
            practiceCount = oldPracticeCount
            practiceShotsTaken = oldPracticeShots
            practicePatternShotIndex = oldPracticePatternShot
            sessionStartedAtMs = oldSessionStarted
            activeSessionIsGame = oldActiveGame
            sessionActive = false
            measurementSuspended = oldSuspended
            offlineTestMode = false
            autoPlayEnabled = oldAuto
            updateAutoButton()
            engine.seedHistory(statsRepository.recent(40))
            updateSettingLabels()
            showHomeMenu()
        }
        dialog.show()
    }

'''

anchor = '    private fun openAccuracyValidationLab() {'
if anchor not in s:
    raise RuntimeError('MainActivity missing hardwareless insertion anchor')
s = s.replace(anchor, lab + anchor, 1)

# Add uncertainty to verbose metrics output as well.
r(
'''            m.confidence?.let {\n                append(\n                    "  Q ${"%.0f".format(it * 100)}%"\n                )\n            }\n        }''',
'''            m.confidence?.let {\n                append(\n                    "  Q ${"%.0f".format(it * 100)}%"\n                )\n            }\n            m.uncertainty?.let { append("\\n${it.compact()}") }\n        }''',
'format uncertainty')

p.write_text(s, encoding='utf-8')
print('V13 MainActivity patch applied')
