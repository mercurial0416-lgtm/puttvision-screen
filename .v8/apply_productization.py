from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))

# MainActivity: V8 systems, profile switching, validation capture, backup import, diagnostics.
path = "app/src/main/java/com/puttvision/screen/MainActivity.kt"
replace_once(path,
'''    private lateinit var putterProfileStore: PutterProfileStore
    private lateinit var matCalibrationManager: MatCalibrationManager
    private lateinit var voiceCoach: HandsFreeVoiceCoach
    private val cameraStability = CameraStabilityController()
''',
'''    private lateinit var putterProfileStore: PutterProfileStore
    private lateinit var matCalibrationManager: MatCalibrationManager
    private lateinit var voiceCoach: HandsFreeVoiceCoach
    private lateinit var userProfileStore: UserProfileStore
    private lateinit var tvCalibrationStore: TvCalibrationStore
    private lateinit var accuracyValidationLab: AccuracyValidationLab
    private lateinit var productBackupManager: ProductBackupManager
    private lateinit var deviceReport: DeviceCapabilityReport
    private val cameraStability = CameraStabilityController()
''')

replace_once(path,
'''    private val permission =
        registerForActivityResult(
''',
'''    private val backupImport =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null || !::productBackupManager.isInitialized) return@registerForActivityResult
            runCatching { productBackupManager.importBackup(uri) }
                .onSuccess { message ->
                    engine.seedHistory(statsRepository.recent(40))
                    toast(message)
                    sessionActive = false
                    measurementSuspended = true
                    showHomeMenu()
                }
                .onFailure { error -> toast("백업 복원 실패: ${error.message ?: "파일 오류"}") }
        }

    private val permission =
        registerForActivityResult(
''')

replace_once(path,
'''        statsRepository =
            StatsRepository(this)

        putterProfileStore = PutterProfileStore(this)
        matCalibrationManager = MatCalibrationManager(this)
        voiceCoach = HandsFreeVoiceCoach(this)
        appUpdater = AppUpdater(this)

        engine.seedHistory(
            statsRepository.all()
        )
''',
'''        userProfileStore = UserProfileStore(this)
        tvCalibrationStore = TvCalibrationStore(this)
        deviceReport = DeviceDiagnostics.inspect(this)
        statsRepository = StatsRepository(this)

        putterProfileStore = PutterProfileStore(this)
        matCalibrationManager = MatCalibrationManager(this)
        voiceCoach = HandsFreeVoiceCoach(this)
        accuracyValidationLab = AccuracyValidationLab(this)
        appUpdater = AppUpdater(this)
        productBackupManager = ProductBackupManager(
            this, statsRepository, userProfileStore, putterProfileStore, tvCalibrationStore
        )

        engine.seedHistory(statsRepository.recent(40))
        statsRepository.setOnLoaded {
            runOnUiThread {
                engine.seedHistory(statsRepository.recent(40))
                if (::menuOverlay.isInitialized && menuOverlay.visibility == View.VISIBLE) {
                    replaceMenuScreen(buildVideoHomeScreen(), null)
                }
            }
        }
''')

replace_once(path,
'''    top.addView(topState(if (hfrHardwareAvailable) "240 FPS" else "CAMERA", hfrHardwareAvailable))
    top.addView(topState("CAL", homography != null), LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(5) })
    top.addView(pvButton("설정", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { showSettingsDialog() }, LinearLayout.LayoutParams(sdp(if (compact) 58 else 66), sdp(if (compact) 29 else 33)).apply { marginStart = sdp(6) })
''',
'''    top.addView(topState(if (hfrHardwareAvailable) "240 FPS" else "CAMERA", hfrHardwareAvailable))
    top.addView(topState("CAL", homography != null), LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(5) })
    top.addView(topState(userProfileStore.current().name, true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = sdp(5) })
    top.addView(pvButton("설정", PvButtonStyle.GHOST, textSp = if (compact) 6.5f else 7.5f, scaled = true, radiusDp = 100f) { showSettingsDialog() }, LinearLayout.LayoutParams(sdp(if (compact) 58 else 66), sdp(if (compact) 29 else 33)).apply { marginStart = sdp(6) })
''')

