package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.max

class V31TrainingTvOverlay(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = V31TrainingSessionRuntime.progress()
        if (!s.running && !s.finished) {
            postInvalidateDelayed(500L)
            return
        }

        val d = resources.displayMetrics.density
        val left = 24f * d
        val top = 24f * d
        val width = 330f * d
        val height = 118f * d
        card.set(left, top, left + width, top + height)

        p.style = Paint.Style.FILL
        p.color = Color.argb(220, 5, 9, 12)
        canvas.drawRoundRect(card, 16f * d, 16f * d, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = Color.argb(120, 92, 255, 190)
        canvas.drawRoundRect(card, 16f * d, 16f * d, p)

        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 13f * d
        p.color = Color.rgb(92, 255, 190)
        canvas.drawText(if (s.finished) "15 MIN TRAINING · COMPLETE" else "15 MIN TRAINING", left + 16f * d, top + 25f * d, p)

        p.textSize = 17f * d
        p.color = Color.WHITE
        canvas.drawText(if (s.finished) s.summary else s.blockTitle, left + 16f * d, top + 50f * d, p)

        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = 11f * d
        p.color = Color.rgb(205, 214, 220)
        val blockNo = when {
            s.finished && s.blockCount > 0 -> "BLOCK ${s.blockCount}/${s.blockCount}"
            s.blockCount > 0 -> "BLOCK ${(s.blockIndex + 1).coerceAtMost(s.blockCount)}/${s.blockCount}"
            else -> "BLOCK --"
        }
        val shotNo = when {
            s.finished && s.shotsInBlock > 0 -> "SHOT ${s.shotsInBlock}/${s.shotsInBlock}"
            s.shotsInBlock > 0 -> "SHOT ${s.shotInBlock.coerceIn(0, s.shotsInBlock)}/${s.shotsInBlock}"
            else -> "SHOT --"
        }
        val streak = "STREAK ${s.streak}"
        canvas.drawText("$blockNo   $shotNo   $streak", left + 16f * d, top + 70f * d, p)

        p.textSize = 10f * d
        p.color = Color.rgb(255, 200, 80)
        val targetText = if (s.finished) "COMPLETE" else "TARGET ${"%.1f".format(s.targetDistanceM)}m"
        canvas.drawText("$targetText   SUCCESS ${s.totalSuccesses}/${s.totalShots}", left + 16f * d, top + 88f * d, p)

        val barLeft = left + 16f * d
        val barTop = top + 98f * d
        val barWidth = width - 32f * d
        val barHeight = 7f * d
        p.color = Color.argb(120, 90, 100, 108)
        canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, 4f * d, 4f * d, p)
        val denominator = max(1, s.shotsInBlock)
        val fraction = if (s.finished) 1f else (s.shotInBlock.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
        p.color = Color.rgb(92, 255, 190)
        canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth * fraction, barTop + barHeight, 4f * d, 4f * d, p)

        postInvalidateDelayed(250L)
    }
}
