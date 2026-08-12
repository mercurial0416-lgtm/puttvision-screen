package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Runtime product metadata shared by the pure rendering / engine layer. */
object ProductRuntime {
    @Volatile var putterProfileName: String = "기본 퍼터"
    @Volatile var putterHeadWidthCm: Double = 11.5
    @Volatile var physicalMatStimpM: Double? = null
}

data class FrameQualitySnapshot(
    val brightness: Double,
    val contrast: Double,
    val sharpnessScore: Int,
    val motionScore: Int,
    val noiseScore: Int,
    val overallScore: Int,
    val hint: String
)

/**
 * Lightweight luma-only quality estimator used while QR calibration is running.
 * It deliberately samples a small grid so it does not compete with ML Kit or HFR.
 */
class CameraQualityEstimator {
    private var previous: IntArray? = null

    fun evaluate(image: ImageProxy): FrameQualitySnapshot {
        val plane = image.planes.firstOrNull()
            ?: return FrameQualitySnapshot(0.0, 0.0, 0, 0, 0, 0, "카메라 프레임을 읽을 수 없습니다")
        val buffer = plane.buffer.duplicate()
        val cols = 32
        val rows = 24
        val values = IntArray(cols * rows)
        var n = 0
        val width = image.width.coerceAtLeast(1)
        val height = image.height.coerceAtLeast(1)

        for (gy in 0 until rows) {
            val y = ((gy + 0.5) * height / rows).toInt().coerceIn(0, height - 1)
            for (gx in 0 until cols) {
                val x = ((gx + 0.5) * width / cols).toInt().coerceIn(0, width - 1)
                val index = y * plane.rowStride + x * plane.pixelStride
                if (index in 0 until buffer.limit()) {
                    values[n++] = buffer.get(index).toInt() and 0xff
                }
            }
        }
        if (n < values.size / 2) {
            return FrameQualitySnapshot(0.0, 0.0, 0, 0, 0, 0, "카메라 프레임 품질을 확인할 수 없습니다")
        }

        val sample = if (n == values.size) values else values.copyOf(n)
        val mean = sample.average()
        val variance = sample.map { (it - mean) * (it - mean) }.average()
        val contrast = sqrt(variance)

        var edgeTotal = 0.0
        var edgeCount = 0
        var residualTotal = 0.0
        var residualCount = 0
        if (n == cols * rows) {
            for (y in 1 until rows - 1) {
                for (x in 1 until cols - 1) {
                    val i = y * cols + x
                    val c = values[i].toDouble()
                    val left = values[i - 1].toDouble()
                    val right = values[i + 1].toDouble()
                    val up = values[i - cols].toDouble()
                    val down = values[i + cols].toDouble()
                    edgeTotal += abs(right - left) + abs(down - up)
                    edgeCount += 2
                    val neighborMean = (left + right + up + down) / 4.0
                    residualTotal += abs(c - neighborMean)
                    residualCount++
                }
            }
        }
        val edge = if (edgeCount > 0) edgeTotal / edgeCount else 0.0
        val residual = if (residualCount > 0) residualTotal / residualCount else 40.0

        val old = previous
        var motionDelta = 0.0
        if (old != null && old.size == sample.size) {
            for (i in sample.indices) motionDelta += abs(sample[i] - old[i])
            motionDelta /= sample.size
        }
        previous = sample.copyOf()

        val brightnessScore = when {
            mean < 35.0 -> (mean / 35.0 * 35.0)
            mean < 60.0 -> 35.0 + (mean - 35.0) / 25.0 * 45.0
            mean <= 195.0 -> 100.0
            mean < 225.0 -> 100.0 - (mean - 195.0) / 30.0 * 55.0
            else -> 35.0
        }.coerceIn(0.0, 100.0)
        val contrastScore = ((contrast - 8.0) / 28.0 * 100.0).coerceIn(0.0, 100.0)
        val sharpnessScore = ((edge - 4.0) / 20.0 * 100.0).coerceIn(0.0, 100.0)
        val motionScore = if (old == null) 82.0 else (100.0 - max(0.0, motionDelta - 2.0) * 5.0).coerceIn(0.0, 100.0)
        val noiseScore = (100.0 - max(0.0, residual - 10.0) * 3.2).coerceIn(0.0, 100.0)
        val overall = (
            brightnessScore * 0.24 +
                contrastScore * 0.14 +
                sharpnessScore * 0.30 +
                motionScore * 0.22 +
                noiseScore * 0.10
            ).toInt().coerceIn(0, 100)

        val hint = when {
            mean < 52.0 -> "주변이 어둡습니다 · 매트 쪽 조명을 켜주세요"
            mean > 215.0 -> "화면이 너무 밝습니다 · 직사광/조명을 줄여주세요"
            sharpnessScore < 48.0 -> "초점이 흐립니다 · 카메라 렌즈를 닦고 폰을 고정하세요"
            motionScore < 55.0 -> "카메라 흔들림이 큽니다 · 거치대를 단단히 고정하세요"
            noiseScore < 48.0 -> "영상 노이즈가 큽니다 · 매트 조명을 조금 밝게 해주세요"
            contrastScore < 35.0 -> "마커 대비가 약합니다 · QR 주변을 깔끔하게 정리하세요"
            else -> "밝기·초점·흔들림이 안정적입니다"
        }

        return FrameQualitySnapshot(
            brightness = mean,
            contrast = contrast,
            sharpnessScore = sharpnessScore.toInt(),
            motionScore = motionScore.toInt(),
            noiseScore = noiseScore.toInt(),
            overallScore = overall,
            hint = hint
        )
    }
}

