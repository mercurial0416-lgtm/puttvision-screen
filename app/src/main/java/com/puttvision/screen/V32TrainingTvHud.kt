package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

/** Compact TV status for the executable 15-minute training session. */
class V32TrainingTvHud(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setWillNotDraw(false) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = V31TrainingSessionRuntime.progress()
        if (!s.running && !s.finished) {
            postInvalidateDelayed(450L)
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val boxW = w * .235f
        val boxH = h * .090f
        val left = w * .018f
        val top = h * .105f
        p.style = Paint.Style.FILL
        p.color = Color.argb(188, 10, 15, 17)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), boxH * .18f, boxH * .18f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.LEFT
        p.textSize = max(8f, w * .0054f)
        p.color = Color.rgb(255, 211, 72)
        c.drawText(if (s.running) "15분 AI 훈련" else "15분 AI 훈련 완료", left + boxW * .06f, top + boxH * .30f, p)

        p.textSize = max(7.5f, w * .0047f)
        p.color = Color.WHITE
        val detail = if (s.running) {
            "${s.blockIndex + 1}/${s.blockCount} · ${s.blockTitle} · ${s.shotInBlock}/${s.shotsInBlock}구 · %.2f m".format(s.targetDistanceM)
        } else {
            s.summary
        }
        c.drawText(detail, left + boxW * .06f, top + boxH * .60f, p)

        p.textSize = max(7f, w * .0043f)
        p.color = Color.argb(205, 210, 224, 220)
        c.drawText("성공 ${s.totalSuccesses}/${s.totalShots} · 연속 ${s.streak}", left + boxW * .06f, top + boxH * .84f, p)
        postInvalidateDelayed(if (s.running) 180L else 500L)
    }
}
