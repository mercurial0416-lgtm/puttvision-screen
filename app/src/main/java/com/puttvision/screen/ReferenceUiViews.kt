package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.sin

class ReferenceImpactPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        p.color = Color.argb(224, 7, 10, 14)
        c.drawRoundRect(r, 14f * d, 14f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = Color.argb(170, 48, 61, 72)
        c.drawRoundRect(RectF(0.5f * d, 0.5f * d, width - 0.5f * d, height - 0.5f * d), 14f * d, 14f * d, p)
        p.style = Paint.Style.FILL

        p.color = Pv.textHi
        p.textSize = 8.5f * d
        p.typeface = Typeface.DEFAULT_BOLD
        c.drawText("임팩트 구간 미리보기", 10f * d, 16f * d, p)

        val cy = height * .50f
        p.color = Color.rgb(42, 52, 64)
        c.drawRoundRect(RectF(10f * d, cy - 7f * d, 54f * d, cy + 7f * d), 5f * d, 5f * d, p)
        p.color = Color.rgb(255, 145, 40)
        c.drawCircle(20f * d, cy, 3.8f * d, p)
        p.color = Pv.info
        c.drawCircle(44f * d, cy, 3.8f * d, p)
        p.color = Color.WHITE
        c.drawCircle(70f * d, cy, 5f * d, p)

        val graphL = 88f * d
        val graphR = width - 10f * d
        val graphTop = 27f * d
        val graphBottom = height - 22f * d
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f * d
        p.color = Color.argb(210, 78, 209, 121)
        val path = Path()
        var first = true
        var x = graphL
        while (x <= graphR) {
            val t = ((x - graphL) / max(1f, graphR - graphL))
            val spike = sin(t * 18f) * 0.12f + if (t in .42f..57f) sin((t - .42f) * 22f) * .46f else 0f
            val y = (graphTop + graphBottom) / 2f - spike * (graphBottom - graphTop)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += 2f * d
        }
        c.drawPath(path, p)
        p.strokeWidth = 1.1f * d
        p.color = Color.argb(220, 174, 111, 255)
        val impactX = graphL + (graphR - graphL) * .5f
        c.drawLine(impactX, graphTop, impactX, graphBottom, p)
        p.style = Paint.Style.FILL

        p.typeface = Typeface.DEFAULT
        p.textSize = 6.5f * d
        p.color = Pv.textLo
        c.drawText("-100ms", graphL, height - 7f * d, p)
        c.drawText("0", impactX - 2f * d, height - 7f * d, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("+100ms", graphR, height - 7f * d, p)
        p.textAlign = Paint.Align.LEFT
    }
}

class ReferenceHeroView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()

        p.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(11, 26, 31), Color.rgb(23, 77, 43), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val horizon = h * .32f
        p.shader = LinearGradient(0f, horizon, 0f, h, Color.rgb(55, 126, 61), Color.rgb(23, 69, 38), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .002f)
        p.color = Color.argb(62, 146, 238, 169)
        for (i in -5..5) {
            val bx = w * .5f + i * w * .11f
            val tx = w * .54f + i * w * .025f
            c.drawLine(bx, h, tx, horizon, p)
        }
        for (i in 1..8) {
            val t = i / 8f
            val y = h - (h - horizon) * t * t
            c.drawLine(w * (.06f + .18f * t), y, w * (.96f - .18f * t), y, p)
        }

        val ballX = w * .38f
        val ballY = h * .66f
        val cupX = w * .69f
        val cupY = h * .43f
        p.strokeWidth = max(3f, w * .008f)
        p.color = Color.argb(225, 236, 255, 241)
        val aim = Path().apply {
            moveTo(ballX, ballY)
            cubicTo(w * .47f, h * .59f, w * .61f, h * .54f, cupX, cupY)
        }
        c.drawPath(aim, p)
        p.style = Paint.Style.FILL

        p.color = Color.argb(80, 0, 0, 0)
        c.drawOval(RectF(ballX - w * .035f, ballY + h * .018f, ballX + w * .035f, ballY + h * .036f), p)
        p.color = Color.WHITE
        c.drawCircle(ballX, ballY, max(7f, w * .018f), p)

        p.color = Color.WHITE
        c.drawRect(cupX - 1.5f, cupY - h * .16f, cupX + 1.5f, cupY, p)
        val flag = Path().apply {
            moveTo(cupX + 1.5f, cupY - h * .16f)
            lineTo(cupX + w * .09f, cupY - h * .135f)
            lineTo(cupX + 1.5f, cupY - h * .105f)
            close()
        }
        p.color = Pv.amber
        c.drawPath(flag, p)
        p.style = Paint.Style.FILL
    }
}
