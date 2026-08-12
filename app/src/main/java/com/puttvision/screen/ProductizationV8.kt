package com.puttvision.screen

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

object ProductSessionRuntime {
    @Volatile var userProfileId: String = "owner"
    @Volatile var userProfileName: String = "나"
    @Volatile var tvScaleX: Float = 1f
    @Volatile var tvScaleY: Float = 1f
    @Volatile var tvOffsetX: Float = 0f
    @Volatile var tvOffsetY: Float = 0f
    @Volatile var tvCalibrationGuide: Boolean = false
    @Volatile var deviceGrade: String = "--"
}

data class UserProfile(
    val id: String,
    val name: String,
    val guest: Boolean
)

class UserProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_users_v1", Context.MODE_PRIVATE)
    private var profiles = load().toMutableList()

    init {
        if (profiles.isEmpty()) {
            profiles += UserProfile("owner", "나", false)
            profiles += UserProfile("guest", "게스트", true)
            persist()
        }
        syncRuntime()
    }

    fun all(): List<UserProfile> = profiles.toList()

    fun current(): UserProfile {
        val id = prefs.getString("current", "owner")
        return profiles.firstOrNull { it.id == id } ?: profiles.first()
    }

    fun select(id: String): UserProfile {
        val p = profiles.firstOrNull { it.id == id } ?: current()
        prefs.edit().putString("current", p.id).apply()
        syncRuntime()
        return p
    }

    fun add(nameRaw: String): UserProfile {
        val name = nameRaw.trim().ifBlank { "사용자 ${profiles.size + 1}" }
            .replace("|", " ").replace(";", " ")
        val existing = profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return select(existing.id)
        val p = UserProfile(UUID.randomUUID().toString().take(10), name, false)
        profiles += p
        persist()
        return select(p.id)
    }

    fun rename(id: String, nameRaw: String): UserProfile {
        val old = profiles.firstOrNull { it.id == id } ?: return current()
        val name = nameRaw.trim().ifBlank { old.name }.replace("|", " ").replace(";", " ")
        val updated = old.copy(name = name)
        profiles[profiles.indexOf(old)] = updated
        persist()
        syncRuntime()
        return updated
    }

    fun delete(id: String) {
        val target = profiles.firstOrNull { it.id == id } ?: return
        if (target.id == "owner" || profiles.size <= 1) return
        profiles.remove(target)
        if (prefs.getString("current", "owner") == id) {
            prefs.edit().putString("current", "owner").apply()
        }
        persist()
        syncRuntime()
    }

    fun exportJson(): JSONObject = JSONObject().apply {
        put("current", current().id)
        put("profiles", JSONArray().apply {
            profiles.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("guest", p.guest)
                })
            }
        })
    }

    fun importJson(obj: JSONObject) {
        val arr = obj.optJSONArray("profiles") ?: return
        val restored = ArrayList<UserProfile>()
        for (i in 0 until arr.length()) {
            val j = arr.optJSONObject(i) ?: continue
            val id = j.optString("id").ifBlank { UUID.randomUUID().toString().take(10) }
            val name = j.optString("name").ifBlank { "사용자" }
            restored += UserProfile(id, name, j.optBoolean("guest", false))
        }
        if (restored.none { it.id == "owner" }) restored.add(0, UserProfile("owner", "나", false))
        if (restored.isNotEmpty()) {
            profiles = restored.distinctBy { it.id }.toMutableList()
            val requested = obj.optString("current", "owner")
            val current = profiles.firstOrNull { it.id == requested }?.id ?: "owner"
            prefs.edit().putString("current", current).apply()
            persist()
            syncRuntime()
        }
    }

    private fun persist() {
        val payload = profiles.joinToString(";") { "${it.id}|${it.name}|${it.guest}" }
        prefs.edit().putString("profiles", payload).apply()
    }

    private fun load(): List<UserProfile> {
        val payload = prefs.getString("profiles", null) ?: return emptyList()
        return payload.split(';').mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 3) return@mapNotNull null
            UserProfile(p[0], p[1], p[2].toBoolean())
        }
    }

    private fun syncRuntime() {
        val p = current()
        ProductSessionRuntime.userProfileId = p.id
        ProductSessionRuntime.userProfileName = p.name
    }
}

