package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure presentation views used by the commercial UI. They never own app state,
 * consume touch events, or alter the putting/camera pipeline.
 */
class CommercialHomeBackdropView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.rgb(5, 8, 11), Color.rgb(7, 14, 16), Color.rgb(7, 22, 17)),
            floatArrayOf(0f, .46f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // A restrained simulator-green silhouette: visible enough to sell the
        // product idea, quiet enough that the interface remains the hero.
        val horizon = h * .36f
        val green = Path().apply {
            moveTo(w * .08f, h)
            lineTo(w * .31f, horizon)
            lineTo(w * .69f, horizon)
            lineTo(w * .97f, h)
            close()
        }
        p.shader = LinearGradient(
            0f, horizon, 0f, h,
            Color.rgb(34, 79, 48), Color.rgb(12, 43, 29), Shader.TileMode.CLAMP
        )
        c.drawPath(green, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .0008f)
        p.color = Color.argb(32, 116, 224, 168)
        for (i in -6..6) {
            c.drawLine(w * .51f + i * w * .035f, horizon, w * .51f + i * w * .11f, h, p)
        }
        for (i in 1..7) {
            val t = i / 7f
            val y = h - (h - horizon) * t * t
            c.drawLine(w * (.08f + .22f * t), y, w * (.97f - .22f * t), y, p)
        }
        p.style = Paint.Style.FILL

        val cupX = w * .61f
        val cupY = h * .48f
        p.color = Color.argb(225, 242, 246, 247)
        c.drawRect(cupX - 1.3f, cupY - h * .13f, cupX + 1.3f, cupY, p)
        p.color = Pv.amber
        val flag = Path().apply {
            moveTo(cupX + 1.5f, cupY - h * .13f)
            lineTo(cupX + w * .055f, cupY - h * .111f)
            lineTo(cupX + 1.5f, cupY - h * .086f)
            close()
        }
        c.drawPath(flag, p)

        val ballX = w * .38f
        val ballY = h * .72f
        p.color = Color.argb(70, 0, 0, 0)
        c.drawOval(RectF(ballX - w * .028f, ballY + h * .012f, ballX + w * .028f, ballY + h * .027f), p)
        p.color = Color.WHITE
        c.drawCircle(ballX, ballY, max(7f, w * .010f), p)

        // Soft premium light bloom; no neon/glow overload.
        p.shader = RadialGradient(w * .62f, h * .42f, w * .34f,
            intArrayOf(Color.argb(36, 95, 224, 158), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w * .62f, h * .42f, w * .34f, p)
        p.shader = null

        p.shader = LinearGradient(0f, h * .55f, 0f, h,
            Color.TRANSPARENT, Color.argb(205, 4, 6, 8), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * .55f, w, h, p)
        p.shader = null
    }
}

class CommercialImpactPreviewView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val card = RectF(0f, 0f, w, h)
        p.color = Color.argb(224, 6, 9, 12)
        c.drawRoundRect(card, 13f * d, 13f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = Color.argb(150, 50, 63, 72)
        c.drawRoundRect(RectF(d / 2, d / 2, w - d / 2, h - d / 2), 13f * d, 13f * d, p)
        p.style = Paint.Style.FILL

        p.color = Pv.textHi
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 7.8f * d
        c.drawText("IMPACT WINDOW", 9f * d, 15f * d, p)
        p.color = Pv.primary
        c.drawCircle(w - 11f * d, 11.5f * d, 2.6f * d, p)

        val graphL = 9f * d
        val graphR = w - 9f * d
        val graphTop = 26f * d
        val graphBottom = h - 16f * d
        val mid = (graphTop + graphBottom) / 2f

        p.style = Paint.Style.STROKE
        p.strokeWidth = .65f * d
        p.color = Color.argb(70, 119, 137, 148)
        c.drawLine(graphL, mid, graphR, mid, p)

        val path = Path()
        var first = true
        var x = graphL
        while (x <= graphR) {
            val t = (x - graphL) / max(1f, graphR - graphL)
            val envelope = if (t in .36f..64f) 1f - kotlin.math.abs(t - .5f) / .14f else 0f
            val y = mid - (sin(t * 31f) * .09f + sin(t * 53f) * .035f + envelope * .42f) * (graphBottom - graphTop)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += 2f * d
        }
        p.strokeWidth = 1.25f * d
        p.color = Pv.primary
        c.drawPath(path, p)

        val impactX = graphL + (graphR - graphL) * .5f
        p.color = Color.argb(230, 156, 123, 255)
        p.strokeWidth = 1f * d
        c.drawLine(impactX, graphTop, impactX, graphBottom, p)
        p.style = Paint.Style.FILL

        p.textSize = 5.7f * d
        p.typeface = Typeface.DEFAULT
        p.color = Pv.textLo
        c.drawText("−100ms", graphL, h - 5f * d, p)
        p.textAlign = Paint.Align.CENTER
        c.drawText("IMPACT", impactX, h - 5f * d, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("+100ms", graphR, h - 5f * d, p)
        p.textAlign = Paint.Align.LEFT
    }
}

