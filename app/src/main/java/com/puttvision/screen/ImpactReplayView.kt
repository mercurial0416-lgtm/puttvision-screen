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
                    stopReplay(recycleFrames = true)
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
        isClickable = false
        isFocusable = false
    }

    fun play(value: ImpactReplay, shot: ShotMetrics) {
        stopReplay(recycleFrames = true)
        replay = value
        metrics = shot
        frame = 0
        loops = 0
        visibility = VISIBLE
        invalidate()
        handler.postDelayed(tick, 55L)
    }


    private fun stopReplay(recycleFrames: Boolean) {
        handler.removeCallbacks(tick)
        val old = replay
        replay = null
        metrics = null
        frame = 0
        loops = 0
        visibility = GONE

        if (recycleFrames) {
            old?.frames?.distinctBy { System.identityHashCode(it) }?.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val r = replay ?: return
        val bmp = r.frames.getOrNull(frame) ?: return

        val left = width * 0.045f
        val top = height * 0.11f
        val right = width * 0.955f
        val bottom = height * 0.53f

        paint.color = Color.argb(242, 9, 12, 16)
        canvas.drawRoundRect(left, top, right, bottom, 26f, 26f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Pv.line
        canvas.drawRoundRect(left, top, right, bottom, 26f, 26f, paint)
        paint.style = Paint.Style.FILL

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

        paint.color = Color.argb(220, 13, 17, 22)
        canvas.drawRoundRect(left + 20f, top + 20f, right - 20f, top + 92f, 18f, 18f, paint)

        paint.textSize = max(25f, width * 0.026f)
        paint.typeface = Typeface.DEFAULT_BOLD

        if (frame == r.impactIndex) {
            paint.color = Pv.amber
            canvas.drawText("IMPACT", left + 40f, top + 65f, paint)
        } else {
            paint.color = Pv.textHi
            canvas.drawText("240FPS IMPACT REPLAY", left + 40f, top + 65f, paint)
        }

        paint.typeface = Typeface.DEFAULT

        metrics?.let { m ->
            paint.color = Pv.textHi
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.textSize = max(20f, width * 0.021f)
            val text =
                "FACE ${m.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   " +
                "PATH ${m.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   " +
                "BALL ${m.ballSpeedMps?.let { "%.2fm/s".format(it) } ?: "--"}"

            canvas.drawText(text, left + 38f, bottom - 28f, paint)
            paint.typeface = Typeface.DEFAULT
        }
    }

    override fun onDetachedFromWindow() {
        stopReplay(recycleFrames = true)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