fun showUserProfileManager(
    context: Context,
    store: UserProfileStore,
    allowChange: Boolean,
    onChanged: (UserProfile) -> Unit
) {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(16), context.pvDp(8), context.pvDp(16), context.pvDp(4))
    }
    root.addView(TextView(context).apply {
        text = if (allowChange) "사용자별 통계·퍼팅 기록을 따로 저장합니다." else "세션 중에는 사용자를 바꿀 수 없습니다."
        setTextColor(Pv.textMid)
        textSize = context.pvSp(9f)
    })
    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context).apply { addView(list) }
    root.addView(scroll, LinearLayout.LayoutParams(-1, context.pvDp(170)).apply { topMargin = context.pvDp(8) })

    val name = EditText(context).apply {
        hint = "새 사용자 이름"
        setTextColor(Pv.textHi)
        setHintTextColor(Pv.textLo)
        backgroundTintList = ColorStateList.valueOf(Pv.primary)
        setSingleLine(true)
        isEnabled = allowChange
    }
    root.addView(name, LinearLayout.LayoutParams(-1, context.pvDp(44)))

    lateinit var dialog: AlertDialog
    fun rebuild() {
        list.removeAllViews()
        val current = store.current()
        store.all().forEach { profile ->
            list.addView(Button(context).apply {
                isAllCaps = false
                text = "${if (profile.id == current.id) "●" else "○"}  ${profile.name}${if (profile.guest) "  · GUEST" else ""}"
                setTextColor(if (profile.id == current.id) Pv.primary else Pv.textHi)
                textSize = context.pvSp(9f)
                gravity = Gravity.CENTER_VERTICAL
                background = context.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
                isEnabled = allowChange
                setOnClickListener {
                    val p = store.select(profile.id)
                    onChanged(p)
                    rebuild()
                }
                setOnLongClickListener {
                    if (allowChange && profile.id != "owner") {
                        store.delete(profile.id)
                        onChanged(store.current())
                        rebuild()
                    }
                    true
                }
            }, LinearLayout.LayoutParams(-1, context.pvDp(42)).apply { bottomMargin = context.pvDp(5) })
        }
    }

    dialog = AlertDialog.Builder(context)
        .setTitle("사용자 프로필")
        .setView(root)
        .setNegativeButton("닫기", null)
        .setPositiveButton("사용자 추가", null)
        .create()
    dialog.setOnShowListener {
        rebuild()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = allowChange
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!allowChange) return@setOnClickListener
            val p = store.add(name.text.toString())
            name.setText("")
            onChanged(p)
            rebuild()
        }
    }
    dialog.show()
}

data class TvCalibration(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float
)

class TvCalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_tv_cal_v1", Context.MODE_PRIVATE)

    init { syncRuntime(current()) }

    fun current(): TvCalibration = TvCalibration(
        prefs.getFloat("sx", 1f).coerceIn(.88f, 1f),
        prefs.getFloat("sy", 1f).coerceIn(.88f, 1f),
        prefs.getFloat("ox", 0f).coerceIn(-.06f, .06f),
        prefs.getFloat("oy", 0f).coerceIn(-.06f, .06f)
    )

    fun set(cal: TvCalibration) {
        val c = TvCalibration(
            cal.scaleX.coerceIn(.88f, 1f),
            cal.scaleY.coerceIn(.88f, 1f),
            cal.offsetX.coerceIn(-.06f, .06f),
            cal.offsetY.coerceIn(-.06f, .06f)
        )
        prefs.edit().putFloat("sx", c.scaleX).putFloat("sy", c.scaleY)
            .putFloat("ox", c.offsetX).putFloat("oy", c.offsetY).apply()
        syncRuntime(c)
    }

    fun reset() = set(TvCalibration(1f, 1f, 0f, 0f))

    private fun syncRuntime(c: TvCalibration) {
        ProductSessionRuntime.tvScaleX = c.scaleX
        ProductSessionRuntime.tvScaleY = c.scaleY
        ProductSessionRuntime.tvOffsetX = c.offsetX
        ProductSessionRuntime.tvOffsetY = c.offsetY
    }
}