/** Pins focus to the putting zone and locks exposure / white balance after metering. */
@OptIn(ExperimentalCamera2Interop::class)
class CameraStabilityController {
    private var activeCamera: Camera? = null

    fun stabilize(camera: Camera, previewView: PreviewView) {
        activeCamera = camera
        previewView.post {
            val w = previewView.width.toFloat().coerceAtLeast(1f)
            val h = previewView.height.toFloat().coerceAtLeast(1f)
            val point = previewView.meteringPointFactory.createPoint(w * 0.50f, h * 0.62f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            ).disableAutoCancel().build()

            val future = camera.cameraControl.startFocusAndMetering(action)
            future.addListener({
                runCatching {
                    val opts = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                        .build()
                    Camera2CameraControl.from(camera.cameraControl)
                        .setCaptureRequestOptions(opts)
                }
            }, ContextCompat.getMainExecutor(previewView.context))
        }
    }

    fun release() {
        val camera = activeCamera ?: return
        runCatching { camera.cameraControl.cancelFocusAndMetering() }
        runCatching {
            Camera2CameraControl.from(camera.cameraControl).clearCaptureRequestOptions()
        }
        activeCamera = null
    }
}

data class PutterProfile(
    val id: String,
    val name: String,
    val headWidthCm: Double
)

class PutterProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_putters_v1", Context.MODE_PRIVATE)
    private var profiles: MutableList<PutterProfile> = loadProfiles().toMutableList()

    init {
        if (profiles.isEmpty()) {
            profiles += PutterProfile("default", "기본 퍼터", 11.5)
            persist()
        }
        syncRuntime()
    }

    fun all(): List<PutterProfile> = profiles.toList()

    fun current(): PutterProfile {
        val id = prefs.getString("current", null)
        return profiles.firstOrNull { it.id == id } ?: profiles.first()
    }

    fun select(id: String): PutterProfile {
        val selected = profiles.firstOrNull { it.id == id } ?: current()
        prefs.edit().putString("current", selected.id).apply()
        syncRuntime()
        return selected
    }

    fun save(nameRaw: String, widthRaw: Double): PutterProfile {
        val name = nameRaw.trim().ifBlank { "내 퍼터" }.replace("|", " ").replace(";", " ")
        val width = widthRaw.coerceIn(8.0, 15.0)
        val existing = profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val profile = if (existing != null) {
            val updated = existing.copy(headWidthCm = width)
            profiles[profiles.indexOf(existing)] = updated
            updated
        } else {
            PutterProfile(UUID.randomUUID().toString().take(8), name, width).also { profiles += it }
        }
        prefs.edit().putString("current", profile.id).apply()
        persist()
        syncRuntime()
        return profile
    }

    fun delete(id: String) {
        if (profiles.size <= 1) return
        profiles.removeAll { it.id == id }
        if (profiles.none { it.id == prefs.getString("current", null) }) {
            prefs.edit().putString("current", profiles.first().id).apply()
        }
        persist()
        syncRuntime()
    }

    private fun syncRuntime() {
        val p = current()
        ProductRuntime.putterProfileName = p.name
        ProductRuntime.putterHeadWidthCm = p.headWidthCm
    }

    private fun persist() {
        val payload = profiles.joinToString(";") { "${it.id}|${it.name}|${it.headWidthCm}" }
        prefs.edit().putString("profiles", payload).apply()
    }

    private fun loadProfiles(): List<PutterProfile> {
        val payload = prefs.getString("profiles", null) ?: return emptyList()
        return payload.split(';').mapNotNull { item ->
            val p = item.split('|')
            if (p.size != 3) return@mapNotNull null
            val width = p[2].toDoubleOrNull() ?: return@mapNotNull null
            PutterProfile(p[0], p[1], width.coerceIn(8.0, 15.0))
        }
    }
}

