package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun showV16PutterFit2Dialog(context: Context, records: List<ShotRecord>) {
    val snapshot = V16PutterFit2.analyze(records)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(8))
    }

    fun t(value: String, size: Float, strong: Boolean = false, accent: Boolean = false) = TextView(context).apply {
        text = value
        textSize = context.pvSp(size)
        setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
        if (strong) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    root.addView(t("PUTTER FIT 2.0", 7f, true, true))
    root.addView(t(snapshot.verdict, 12f, true).apply { setPadding(0, context.pvDp(7), 0, context.pvDp(8)) })

    snapshot.currentRecommendation?.let { r ->
        root.addView(t("추천 타입 · ${r.balance.label} / ${r.head.label}", 9f, true, true))
        root.addView(t(r.reason, 7.5f).apply { setPadding(0, context.pvDp(3), 0, context.pvDp(8)) })
    }

    snapshot.ranking.take(4).forEachIndexed { index, perf ->
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.pvRounded(Pv.surfaceHi, Pv.rMd, Pv.line)
            setPadding(context.pvDp(10), context.pvDp(7), context.pvDp(10), context.pvDp(7))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(t("${index + 1}. ${perf.putterName}", 9f, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(t("${perf.fitScore}", 17f, true, true))
            })
            addView(t("${perf.shots}구 · 강점 ${perf.strengths.joinToString(", ")} · 약점 ${perf.weaknesses.joinToString(", ")}", 7.2f).apply {
                setPadding(0, context.pvDp(3), 0, 0)
            })
            val detail = buildString {
                perf.faceStdDeg?.let { append("FACE σ${"%.2f".format(it)}°  ") }
                perf.impactStdMm?.let { append("IMP σ${"%.1f".format(it)}mm  ") }
                append("START σ${"%.2f".format(perf.launchStdDeg)}°  SPEED ${"%.1f".format(perf.speedCvPct)}%")
            }
            addView(t(detail, 6.8f))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.pvDp(5) })
    }

    if (snapshot.ranking.isEmpty()) {
        root.addView(t("같은 퍼터로 최소 8구부터 성능점수, 20구부터 헤드/밸런스 추천이 활성화됩니다.", 8.5f))
    }

    AlertDialog.Builder(context)
        .setTitle("퍼터 피팅 2.0")
        .setView(root)
        .setPositiveButton("확인", null)
        .show()
}