fun showTvCalibrationDialog(context: Context, store: TvCalibrationStore) {
    ProductSessionRuntime.tvCalibrationGuide = true
    var cal = store.current()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(16), context.pvDp(8), context.pvDp(16), context.pvDp(4))
    }
    val status = TextView(context).apply {
        setTextColor(Pv.textHi)
        textSize = context.pvSp(9f)
    }
    root.addView(TextView(context).apply {
        text = "TV의 초록 모서리가 화면 안쪽에 정확히 보이도록 맞추세요. 변경값은 즉시 TV에 반영됩니다."
        setTextColor(Pv.textMid)
        textSize = context.pvSp(8.5f)
    })
    root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.pvDp(8) })

    fun refreshStatus() {
        status.text = "가로 ${"%.1f".format(cal.scaleX * 100)}%  ·  세로 ${"%.1f".format(cal.scaleY * 100)}%  ·  X ${"%+.1f".format(cal.offsetX * 100)}%  ·  Y ${"%+.1f".format(cal.offsetY * 100)}%"
    }

    fun slider(label: String, max: Int, progress: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(Pv.textHi)
            textSize = context.pvSp(8f)
        })
        row.addView(SeekBar(context).apply {
            this.max = max
            this.progress = progress
            progressTintList = ColorStateList.valueOf(Pv.primary)
            thumbTintList = ColorStateList.valueOf(Pv.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onChange(progress) }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.pvDp(7) })
    }

    slider("가로 안전영역", 120, ((cal.scaleX - .88f) * 1000).toInt()) {
        cal = cal.copy(scaleX = .88f + it / 1000f); store.set(cal); refreshStatus()
    }
    slider("세로 안전영역", 120, ((cal.scaleY - .88f) * 1000).toInt()) {
        cal = cal.copy(scaleY = .88f + it / 1000f); store.set(cal); refreshStatus()
    }
    slider("좌우 위치", 120, ((cal.offsetX + .06f) * 1000).toInt()) {
        cal = cal.copy(offsetX = -.06f + it / 1000f); store.set(cal); refreshStatus()
    }
    slider("상하 위치", 120, ((cal.offsetY + .06f) * 1000).toInt()) {
        cal = cal.copy(offsetY = -.06f + it / 1000f); store.set(cal); refreshStatus()
    }
    refreshStatus()

    val dialog = AlertDialog.Builder(context)
        .setTitle("TV 화면 보정")
        .setView(root)
        .setNeutralButton("초기화") { _, _ -> store.reset() }
        .setPositiveButton("완료", null)
        .create()
    dialog.setOnDismissListener { ProductSessionRuntime.tvCalibrationGuide = false }
    dialog.show()
}

data class DeviceCapabilityReport(
    val grade: String,
    val maxHfrFps: Int,
    val bestHfrSize: String,
    val hardwareLevel: String,
    val memoryMb: Int,
    val model: String,
    val recommendation: String
)

