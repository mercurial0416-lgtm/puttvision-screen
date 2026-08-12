package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/**
 * Live pre-shot readiness. Image quality is a hard gate; ball / putter recognition
 * is deliberately a soft signal so AUTO can arm before the player settles over the ball.
 */
data class LiveQualityGateSnapshot(
    val score: Int,
    val frameScore: Int,
    val ballReadyPct: Int,
    val putterReadyPct: Int,
    val blocked: Boolean,
    val label: String,
    val hint: String
)

object LiveQualityGate {
    fun build(
        frame: FrameQualitySnapshot,
        ballReady: Double,
        putterReady: Double
    ): LiveQualityGateSnapshot {
        val ballPct = (ballReady.coerceIn(0.0, 1.0) * 100.0).toInt()
        val putterPct = (putterReady.coerceIn(0.0, 1.0) * 100.0).toInt()
        val environmentBlocked =
            frame.overallScore < 58 ||
                frame.sharpnessScore < 38 ||
                frame.motionScore < 42 ||
                frame.brightness < 40.0 ||
                frame.brightness > 220.0

        // Recognition contributes to the visible readiness score but never blocks
        // arming on its own. A player may place the ball after AUTO has already armed.
        val score = (frame.overallScore * 0.86 + ((ballPct + putterPct) / 2.0) * 0.14)
            .toInt().coerceIn(0, 100)
        val label = when {
            environmentBlocked -> "ENV ${frame.overallScore}"
            ballPct >= 55 && putterPct >= 45 -> "READY $score"
            else -> "SET $score"
        }
        val hint = when {
            environmentBlocked -> frame.hint
            ballPct < 35 -> "환경은 정상입니다 · 공을 퍼팅 위치에 놓아주세요"
            putterPct < 30 -> "환경은 정상입니다 · 퍼터 마커가 카메라에 보이는지 확인하세요"
            else -> "측정 환경 안정 · BALL ${ballPct}% · PUTTER ${putterPct}%"
        }
        return LiveQualityGateSnapshot(
            score = score,
            frameScore = frame.overallScore,
            ballReadyPct = ballPct,
            putterReadyPct = putterPct,
            blocked = environmentBlocked,
            label = label,
            hint = hint
        )
    }
}

/** Robust, bounded correction model learned only from matched reference-sensor shots. */
data class AccuracyCorrectionModel(
    val sampleCount: Int,
    val ballScale: Double,
    val launchOffsetDeg: Double,
    val headScale: Double,
    val faceOffsetDeg: Double,
    val pathOffsetDeg: Double,
    val improvementPct: Double,
    val updatedAtMs: Long = System.currentTimeMillis()
)

