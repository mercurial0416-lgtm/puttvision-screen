from pathlib import Path
import re
import shutil

ROOT = Path('.')


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f'pattern not found in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1))


def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)

# ---------------------------------------------------------------------------
# 1) Distribution flavors: consumer APK contains no deploy console, developer does.
# ---------------------------------------------------------------------------
write('app/build.gradle.kts', r'''plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val licensePublicKey = (System.getenv("PV_LICENSE_PUBLIC_KEY_B64") ?: "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.puttvision.screen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.puttvision.screen"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("PV_VERSION_CODE")?.toIntOrNull() ?: 103
        versionName = System.getenv("PV_VERSION_NAME") ?: "1.0.3-build-fix"
        buildConfigField("String", "LICENSE_PUBLIC_KEY_B64", "\"$licensePublicKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("consumer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_BUILD", "false")
        }
        create("developer") {
            dimension = "distribution"
            buildConfigField("boolean", "DEVELOPER_BUILD", "true")
        }
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("PUTTVISION_STORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("PUTTVISION_STORE_PASSWORD")
                keyAlias = System.getenv("PUTTVISION_KEY_ALIAS")
                keyPassword = System.getenv("PUTTVISION_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.camera.core.ExperimentalSessionConfig",
            "-opt-in=androidx.camera.video.ExperimentalHighSpeedVideo",
            "-opt-in=androidx.camera.camera2.interop.ExperimentalCamera2Interop"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")

    val cameraX = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-video:$cameraX")

    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    kapt("androidx.room:room-compiler:$room")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation("junit:junit:4.13.2")
}
''')

manifest_path = ROOT / 'app/src/main/AndroidManifest.xml'
manifest = manifest_path.read_text()
manifest = manifest.replace('android:allowBackup="true"', 'android:allowBackup="false"\n        android:usesCleartextTraffic="false"')
manifest = re.sub(
    r'\n\s*<activity\n\s*android:name="\.DeployActivity".*?</activity>\n',
    '\n',
    manifest,
    count=1,
    flags=re.S,
)
if '.DeployActivity' in manifest:
    raise RuntimeError('DeployActivity remained in consumer/main manifest')
manifest_path.write_text(manifest)