object DeviceDiagnostics {
    fun inspect(context: Context): DeviceCapabilityReport {
        var maxFps = 0
        var bestSize = "--"
        var hw = "UNKNOWN"
        runCatching {
            val manager = context.getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull { cameraId ->
                manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.first()
            val chars = manager.getCameraCharacteristics(id)
            hw = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "UNKNOWN"
            }
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            map?.highSpeedVideoSizes?.forEach { size ->
                val ranges = map.getHighSpeedVideoFpsRangesFor(size)
                val fps = ranges.maxOfOrNull { it.upper } ?: 0
                if (fps > maxFps || (fps == maxFps && size.width * size.height > bestSizeArea(bestSize))) {
                    maxFps = fps
                    bestSize = "${size.width}×${size.height}"
                }
            }
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val memory = am.memoryClass
        val grade = when {
            maxFps >= 240 && memory >= 256 && hw in setOf("FULL", "LEVEL_3") -> "A+"
            maxFps >= 240 -> "A"
            maxFps >= 120 -> "A-"
            maxFps >= 60 -> "B+"
            else -> "B"
        }
        val rec = when {
            maxFps >= 240 -> "PRECISION 240fps 권장 · 최고 정확도 프로필"
            maxFps >= 120 -> "PRECISION 120fps 권장 · 충분한 실전 측정"
            else -> "NORMAL 추적 사용 · 240fps 지원 기기에서 정밀도가 더 높습니다"
        }
        ProductSessionRuntime.deviceGrade = grade
        return DeviceCapabilityReport(grade, maxFps, bestSize, hw, memory, "${Build.MANUFACTURER} ${Build.MODEL}", rec)
    }

    private fun bestSizeArea(text: String): Int {
        val p = text.split('×')
        return if (p.size == 2) (p[0].toIntOrNull() ?: 0) * (p[1].toIntOrNull() ?: 0) else 0
    }
}

fun showDeviceDiagnostics(context: Context, report: DeviceCapabilityReport, calibrationScore: Int) {
    val message = buildString {
        append("기기 등급  ${report.grade}\n\n")
        append("모델  ${report.model}\n")
        append("고속카메라  ${if (report.maxHfrFps > 0) "최대 ${report.maxHfrFps}fps · ${report.bestHfrSize}" else "전용 HFR 미검출"}\n")
        append("Camera2  ${report.hardwareLevel}\n")
        append("앱 메모리 클래스  ${report.memoryMb}MB\n")
        if (calibrationScore > 0) append("현재 캘리브레이션  ${calibrationScore}점\n")
        append("\n${report.recommendation}")
    }
    AlertDialog.Builder(context)
        .setTitle("PHONE DIAGNOSTIC · ${report.grade}")
        .setMessage(message)
        .setPositiveButton("확인", null)
        .show()
}

data class ValidationSample(
    val id: String,
    val timestampMs: Long,
    val measuredBall: Double,
    val measuredLaunch: Double,
    val measuredHead: Double?,
    val measuredFace: Double?,
    val measuredPath: Double?,
    val confidence: Double?,
    val refBall: Double? = null,
    val refLaunch: Double? = null,
    val refHead: Double? = null,
    val refFace: Double? = null,
    val refPath: Double? = null
)

class AccuracyValidationLab(private val context: Context) {
    private val prefs = context.getSharedPreferences("puttvision_validation_v1", Context.MODE_PRIVATE)
    private var samples = load().toMutableList()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    fun capture(metrics: ShotMetrics) {
        if (!enabled) return
        samples += ValidationSample(
            id = UUID.randomUUID().toString().take(10),
            timestampMs = System.currentTimeMillis(),
            measuredBall = metrics.ballSpeedMps,
            measuredLaunch = metrics.launchAngleDeg,
            measuredHead = metrics.headSpeedMps,
            measuredFace = metrics.faceAngleDeg,
            measuredPath = metrics.pathAngleDeg,
            confidence = metrics.confidence
        )
        while (samples.size > 300) samples.removeAt(0)
        persist()
    }

    fun latest(): ValidationSample? = samples.lastOrNull()
    fun all(): List<ValidationSample> = samples.toList()
    fun matched(): List<ValidationSample> = samples.filter { it.refBall != null || it.refLaunch != null || it.refHead != null || it.refFace != null || it.refPath != null }

    fun setReference(id: String, ball: Double?, launch: Double?, head: Double?, face: Double?, path: Double?) {
        val old = samples.firstOrNull { it.id == id } ?: return
        val updated = old.copy(refBall = ball, refLaunch = launch, refHead = head, refFace = face, refPath = path)
        samples[samples.indexOf(old)] = updated
        persist()
    }

    fun clear() {
        samples.clear()
        persist()
    }

