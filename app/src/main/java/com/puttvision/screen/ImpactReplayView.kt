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
    private var referenceMetrics: ShotMetrics? = null
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

    fun play(value: ImpactReplay, shot: ShotMetrics, reference: ShotMetrics? = null) {
        stopReplay(recycleFrames = true)
        replay = value
        metrics = shot
        referenceMetrics = reference
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
        referenceMetrics = null
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
            drawBestShotComparison(canvas, media)
            drawV19StudioComparison(canvas, media)
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

    private fun drawBestShotComparison(canvas: Canvas, media: RectF) {
        val current = metrics ?: return
        val best = referenceMetrics ?: return
        val cx = media.centerX()
        val baseY = media.bottom - media.height() * .08f
        val len = media.height() * .43f

        fun lineForAngle(angleDeg: Double?, color: Int, widthPx: Float) {
            val a = Math.toRadians(angleDeg ?: 0.0)
            val dx = kotlin.math.sin(a).toFloat() * len
            val dy = kotlin.math.cos(a).toFloat() * len
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = widthPx
            paint.color = color
            canvas.drawLine(cx, baseY, cx + dx, baseY - dy, paint)
            paint.style = Paint.Style.FILL
        }

        lineForAngle(best.pathAngleDeg ?: best.launchAngleDeg, Color.argb(175, 96, 178, 255), max(2f, width * .0015f))
        lineForAngle(current.pathAngleDeg ?: current.launchAngleDeg, Color.argb(230, 78, 209, 121), max(3f, width * .0022f))

        val faceY = baseY - len * .23f
        fun face(angle: Double?, color: Int, stroke: Float) {
            val a = Math.toRadians(angle ?: 0.0)
            val half = media.width() * .055f
            val dx = kotlin.math.cos(a).toFloat() * half
            val dy = kotlin.math.sin(a).toFloat() * half
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = color
            canvas.drawLine(cx - dx, faceY - dy, cx + dx, faceY + dy, paint)
            paint.style = Paint.Style.FILL
        }
        face(best.faceAngleDeg, Color.argb(175, 96, 178, 255), max(2f, width * .0015f))
        face(current.faceAngleDeg, Color.argb(235, 246, 190, 74), max(3f, width * .0022f))

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = max(9f, width * .0075f)
        paint.color = Color.argb(215, 96, 178, 255)
        canvas.drawText("BEST", media.left + media.width() * .025f, media.top + media.height() * .075f, paint)
        paint.color = Pv.primary
        canvas.drawText("CURRENT", media.left + media.width() * .025f, media.top + media.height() * .125f, paint)
        paint.color = Pv.textMid
        paint.textSize = max(8f, width * .0067f)
        canvas.drawText(
            "PATH ${current.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"} / ${best.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}   ·   FACE ${current.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"} / ${best.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--"}",
            media.left + media.width() * .025f,
            media.bottom - media.height() * .035f,
            paint
        )
    }

    private fun drawV19StudioComparison(canvas: Canvas, media: RectF) {
        val model = V19StrokeStudioRuntime.latest ?: return
        if (model.current.size < 2 || model.ideal.size < 2) return
        val box = RectF(
            media.right - media.width() * .34f,
            media.top + media.height() * .06f,
            media.right - media.width() * .025f,
            media.bottom - media.height() * .08f
        )
        paint.color = Color.argb(126, 4, 8, 10)
        canvas.drawRoundRect(box, max(9f, width * .006f), max(9f, width * .006f), paint)

        fun trace(points: List<V19StrokeNode>, color: Int, stroke: Float) {
            if (points.size < 2) return
            val xMax = max(4.0, points.maxOf { kotlin.math.abs(it.xCm) } + 1.0)
            val yMin = points.minOf { it.yCm }
            val yMax = points.maxOf { it.yCm }.coerceAtLeast(yMin + 1.0)
            fun sx(x: Double) = box.centerX() + (x / xMax).toFloat() * box.width() * .44f
            fun sy(y: Double) = box.bottom - box.height() * .08f - ((y - yMin) / (yMax - yMin)).toFloat() * box.height() * .74f
            val path = Path().apply {
                moveTo(sx(points.first().xCm), sy(points.first().yCm))
                points.drop(1).forEach { lineTo(sx(it.xCm), sy(it.yCm)) }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = stroke
            paint.color = color
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = Color.argb(42, 246, 190, 74)
        val corridor = (model.corridorCm / 5.0).toFloat().coerceIn(.03f, .22f) * box.width()
        canvas.drawRect(box.centerX() - corridor, box.top + box.height() * .12f, box.centerX() + corridor, box.bottom - box.height() * .06f, paint)
        trace(model.ghost, Color.argb(180, 86, 167, 255), max(2f, width * .0012f))
        trace(model.ideal, Color.argb(220, 246, 190, 74), max(2f, width * .0014f))
        trace(model.current, Color.argb(235, 78, 209, 121), max(3f, width * .0019f))

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = max(8f, width * .0068f)
        paint.color = Color.WHITE
        canvas.drawText("STROKE Q${model.quality}", box.left + box.width() * .06f, box.top + box.height() * .09f, paint)
        paint.textSize = max(6.5f, width * .0055f)
        paint.color = Color.argb(210, 220, 228, 221)
        canvas.drawText("GREEN CURRENT  ·  GOLD IDEAL  ·  BLUE BEST", box.left + box.width() * .06f, box.bottom - box.height() * .025f, paint)
    }

    override fun onDetachedFromWindow() {
        stopReplay(recycleFrames = true)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
