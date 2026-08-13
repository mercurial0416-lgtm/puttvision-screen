package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context

enum class V27CupPace(val label: String, val targetCupSpeedMps: Double) {
    DIE("죽는 페이스 · 0.25 m/s", 0.25),
    SOFT("부드럽게 · 0.40 m/s", 0.40),
    STANDARD("기준 · 0.55 m/s", 0.55),
    FIRM("강하게 · 0.70 m/s", 0.70),
    ATTACK("공격적 · 0.85 m/s", 0.85)
}

object V27CupPaceRuntime {
    private const val PREF = "puttvision_v27_cup_pace"
    @Volatile var pace: V27CupPace = V27CupPace.STANDARD
        private set
    @Volatile private var installed = false

    val targetCupSpeedMps: Double get() = pace.targetCupSpeedMps
    val cachePart: Int get() = (targetCupSpeedMps * 100.0).toInt()

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            pace = runCatching {
                V27CupPace.valueOf(p.getString("pace", V27CupPace.STANDARD.name)!!)
            }.getOrDefault(V27CupPace.STANDARD)
            installed = true
        }
    }

    fun set(context: Context, value: V27CupPace) {
        install(context)
        pace = value
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("pace", value.name).apply()
        GreenReadRuntime.clearRuntimeCache()
    }

    fun label(): String = pace.label
}

fun showV27CupPaceDialog(context: Context) {
    V27CupPaceRuntime.install(context)
    val values = V27CupPace.entries
    val selected = values.indexOf(V27CupPaceRuntime.pace).coerceAtLeast(0)
    AlertDialog.Builder(context)
        .setTitle("홀 통과 페이스")
        .setSingleChoiceItems(values.map { it.label }.toTypedArray(), selected) { dialog, which ->
            V27CupPaceRuntime.set(context, values[which])
            dialog.dismiss()
        }
        .setNegativeButton("닫기", null)
        .show()
}