class CommercialModeVisualView(
    context: Context,
    private val game: Boolean
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.shader = LinearGradient(0f, 0f, w, h,
            if (game) intArrayOf(Color.rgb(24, 18, 11), Color.rgb(10, 12, 14))
            else intArrayOf(Color.rgb(11, 28, 21), Color.rgb(8, 12, 14)),
            null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, p)
        p.shader = null

        val accent = if (game) Pv.amber else Pv.primary
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, w * .004f)
        p.color = Color.argb(185, Color.red(accent), Color.green(accent), Color.blue(accent))
        val route = Path().apply {
            moveTo(w * .18f, h * .78f)
            cubicTo(w * .34f, h * .65f, w * .48f, h * .56f, w * .69f, h * .30f)
        }
        c.drawPath(route, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(w * .18f, h * .78f, max(6f, w * .018f), p)
        p.color = accent
        c.drawCircle(w * .69f, h * .30f, max(4f, w * .012f), p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(20f, w * .048f)
        p.color = Pv.textHi
        c.drawText(if (game) "MATCH PLAY" else "PRACTICE LAB", w * .10f, h * .23f, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = max(11f, w * .022f)
        p.color = Pv.textMid
        c.drawText(if (game) "1–4 PLAYER · COURSE · CHALLENGE" else "DISTANCE · CUP · BREAK · REPEAT", w * .10f, h * .31f, p)
    }
}

class CommercialSetupDiagramView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        p.color = Color.rgb(9, 14, 17)
        c.drawRoundRect(RectF(0f, 0f, w, h), 24f, 24f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(120, 78, 209, 121)
        val mat = RectF(w * .20f, h * .24f, w * .80f, h * .82f)
        c.drawRoundRect(mat, 16f, 16f, p)
        p.color = Color.argb(80, 105, 126, 139)
        c.drawLine(w * .5f, mat.top, w * .5f, mat.bottom, p)
        p.style = Paint.Style.FILL

        val markerR = max(5f, w * .015f)
        listOf(
            mat.left + 14f to mat.top + 14f,
            mat.right - 14f to mat.top + 14f,
            mat.left + 14f to mat.bottom - 14f,
            mat.right - 14f to mat.bottom - 14f
        ).forEach { (x, y) ->
            p.color = Pv.primary
            c.drawCircle(x, y, markerR, p)
        }
        p.color = Color.WHITE
        c.drawCircle(w * .5f, h * .67f, markerR * 1.2f, p)

        p.color = Pv.info
        c.drawRoundRect(RectF(w * .42f, h * .06f, w * .58f, h * .13f), 9f, 9f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.argb(160, 96, 178, 255)
        c.drawLine(w * .5f, h * .13f, w * .5f, h * .24f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(11f, w * .025f)
        p.color = Pv.textHi
        c.drawText("PHONE CAMERA", w * .5f, h * .18f, p)
        p.textSize = max(9f, w * .019f)
        p.typeface = Typeface.DEFAULT
        p.color = Pv.textMid
        c.drawText("4 MARKERS + BALL IN FRAME", w * .5f, h * .94f, p)
        p.textAlign = Paint.Align.LEFT
    }
}
