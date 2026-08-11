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
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Dim only enough to focus the replay while keeping the live camera context visible.
        paint.color = Color.argb(78, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)

        val left = w * .105f
        val right = w * .895f
        val top = h * .085f
        val bottom = h * .69f
        val radius = max(20f, h * .038f)

        paint.color = Color.argb(246, 5, 8, 11)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.2f, w * .0012f)
        paint.color = Color.argb(155, 58, 72, 82)
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        paint.style = Paint.Style.FILL

        val headerH = h * .105f
        val mediaTop = top + headerH
        val mediaBottom = bottom - h * .13f
        val mediaLeft = left + w * .014f
        val mediaRight = right - w * .014f
        val media = RectF(mediaLeft, mediaTop, mediaRight, mediaBottom)

        // Header
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = max(15f, w * .015f)
        paint.color = if (frame == r.impactIndex) Pv.amber else Pv.textHi
        canvas.drawText(if (frame == r.impactIndex) "IMPACT" else "240 FPS REPLAY", left + w * .020f, top + headerH * .60f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = max(9f, w * .008f)
        paint.color = Pv.textLo
        canvas.drawText("HIGH-SPEED CONTACT WINDOW", left + w * .020f, top + headerH * .82f, paint)

        val total = max(1, r.frames.size)
        val progress = (frame + 1).toFloat() / total
        val trackLeft = right - w * .20f
        val trackRight = right - w * .020f
        val trackY = top + headerH * .56f
        paint.color = Color.rgb(38, 47, 55)
        canvas.drawRoundRect(RectF(trackLeft, trackY - 2.5f, trackRight, trackY + 2.5f), 4f, 4f, paint)
        paint.color = if (frame == r.impactIndex) Pv.amber else Pv.primary
        canvas.drawRoundRect(RectF(trackLeft, trackY - 2.5f, trackLeft + (trackRight - trackLeft) * progress, trackY + 2.5f), 4f, 4f, paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = max(9f, w * .008f)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.color = Pv.textMid
        canvas.drawText("${frame + 1}/$total", trackRight, top + headerH * .83f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Media with aspect-preserving fit.
        paint.color = Color.BLACK
        canvas.drawRoundRect(media, max(10f, h * .018f), max(10f, h * .018f), paint)
        val srcAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
        val dstAspect = media.width() / media.height().coerceAtLeast(1f)
        val target = if (srcAspect > dstAspect) {
            val fitH = media.width() / srcAspect
            RectF(media.left, media.centerY() - fitH / 2f, media.right, media.centerY() + fitH / 2f)
        } else {
            val fitW = media.height() * srcAspect
            RectF(media.centerX() - fitW / 2f, media.top, media.centerX() + fitW / 2f, media.bottom)
        }
        canvas.drawBitmap(bmp, null, target, paint)

        if (frame == r.impactIndex) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, w * .002f)
            paint.color = Color.argb(220, 246, 190, 74)
            canvas.drawRoundRect(media, max(10f, h * .018f), max(10f, h * .018f), paint)
            paint.style = Paint.Style.FILL
        }

        // Telemetry rail
        val railTop = mediaBottom + h * .022f
        metrics?.let { m ->
            val items = listOf(
                "FACE" to (m.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"),
                "PATH" to (m.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"),
                "BALL" to (m.ballSpeedMps?.let { "%.2f m/s".format(it) } ?: "--"),
                "HEAD" to (m.headSpeedMps?.let { "%.2f m/s".format(it) } ?: "--")
            )
            val colW = (right - left - w * .04f) / items.size
            items.forEachIndexed { index, (label, value) ->
                val x = left + w * .020f + colW * index
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = max(8f, w * .0073f)
                paint.color = Pv.textLo
                canvas.drawText(label, x, railTop, paint)
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                paint.textSize = max(15f, w * .0132f)
                paint.color = if (index == 2) Pv.primary else Pv.textHi
                canvas.drawText(value, x, railTop + h * .046f, paint)
            }
        }
        paint.typeface = Typeface.DEFAULT
    }

    override fun onDetachedFromWindow() {
        stopReplay(recycleFrames = true)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
