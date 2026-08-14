package com.puttvision.screen

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Improvement 27: session analysis becomes a compact visual dashboard instead of a text dump. */
fun showV49SessionInsightsDialog(context: Context) {
    val s = V49LiveSessionInsights.snapshot()
    val records = V49SessionWindow.current(V47SoloIntegrityRuntime.latestHistory?.records.orEmpty())
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, context.pvDp(10), 0, context.pvDp(4)) }

    val hero = context.v51ElevatedPanel(12).apply {
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "LIVE SESSION · ${s.sampleCount} SHOTS"
            setTextColor(Pv.textHi); textSize = context.pvSp(8.5f); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val momentumTone = when { (s.momentumDelta ?: .0) >= 6 -> V51Tone.GOOD; (s.momentumDelta ?: .0) <= -6 -> V51Tone.BAD; else -> V51Tone.NEUTRAL }
        header.addView(context.v51StatusPill(if (s.lateSessionFade) "FADE" else if ((s.momentumDelta ?: .0) > 2) "UP" else "STABLE", if (s.lateSessionFade) V51Tone.WARN else momentumTone))
        addView(header)
        addView(TextView(context).apply {
            text = s.headline
            setTextColor(Pv.textHi); textSize = context.pvSp(14f); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, context.pvDp(8), 0, context.pvDp(3))
        })
        addView(TextView(context).apply { text=s.momentumLabel;setTextColor(v51ToneColor(momentumTone));textSize=context.pvSp(7.5f);typeface=Typeface.DEFAULT_BOLD })
        addView(V51SparklineView(context).apply {
            values = records.map { it.strokeScore.total.toDouble() }
            tone = momentumTone
        }, LinearLayout.LayoutParams(-1, context.pvDp(62)).apply { topMargin=context.pvDp(6) })
    }
    root.addView(hero, LinearLayout.LayoutParams(-1,-2))

    fun pair(a: LinearLayout, b: LinearLayout) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=context.pvDp(5) })
        addView(b, LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=context.pvDp(5) })
    }
    val directionTone = if ((s.directionBiasDeg?.let { kotlin.math.abs(it) } ?: 0.0) >= .65) V51Tone.WARN else V51Tone.GOOD
    val paceTone = if ((s.paceBiasCm?.let { kotlin.math.abs(it) } ?: 0.0) >= 25.0) V51Tone.WARN else V51Tone.GOOD
    val confTone = when { s.confidenceDeltaPct == null -> V51Tone.NEUTRAL; s.confidenceDeltaPct <= -8 -> V51Tone.BAD; s.confidenceDeltaPct >= 6 -> V51Tone.GOOD; else -> V51Tone.INFO }
    val streakTone = if (s.consistencyStreak >= 5) V51Tone.GOOD else if (s.consistencyStreak >= 2) V51Tone.INFO else V51Tone.NEUTRAL

    root.addView(pair(
        context.v51MetricCard("START BIAS", s.directionBiasDeg?.let { "%+.2f°".format(it) } ?: "--", s.directionLabel, directionTone),
        context.v51MetricCard("PACE BIAS", s.paceBiasCm?.let { "%+.0fcm".format(it) } ?: "--", s.paceLabel, paceTone)
    ), LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.pvDp(10) })
    root.addView(pair(
        context.v51MetricCard("MEASURE Q", s.confidenceDeltaPct?.let { "%+.0f%%p".format(it) } ?: "--", s.confidenceLabel, confTone),
        context.v51MetricCard("QUALITY STREAK", "${s.consistencyStreak}", s.streakLabel, streakTone)
    ), LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.pvDp(10) })

    val bestScore = s.personalBestScore ?: 0
    root.addView(context.v51Meter("SESSION BEST", bestScore, V51VisualPolicy.toneForScore(bestScore), s.personalBestLabel), LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.pvDp(12) })

    val quick = context.v51ElevatedPanel(12).apply {
        addView(context.v51StatusPill("10 MIN QUICK FIX", V51Tone.GOOD))
        addView(TextView(context).apply {
            text=s.quickPlanReason;setTextColor(Pv.textHi);textSize=context.pvSp(12f);typeface=Typeface.DEFAULT_BOLD
            setPadding(0,context.pvDp(8),0,context.pvDp(3))
        })
        addView(TextView(context).apply {
            text="${s.quickPlan.blocks.size} blocks · ${s.quickPlan.estimatedMinutes} min · ${s.quickPlan.blocks.sumOf { it.shots }} shots"
            setTextColor(Pv.textMid);textSize=context.pvSp(7.5f)
        })
        if(s.lateSessionFade) addView(TextView(context).apply { text=s.fadeLabel;setTextColor(Pv.amber);textSize=context.pvSp(7.3f);typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(5),0,0) })
    }
    root.addView(quick, LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.pvDp(12) })

    context.pvDialog(
        title = "세션 인사이트",
        content = root,
        dismissLabel = "닫기",
        extraActions = listOf("10분 훈련 시작" to {
            val ok = V31TrainingSessionRuntime.start(s.quickPlan)
            Toast.makeText(context, if(ok) "세션 맞춤 퀵 훈련 시작" else "훈련을 시작할 수 없습니다", Toast.LENGTH_SHORT).show()
        })
    ).show()
}