replace_once(path,
'''    tools.addView(tool("ANALYTICS", "샷 기록 / STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("PRODUCT", "장비 · 매트 · 핸즈프리") {
        showProductSetupDialog(this, putterProfileStore, matCalibrationManager, voiceCoach)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DISPLAY", "TV 다시 연결") { displayController.refresh() }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
''',
'''    tools.addView(tool("ANALYTICS", "샷 기록 / STATS") { closeThen { showStats(resumeAfter = wasActiveSession) } }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("USER", "${userProfileStore.current().name} · 사용자 전환") {
        showUserProfileManager(this, userProfileStore, allowChange = !sessionActive) { profile ->
            engine.seedHistory(statsRepository.recent(40))
            toast("사용자: ${profile.name}")
        }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("PHONE ${deviceReport.grade}", "기기 호환성 진단") {
        deviceReport = DeviceDiagnostics.inspect(this)
        showDeviceDiagnostics(this, deviceReport, lastCalibrationQualityScore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("ACCURACY", "기준 센서 정확도 검증") {
        showAccuracyValidationLab(this, accuracyValidationLab)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("PRODUCT", "장비 · 매트 · 핸즈프리") {
        showProductSetupDialog(this, putterProfileStore, matCalibrationManager, voiceCoach)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("DISPLAY", "TV 다시 연결") { displayController.refresh() }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("TV CAL", "TV 화면 크기 · 위치 보정") {
        showTvCalibrationDialog(this, tvCalibrationStore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("BACKUP", "기록 · 설정 백업 / 복원") {
        showBackupDialog(this, productBackupManager) { backupImport.launch("application/json") }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
''')

replace_once(path,
'''    columns.addView(tools, LinearLayout.LayoutParams(0, -2, .78f))
    shell.addView(columns)
''',
'''    val toolsScroll = ScrollView(this).apply {
        isFillViewport = true
        addView(tools, ScrollView.LayoutParams(-1, -2))
    }
    columns.addView(toolsScroll, LinearLayout.LayoutParams(0, if (compact) pvDp(300) else pvDp(390), .78f))
    shell.addView(columns)
''')

replace_once(path,
'''        updateMetricCards(processedMetrics)
        if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.observe(processedMetrics)
        }

        engine.launch(
''',
'''        updateMetricCards(processedMetrics)
        if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.observe(processedMetrics)
        }
        if (::accuracyValidationLab.isInitialized) {
            accuracyValidationLab.capture(processedMetrics)
        }

        engine.launch(
''')

replace_once(path,
'''        replay?.let {
            replayView.play(
                it,
                processedMetrics
            )
        }
''',
'''        replay?.let {
            replayView.play(
                it,
                processedMetrics,
                bestReferenceMetrics()
            )
        }
''')

replace_once(path,
'''    private fun startSimulationTicker() {
''',
'''    private fun bestReferenceMetrics(): ShotMetrics? {
        val distance = engine.settings.holeDistanceM
        return statsRepository.all()
            .asSequence()
            .filter { kotlin.math.abs(it.targetDistanceM - distance) <= 0.75 }
            .maxByOrNull { it.strokeScore.total }
            ?.metrics
    }

    private fun startSimulationTicker() {
''')

replace_once(path,
'''        box.addView(pvEyebrow("누적 기록 · CAREER TOTALS"))
''',
'''        box.addView(pvEyebrow("${userProfileStore.current().name} · 누적 기록 · CAREER TOTALS"))
''')

replace_once(path,
'''        if (::appUpdater.isInitialized) appUpdater.close()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdownNow()
''',
'''        if (::appUpdater.isInitialized) appUpdater.close()
        if (::statsRepository.isInitialized) statsRepository.close()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdownNow()
''')

# GreenView: persisted TV safe-area calibration plus calibration guide.
path = "app/src/main/java/com/puttvision/screen/GreenView.kt"
replace_once(path,
'''    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCourse(canvas)
        drawGreen(canvas)
        drawBrandRail(canvas)
        drawShotTelemetry(canvas)
        drawResult(canvas)
        postInvalidateOnAnimation()
    }
''',
'''    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(width * ProductSessionRuntime.tvOffsetX, height * ProductSessionRuntime.tvOffsetY)
        canvas.scale(
            ProductSessionRuntime.tvScaleX,
            ProductSessionRuntime.tvScaleY,
            width / 2f,
            height / 2f
        )
        drawCourse(canvas)
        drawGreen(canvas)
        drawBrandRail(canvas)
        drawShotTelemetry(canvas)
        drawResult(canvas)
        if (ProductSessionRuntime.tvCalibrationGuide) drawTvCalibrationGuide(canvas)
        canvas.restoreToCount(save)
        postInvalidateOnAnimation()
    }

    private fun drawTvCalibrationGuide(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = max(8f, min(w, h) * .018f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(3f, min(w, h) * .004f)
        p.color = Pv.primary
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), inset, inset, p)
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(18f, w * .014f)
        p.color = Color.WHITE
        c.drawText("TV SAFE AREA · 초록 테두리가 전부 보이게 맞추세요", inset * 1.6f, inset * 3.0f, p)
        val arm = min(w, h) * .055f
        p.strokeWidth = max(5f, min(w, h) * .006f)
        p.color = Pv.primary
        p.style = Paint.Style.STROKE
        listOf(
            floatArrayOf(inset, inset, inset + arm, inset, inset, inset + arm),
            floatArrayOf(w - inset, inset, w - inset - arm, inset, w - inset, inset + arm),
            floatArrayOf(inset, h - inset, inset + arm, h - inset, inset, h - inset - arm),
            floatArrayOf(w - inset, h - inset, w - inset - arm, h - inset, w - inset, h - inset - arm)
        ).forEach { a ->
            c.drawLine(a[0], a[1], a[2], a[3], p)
            c.drawLine(a[0], a[1], a[4], a[5], p)
        }
        p.style = Paint.Style.FILL
    }
''')