object AccuracyModelCalculator {
    private fun median(values: List<Double>, fallback: Double): Double {
        if (values.isEmpty()) return fallback
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    fun derive(samples: List<ValidationSample>): AccuracyCorrectionModel? {
        val matched = samples.filter {
            it.refBall != null || it.refLaunch != null || it.refHead != null || it.refFace != null || it.refPath != null
        }
        if (matched.size < 8) return null

        val ballScale = median(matched.mapNotNull { s ->
            val r = s.refBall
            if (r != null && s.measuredBall > 0.15) r / s.measuredBall else null
        }, 1.0).coerceIn(0.92, 1.08)
        val headScale = median(matched.mapNotNull { s ->
            val m = s.measuredHead
            val r = s.refHead
            if (m != null && r != null && m > 0.10) r / m else null
        }, 1.0).coerceIn(0.92, 1.08)
        val launchOffset = median(matched.mapNotNull { s -> s.refLaunch?.let { it - s.measuredLaunch } }, 0.0)
            .coerceIn(-1.20, 1.20)
        val faceOffset = median(matched.mapNotNull { s ->
            val m = s.measuredFace
            val r = s.refFace
            if (m != null && r != null) r - m else null
        }, 0.0).coerceIn(-1.50, 1.50)
        val pathOffset = median(matched.mapNotNull { s ->
            val m = s.measuredPath
            val r = s.refPath
            if (m != null && r != null) r - m else null
        }, 0.0).coerceIn(-1.50, 1.50)

        val candidate = AccuracyCorrectionModel(
            sampleCount = matched.size,
            ballScale = ballScale,
            launchOffsetDeg = launchOffset,
            headScale = headScale,
            faceOffsetDeg = faceOffset,
            pathOffsetDeg = pathOffset,
            improvementPct = 0.0
        )
        val before = normalizedError(matched, null)
        val after = normalizedError(matched, candidate)
        if (!before.isFinite() || before <= 1e-9 || !after.isFinite()) return null
        val improvement = ((before - after) / before * 100.0).coerceIn(-100.0, 100.0)
        return candidate.copy(improvementPct = improvement)
    }

    private fun normalizedError(samples: List<ValidationSample>, model: AccuracyCorrectionModel?): Double {
        val errors = ArrayList<Double>()
        samples.forEach { s ->
            val ball = s.measuredBall * (model?.ballScale ?: 1.0)
            val launch = s.measuredLaunch + (model?.launchOffsetDeg ?: 0.0)
            val head = s.measuredHead?.times(model?.headScale ?: 1.0)
            val face = s.measuredFace?.plus(model?.faceOffsetDeg ?: 0.0)
            val path = s.measuredPath?.plus(model?.pathOffsetDeg ?: 0.0)
            s.refBall?.takeIf { abs(it) > .05 }?.let { errors += abs(ball - it) / abs(it) / 0.03 }
            s.refLaunch?.let { errors += abs(launch - it) / 0.45 }
            if (head != null) s.refHead?.takeIf { abs(it) > .05 }?.let { errors += abs(head - it) / abs(it) / 0.04 }
            if (face != null) s.refFace?.let { errors += abs(face - it) / 0.55 }
            if (path != null) s.refPath?.let { errors += abs(path - it) / 0.55 }
        }
        return errors.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
    }

    fun apply(model: AccuracyCorrectionModel, metrics: ShotMetrics): ShotMetrics {
        val ball = metrics.ballSpeedMps * model.ballScale
        val head = metrics.headSpeedMps?.times(model.headScale)
        val launch = metrics.launchAngleDeg + model.launchOffsetDeg
        val face = metrics.faceAngleDeg?.plus(model.faceOffsetDeg)
        val path = metrics.pathAngleDeg?.plus(model.pathOffsetDeg)
        val f2p = if (face != null && path != null) face - path else metrics.faceToPathDeg
        val smash = head?.takeIf { it > .05 }?.let { ball / it } ?: metrics.smash
        return metrics.copy(
            ballSpeedMps = ball,
            launchAngleDeg = launch,
            headSpeedMps = head,
            faceAngleDeg = face,
            pathAngleDeg = path,
            faceToPathDeg = f2p,
            smash = smash
        )
    }
}

class AccuracyAutoTuner(context: Context, private val deviceKey: String) {
    private val prefs = context.getSharedPreferences("puttvision_accuracy_tune_v1", Context.MODE_PRIVATE)
    @Volatile private var activeKey: String = AccuracyProfileKey.build(deviceKey, "BACK", 0, "NORMAL", Build.VERSION.SDK_INT)
    @Volatile private var model: AccuracyCorrectionModel? = loadProfile(activeKey) ?: loadLegacy()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    @Synchronized
    fun selectProfile(
        fps: Int,
        resolution: String,
        cameraId: String,
        api: Int = Build.VERSION.SDK_INT
    ): String {
        val next = AccuracyProfileKey.build(deviceKey, cameraId, fps, resolution, api)
        if (next != activeKey) {
            activeKey = next
            model = loadProfile(next)
        }
        return next
    }

    fun profileKey(): String = activeKey

    fun refresh(samples: List<ValidationSample>, force: Boolean = false): AccuracyCorrectionModel? {
        val exact = samples.filter { it.profileKey == activeKey }
        val scoped = if (exact.size >= 8) exact else samples.filter { it.profileKey == activeKey || it.profileKey == null }
        val derived = AccuracyModelCalculator.derive(scoped) ?: return model
        val current = model
        if (!force && current != null && current.sampleCount == derived.sampleCount) return current
        if (derived.improvementPct >= 7.0) {
            model = derived
            persist(activeKey, derived)
        }
        return model
    }

