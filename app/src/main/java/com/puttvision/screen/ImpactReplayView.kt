package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.math.max

class ImpactReplayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())

    private var replay: ImpactReplay? = null
    private var metrics: ShotMetrics? = null
    private var frame = 0
    private var loops = 0

    private val tick = object : Runnable {
        override fun run() {
            val r = replay ?: return

            frame++
            if (frame >= r.frames.size) {
                frame = 0
                loops++

                if (loops >= 2) {
                    visibility = GONE
                    replay = null
                    return
                }
            }

            invalidate()
            handler.postDelayed(this, 55L)
        }
    }

    init {
        visibility = GONE
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun play(value: ImpactReplay, shot: ShotMetrics) {
        handler.removeCallbacks(tick)
        replay = value
        metrics = shot
        frame = 0
        loops = 0
        visibility = VISIBLE
        invalidate()
        handler.postDelayed(tick, 55L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val r = replay ?: return
        val bmp = r.frames.getOrNull(frame) ?: return

        val left = width * 0.045f
        val top = height * 0.11f
        val right = width * 0.955f
        val bottom = height * 0.53f

        paint.color = Color.argb(225, 0, 0, 0)
        canvas.drawRoundRect(left, top, right, bottom, 26f, 26f, paint)

        val inner = RectF(left + 12f, top + 12f, right - 12f, bottom - 12f)

        val srcAspect = bmp.width.toFloat() / bmp.height
        val dstAspect = inner.width() / inner.height()

        val target =
            if (srcAspect > dstAspect) {
                val h = inner.width() / srcAspect
                RectF(inner.left, inner.centerY() - h / 2f, inner.right, inner.centerY() + h / 2f)
            } else {
                val w = inner.height() * srcAspect
                RectF(inner.centerX() - w / 2f, inner.top, inner.centerX() + w / 2f, inner.bottom)
            }

        canvas.drawBitmap(bmp, null, target, paint)

        paint.color = Color.argb(185, 0, 0, 0)
        canvas.drawRoundRect(left + 20f, top + 20f, right - 20f, top + 92f, 18f, 18f, paint)

        paint.textSize = max(25f, width * 0.026f)
        paint.typeface = Typeface.DEFAULT_BOLD

        if (frame == r.impactIndex) {
            paint.color = Color.rgb(255, 214, 64)
            canvas.drawText("IMPACT", left + 40f, top + 65f, paint)
        } else {
            paint.color = Color.WHITE
            canvas.drawText("240FPS IMPACT REPLAY", left + 40f, top + 65f, paint)
        }

        paint.typeface = Typeface.DEFAULT

        metrics?.let { m ->
            paint.color = Color.WHITE
            paint.textSize = max(20f, width * 0.021f)
            val text =
                "FACE ${m.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   " +
                "PATH ${m.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   " +
                "BALL ${"%.2fm/s".format(m.ballSpeedMps)}"

            canvas.drawText(text, left + 38f, bottom - 28f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