# ImpactReplayView: compare current stroke geometry with the best comparable saved shot.
path = "app/src/main/java/com/puttvision/screen/ImpactReplayView.kt"
replace_once(path,
'''    private var replay: ImpactReplay? = null
    private var metrics: ShotMetrics? = null
''',
'''    private var replay: ImpactReplay? = null
    private var metrics: ShotMetrics? = null
    private var referenceMetrics: ShotMetrics? = null
''')
replace_once(path,
'''    fun play(value: ImpactReplay, shot: ShotMetrics) {
        stopReplay(recycleFrames = true)
        replay = value
        metrics = shot
''',
'''    fun play(value: ImpactReplay, shot: ShotMetrics, reference: ShotMetrics? = null) {
        stopReplay(recycleFrames = true)
        replay = value
        metrics = shot
        referenceMetrics = reference
''')
replace_once(path,
'''        replay = null
        metrics = null
        frame = 0
''',
'''        replay = null
        metrics = null
        referenceMetrics = null
        frame = 0
''')
replace_once(path,
'''        if (frame == r.impactIndex) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, w * .002f)
            paint.color = Color.argb(220, 246, 190, 74)
            canvas.drawRoundRect(media, max(10f, h * .018f), max(10f, h * .018f), paint)
            paint.style = Paint.Style.FILL
        }

        // Telemetry rail
''',
'''        if (frame == r.impactIndex) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, w * .002f)
            paint.color = Color.argb(220, 246, 190, 74)
            canvas.drawRoundRect(media, max(10f, h * .018f), max(10f, h * .018f), paint)
            paint.style = Paint.Style.FILL
            drawBestShotComparison(canvas, media)
        }

        // Telemetry rail
''')
replace_once(path,
'''    override fun onDetachedFromWindow() {
''',
'''    private fun drawBestShotComparison(canvas: Canvas, media: RectF) {
        val current = metrics ?: return
        val best = referenceMetrics ?: return
        val cx = media.centerX()
        val baseY = media.bottom - media.height() * .08f
        val len = media.height() * .43f

        fun lineForAngle(angleDeg: Double?, color: Int, widthPx: Float) {
            val a = Math.toRadians(angleDeg ?: 0.0)
            val dx = kotlin.math.sin(a).toFloat() * len
            val dy = kotlin.math.cos(a).toFloat() * len
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = widthPx
            paint.color = color
            canvas.drawLine(cx, baseY, cx + dx, baseY - dy, paint)
            paint.style = Paint.Style.FILL
        }

        lineForAngle(best.pathAngleDeg ?: best.launchAngleDeg, Color.argb(175, 96, 178, 255), max(2f, width * .0015f))
        lineForAngle(current.pathAngleDeg ?: current.launchAngleDeg, Color.argb(230, 78, 209, 121), max(3f, width * .0022f))

        val faceY = baseY - len * .23f
        fun face(angle: Double?, color: Int, stroke: Float) {
            val a = Math.toRadians(angle ?: 0.0)
            val half = media.width() * .055f
            val dx = kotlin.math.cos(a).toFloat() * half
            val dy = kotlin.math.sin(a).toFloat() * half
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = color
            canvas.drawLine(cx - dx, faceY - dy, cx + dx, faceY + dy, paint)
            paint.style = Paint.Style.FILL
        }
        face(best.faceAngleDeg, Color.argb(175, 96, 178, 255), max(2f, width * .0015f))
        face(current.faceAngleDeg, Color.argb(235, 246, 190, 74), max(3f, width * .0022f))

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = max(9f, width * .0075f)
        paint.color = Color.argb(215, 96, 178, 255)
        canvas.drawText("BEST", media.left + media.width() * .025f, media.top + media.height() * .075f, paint)
        paint.color = Pv.primary
        canvas.drawText("CURRENT", media.left + media.width() * .025f, media.top + media.height() * .125f, paint)
        paint.color = Pv.textMid
        paint.textSize = max(8f, width * .0067f)
        canvas.drawText(
            "PATH ${current.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"} / ${best.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   ·   FACE ${current.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"} / ${best.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}",
            media.left + media.width() * .025f,
            media.bottom - media.height() * .035f,
            paint
        )
    }

    override fun onDetachedFromWindow() {
''')

