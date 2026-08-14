package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun showV49TrainingControlDialog(context: Context) {
    val p = V31TrainingSessionRuntime.progress()
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(10), context.pvDp(18), context.pvDp(6))
    }
    fun row(text: String, strong: Boolean = false, accent: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = context.pvSp(if (strong) 10f else 8f)
        setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
        if (strong) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, context.pvDp(3), 0, context.pvDp(3))
    }
    root.addView(row("15 MIN TRAINING CONTROL", true, true))
    root.addView(row(p.summary, true))
    if (p.running || p.finished) {
        root.addView(row("진행 ${p.completionPct}% · 현재 블록 성공 ${p.blockSuccessPct}% · ETA ${p.estimatedRemainingMinutes}분"))
        root.addView(row("${p.blockTitle} · ${p.shotInBlock}/${p.shotsInBlock}구 · 전체 성공 ${p.totalSuccesses}/${p.totalShots}"))
    }
    p.lastCompletedSummary?.let { root.addView(row("직전 완료 · $it")) }

    val builder = AlertDialog.Builder(context).setTitle("훈련 제어").setView(root)
    when {
        p.running && p.paused -> builder
            .setPositiveButton("재개") { _, _ -> toast(context, V31TrainingSessionRuntime.resume(), "훈련 재개", "재개할 훈련이 없습니다") }
            .setNeutralButton("블록 재시작") { _, _ -> toast(context, V31TrainingSessionRuntime.restartCurrentBlock(), "현재 블록 재시작", "재시작 실패") }
            .setNegativeButton("종료") { _, _ -> V31TrainingSessionRuntime.stop(true) }
        p.running -> builder
            .setPositiveButton("일시정지") { _, _ -> toast(context, V31TrainingSessionRuntime.pause(), "훈련 일시정지", "일시정지 실패") }
            .setNeutralButton("블록 건너뛰기") { _, _ -> toast(context, V31TrainingSessionRuntime.skipCurrentBlock(), "다음 블록으로 이동", "건너뛰기 실패") }
            .setNegativeButton("종료") { _, _ -> V31TrainingSessionRuntime.stop(true) }
        else -> builder
            .setPositiveButton("최약 블록 재훈련") { _, _ -> toast(context, V31TrainingSessionRuntime.retryWeakestBlock(), "약점 재훈련 시작", "재훈련할 완료 기록이 없습니다") }
            .setNegativeButton("닫기", null)
    }
    builder.show()
}

private fun toast(context: Context, ok: Boolean, yes: String, no: String) {
    Toast.makeText(context, if (ok) yes else no, Toast.LENGTH_SHORT).show()
}