    fun summaryText(): String {
        val m = matched()
        if (m.isEmpty()) return "기준 센서와 매칭된 샷이 없습니다."
        fun mean(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.average()
        val ballPct = mean(m.mapNotNull { s -> s.refBall?.takeIf { abs(it) > .01 }?.let { abs(s.measuredBall - it) / abs(it) * 100.0 } })
        val launch = mean(m.mapNotNull { s -> s.refLaunch?.let { abs(s.measuredLaunch - it) } })
        val headPct = mean(m.mapNotNull { s -> val a = s.measuredHead; val b = s.refHead; if (a != null && b != null && abs(b) > .01) abs(a - b) / abs(b) * 100.0 else null })
        val face = mean(m.mapNotNull { s -> val a = s.measuredFace; val b = s.refFace; if (a != null && b != null) abs(a - b) else null })
        val path = mean(m.mapNotNull { s -> val a = s.measuredPath; val b = s.refPath; if (a != null && b != null) abs(a - b) else null })
        return buildString {
            append("매칭 ${m.size}샷")
            ballPct?.let { append(" · BALL ${"%.1f".format(it)}%") }
            launch?.let { append(" · START ${"%.2f".format(it)}°") }
            headPct?.let { append(" · HEAD ${"%.1f".format(it)}%") }
            face?.let { append(" · FACE ${"%.2f".format(it)}°") }
            path?.let { append(" · PATH ${"%.2f".format(it)}°") }
        }
    }

    fun exportCsv(activity: Activity) {
        val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "puttvision-validation-${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("timestamp,measured_ball,ref_ball,ball_error_pct,measured_launch,ref_launch,launch_error_deg,measured_head,ref_head,head_error_pct,measured_face,ref_face,face_error_deg,measured_path,ref_path,path_error_deg,confidence")
            samples.forEach { s ->
                fun pct(a: Double?, b: Double?): String = if (a != null && b != null && abs(b) > .01) "%.3f".format(abs(a - b) / abs(b) * 100.0) else ""
                fun deg(a: Double?, b: Double?): String = if (a != null && b != null) "%.3f".format(abs(a - b)) else ""
                out.appendLine(listOf(
                    s.timestampMs,
                    s.measuredBall, s.refBall ?: "", pct(s.measuredBall, s.refBall),
                    s.measuredLaunch, s.refLaunch ?: "", deg(s.measuredLaunch, s.refLaunch),
                    s.measuredHead ?: "", s.refHead ?: "", pct(s.measuredHead, s.refHead),
                    s.measuredFace ?: "", s.refFace ?: "", deg(s.measuredFace, s.refFace),
                    s.measuredPath ?: "", s.refPath ?: "", deg(s.measuredPath, s.refPath),
                    s.confidence ?: ""
                ).joinToString(","))
            }
        }
        shareFile(activity, file, "text/csv", "PuttVision 정확도 검증 CSV")
    }

