package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun showV16PersonalCoachDialog(context: Context) {
    val snap = V16Runtime.personalCoach
    val plan = V16Runtime.trainingPlan
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

    root.addView(t("PERSONAL AI COACH", 7f, true, true))
    if (snap == null) {
        root.addView(t("8구부터 개인 기준선을 만듭니다", 15f, true).apply { setPadding(0, context.pvDp(8), 0, context.pvDp(5)) })
        root.addView(t("거리·그린스피드·경사별 미스 패턴을 따로 분석해 단순 평균이 아닌 개인 고질 패턴을 찾습니다.", 8.5f))
    } else {
        root.addView(t(snap.primary.headline, 16f, true, true).apply { setPadding(0, context.pvDp(8), 0, context.pvDp(4)) })
        root.addView(t(snap.primary.detail, 8.5f))
        root.addView(t("최근 ${snap.baseline.sampleCount}구 · 개선지수 ${snap.improvementScore}/100", 8f, true).apply {
            setPadding(0, context.pvDp(8), 0, context.pvDp(8))
        })
        snap.topInsights.drop(1).take(3).forEach { insight ->
            root.addView(t("• ${insight.headline} — ${insight.detail}", 7.6f).apply { setPadding(0, context.pvDp(3), 0, context.pvDp(3)) })
        }
    }

    root.addView(t(plan.title, 12f, true, true).apply { setPadding(0, context.pvDp(12), 0, context.pvDp(6)) })
    plan.blocks.forEachIndexed { i, block ->
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.pvDp(4), 0, context.pvDp(4))
            addView(t("${i + 1}", 11f, true, true), LinearLayout.LayoutParams(context.pvDp(28), -2))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(t("${block.title} · ${block.shots}구 · ${"%.1f".format(block.distanceM)}m", 8.8f, true))
                addView(t(block.successRule, 7.3f))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    AlertDialog.Builder(context)
        .setTitle("오늘의 코칭")
        .setView(root)
        .setPositiveButton("확인", null)
        .show()
}
