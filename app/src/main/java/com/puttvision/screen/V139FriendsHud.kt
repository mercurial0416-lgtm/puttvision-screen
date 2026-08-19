package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Clean-room HUD calibrated from publicly visible Friends Screen putting imagery.
 * Brand marks, proprietary icons and character assets are intentionally not reproduced.
 */
class V139FriendsHud(context: Context, private val game: GameEngine) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w / 1536f, h / 864f).coerceAtLeast(.58f)
        val settings = game.settings
        val state = game.state
        val distance = settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0

        drawAimGuide(canvas, w, h, s, state?.running == true)
        drawTopTarget(canvas, w, s, distance)
        drawStatus(canvas, w, h, s, state)
        drawCompactRead(canvas, w, h, s, settings)
        postInvalidateOnAnimation()
    }

    private fun drawAimGuide(canvas: Canvas, w: Float, h: Float, s: Float, running: Boolean) {
        if (running) return
        val cx = w * .5f
        val startY = h * V139FriendsReference.BALL_SCREEN_Y01
        val endY = h * V139FriendsReference.FLAG_SCREEN_Y01 + 18f * s

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f * s
        p.color = Color.argb(205, 223, 55, 43)
        canvas.drawLine(cx, startY - 10f * s, cx, endY, p)

        // Public reference uses a small dotted aiming ring around the address ball.
        p.style = Paint.Style.FILL
        p.color = Color.argb(215, 248, 248, 244)
        val r = 25f * s
        for (i in 0 until 12) {
            val a = Math.PI * 2.0 * i / 12.0
            canvas.drawCircle(
                cx + cos(a).toFloat() * r,
                startY + sin(a).toFloat() * r * .55f,
                2.15f * s,
                p
            )
        }
    }

    private fun drawTopTarget(canvas: Canvas, w: Float, s: Float, distance: Double) {
        val totalW = 256f * s
        val left = w * .5f - totalW * .5f
        val top = 12f * s
        val gap = 7f * s
        val leftW = 148f * s
        val rightW = totalW - leftW - gap

        p.style = Paint.Style.FILL
        p.color = Color.argb(207, 47, 48, 70)
        canvas.drawRoundRect(RectF(left, top, left + leftW, top + 39f * s), 12f * s, 12f * s, p)
        canvas.drawRoundRect(RectF(left + leftW + gap, top, left + totalW, top + 39f * s), 12f * s, 12f * s, p)

        p.typeface = medium
        p.textAlign = Paint.Align.CENTER
        p.textSize = 12.5f * s
        p.color = Color.rgb(245, 213, 67)
        canvas.drawText(String.format(Locale.US, "목표 거리 %.1fm", distance), left + leftW * .5f, top + 25f * s, p)

        p.color = Color.rgb(238, 239, 243)
        canvas.drawText("보통", left + leftW + gap + rightW * .5f, top + 25f * s, p)

        val pillW = 56f * s
        val pillTop = top + 43f * s
        p.color = Color.argb(185, 67, 58, 75)
        canvas.drawRoundRect(RectF(w * .5f - pillW * .5f, pillTop, w * .5f + pillW * .5f, pillTop + 24f * s), 8f * s, 8f * s, p)
        p.typeface = regular
        p.textSize = 10.5f * s
        p.color = Color.WHITE
        canvas.drawText("퍼터", w * .5f, pillTop + 16.5f * s, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawStatus(canvas: Canvas, w: Float, h: Float, s: Float, state: SimState?) {
        val text = when {
            state?.cupPhase == V134CupPhase.RIM -> "RIM"
            state?.cupPhase == V134CupPhase.DROP -> "DROP"
            state?.running == true -> "ROLLING"
            else -> "WAIT"
        }
        val boxW = if (text == "ROLLING") 96f * s else 72f * s
        val boxH = 30f * s
        val cx = w * .5f
        val cy = h * .875f
        p.style = Paint.Style.FILL
        p.color = Color.argb(210, 91, 61, 53)
        canvas.drawRoundRect(RectF(cx - boxW * .5f, cy - boxH * .5f, cx + boxW * .5f, cy + boxH * .5f), 12f * s, 12f * s, p)
        p.typeface = medium
        p.textSize = 11.5f * s
        p.textAlign = Paint.Align.CENTER
        p.color = Color.rgb(232, 219, 204)
        canvas.drawText(text, cx, cy + 4f * s, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawCompactRead(canvas: Canvas, w: Float, h: Float, s: Float, settings: GreenSettings) {
        val cardW = 178f * s
        val cardH = 88f * s
        val right = w - 18f * s
        val top = 18f * s
        p.style = Paint.Style.FILL
        p.color = Color.argb(165, 20, 35, 45)
        canvas.drawRoundRect(RectF(right - cardW, top, right, top + cardH), 10f * s, 10f * s, p)

        p.typeface = medium
        p.textSize = 10.5f * s
        p.color = Color.WHITE
        canvas.drawText("GREEN", right - cardW + 12f * s, top + 19f * s, p)
        p.typeface = regular
        p.textSize = 10f * s
        p.color = Color.rgb(207, 220, 221)
        canvas.drawText(String.format(Locale.US, "STIMP %.1f", settings.stimpMeters), right - cardW + 12f * s, top + 39f * s, p)
        canvas.drawText(String.format(Locale.US, "SIDE %+.1f%%", settings.sideSlopePct), right - cardW + 12f * s, top + 57f * s, p)
        canvas.drawText(String.format(Locale.US, "LONG %+.1f%%", settings.longSlopePct), right - cardW + 12f * s, top + 75f * s, p)
    }
}