write('app/src/developer/AndroidManifest.xml', '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name=".DeployActivity"
            android:label="PuttVision Deploy"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/zip" />
                <data android:mimeType="application/x-zip-compressed" />
                <data android:mimeType="application/octet-stream" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''')

# Move developer-only source out of the consumer source set.
dev_dir = ROOT / 'app/src/developer/java/com/puttvision/screen'
dev_dir.mkdir(parents=True, exist_ok=True)
for name in ['DeployActivity.kt', 'DeployBundle.kt', 'GitHubDeployClient.kt', 'OneTapDeployController.kt']:
    src = ROOT / f'app/src/main/java/com/puttvision/screen/{name}'
    dst = dev_dir / name
    if src.exists():
        shutil.move(str(src), str(dst))

# Deploy console: receiving a share is no longer enough to mutate GitHub. User must press deploy.
deploy = dev_dir / 'DeployActivity.kt'
text = deploy.read_text()
text = text.replace(
'''        // Share -> PuttVision Deploy is intentionally zero-navigation after the first GitHub setup.
        if (pendingSharedZip != null && SecureTokenStore(this).hasToken()) {
            consumeSharedOrPick()
        }
''',
'''        // A shared ZIP is staged only. Never mutate GitHub without an explicit tap in this Activity.
        if (pendingSharedZip != null) {
            status.text = "공유 ZIP 수신 · 배포 버튼을 눌러 확인하세요"
            deployButton.text = "수신 ZIP 확인 → 배포"
        }
''')
text = text.replace(
'''        if (pendingSharedZip != null) {
            if (SecureTokenStore(this).hasToken()) consumeSharedOrPick() else controller.showTokenSetup()
        }
''',
'''        if (pendingSharedZip != null) {
            status.text = "공유 ZIP 수신 · 배포 버튼을 눌러 확인하세요"
            deployButton.text = "수신 ZIP 확인 → 배포"
        }
''')
deploy.write_text(text)

# Consumer updater never reads the developer/private GitHub credential path.
replace_once(
    'app/src/main/java/com/puttvision/screen/AppUpdater.kt',
'''        val token = tokenStore.loadToken()
        val privateInfo = if (!token.isNullOrBlank()) {
            runCatching { fetchGitHubRelease(token) }.getOrNull()
        } else null
''',
'''        val token = if (BuildConfig.DEVELOPER_BUILD) tokenStore.loadToken() else null
        val privateInfo = if (BuildConfig.DEVELOPER_BUILD && !token.isNullOrBlank()) {
            runCatching { fetchGitHubRelease(token) }.getOrNull()
        } else null
''')

# ---------------------------------------------------------------------------
# 2) Continuous live image-quality / object readiness signal.
# ---------------------------------------------------------------------------
sv = ROOT / 'app/src/main/java/com/puttvision/screen/ShotVisionAnalyzer.kt'
text = sv.read_text()
text = text.replace(
'''    private val tracker: ShotTracker,
    private val onOverlay: (VisionOverlay) -> Unit,
    private val onShotReady: (ShotMetrics) -> Unit
''',
'''    private val tracker: ShotTracker,
    private val onOverlay: (VisionOverlay) -> Unit,
    private val onQuality: (LiveQualityGateSnapshot) -> Unit = {},
    private val onShotReady: (ShotMetrics) -> Unit
''')
text = text.replace(
'''    private var lastBall: PointF? = null
    private var lastHeel: PointF? = null
    private var lastToe: PointF? = null
''',
'''    private var lastBall: PointF? = null
    private var lastHeel: PointF? = null
    private var lastToe: PointF? = null
    private val qualityEstimator = CameraQualityEstimator()
    private var qualityFrame = 0
    private var ballReadiness = 0.0
    private var putterReadiness = 0.0
''')
text = text.replace(
'''        try {
            val yPlane = image.planes[0]
''',
'''        try {
            val sampledQuality = if (++qualityFrame % 5 == 0) qualityEstimator.evaluate(image) else null
            val yPlane = image.planes[0]
''')
text = text.replace(
'''            if (toe != null) lastToe = toe

            val t = image.imageInfo.timestamp
''',
'''            if (toe != null) lastToe = toe

            ballReadiness = ballReadiness * 0.82 + (if (ball != null) 1.0 else 0.0) * 0.18
            putterReadiness = putterReadiness * 0.82 + (if (heel != null && toe != null) 1.0 else 0.0) * 0.18
            sampledQuality?.let { onQuality(LiveQualityGate.build(it, ballReadiness, putterReadiness)) }

            val t = image.imageInfo.timestamp
''')
sv.write_text(text)

# ---------------------------------------------------------------------------
# 3) MainActivity integration: gate, auto tune, setup, support, licensing.
# ---------------------------------------------------------------------------
main = ROOT / 'app/src/main/java/com/puttvision/screen/MainActivity.kt'
text = main.read_text()
text = text.replace(
'''    private lateinit var productBackupManager: ProductBackupManager
    private lateinit var deviceReport: DeviceCapabilityReport
    private val cameraStability = CameraStabilityController()
''',
'''    private lateinit var productBackupManager: ProductBackupManager
    private lateinit var deviceReport: DeviceCapabilityReport
    private lateinit var accuracyAutoTuner: AccuracyAutoTuner
    private lateinit var supportDiagnostics: SupportDiagnosticsExporter
    private lateinit var offlineLicenseManager: OfflineLicenseManager
    private var liveQualitySnapshot: LiveQualityGateSnapshot? = null
    private var firstRunWizardShown = false
    private val cameraStability = CameraStabilityController()
''')
text = text.replace(
'''    private lateinit var tvStatus: TextView
    private lateinit var hfrStatus: TextView
''',
'''    private lateinit var tvStatus: TextView
    private lateinit var hfrStatus: TextView
    private lateinit var qualityStatus: TextView
''')
text = text.replace(
'''                    engine.seedHistory(statsRepository.recent(40))
                    toast(message)
''',
'''                    engine.seedHistory(statsRepository.recent(40))
                    if (::accuracyAutoTuner.isInitialized) accuracyAutoTuner.reload()
                    toast(message)
''')
# Add license picker after backup picker.
needle = '''    private val permission =
        registerForActivityResult(
'''
license_picker = '''    private val licenseImport =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null || !::offlineLicenseManager.isInitialized) return@registerForActivityResult
            runCatching { offlineLicenseManager.importLicense(uri) }
                .onSuccess { toast("라이선스 활성화 · ${it.state}") }
                .onFailure { toast("라이선스 오류: ${it.message ?: "파일 오류"}") }
        }

    private val permission =
        registerForActivityResult(
'''
if needle not in text:
    raise RuntimeError('permission insertion point missing')
text = text.replace(needle, license_picker, 1)
text = text.replace(
'''        accuracyValidationLab = AccuracyValidationLab(this)
        appUpdater = AppUpdater(this)
''',
'''        accuracyValidationLab = AccuracyValidationLab(this)
        accuracyAutoTuner = AccuracyAutoTuner(this, deviceReport.model)
        supportDiagnostics = SupportDiagnosticsExporter(this)
        offlineLicenseManager = OfflineLicenseManager(this)
        appUpdater = AppUpdater(this)
''')
# Quality pill in the live top rail.
text = text.replace(
'''    hfrStatus = statusPill("HFR", Pv.primary) { toast(lastHfrStatusMessage) }
    topBar.addView(hfrStatus)
    tvStatus = statusPill("TV", Pv.textMid) { toast(lastTvStatusMessage) }
''',
'''    hfrStatus = statusPill("HFR", Pv.primary) { toast(lastHfrStatusMessage) }
    topBar.addView(hfrStatus)
    qualityStatus = statusPill("ENV --", Pv.textMid) {
        toast(liveQualitySnapshot?.hint ?: "측정 환경 분석 중")
    }
    topBar.addView(qualityStatus, LinearLayout.LayoutParams(-2, -2).apply { marginStart = pvDp(4) })
    tvStatus = statusPill("TV", Pv.textMid) { toast(lastTvStatusMessage) }
''')
# Calibration success launches first-run guide once.
text = text.replace(
'''                        installNormalAnalyzer(result.homography)
                        maybeAutoStartAfterCalibration()
''',
'''                        installNormalAnalyzer(result.homography)
                        maybeShowFirstRunWizard()
                        maybeAutoStartAfterCalibration()
''')
# Live quality callback.
text = text.replace(
'''                onOverlay = { visual ->
                    runOnUiThread {
                        if (!sessionActive || measurementSuspended) return@runOnUiThread
                        overlay.lastOverlay = visual
                        overlay.invalidate()
                    }
                },
                onShotReady = { metrics ->
''',
'''                onOverlay = { visual ->
                    runOnUiThread {
                        if (!sessionActive || measurementSuspended) return@runOnUiThread
                        overlay.lastOverlay = visual
                        overlay.invalidate()
                    }
                },
                onQuality = { quality ->
                    runOnUiThread {
                        liveQualitySnapshot = quality
                        updateLiveQualityStatus(quality)
                    }
                },
                onShotReady = { metrics ->
''')
# Insert first-run helper before maybeAutoStart.
text = text.replace(
'''    private fun maybeAutoStartAfterCalibration() {
''',
'''    private fun maybeShowFirstRunWizard(force: Boolean = false) {
        if (!force && firstRunWizardShown) return
        firstRunWizardShown = true
        FirstRunSetupV9.showIfNeeded(
            activity = this,
            report = deviceReport,
            calibrationScore = lastCalibrationQualityScore,
            calibrationGrade = lastCalibrationQualityGrade,
            putters = putterProfileStore,
            mat = matCalibrationManager,
            force = force,
            onRecalibrate = { beginAutoCalibration() },
            onTvCalibration = { showTvCalibrationDialog(this, tvCalibrationStore) }
        )
    }

    private fun maybeAutoStartAfterCalibration() {
''')
# License + environmental gate before arming.
text = text.replace(
'''        measurementSuspended = false
        stopSimulation()
''',
'''        if (::offlineLicenseManager.isInitialized) {
            val license = offlineLicenseManager.status()
            if (!license.usable) {
                showOfflineLicenseDialog(this, offlineLicenseManager) { licenseImport.launch("application/json") }
                return
            }
        }

        liveQualitySnapshot?.let { q ->
            if (q.blocked) {
                shotPanelTitle.text = "ENVIRONMENT"
                shotPanelTitle.setTextColor(Pv.amber)
                metricText.text = q.hint
                overlay.status = "${q.label} · WAIT"
                overlay.invalidate()
                if (::voiceCoach.isInitialized) voiceCoach.speakCalibrationProblem(q.hint)
                if (autoPlayEnabled) mainHandler.postDelayed({ if (sessionActive && !measurementSuspended) armPrecision() }, 1100L)
                return
            }
        }

        measurementSuspended = false
        stopSimulation()
''', 1)
# Measurement flow: validation sees untuned raw device result; simulation/UI get accepted tuned result.
old = '''        val processedMetrics = if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.applyFallback(metrics)
        } else metrics
        val confidence = processedMetrics.confidence
        val rejectThreshold = if (source.startsWith("PRECISION")) 0.65 else 0.38
'''
new = '''        val baseMetrics = if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.applyFallback(metrics)
        } else metrics
        val confidence = baseMetrics.confidence
        val rejectThreshold = if (source.startsWith("PRECISION")) 0.65 else 0.38
'''
if old not in text: raise RuntimeError('handleMeasuredShot base block missing')
text = text.replace(old, new, 1)
text = text.replace(
'''        updateMetricCards(processedMetrics)
        if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.observe(processedMetrics)
        }
        if (::accuracyValidationLab.isInitialized) {
            accuracyValidationLab.capture(processedMetrics)
        }

        engine.launch(
            processedMetrics
        )
''',
'''        if (::matCalibrationManager.isInitialized) {
            matCalibrationManager.observe(baseMetrics)
        }
        if (::accuracyValidationLab.isInitialized) {
            accuracyValidationLab.capture(baseMetrics)
        }
        if (::accuracyAutoTuner.isInitialized && ::accuracyValidationLab.isInitialized) {
            accuracyAutoTuner.refresh(accuracyValidationLab.matched())
        }
        val processedMetrics = if (::accuracyAutoTuner.isInitialized) accuracyAutoTuner.apply(baseMetrics) else baseMetrics
        updateMetricCards(processedMetrics)

        engine.launch(
            processedMetrics
        )
''', 1)
# Settings: setup, auto tune, support, license.
text = text.replace(
'''    tools.addView(tool("PHONE ${deviceReport.grade}", "기기 호환성 진단") {
        deviceReport = DeviceDiagnostics.inspect(this)
        showDeviceDiagnostics(this, deviceReport, lastCalibrationQualityScore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("ACCURACY", "기준 센서 정확도 검증") {
''',
'''    tools.addView(tool("PHONE ${deviceReport.grade}", "기기 호환성 진단") {
        deviceReport = DeviceDiagnostics.inspect(this)
        showDeviceDiagnostics(this, deviceReport, lastCalibrationQualityScore)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("QUICK SETUP", "초기 설치 마법사 다시 실행") {
        settingsDialog?.dismiss()
        settingsDialog = null
        maybeShowFirstRunWizard(force = true)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("ACCURACY", "기준 센서 정확도 검증") {
''', 1)
text = text.replace(
'''    tools.addView(tool("PRODUCT", "장비 · 매트 · 핸즈프리") {
''',
'''    tools.addView(tool("AUTO TUNE", "기기별 측정 편향 자동 보정") {
        accuracyAutoTuner.refresh(accuracyValidationLab.matched())
        showAccuracyTuningDialog(this, accuracyAutoTuner, accuracyValidationLab)
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("PRODUCT", "장비 · 매트 · 핸즈프리") {
''', 1)
text = text.replace(
'''    tools.addView(tool("BACKUP", "기록 · 설정 백업 / 복원") {
        showBackupDialog(this, productBackupManager) { backupImport.launch("application/json") }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
''',
'''    tools.addView(tool("BACKUP", "기록 · 설정 백업 / 복원") {
        showBackupDialog(this, productBackupManager) { backupImport.launch("application/json") }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("SUPPORT", "고객지원 진단 ZIP 내보내기") {
        supportDiagnostics.export(
            report = deviceReport,
            calibrationScore = lastCalibrationQualityScore,
            calibrationGrade = lastCalibrationQualityGrade,
            live = liveQualitySnapshot,
            stats = statsRepository,
            putter = putterProfileStore.current(),
            mat = matCalibrationManager,
            tv = tvCalibrationStore,
            tuner = accuracyAutoTuner,
            license = offlineLicenseManager,
            hfrStatus = lastHfrStatusMessage
        )
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
    tools.addView(tool("LICENSE", "기기 활성화 · 라이선스") {
        showOfflineLicenseDialog(this, offlineLicenseManager) { licenseImport.launch("application/json") }
    }, LinearLayout.LayoutParams(-1, pvDp(if (compact) 44 else 50)).apply { topMargin = pvDp(6) })
''', 1)
# Live quality UI helper.
text = text.replace(
'''    private fun setHfrStatus(short: String, detail: String = short) {
        lastHfrStatusMessage = detail
        if (!::hfrStatus.isInitialized) return
        hfrStatus.text = short
    }

    private fun updateAutoButton() {
''',
'''    private fun setHfrStatus(short: String, detail: String = short) {
        lastHfrStatusMessage = detail
        if (!::hfrStatus.isInitialized) return
        hfrStatus.text = short
    }

    private fun updateLiveQualityStatus(q: LiveQualityGateSnapshot) {
        if (!::qualityStatus.isInitialized) return
        qualityStatus.text = q.label
        qualityStatus.setTextColor(if (q.blocked) Pv.amber else if (q.label.startsWith("READY")) Pv.primary else Pv.textMid)
    }

    private fun updateAutoButton() {
''', 1)
main.write_text(text)

# ---------------------------------------------------------------------------
# 4) Backup V9 calibration/setup preferences, but never device-bound license payload.
# ---------------------------------------------------------------------------
p = ROOT / 'app/src/main/java/com/puttvision/screen/ProductizationV8.kt'
text = p.read_text()
text = text.replace('put("schema", 2)', 'put("schema", 3)', 1)
text = text.replace(
'''            val voicePrefs = activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE)
            put("voiceEnabled", voicePrefs.getBoolean("enabled", true))
''',
'''            val voicePrefs = activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE)
            put("voiceEnabled", voicePrefs.getBoolean("enabled", true))
            val tunePrefs = activity.getSharedPreferences("puttvision_accuracy_tune_v1", Context.MODE_PRIVATE)
            put("accuracyTune", JSONObject().apply {
                put("enabled", tunePrefs.getBoolean("enabled", true))
                put("model", tunePrefs.getString("model", ""))
            })
            val setupPrefs = activity.getSharedPreferences("puttvision_first_run_v9", Context.MODE_PRIVATE)
            put("setupV9Completed", setupPrefs.getBoolean("completed", false))
''', 1)
text = text.replace(
'''        require(root.optInt("schema", 0) in 1..2) { "지원하지 않는 백업 형식입니다" }
''',
'''        require(root.optInt("schema", 0) in 1..3) { "지원하지 않는 백업 형식입니다" }
''', 1)
text = text.replace(
'''        activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", root.optBoolean("voiceEnabled", true)).apply()
        return "백업 복원 완료 · ${stats.allProfiles().size}샷"
''',
'''        activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", root.optBoolean("voiceEnabled", true)).apply()
        root.optJSONObject("accuracyTune")?.let { j ->
            activity.getSharedPreferences("puttvision_accuracy_tune_v1", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", j.optBoolean("enabled", true))
                .putString("model", j.optString("model", ""))
                .apply()
        }
        if (root.has("setupV9Completed")) {
            activity.getSharedPreferences("puttvision_first_run_v9", Context.MODE_PRIVATE).edit()
                .putBoolean("completed", root.optBoolean("setupV9Completed", false)).apply()
        }
        return "백업 복원 완료 · ${stats.allProfiles().size}샷"
''', 1)
p.write_text(text)

print('V9 integration staged successfully')
