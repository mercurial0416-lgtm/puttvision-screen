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
        val refreshMs = V41TrainingTvPolicy.refreshDelayMs(s)
        if (!s.running && !s.finished) {
            postInvalidateDelayed(refreshMs)
            return
        }

        val layout = V41TrainingTvPolicy.layout(width, height)
        val scale = layout.scale
        val left = layout.left
        val top = layout.top
        val cardWidth = layout.width
        val cardHeight = layout.height
        card.set(left, top, left + cardWidth, top + cardHeight)

        p.style = Paint.Style.FILL
        p.color = Color.argb(220, 5, 9, 12)
        canvas.drawRoundRect(card, 16f * scale, 16f * scale, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, 1.25f * scale)
        p.color = Color.argb(120, 92, 255, 190)
        canvas.drawRoundRect(card, 16f * scale, 16f * scale, p)

        val x = left + 16f * scale
        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 13f * scale
        p.color = Color.rgb(92, 255, 190)
        canvas.drawText(if (s.finished) "15 MIN TRAINING · COMPLETE" else "15 MIN TRAINING", x, top + 26f * scale, p)

        p.textSize = 17f * scale
        p.color = Color.WHITE
        canvas.drawText(if (s.finished) s.summary else s.blockTitle, x, top + 52f * scale, p)

        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = 11f * scale
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
        canvas.drawText("$blockNo   $shotNo   STREAK ${s.streak}", x, top + 74f * scale, p)

        p.textSize = 10f * scale
        p.color = Color.rgb(255, 200, 80)
        val targetText = if (s.finished) "COMPLETE" else "TARGET ${"%.1f".format(s.targetDistanceM)}m"
        canvas.drawText("$targetText   SUCCESS ${s.totalSuccesses}/${s.totalShots}", x, top + 94f * scale, p)

        val barLeft = x
        val barTop = top + 106f * scale
        val barWidth = (cardWidth - 32f * scale).coerceAtLeast(20f * scale)
        val barHeight = 7f * scale
        p.color = Color.argb(120, 90, 100, 108)
        canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, 4f * scale, 4f * scale, p)
        val denominator = max(1, s.shotsInBlock)
        val fraction = if (s.finished) 1f else (s.shotInBlock.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
        p.color = Color.rgb(92, 255, 190)
        canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth * fraction, barTop + barHeight, 4f * scale, 4f * scale, p)

        postInvalidateDelayed(refreshMs)
    }
}