fun showPutterProfileManager(
    context: Context,
    store: PutterProfileStore,
    onChanged: (PutterProfile) -> Unit
) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(10), context.pvDp(18), context.pvDp(4))
    }
    root.addView(TextView(context).apply {
        text = "퍼터 헤드 폭을 실제 값으로 맞추면 TV의 ‘몇 헤드’ 가이드가 내 퍼터 기준으로 계산됩니다."
        setTextColor(Pv.textMid)
        textSize = context.pvSp(9f)
        setPadding(0, 0, 0, context.pvDp(8))
    })

    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context).apply { addView(list) }
    root.addView(scroll, LinearLayout.LayoutParams(-1, context.pvDp(150)))

    lateinit var dialog: AlertDialog
    fun rebuild() {
        list.removeAllViews()
        val current = store.current()
        store.all().forEach { profile ->
            val row = Button(context).apply {
                isAllCaps = false
                text = "${if (profile.id == current.id) "●" else "○"}  ${profile.name}    ${"%.1f".format(profile.headWidthCm)}cm"
                setTextColor(if (profile.id == current.id) Pv.primary else Pv.textHi)
                textSize = context.pvSp(9f)
                gravity = Gravity.CENTER_VERTICAL
                background = context.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
                setOnClickListener {
                    val selected = store.select(profile.id)
                    onChanged(selected)
                    rebuild()
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, context.pvDp(42)).apply { bottomMargin = context.pvDp(5) })
        }
    }

    val name = EditText(context).apply {
        hint = "퍼터 이름"
        setText(store.current().name)
        setTextColor(Pv.textHi)
        setHintTextColor(Pv.textLo)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Pv.primary)
        setSingleLine(true)
    }
    val width = EditText(context).apply {
        hint = "헤드 폭(cm)"
        setText("%.1f".format(store.current().headWidthCm))
        setTextColor(Pv.textHi)
        setHintTextColor(Pv.textLo)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        backgroundTintList = android.content.res.ColorStateList.valueOf(Pv.primary)
        setSingleLine(true)
    }
    root.addView(name, LinearLayout.LayoutParams(-1, context.pvDp(46)))
    root.addView(width, LinearLayout.LayoutParams(-1, context.pvDp(46)))

    dialog = AlertDialog.Builder(context)
        .setTitle("내 퍼터")
        .setView(root)
        .setNegativeButton("닫기", null)
        .setPositiveButton("저장 / 적용", null)
        .create()
    dialog.setOnShowListener {
        rebuild()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cm = width.text.toString().toDoubleOrNull()
            if (cm == null || cm !in 8.0..15.0) {
                Toast.makeText(context, "퍼터 헤드 폭은 8.0~15.0cm로 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val saved = store.save(name.text.toString(), cm)
            onChanged(saved)
            dialog.dismiss()
        }
    }
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.show()
}

class MatCalibrationManager(context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_mat_cal_v1", Context.MODE_PRIVATE)
    private val samples = mutableListOf<Double>()

    init {
        prefs.getString("samples", "")
            ?.split(',')
            ?.mapNotNull { it.toDoubleOrNull() }
            ?.filter { it in 1.5..5.0 }
            ?.let { samples += it.takeLast(9) }
        syncRuntime()
    }

    fun observe(metrics: ShotMetrics) {
        val confidence = metrics.confidence ?: 0.0
        val stimp = metrics.estimatedMatStimpM ?: return
        if (confidence < 0.72 || stimp !in 1.5..5.0) return
        samples += stimp
        while (samples.size > 9) samples.removeAt(0)
        persist()
        syncRuntime()
    }

    fun calibratedStimp(): Double? {
        if (samples.size < 3) return null
        val sorted = samples.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }

    fun sampleCount(): Int = samples.size

    fun statusLabel(): String = calibratedStimp()?.let {
        "실측 ${"%.2f".format(it)}m · ${samples.size}샷"
    } ?: "자동 측정 ${samples.size}/3"

    fun reset() {
        samples.clear()
        persist()
        syncRuntime()
    }

    /**
     * HFR normally performs per-shot back-extrapolation. If that estimate is
     * missing, use the persisted physical-mat median to recover impact speed.
     */
    fun applyFallback(metrics: ShotMetrics): ShotMetrics {
        if (metrics.estimatedMatDecelMps2 != null) return metrics
        val raw = metrics.rawBallSpeedMps ?: return metrics
        val stimp = calibratedStimp() ?: return metrics
        val stimpmeterLaunch = 1.95072
        val decel = stimpmeterLaunch * stimpmeterLaunch / (2.0 * stimp)
        val corrected = sqrt(raw * raw + 2.0 * decel * 0.10)
        if (!corrected.isFinite() || corrected <= metrics.ballSpeedMps) return metrics
        val newSmash = metrics.headSpeedMps?.takeIf { it > 0.05 }?.let { corrected / it }
        return metrics.copy(
            ballSpeedMps = corrected,
            smash = newSmash ?: metrics.smash,
            estimatedMatDecelMps2 = decel,
            estimatedMatStimpM = stimp
        )
    }

    private fun persist() {
        prefs.edit().putString("samples", samples.joinToString(",")).apply()
    }

    private fun syncRuntime() {
        ProductRuntime.physicalMatStimpM = calibratedStimp()
    }
}