    fun apply(metrics: ShotMetrics): ShotMetrics {
        val m = model
        return if (enabled && m != null && m.improvementPct >= 7.0) AccuracyModelCalculator.apply(m, metrics) else metrics
    }

    fun current(): AccuracyCorrectionModel? = model

    fun reset() {
        model = null
        prefs.edit().remove(AccuracyProfileKey.slot(activeKey)).apply()
    }

    fun reload() { model = loadProfile(activeKey) ?: loadLegacy() }

    fun summary(): String {
        val mode = activeKey.substringAfter("|CAM:", "BACK|FPS:0|SIZE:NORMAL|API:${Build.VERSION.SDK_INT}")
        val m = model ?: return "$mode · 기준 센서 8샷 이상 필요"
        return "$mode · ${m.sampleCount}샷 · 개선 ${"%.0f".format(m.improvementPct)}% · BALL ×${"%.3f".format(m.ballScale)} · START ${"%+.2f".format(m.launchOffsetDeg)}°"
    }

    private fun persist(profileKey: String, m: AccuracyCorrectionModel) {
        val j = JSONObject().apply {
            put("device", deviceKey)
            put("profile", profileKey)
            put("n", m.sampleCount)
            put("ball", m.ballScale)
            put("launch", m.launchOffsetDeg)
            put("head", m.headScale)
            put("face", m.faceOffsetDeg)
            put("path", m.pathOffsetDeg)
            put("improvement", m.improvementPct)
            put("updated", m.updatedAtMs)
        }
        prefs.edit().putString(AccuracyProfileKey.slot(profileKey), j.toString()).apply()
    }

    private fun loadProfile(profileKey: String): AccuracyCorrectionModel? {
        val raw = prefs.getString(AccuracyProfileKey.slot(profileKey), null) ?: return null
        return parseModel(raw, expectedProfile = profileKey)
    }

    private fun loadLegacy(): AccuracyCorrectionModel? {
        val raw = prefs.getString("model", null) ?: return null
        return parseModel(raw, expectedProfile = null)
    }

    private fun parseModel(raw: String, expectedProfile: String?): AccuracyCorrectionModel? = runCatching {
        val j = JSONObject(raw)
        if (j.optString("device") != deviceKey) return@runCatching null
        if (expectedProfile != null && j.optString("profile") != expectedProfile) return@runCatching null
        AccuracyCorrectionModel(
            sampleCount = j.getInt("n"),
            ballScale = j.getDouble("ball"),
            launchOffsetDeg = j.getDouble("launch"),
            headScale = j.getDouble("head"),
            faceOffsetDeg = j.getDouble("face"),
            pathOffsetDeg = j.getDouble("path"),
            improvementPct = j.getDouble("improvement"),
            updatedAtMs = j.optLong("updated", 0L)
        )
    }.getOrNull()
}

fun showAccuracyTuningDialog(activity: Activity, tuner: AccuracyAutoTuner, lab: AccuracyValidationLab) {
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.pvDp(16), activity.pvDp(8), activity.pvDp(16), activity.pvDp(4))
    }
    val status = TextView(activity).apply {
        text = "AUTO TUNE ${if (tuner.enabled) "ON" else "OFF"}\n${tuner.summary()}"
        setTextColor(Pv.textHi)
        textSize = activity.pvSp(9f)
        setLineSpacing(activity.pvDp(3).toFloat(), 1f)
    }
    root.addView(status)
    root.addView(TextView(activity).apply {
        text = "기준 센서와 매칭된 샷에서 기기별 편향을 계산합니다. 최소 8샷, 예상 오차 개선 7% 이상일 때만 자동 적용합니다."
        setTextColor(Pv.textMid)
        textSize = activity.pvSp(8f)
        setPadding(0, activity.pvDp(8), 0, 0)
    })
    val dialog = AlertDialog.Builder(activity)
        .setTitle("ACCURACY AUTO TUNE")
        .setView(root)
        .setNegativeButton("닫기", null)
        .setNeutralButton(if (tuner.enabled) "보정 끄기" else "보정 켜기") { _, _ -> tuner.enabled = !tuner.enabled }
        .setPositiveButton("다시 계산") { _, _ ->
            val m = tuner.refresh(lab.matched(), force = true)
            Toast.makeText(activity, m?.let { "자동 보정 갱신 · ${tuner.summary()}" } ?: "기준값이 8샷 이상 필요합니다", Toast.LENGTH_LONG).show()
        }
        .create()
    dialog.setOnLongClickListenerForReset(tuner)
    dialog.show()
}

