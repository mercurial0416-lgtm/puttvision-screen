package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.max

/** Minimal product artwork used by the V5 launcher. Purely visual. */
class PremiumHomeStageView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(4, 7, 9), Color.rgb(7, 13, 12), Color.rgb(4, 6, 8)),
            floatArrayOf(0f, .54f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // restrained halo behind the green, avoiding the typical neon-dashboard look
        p.shader = RadialGradient(
            w * .34f, h * .52f, w * .42f,
            intArrayOf(Color.argb(70, 53, 143, 82), Color.argb(18, 28, 83, 54), Color.TRANSPARENT),
            floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w * .74f, h, p)
        p.shader = null

        val green = RectF(w * .055f, h * .23f, w * .62f, h * .86f)
        p.shader = LinearGradient(
            green.left, green.top, green.right, green.bottom,
            intArrayOf(Color.rgb(31, 91, 53), Color.rgb(22, 68, 42), Color.rgb(12, 42, 29)),
            null, Shader.TileMode.CLAMP
        )
        c.drawOval(green, p)
        p.shader = null

        // precise but quiet grid
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .0011f)
        p.color = Color.argb(48, 195, 235, 207)
        for (i in 1..8) {
            val y = green.top + green.height() * i / 9f
            val t = (y - green.top) / green.height()
            val half = green.width() * (.22f + .23f * t)
            c.drawLine(green.centerX() - half, y, green.centerX() + half, y, p)
        }
        for (i in -4..4) {
            path.reset()
            val frac = i / 4f
            path.moveTo(green.centerX() + frac * green.width() * .12f, green.top)
            path.lineTo(green.centerX() + frac * green.width() * .43f, green.bottom)
            c.drawPath(path, p)
        }

        // start line / cup
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(3f, w * .0028f)
        p.color = Color.argb(215, 235, 244, 238)
        path.reset()
        path.moveTo(w * .22f, h * .71f)
        path.cubicTo(w * .28f, h * .63f, w * .36f, h * .52f, w * .43f, h * .40f)
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL

        p.color = Color.WHITE
        c.drawCircle(w * .22f, h * .71f, max(7f, w * .008f), p)
        val cupX = w * .43f
        val cupY = h * .40f
        p.color = Color.argb(130, 0, 0, 0)
        c.drawOval(RectF(cupX - w * .012f, cupY - h * .004f, cupX + w * .012f, cupY + h * .006f), p)
        p.color = Color.WHITE
        c.drawRect(cupX - 1.4f, cupY - h * .105f, cupX + 1.4f, cupY, p)
        path.reset()
        path.moveTo(cupX + 2f, cupY - h * .105f)
        path.lineTo(cupX + w * .035f, cupY - h * .091f)
        path.lineTo(cupX + 2f, cupY - h * .073f)
        path.close()
        p.color = Pv.amber
        c.drawPath(path, p)

        // vignette so the text side remains clean
        p.shader = LinearGradient(
            w * .45f, 0f, w * .78f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(190, 4, 7, 9)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(w * .42f, 0f, w * .80f, h, p)
        p.shader = null
    }
}
