package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

fun showV36OnlineMatchDialog(context: Context) {
    val handler = Handler(Looper.getMainLooper())
    val dialog = AlertDialog.Builder(context)
        .setTitle("ONLINE MATCH")
        .setMessage("서버 경기 상태 확인 중…")
        .setPositiveButton("기권", null)
        .setNegativeButton("닫기", null)
        .create()

    var requestRunning = false

    fun render(state: V36OnlineSnapshot?) {
        if (!dialog.isShowing || state == null) return
        val me = state.me()
        val opponent = state.opponent()
        val myDistance = me?.remainingM?.let { " · %.2f m".format(it) }.orEmpty()
        val opponentDistance = opponent?.remainingM?.let { " · %.2f m".format(it) }.orEmpty()
        val live = if (opponent?.online == true) "LIVE" else "RECONNECTING"
        val status = if (state.finished()) "FINISHED" else "LIVE MATCH"
        val result = if (state.finished()) {
            val delta = me?.ratingDelta
            val outcome = when {
                me?.forfeited == true -> "패배 · 기권"
                opponent?.forfeited == true -> "승리 · 상대 기권"
                delta != null && delta > 0 -> "승리"
                delta != null && delta < 0 -> "패배"
                else -> "무승부 / 종료"
            }
            val rating = if (me?.ratingBefore != null && me.ratingAfter != null) {
                val d = me.ratingDelta ?: 0
                "R${me.ratingBefore} → R${me.ratingAfter} (${if (d >= 0) "+" else ""}$d)"
            } else "서버 정산 완료"
            "\n\n$outcome\n$rating"
        } else ""

        dialog.setTitle("ONLINE · $status")
        dialog.setMessage(
            "나 · ${me?.shotNo ?: 0}/9$myDistance\n" +
                "${opponent?.name ?: "OPPONENT"} · ${opponent?.shotNo ?: 0}/9$opponentDistance · $live" +
                result
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = if (state.finished()) "정산 완료" else "기권"
            isEnabled = !state.finished()
        }
    }

    fun refresh() {
        if (!dialog.isShowing || requestRunning) return
        requestRunning = true
        V36OnlinePresenceRuntime.forceRefresh(context) { result ->
            requestRunning = false
            result.onSuccess(::render)
                .onFailure {
                    V36OnlinePresenceRuntime.snapshot()?.let(::render)
                    if (dialog.isShowing && V36OnlinePresenceRuntime.snapshot() == null) {
                        dialog.setMessage("경기 상태 동기화 실패 · 자동 재시도 중…")
                    }
                }
            if (dialog.isShowing) handler.postDelayed({ refresh() }, 1_500L)
        }
    }

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val state = V36OnlinePresenceRuntime.snapshot()
            if (state?.finished() == true) return@setOnClickListener
            AlertDialog.Builder(context)
                .setTitle("온라인 경기 기권")
                .setMessage("기권하면 서버에서 즉시 패배와 레이팅을 정산합니다.")
                .setPositiveButton("기권") { _, _ ->
                    V36OnlinePresenceRuntime.forfeit(context) { result ->
                        result.onSuccess(::render)
                            .onFailure { Toast.makeText(context, "기권 처리 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
        V36OnlinePresenceRuntime.snapshot()?.let(::render)
        refresh()
    }
    dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
    dialog.show()
}
