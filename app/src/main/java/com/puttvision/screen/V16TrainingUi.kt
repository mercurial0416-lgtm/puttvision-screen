package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun showV16PersonalCoachDialog(context: Context) {
    val snap = V16Runtime.personalCoach
    val adaptive = V46AdaptiveCoachRuntime.snapshot
    val plan = V46AdaptiveTrainingPlan.adapt(V16Runtime.trainingPlan, adaptive)
    val progress = V31TrainingSessionRuntime.progress()
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
    if (progress.running || progress.finished) {
        val status = if (progress.running) {
            "훈련 진행 · ${progress.summary} · ${progress.blockTitle} · ${"%.2f".format(progress.targetDistanceM)} m"
        } else {
            "훈련 완료 · ${progress.summary}"
        }
        root.addView(t(status, 8.5f, true, true).apply {
            setPadding(0, context.pvDp(7), 0, context.pvDp(5))
        })
    }

    if (adaptive != null) {
        val confidencePct = (adaptive.confidence * 100).toInt().coerceIn(0, 100)
        root.addView(t(adaptive.headline, 16f, true, true).apply {
            setPadding(0, context.pvDp(8), 0, context.pvDp(4))
        })
        root.addView(t(adaptive.detail, 8.3f))
        root.addView(t(
            "한 가지 큐 · ${adaptive.prescription.cue}",
            9.2f,
            true,
            true
        ).apply { setPadding(0, context.pvDp(7), 0, context.pvDp(3)) })
        root.addView(t(
            "신뢰 $confidencePct% · ${adaptive.trend.label} · ${adaptive.prescription.shots}구 · ${adaptive.prescription.successRule}",
            7.7f,
            true
        ).apply { setPadding(0, 0, 0, context.pvDp(7)) })
        if (adaptive.alternatives.isNotEmpty()) {
            val next = adaptive.alternatives.joinToString(" · ") { "${it.focus.label} ${it.score}" }
            root.addView(t("다음 후보 · $next", 7.1f))
        }
    } else if (snap == null) {
        root.addView(t("8구부터 개인 기준선을 만듭니다", 15f, true).apply { setPadding(0, context.pvDp(8), 0, context.pvDp(5)) })
        root.addView(t("거리·그린스피드·경사별 미스 패턴을 따로 분석해 단순 평균이 아닌 개인 고질 패턴을 찾습니다.", 8.5f))
    } else {
        root.addView(t(snap.primary.headline, 16f, true, true).apply { setPadding(0, context.pvDp(8), 0, context.pvDp(4)) })
        root.addView(t(snap.primary.detail, 8.5f))
    }

    if (snap != null) {
        root.addView(t("최근 ${snap.baseline.sampleCount}구 · 기존 개선지수 ${snap.improvementScore}/100", 7.6f, true).apply {
            setPadding(0, context.pvDp(7), 0, context.pvDp(6))
        })
        snap.topInsights
            .filter { adaptive == null || !it.headline.contains(adaptive.focus.label) }
            .take(2)
            .forEach { insight ->
                root.addView(t("• 보조 분석 · ${insight.headline} — ${insight.detail}", 7.2f).apply {
                    setPadding(0, context.pvDp(2), 0, context.pvDp(2))
                })
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
                addView(t("${block.title} · ${block.shots}구 · ${"%.2f".format(block.distanceM)} m", 8.8f, true))
                addView(t(block.successRule, 7.3f))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    val builder = AlertDialog.Builder(context)
        .setTitle("오늘의 코칭")
        .setView(root)
        .setNegativeButton("닫기", null)

    if (progress.running) {
        builder.setPositiveButton("훈련 중지") { _, _ ->
            V31TrainingSessionRuntime.stop(true)
            Toast.makeText(context, "15분 훈련 중지 · 기존 그린 설정 복구", Toast.LENGTH_SHORT).show()
        }
    } else {
        builder.setPositiveButton(if (progress.finished) "다시 시작" else "15분 훈련 시작") { _, _ ->
            val started = V31TrainingSessionRuntime.start(plan)
            Toast.makeText(
                context,
                if (started) "15분 자동훈련 시작 · 포커스 ${adaptive?.focus?.label ?: "기본 루틴"}" else "훈련 시작 실패",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    builder.show()
}
