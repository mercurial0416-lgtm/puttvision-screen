package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun showV49SessionInsightsDialog(context: Context) {
    val s = V49SessionInsightsRuntime.snapshot
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(6))
    }
    fun add(text: String, strong: Boolean = false, accent: Boolean = false) {
        root.addView(TextView(context).apply {
            this.text = text
            textSize = context.pvSp(if (strong) 9.5f else 8f)
            setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
            if (strong) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, context.pvDp(3), 0, context.pvDp(3))
        })
    }
    add("LIVE SESSION INSIGHTS · ${s.sampleCount}구", true, true)
    add(s.momentumLabel, true)
    add(s.directionLabel)
    add(s.paceLabel)
    add(s.confidenceLabel)
    add(s.fadeLabel)
    add(s.personalBestLabel)
    add(s.streakLabel)
    add("추천 · ${s.quickPlan.title} · ${s.quickPlanReason}", true, true)

    AlertDialog.Builder(context)
        .setTitle("세션 인사이트")
        .setView(root)
        .setPositiveButton("추천 10분 훈련 시작") { _, _ ->
            val ok = V31TrainingSessionRuntime.start(s.quickPlan)
            Toast.makeText(context, if (ok) "세션 맞춤 퀵 훈련 시작" else "훈련을 시작할 수 없습니다", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("닫기", null)
        .show()
}