    private fun persist() {
        val arr = JSONArray()
        samples.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("t", s.timestampMs)
                put("mb", s.measuredBall); put("ml", s.measuredLaunch)
                putNullable("mh", s.measuredHead); putNullable("mf", s.measuredFace); putNullable("mp", s.measuredPath); putNullable("c", s.confidence)
                putNullable("rb", s.refBall); putNullable("rl", s.refLaunch); putNullable("rh", s.refHead); putNullable("rf", s.refFace); putNullable("rp", s.refPath)
            })
        }
        prefs.edit().putString("samples", arr.toString()).apply()
    }

    private fun load(): List<ValidationSample> {
        val raw = prefs.getString("samples", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    add(ValidationSample(
                        id = j.optString("id").ifBlank { UUID.randomUUID().toString().take(10) },
                        timestampMs = j.optLong("t"),
                        measuredBall = j.optDouble("mb"), measuredLaunch = j.optDouble("ml"),
                        measuredHead = j.optNullableDouble("mh"), measuredFace = j.optNullableDouble("mf"), measuredPath = j.optNullableDouble("mp"), confidence = j.optNullableDouble("c"),
                        refBall = j.optNullableDouble("rb"), refLaunch = j.optNullableDouble("rl"), refHead = j.optNullableDouble("rh"), refFace = j.optNullableDouble("rf"), refPath = j.optNullableDouble("rp")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }
}

fun showAccuracyValidationLab(activity: Activity, lab: AccuracyValidationLab) {
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.pvDp(16), activity.pvDp(8), activity.pvDp(16), activity.pvDp(4))
    }
    val state = TextView(activity).apply { setTextColor(Pv.textHi); textSize = activity.pvSp(9f) }
    val summary = TextView(activity).apply { setTextColor(Pv.textMid); textSize = activity.pvSp(8f) }
    root.addView(state)
    root.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(5) })

    fun refresh() {
        state.text = "검증모드 ${if (lab.enabled) "ON" else "OFF"}  ·  측정 ${lab.all().size}샷  ·  기준값 ${lab.matched().size}샷"
        summary.text = lab.summaryText()
    }
    refresh()

    val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    lateinit var dialog: AlertDialog
    actions.addView(activity.pvButton(if (lab.enabled) "검증 끄기" else "검증 켜기", PvButtonStyle.SECONDARY) {
        lab.enabled = !lab.enabled
        refresh()
        dialog.dismiss()
        showAccuracyValidationLab(activity, lab)
    }, LinearLayout.LayoutParams(0, activity.pvDp(42), 1f))
    actions.addView(activity.pvButton("최근 샷 기준값", PvButtonStyle.PRIMARY) {
        val latest = lab.latest()
        if (latest == null) {
            Toast.makeText(activity, "먼저 검증모드를 켜고 한 샷 측정하세요", Toast.LENGTH_SHORT).show()
        } else {
            showValidationReferenceDialog(activity, lab, latest) { dialog.dismiss(); showAccuracyValidationLab(activity, lab) }
        }
    }, LinearLayout.LayoutParams(0, activity.pvDp(42), 1f).apply { marginStart = activity.pvDp(6) })
    root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(10) })

    val secondary = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    secondary.addView(activity.pvButton("CSV 내보내기", PvButtonStyle.GHOST) { lab.exportCsv(activity) }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f))
    secondary.addView(activity.pvButton("검증 기록 초기화", PvButtonStyle.GHOST) { lab.clear(); refresh() }, LinearLayout.LayoutParams(0, activity.pvDp(38), 1f).apply { marginStart = activity.pvDp(6) })
    root.addView(secondary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.pvDp(6) })

    dialog = AlertDialog.Builder(activity).setTitle("ACCURACY LAB").setView(root).setNegativeButton("닫기", null).create()
    dialog.show()
}

private fun showValidationReferenceDialog(
    activity: Activity,
    lab: AccuracyValidationLab,
    sample: ValidationSample,
    onSaved: () -> Unit
) {
    val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(activity.pvDp(16), activity.pvDp(6), activity.pvDp(16), 0) }
    fun field(label: String, measured: Double?, current: Double?): EditText {
        return EditText(activity).apply {
            hint = "$label · PV ${measured?.let { "%.3f".format(it) } ?: "--"}"
            current?.let { setText("%.3f".format(it)) }
            setTextColor(Pv.textHi); setHintTextColor(Pv.textLo)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            backgroundTintList = ColorStateList.valueOf(Pv.primary)
            setSingleLine(true)
        }
    }
    val ball = field("기준 BALL m/s", sample.measuredBall, sample.refBall)
    val launch = field("기준 START °", sample.measuredLaunch, sample.refLaunch)
    val head = field("기준 HEAD m/s", sample.measuredHead, sample.refHead)
    val face = field("기준 FACE °", sample.measuredFace, sample.refFace)
    val path = field("기준 PATH °", sample.measuredPath, sample.refPath)
    listOf(ball, launch, head, face, path).forEach { root.addView(it, LinearLayout.LayoutParams(-1, activity.pvDp(44))) }

    val dialog = AlertDialog.Builder(activity).setTitle("기준 센서 값 입력").setView(root).setNegativeButton("취소", null).setPositiveButton("저장", null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            lab.setReference(sample.id, ball.text.toString().toDoubleOrNull(), launch.text.toString().toDoubleOrNull(), head.text.toString().toDoubleOrNull(), face.text.toString().toDoubleOrNull(), path.text.toString().toDoubleOrNull())
            dialog.dismiss(); onSaved()
        }
    }
    dialog.show()
}