fun showMatCalibrationManager(context: Context, manager: MatCalibrationManager) {
    val calibrated = manager.calibratedStimp()
    val message = buildString {
        append("PuttVision은 신뢰도 높은 HFR 샷의 실제 감속을 모아 물리 퍼팅매트 속도를 자동 학습합니다.\n\n")
        if (calibrated != null) {
            append("현재 매트: ${"%.2f".format(calibrated)}m  ·  ${manager.sampleCount()}개 유효 샷\n")
            append("개별 샷 감속값이 부족할 때 이 기준값으로 임팩트 속도를 보정합니다.")
        } else {
            append("현재 ${manager.sampleCount()}/3샷 수집됨\n")
            append("평지에서 보통 세기로 몇 번 퍼팅하면 자동으로 완료됩니다.")
        }
    }
    AlertDialog.Builder(context)
        .setTitle("물리 매트 자동 보정")
        .setMessage(message)
        .setNegativeButton("닫기", null)
        .setPositiveButton("다시 측정") { _, _ ->
            manager.reset()
            Toast.makeText(context, "매트 보정값을 초기화했습니다 · 다음 HFR 샷부터 다시 학습합니다", Toast.LENGTH_SHORT).show()
        }
        .show()
}

class HandsFreeVoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE)
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ready = false
    private var lastReadyAt = 0L

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        private set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = tts.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            tts.setSpeechRate(1.02f)
        }
    }

    fun toggle(): Boolean {
        enabled = !enabled
        if (enabled) speak("음성 안내를 시작합니다", flush = true)
        else tts.stop()
        return enabled
    }

    fun speakReady(read: GreenRead? = null) {
        val now = System.currentTimeMillis()
        if (now - lastReadyAt < 1500L) return
        lastReadyAt = now
        val guide = when {
            read == null || read.aimSideLabel == "센터" -> "준비 완료. 에임은 센터입니다."
            else -> "준비 완료. ${read.aimSideLabel} ${"%.1f".format(read.cupCount)}컵을 보세요."
        }
        speak(guide, flush = true)
    }

    fun speakRetry(confidencePct: Int) {
        speak("측정 품질 ${confidencePct}퍼센트. 다시 퍼팅해 주세요.", flush = true)
    }

    fun speakCalibrationProblem(hint: String) {
        speak(hint.replace("·", "."), flush = true)
    }

    fun speakResult(result: SimResult, launchAngleDeg: Double?) {
        val direction = when {
            result.holed -> ""
            result.finishX > 0.03 -> " 오른쪽입니다."
            result.finishX < -0.03 -> " 왼쪽입니다."
            else -> " 센터입니다."
        }
        val start = launchAngleDeg?.let {
            if (abs(it) < 0.25) "" else " 출발선 ${if (it > 0) "오른쪽" else "왼쪽"} ${"%.1f".format(abs(it))}도."
        } ?: ""
        val text = if (result.holed) {
            "홀인.$start"
        } else {
            "컵까지 ${"%.0f".format(result.distanceToCupM * 100.0)}센티.$direction$start"
        }
        speak(text, flush = true)
    }

    fun shutdown() {
        ready = false
        tts.stop()
        tts.shutdown()
    }

    private fun speak(text: String, flush: Boolean) {
        if (!enabled || !ready) return
        tts.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "pv-${System.nanoTime()}"
        )
    }
}

fun View.productHaptic() {
    runCatching { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
}

fun View.installProductPressFeedback() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.985f).scaleY(0.985f).alpha(0.88f).setDuration(70L).start()
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(110L).start()
            }
        }
        false
    }
}

fun View.animateProductEnter() {
    alpha = 0f
    translationY = resources.displayMetrics.density * 8f
    animate().alpha(1f).translationY(0f).setDuration(180L).start()
}