private fun AlertDialog.setOnLongClickListenerForReset(tuner: AccuracyAutoTuner) {
    setOnShowListener {
        getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnLongClickListener {
            tuner.reset()
            Toast.makeText(context, "기기별 자동 보정값을 초기화했습니다", Toast.LENGTH_SHORT).show()
            dismiss()
            true
        }
    }
}

object FirstRunSetupV9 {
    private const val PREF = "puttvision_first_run_v9"
    private const val KEY_DONE = "completed"

    fun showIfNeeded(
        activity: Activity,
        report: DeviceCapabilityReport,
        calibrationScore: Int,
        calibrationGrade: String,
        putters: PutterProfileStore,
        mat: MatCalibrationManager,
        force: Boolean = false,
        onRecalibrate: () -> Unit,
        onTvCalibration: () -> Unit
    ): Boolean {
        val prefs = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean(KEY_DONE, false)) return false
        val current = putters.current()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.pvDp(18), activity.pvDp(8), activity.pvDp(18), activity.pvDp(4))
        }
        fun step(kicker: String, title: String, detail: String) {
            root.addView(TextView(activity).apply {
                text = kicker
                setTextColor(Pv.primary)
                textSize = activity.pvSp(6.5f)
                letterSpacing = .12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, activity.pvDp(7), 0, 0)
            })
            root.addView(TextView(activity).apply {
                text = title
                setTextColor(Pv.textHi)
                textSize = activity.pvSp(10f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            root.addView(TextView(activity).apply {
                text = detail
                setTextColor(Pv.textMid)
                textSize = activity.pvSp(7.8f)
            })
        }
        step("01 · PHONE", "${report.grade} · ${report.maxHfrFps.takeIf { it > 0 }?.let { "${it}fps" } ?: "NORMAL"}", report.recommendation)
        step("02 · CAMERA", "CAL $calibrationScore · $calibrationGrade", "현재 카메라 위치·밝기·선명도 검사를 통과했습니다.")
        step("03 · MAT", mat.statusLabel(), "평지 퍼팅 3샷부터 실제 매트 감속을 자동 학습합니다.")
        step("04 · PUTTER", "내 퍼터 크기", "TV의 ‘몇 헤드’ 가이드를 실제 퍼터 폭 기준으로 맞춥니다.")

        val name = EditText(activity).apply {
            setText(current.name)
            hint = "퍼터 이름"
            setTextColor(Pv.textHi)
            setHintTextColor(Pv.textLo)
            setSingleLine(true)
        }
        val width = EditText(activity).apply {
            setText("%.1f".format(current.headWidthCm))
            hint = "헤드 폭 cm · 8~15"
            setTextColor(Pv.textHi)
            setHintTextColor(Pv.textLo)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
        root.addView(name, LinearLayout.LayoutParams(-1, activity.pvDp(42)).apply { topMargin = activity.pvDp(4) })
        root.addView(width, LinearLayout.LayoutParams(-1, activity.pvDp(42)))
        step("05 · TV", "화면 안전영역", "HDMI / DeX 연결 후 모서리가 잘리면 TV 화면 보정에서 한 번만 맞추면 됩니다.")

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (force) "빠른 설치 다시 실행" else "PuttVision 빠른 설치")
            .setView(root)
            .setNegativeButton("캘 다시") { _, _ -> onRecalibrate() }
            .setNeutralButton("TV 보정") { _, _ -> onTvCalibration() }
            .setPositiveButton("설정 완료", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cm = width.text.toString().toDoubleOrNull()
                if (cm == null || cm !in 8.0..15.0) {
                    Toast.makeText(activity, "퍼터 헤드 폭은 8.0~15.0cm로 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                putters.save(name.text.toString(), cm)
                prefs.edit().putBoolean(KEY_DONE, true).putLong("completedAt", System.currentTimeMillis()).apply()
                dialog.dismiss()
                Toast.makeText(activity, "설치 완료 · 이제 공을 놓고 퍼팅하면 됩니다", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
        return true
    }
}

data class OfflineLicenseStatus(
    val usable: Boolean,
    val state: String,
    val detail: String,
    val customer: String? = null,
    val expiresAtMs: Long? = null
)

/**
 * Optional signed offline license path for consumer builds.
 * Until PV_LICENSE_PUBLIC_KEY_B64 is configured at build time, enforcement is disabled
 * so development and existing installs are never accidentally locked out.
 */
class OfflineLicenseManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_license_v1", Context.MODE_PRIVATE)

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val raw = "${context.packageName}|$androidId|${Build.MANUFACTURER}|${Build.MODEL}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }.uppercase(Locale.US)
    }

    fun status(): OfflineLicenseStatus {
        if (BuildConfig.DEVELOPER_BUILD) {
            return OfflineLicenseStatus(true, "DEVELOPER", "개발자 빌드 · 라이선스 제한 없음")
        }
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY_B64.trim()
        if (publicKey.isBlank()) {
            return OfflineLicenseStatus(true, "LOCAL", "판매용 라이선스 공개키 미설정 · 현재는 로컬 사용 허용")
        }
        val raw = prefs.getString("license", null)
            ?: return OfflineLicenseStatus(false, "ACTIVATION REQUIRED", "기기 활성화 파일이 필요합니다")
        return verify(raw, publicKey)
    }

    fun importLicense(uri: Uri): OfflineLicenseStatus {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("라이선스 파일을 열 수 없습니다")
        val publicKey = BuildConfig.LICENSE_PUBLIC_KEY_B64.trim()
        require(publicKey.isNotBlank()) { "이 빌드에는 라이선스 공개키가 설정되지 않았습니다" }
        val result = verify(raw, publicKey)
        require(result.usable) { result.detail }
        prefs.edit().putString("license", raw).apply()
        return result
    }

    fun clear() { prefs.edit().remove("license").apply() }

    private fun verify(raw: String, publicKeyB64: String): OfflineLicenseStatus {
        return runCatching {
            val outer = JSONObject(raw)
            val payloadBytes = Base64.decode(outer.getString("payload"), Base64.DEFAULT)
            val signatureBytes = Base64.decode(outer.getString("signature"), Base64.DEFAULT)
            val keyBytes = Base64.decode(publicKeyB64, Base64.DEFAULT)
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(payloadBytes)
            if (!verifier.verify(signatureBytes)) {
                return@runCatching OfflineLicenseStatus(false, "INVALID", "라이선스 서명 검증 실패")
            }
            val payload = JSONObject(payloadBytes.toString(Charsets.UTF_8))
            val boundDevice = payload.optString("deviceId")
            val expires = payload.optLong("expiresAtMs", Long.MAX_VALUE)
            val customer = payload.optString("customer").takeIf { it.isNotBlank() }
            when {
                boundDevice != deviceId() -> OfflineLicenseStatus(false, "WRONG DEVICE", "다른 기기에 발급된 라이선스입니다", customer, expires)
                expires < System.currentTimeMillis() -> OfflineLicenseStatus(false, "EXPIRED", "라이선스 기간이 만료되었습니다", customer, expires)
                else -> OfflineLicenseStatus(true, "ACTIVE", "기기 활성화 완료", customer, expires)
            }
        }.getOrElse { OfflineLicenseStatus(false, "INVALID", it.message ?: "라이선스 파일 오류") }
    }
}

fun showOfflineLicenseDialog(activity: Activity, manager: OfflineLicenseManager, onImport: () -> Unit) {
    val s = manager.status()
    val message = buildString {
        append("상태  ${s.state}\n")
        append("기기 ID  ${manager.deviceId()}\n")
        s.customer?.let { append("사용자  $it\n") }
        s.expiresAtMs?.takeIf { it != Long.MAX_VALUE }?.let { append("만료  $it\n") }
        append("\n${s.detail}")
    }
    val dialog = AlertDialog.Builder(activity)
        .setTitle("LICENSE · ${s.state}")
        .setMessage(message)
        .setNegativeButton("닫기", null)
        .setNeutralButton("기기 ID 복사") { _, _ ->
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("PuttVision device ID", manager.deviceId()))
            Toast.makeText(activity, "기기 ID 복사됨", Toast.LENGTH_SHORT).show()
        }
        .setPositiveButton("활성화 파일") { _, _ -> onImport() }
        .create()
    dialog.show()
}

class SupportDiagnosticsExporter(private val activity: Activity) {
    fun export(
        report: DeviceCapabilityReport,
        calibrationScore: Int,
        calibrationGrade: String,
        live: LiveQualityGateSnapshot?,
        stats: StatsRepository,
        putter: PutterProfile,
        mat: MatCalibrationManager,
        tv: TvCalibrationStore,
        tuner: AccuracyAutoTuner,
        license: OfflineLicenseManager,
        hfrStatus: String
    ) {
        val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
        val zip = File(dir, "PuttVision-support-${System.currentTimeMillis()}.zip")
        val recent = stats.recent(20)
        val json = JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("version", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("channel", if (BuildConfig.DEVELOPER_BUILD) "developer" else "consumer")
            put("device", JSONObject().apply {
                put("model", report.model); put("grade", report.grade); put("maxHfrFps", report.maxHfrFps)
                put("hfrSize", report.bestHfrSize); put("camera2", report.hardwareLevel); put("memoryMb", report.memoryMb)
            })
            put("calibration", JSONObject().apply { put("score", calibrationScore); put("grade", calibrationGrade) })
            live?.let { q -> put("liveQuality", JSONObject().apply {
                put("score", q.score); put("frameScore", q.frameScore); put("ballReady", q.ballReadyPct)
                put("putterReady", q.putterReadyPct); put("blocked", q.blocked); put("hint", q.hint)
            }) }
            put("putter", JSONObject().apply { put("name", putter.name); put("headWidthCm", putter.headWidthCm) })
            put("mat", JSONObject().apply { put("status", mat.statusLabel()); put("stimp", mat.calibratedStimp() ?: JSONObject.NULL) })
            put("tv", JSONObject().apply {
                val c = tv.current(); put("scaleX", c.scaleX); put("scaleY", c.scaleY); put("offsetX", c.offsetX); put("offsetY", c.offsetY)
            })
            put("autoTune", tuner.summary())
            put("licenseState", license.status().state)
            put("hfrStatus", hfrStatus)
            put("lastCrashPresent", CrashJournal.lastCrash(activity) != null)
            put("shotCount", stats.all().size)
        }

        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            fun textEntry(name: String, text: String) {
                out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
            }
            textEntry("diagnostics.json", json.toString(2))
            val csv = buildString {
                appendLine("timestamp,target_m,stimp,ball_mps,start_deg,head_mps,face_deg,path_deg,confidence,score,cup_error_cm")
                recent.forEach { r ->
                    appendLine(listOf(
                        r.timestampMs, r.targetDistanceM, r.stimpMeters,
                        r.metrics.ballSpeedMps, r.metrics.launchAngleDeg,
                        r.metrics.headSpeedMps ?: "", r.metrics.faceAngleDeg ?: "", r.metrics.pathAngleDeg ?: "",
                        r.metrics.confidence ?: "", r.strokeScore.total,
                        r.result?.distanceToCupM?.times(100.0) ?: ""
                    ).joinToString(","))
                }
            }
            textEntry("recent-shots.csv", csv)
            CrashJournal.lastCrash(activity)?.let { textEntry("last-crash.txt", it) }
            textEntry("README.txt", "PuttVision support diagnostics. No camera images, GitHub token, license payload, or Android ID are included.\n")
        }

        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", zip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "PuttVision 고객지원 진단 보내기"))
    }
}