class ProductBackupManager(
    private val activity: Activity,
    private val stats: StatsRepository,
    private val users: UserProfileStore,
    private val putters: PutterProfileStore,
    private val tv: TvCalibrationStore
) {
    fun exportBackup() {
        val root = JSONObject().apply {
            put("schema", 2)
            put("createdAt", System.currentTimeMillis())
            put("users", users.exportJson())
            put("shots", stats.exportJson())
            put("putters", JSONArray().apply {
                putters.all().forEach { p -> put(JSONObject().apply { put("name", p.name); put("width", p.headWidthCm); put("current", p.id == putters.current().id) }) }
            })
            put("tv", JSONObject().apply {
                val c = tv.current(); put("sx", c.scaleX); put("sy", c.scaleY); put("ox", c.offsetX); put("oy", c.offsetY)
            })
            val matPrefs = activity.getSharedPreferences("puttvision_mat_cal_v1", Context.MODE_PRIVATE)
            put("matSamples", matPrefs.getString("samples", ""))
            val voicePrefs = activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE)
            put("voiceEnabled", voicePrefs.getBoolean("enabled", true))
        }
        val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "PuttVision-backup-${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2))
        shareFile(activity, file, "application/json", "PuttVision 백업")
    }

    fun importBackup(uri: Uri): String {
        val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("백업 파일을 열 수 없습니다")
        val root = JSONObject(text)
        require(root.optInt("schema", 0) in 1..2) { "지원하지 않는 백업 형식입니다" }
        root.optJSONObject("users")?.let { users.importJson(it) }
        root.optJSONArray("shots")?.let { stats.importJson(it, replace = true) }
        root.optJSONArray("putters")?.let { arr ->
            val existing = putters.all().toList()
            existing.drop(1).forEach { putters.delete(it.id) }
            var selectedName: String? = null
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val p = putters.save(j.optString("name", "퍼터"), j.optDouble("width", 11.5))
                if (j.optBoolean("current", false)) selectedName = p.name
            }
            selectedName?.let { name -> putters.all().firstOrNull { it.name == name }?.let { putters.select(it.id) } }
        }
        root.optJSONObject("tv")?.let { j ->
            tv.set(TvCalibration(j.optDouble("sx", 1.0).toFloat(), j.optDouble("sy", 1.0).toFloat(), j.optDouble("ox", 0.0).toFloat(), j.optDouble("oy", 0.0).toFloat()))
        }
        activity.getSharedPreferences("puttvision_mat_cal_v1", Context.MODE_PRIVATE).edit()
            .putString("samples", root.optString("matSamples", "")).apply()
        activity.getSharedPreferences("puttvision_voice_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", root.optBoolean("voiceEnabled", true)).apply()
        return "백업 복원 완료 · ${stats.allProfiles().size}샷"
    }
}

fun showBackupDialog(activity: Activity, manager: ProductBackupManager, onImportRequested: () -> Unit) {
    AlertDialog.Builder(activity)
        .setTitle("백업 · 기기 이전")
        .setMessage("샷 기록, 사용자, 퍼터, 매트/TV 설정을 한 파일로 내보내거나 복원합니다.")
        .setNegativeButton("닫기", null)
        .setNeutralButton("백업 복원") { _, _ -> onImportRequested() }
        .setPositiveButton("백업 내보내기") { _, _ -> manager.exportBackup() }
        .show()
}

private fun shareFile(activity: Activity, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, title))
}

private fun JSONObject.putNullable(key: String, value: Double?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).takeIf { it.isFinite() }
}
