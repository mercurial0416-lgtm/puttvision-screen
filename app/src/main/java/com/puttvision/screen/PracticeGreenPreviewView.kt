package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PracticeGreenPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var styleIndex: Int = 0

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.color = Color.rgb(103, 109, 132)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 22f, 22f, p)

        val inset = min(w, h) * .06f
        val r = RectF(inset, inset, w - inset, h - inset)
        val blob = buildBlob(styleIndex, r)

        canvas.save()
        canvas.clipPath(blob)
        drawBands(canvas, r, styleIndex)
        canvas.restore()

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.5f, min(w, h) * .012f)
        p.color = Color.argb(115, 10, 18, 28)
        canvas.drawPath(blob, p)
        p.style = Paint.Style.FILL
    }

    private fun buildBlob(style: Int, r: RectF): Path = Path().apply {
        when (style % 6) {
            0 -> addOval(RectF(r.left + r.width() * .08f, r.top + r.height() * .1f, r.right - r.width() * .08f, r.bottom - r.height() * .1f), Path.Direction.CW)
            1 -> {
                moveTo(r.left + r.width() * .18f, r.top + r.height() * .12f)
                cubicTo(r.left, r.top + r.height() * .34f, r.left + r.width() * .1f, r.bottom - r.height() * .07f, r.left + r.width() * .34f, r.bottom - r.height() * .1f)
                cubicTo(r.right - r.width() * .08f, r.bottom - r.height() * .14f, r.right - r.width() * .08f, r.top + r.height() * .18f, r.left + r.width() * .18f, r.top + r.height() * .12f)
                close()
            }
            2 -> {
                moveTo(r.left + r.width() * .14f, r.top + r.height() * .18f)
                cubicTo(r.left, r.top + r.height() * .4f, r.left + r.width() * .08f, r.bottom - r.height() * .05f, r.left + r.width() * .36f, r.bottom - r.height() * .03f)
                cubicTo(r.right - r.width() * .08f, r.bottom, r.right, r.top + r.height() * .4f, r.right - r.width() * .12f, r.top + r.height() * .12f)
                cubicTo(r.right - r.width() * .35f, r.top, r.left + r.width() * .3f, r.top + r.height() * .02f, r.left + r.width() * .14f, r.top + r.height() * .18f)
                close()
            }
            3 -> addOval(RectF(r.left + r.width() * .11f, r.top + r.height() * .08f, r.right - r.width() * .11f, r.bottom - r.height() * .08f), Path.Direction.CW)
            4 -> {
                moveTo(r.left + r.width() * .16f, r.top + r.height() * .12f)
                cubicTo(r.left, r.top + r.height() * .3f, r.left + r.width() * .04f, r.bottom - r.height() * .06f, r.left + r.width() * .3f, r.bottom - r.height() * .08f)
                cubicTo(r.right - r.width() * .08f, r.bottom - r.height() * .12f, r.right - r.width() * .06f, r.top + r.height() * .18f, r.left + r.width() * .46f, r.top + r.height() * .1f)
                close()
            }
            else -> {
                moveTo(r.left + r.width() * .2f, r.top + r.height() * .08f)
                cubicTo(r.left, r.top + r.height() * .3f, r.left + r.width() * .06f, r.bottom - r.height() * .08f, r.left + r.width() * .28f, r.bottom - r.height() * .04f)
                cubicTo(r.right - r.width() * .1f, r.bottom, r.right, r.top + r.height() * .36f, r.right - r.width() * .12f, r.top + r.height() * .1f)
                cubicTo(r.right - r.width() * .34f, r.top, r.left + r.width() * .34f, r.top + r.height() * .02f, r.left + r.width() * .2f, r.top + r.height() * .08f)
                close()
            }
        }
    }

    private fun drawBands(canvas: Canvas, r: RectF, style: Int) {
        val palettes = listOf(
            intArrayOf(Color.rgb(20, 246, 76), Color.rgb(31, 213, 239), Color.rgb(12, 29, 242), Color.rgb(31, 213, 239), Color.rgb(20, 246, 76)),
            intArrayOf(Color.rgb(12, 29, 242), Color.rgb(31, 213, 239), Color.rgb(20, 246, 76), Color.rgb(31, 213, 239), Color.rgb(12, 29, 242)),
            intArrayOf(Color.rgb(20, 246, 76), Color.rgb(31, 213, 239), Color.rgb(12, 29, 242), Color.rgb(31, 213, 239), Color.rgb(20, 246, 76)),
            intArrayOf(Color.rgb(232, 38, 35), Color.rgb(241, 220, 32), Color.rgb(20, 246, 76), Color.rgb(31, 213, 239), Color.rgb(12, 29, 242), Color.rgb(31, 213, 239), Color.rgb(20, 246, 76), Color.rgb(241, 220, 32), Color.rgb(232, 38, 35)),
            intArrayOf(Color.rgb(12, 29, 242), Color.rgb(31, 213, 239), Color.rgb(20, 246, 76), Color.rgb(241, 220, 32), Color.rgb(232, 38, 35)),
            intArrayOf(Color.rgb(232, 38, 35), Color.rgb(241, 220, 32), Color.rgb(20, 246, 76), Color.rgb(31, 213, 239), Color.rgb(12, 29, 242))
        )
        val colors = palettes[style % palettes.size]
        val w = r.width()
        val h = r.height()

        if (style % 6 in listOf(0, 1, 3, 4)) {
            val stripes = colors.size * 2
            for (i in 0 until stripes) {
                val x0 = r.left + w * i / stripes
                val x1 = r.left + w * (i + 1) / stripes
                p.color = colors[i % colors.size]
                canvas.drawRect(x0, r.top, x1, r.bottom, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, min(w, h) * .01f)
            p.color = Color.argb(70, 0, 0, 0)
            for (i in 0..8) {
                val x = r.left + w * i / 8f
                val path = Path().apply {
                    moveTo(x, r.top)
                    for (step in 1..24) {
                        val yy = r.top + h * step / 24f
                        val drift = sin(step / 24f * 4f + i * .7f + style) * w * .015f
                        lineTo(x + drift, yy)
                    }
                }
                canvas.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        } else {
            val cx = r.centerX() + w * .08f
            val cy = r.centerY() + h * .08f
            val maxRadius = min(w, h) * .55f
            for (i in colors.indices.reversed()) {
                val f = (i + 1) / colors.size.toFloat()
                p.color = colors[i]
                canvas.drawOval(RectF(cx - maxRadius * f, cy - maxRadius * f * .86f, cx + maxRadius * f, cy + maxRadius * f * .86f), p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(1f, min(w, h) * .01f)
            p.color = Color.argb(72, 0, 0, 0)
            for (i in 1..8) {
                val f = i / 8f
                canvas.drawOval(RectF(cx - maxRadius * f, cy - maxRadius * f * .86f, cx + maxRadius * f, cy + maxRadius * f * .86f), p)
            }
            p.style = Paint.Style.FILL
        }
    }
}
