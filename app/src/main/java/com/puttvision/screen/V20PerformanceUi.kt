package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

object V20ProductPreferences {
    private const val PREF = "puttvision_v20"
    private const val KEY_READ = "green_read_mode"

    fun install(context: Context) {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_READ, V20ReadMode.AUTO.name)
        V20GreenReadTrainingRuntime.mode = runCatching { V20ReadMode.valueOf(saved ?: V20ReadMode.AUTO.name) }
            .getOrDefault(V20ReadMode.AUTO)
    }

    fun setReadMode(context: Context, mode: V20ReadMode) {
        V20GreenReadTrainingRuntime.mode = mode
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_READ, mode.name).apply()
    }
}

fun showV20GreenReadModeDialog(context: Context) {
    val values = V20ReadMode.entries
    val selected = values.indexOf(V20GreenReadTrainingRuntime.mode).coerceAtLeast(0)
    AlertDialog.Builder(context)
        .setTitle("블라인드 그린 리드")
        .setSingleChoiceItems(values.map { it.label }.toTypedArray(), selected) { dialog, which ->
            V20ProductPreferences.setReadMode(context, values[which])
            dialog.dismiss()
        }
        .setNegativeButton("닫기", null)
        .show()
}

fun showV20PerformanceCompareDialog(context: Context) {
    val report = V20PerformanceRuntime.report
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(6), context.pvDp(18), context.pvDp(8))
    }
    fun text(value: String, size: Float, strong: Boolean = false, accent: Boolean = false) = TextView(context).apply {
        text = value
        setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
        textSize = context.pvSp(size)
        if (strong) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    root.addView(text("PERFORMANCE COMPARE", 7f, strong = true, accent = true))
    root.addView(text(report.headline, 14f, strong = true).apply { setPadding(0, context.pvDp(6), 0, context.pvDp(3)) })
    root.addView(text(report.detail, 8f).apply { setPadding(0, 0, 0, context.pvDp(10)) })

    report.trend?.let { t ->
        root.addView(text("BEFORE → NOW", 6.5f, strong = true, accent = true))
        root.addView(text(
            "${t.baselineShots}구 vs ${t.recentShots}구  ·  종합 ${if (t.scoreDelta >= 0) "+" else ""}${"%.1f".format(t.scoreDelta)}점  ·  출발분산 ${if (t.launchStdDeltaDeg >= 0) "+" else ""}${"%.2f".format(t.launchStdDeltaDeg)}°",
            8f, strong = true
        ).apply { setPadding(0, context.pvDp(4), 0, context.pvDp(10)) })
    }

    if (report.putters.isNotEmpty()) {
        root.addView(text("PUTTER RANKING", 6.5f, strong = true, accent = true))
        report.putters.take(5).forEachIndexed { index, row ->
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.pvDp(5), 0, context.pvDp(5))
                addView(text("${index + 1}. ${row.label}", 8.5f, strong = true), LinearLayout.LayoutParams(0, -2, .40f))
                addView(text("${"%.1f".format(row.score)}점 · ${row.shots}구", 8f), LinearLayout.LayoutParams(0, -2, .22f))
                addView(text("START σ ${"%.2f".format(row.launchStdDeg)}°", 7.5f), LinearLayout.LayoutParams(0, -2, .22f))
                addView(text("MAKE ${"%.0f".format(row.makePct)}%", 7.5f), LinearLayout.LayoutParams(0, -2, .16f))
            })
        }
    } else {
        root.addView(text("퍼터별 6구 이상 쌓이면 같은 조건에서 어느 퍼터가 실제로 더 잘 맞는지 랭킹이 열립니다.", 8f))
    }

    AlertDialog.Builder(context)
        .setTitle("성능 비교")
        .setView(root)
        .setPositiveButton("확인", null)
        .show()
}
