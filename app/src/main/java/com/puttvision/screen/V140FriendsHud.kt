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
 * Minimal putting HUD calibrated against Kakao VX's publicly visible Friends Screen putting still.
 * No logos, characters or extracted assets are reproduced.
 */
class V140FriendsHud(context: Context, private val game: GameEngine) : View(context) {
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
        postInvalidateOnAnimation()
    }

    private fun drawAimGuide(canvas: Canvas, w: Float, h: Float, s: Float, running: Boolean) {
        if (running) return
        val cx = w * .5f
        val ballY = h * V140FriendsReference.BALL_SCREEN_Y01
        val flagY = h * V140FriendsReference.FLAG_SCREEN_Y01

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.0f * s
        p.color = Color.argb(225, 211, 42, 34)
        canvas.drawLine(cx, ballY - 10f * s, cx, flagY + 8f * s, p)

        // Small dotted ellipse around the address ball, matching the public putting still.
        p.style = Paint.Style.FILL
        p.color = Color.argb(235, 250, 250, 244)
        val rx = 21.5f * s
        val ry = 11.5f * s
        for (i in 0 until 14) {
            val a = Math.PI * 2.0 * i / 14.0
            canvas.drawCircle(
                cx + cos(a).toFloat() * rx,
                ballY + sin(a).toFloat() * ry,
                1.75f * s,
                p
            )
        }
    }

    private fun drawTopTarget(canvas: Canvas, w: Float, s: Float, distance: Double) {
        val top = 8f * s
        val leftW = 132f * s
        val rightW = 82f * s
        val gap = 5f * s
        val h = 31f * s
        val totalW = leftW + rightW + gap
        val left = w * .5f - totalW * .5f

        p.style = Paint.Style.FILL
        p.color = Color.argb(220, 49, 46, 68)
        canvas.drawRoundRect(RectF(left, top, left + leftW, top + h), 10f * s, 10f * s, p)
        canvas.drawRoundRect(RectF(left + leftW + gap, top, left + totalW, top + h), 10f * s, 10f * s, p)

        p.typeface = medium
        p.textAlign = Paint.Align.CENTER
        p.textSize = 10.8f * s
        p.color = Color.rgb(248, 211, 46)
        canvas.drawText(String.format(Locale.US, "목표 거리 %.0fm", distance), left + leftW * .5f, top + 20f * s, p)

        p.color = Color.rgb(118, 218, 91)
        canvas.drawText("보통", left + leftW + gap + rightW * .5f, top + 20f * s, p)

        val clubW = 46f * s
        val clubTop = top + h + 2f * s
        p.color = Color.argb(218, 80, 61, 78)
        canvas.drawRoundRect(
            RectF(w * .5f - clubW * .5f - 28f * s, clubTop, w * .5f + clubW * .5f - 28f * s, clubTop + 20f * s),
            7f * s,
            7f * s,
            p
        )
        p.typeface = regular
        p.textSize = 9.2f * s
        p.color = Color.WHITE
        canvas.drawText("퍼터", w * .5f - 28f * s, clubTop + 13.8f * s, p)

        // Generic ball icon visible in the public composition; deliberately not branded.
        val iconX = w * .5f + 53f * s
        val iconY = clubTop + 7f * s
        p.color = Color.argb(100, 49, 104, 56)
        canvas.drawCircle(iconX + 2f * s, iconY + 2f * s, 9f * s, p)
        p.color = Color.rgb(250, 250, 247)
        canvas.drawCircle(iconX, iconY, 8f * s, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawStatus(canvas: Canvas, w: Float, h: Float, s: Float, state: SimState?) {
        val text = when {
            state?.cupPhase == V134CupPhase.RIM -> "RIM"
            state?.cupPhase == V134CupPhase.DROP -> "DROP"
            state?.running == true -> "ROLLING"
            else -> "WAIT"
        }
        val boxW = if (text == "ROLLING") 78f * s else 62f * s
        val boxH = 24f * s
        val cx = w * .5f
        val cy = h * V140FriendsReference.WAIT_SCREEN_Y01
        p.style = Paint.Style.FILL
        p.color = Color.argb(220, 105, 66, 54)
        canvas.drawRoundRect(RectF(cx - boxW * .5f, cy - boxH * .5f, cx + boxW * .5f, cy + boxH * .5f), 10f * s, 10f * s, p)
        p.typeface = medium
        p.textSize = 9.3f * s
        p.textAlign = Paint.Align.CENTER
        p.color = Color.rgb(224, 207, 187)
        canvas.drawText(text, cx, cy + 3.2f * s, p)
        p.textAlign = Paint.Align.LEFT
    }
}