# App updater: consumer public manifest is primary; private GitHub channel can transparently win when newer.
path = "app/src/main/java/com/puttvision/screen/AppUpdater.kt"
replace_once(path,
'''    private fun fetchInfo(): UpdateInfo {
        val token = tokenStore.loadToken()
        if (!token.isNullOrBlank()) {
            try {
                return fetchGitHubRelease(token)
            } catch (_: Throwable) {
                // Keep the public manifest as a bootstrap fallback when GitHub auth/network fails.
            }
        }
        return fetchFallbackManifest()
    }
''',
'''    private fun fetchInfo(): UpdateInfo {
        val publicInfo = runCatching { fetchFallbackManifest() }.getOrNull()
        val token = tokenStore.loadToken()
        val privateInfo = if (!token.isNullOrBlank()) {
            runCatching { fetchGitHubRelease(token) }.getOrNull()
        } else null
        return listOfNotNull(publicInfo, privateInfo)
            .maxByOrNull { it.versionCode }
            ?: error("업데이트 채널에 연결할 수 없습니다")
    }
''')

# FileProvider paths for validation CSV and JSON backups.
path = "app/src/main/res/xml/file_paths.xml"
replace_once(path,
'''    <cache-path name="updates" path="updates/" />
''',
'''    <cache-path name="updates" path="updates/" />
    <cache-path name="exports" path="exports/" />
''')

# Release pipeline: always build a public updater bundle and optionally deploy it to Vercel when sales secrets are configured.
path = ".github/workflows/release-apk.yml"
replace_once(path,
'''      - name: Publish private GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
        shell: bash
        run: |
          if gh release view "$PV_TAG" >/dev/null 2>&1; then
            gh release upload "$PV_TAG" puttvision.apk puttvision.apk.sha256 --clobber
            gh release edit "$PV_TAG" --title "PuttVision $PV_VERSION_NAME" --notes "Automated PuttVision release $PV_VERSION_NAME" --latest
          else
            gh release create "$PV_TAG" puttvision.apk puttvision.apk.sha256 \\
              --title "PuttVision $PV_VERSION_NAME" \\
              --notes "Automated PuttVision release $PV_VERSION_NAME" \\
              --latest
          fi

      - uses: actions/upload-artifact@v4
''',
'''      - name: Publish private GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
        shell: bash
        run: |
          if gh release view "$PV_TAG" >/dev/null 2>&1; then
            gh release upload "$PV_TAG" puttvision.apk puttvision.apk.sha256 --clobber
            gh release edit "$PV_TAG" --title "PuttVision $PV_VERSION_NAME" --notes "Automated PuttVision release $PV_VERSION_NAME" --latest
          else
            gh release create "$PV_TAG" puttvision.apk puttvision.apk.sha256 \\
              --title "PuttVision $PV_VERSION_NAME" \\
              --notes "Automated PuttVision release $PV_VERSION_NAME" \\
              --latest
          fi

      - name: Prepare consumer update bundle
        shell: bash
        run: |
          mkdir -p public-update
          cp puttvision.apk public-update/puttvision.apk
          SHA=$(cut -d' ' -f1 puttvision.apk.sha256)
          cat > public-update/update.json <<EOF
          {
            "versionCode": $PV_VERSION_CODE,
            "versionName": "$PV_VERSION_NAME",
            "apkUrl": "https://puttvision-update.vercel.app/puttvision.apk",
            "sha256": "$SHA"
          }
          EOF
          cat > public-update/vercel.json <<'EOF'
          {
            "headers": [
              {"source": "/update.json", "headers": [{"key": "Cache-Control", "value": "public, max-age=0, must-revalidate"}]},
              {"source": "/puttvision.apk", "headers": [{"key": "Cache-Control", "value": "public, max-age=60"}]}
            ]
          }
          EOF

      - name: Publish consumer updater to Vercel
        if: ${{ secrets.VERCEL_TOKEN != '' && secrets.VERCEL_ORG_ID != '' && secrets.VERCEL_PROJECT_ID != '' }}
        env:
          VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}
          VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}
          VERCEL_PROJECT_ID: ${{ secrets.VERCEL_PROJECT_ID }}
        shell: bash
        run: npx --yes vercel@latest deploy public-update --prod --yes --token "$VERCEL_TOKEN"

      - uses: actions/upload-artifact@v4
        with:
          name: PuttVision-Public-Update-${{ env.PV_VERSION_NAME }}
          path: public-update/

      - uses: actions/upload-artifact@v4
''')
