package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Result-only TV overlay. It stays off the simulator during address/roll and appears briefly after
 * the ball stops, so analysis depth does not turn the main TV back into a dashboard.
 */
class V19StrokeStudioOverlay(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var resultIdentity = 0
    private var shownAtMs = 0L

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val result = engine.lastResult
        val model = engine.strokeStudio
        if (result == null || model == null) {
            postInvalidateDelayed(100L)
            return
        }
        val identity = System.identityHashCode(result)
        if (identity != resultIdentity) {
            resultIdentity = identity
            shownAtMs = SystemClock.uptimeMillis()
        }
        val age = SystemClock.uptimeMillis() - shownAtMs
        if (age > 3300L) return

        val w = width.toFloat()
        val h = height.toFloat()
        val boxW = w * .285f
        val boxH = h * .315f
        val left = w - boxW - w * .025f
        val top = h * .135f
        val alpha = when {
            age < 250L -> (age / 250f).coerceIn(0f, 1f)
            age > 2850L -> ((3300L - age) / 450f).coerceIn(0f, 1f)
            else -> 1f
        }

        p.color = Color.argb((205 * alpha).toInt(), 10, 15, 15)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), h * .020f, h * .020f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, w * .0008f)
        p.color = Color.argb((90 * alpha).toInt(), 255, 255, 255)
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), h * .020f, h * .020f, p)
        p.style = Paint.Style.FILL

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(9f, w * .0066f)
        p.color = Color.argb((235 * alpha).toInt(), 255, 255, 255)
        c.drawText("STROKE STUDIO  ·  Q${model.quality}", left + boxW * .06f, top + boxH * .105f, p)
        p.textSize = max(7f, w * .0050f)
        p.color = Color.argb((175 * alpha).toInt(), 220, 229, 222)
        c.drawText(model.headline, left + boxW * .06f, top + boxH * .175f, p)

        val plot = RectF(
            left + boxW * .08f,
            top + boxH * .22f,
            left + boxW * .72f,
            top + boxH * .92f
        )
        drawCorridor(c, plot, model, alpha)
        drawTrace(c, plot, model.ghost, Color.argb((165 * alpha).toInt(), 83, 174, 255), max(2f, w * .00125f))
        drawTrace(c, plot, model.ideal, Color.argb((190 * alpha).toInt(), 255, 215, 78), max(2f, w * .00125f))
        drawTrace(c, plot, model.current, Color.argb((235 * alpha).toInt(), 92, 232, 132), max(3f, w * .0019f))
        drawFaceBars(c, plot, model.current, alpha)

        val tx = left + boxW * .76f
        var ty = top + boxH * .30f
        fun legend(label: String, value: String, color: Int) {
            p.color = Color.argb((230 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            c.drawCircle(tx, ty - h * .004f, max(3f, w * .002f), p)
            p.color = Color.argb((220 * alpha).toInt(), 244, 247, 244)
            p.textSize = max(7f, w * .0049f)
            p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(label, tx + boxW * .045f, ty, p)
            p.textSize = max(6.5f, w * .0045f)
            p.color = Color.argb((170 * alpha).toInt(), 215, 224, 216)
            c.drawText(value, tx, ty + boxH * .075f, p)
            ty += boxH * .175f
        }
        legend("CURRENT", if (model.measuredTrace) "실측 헤드" else "지표 기반", Color.rgb(92, 232, 132))
        legend("IDEAL", "코리더 ±${"%.1f".format(model.corridorCm)}cm", Color.rgb(255, 215, 78))
        if (model.ghost.isNotEmpty()) legend("BEST", "내 최고 샷", Color.rgb(83, 174, 255))

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(6.5f, w * .0045f)
        p.color = Color.argb((185 * alpha).toInt(), 225, 232, 226)
        c.drawText("RMS ${"%.2f".format(model.pathRmsCm)}cm", tx, top + boxH * .89f, p)

        if (age < 3300L) postInvalidateDelayed(16L)
    }

    private fun drawCorridor(c: Canvas, plot: RectF, model: V19StrokeStudioModel, alpha: Float) {
        val scaleX = plot.width() / 8.0f
        val corridorPx = (model.corridorCm * scaleX).toFloat()
        p.color = Color.argb((32 * alpha).toInt(), 255, 215, 78)
        c.drawRect(plot.centerX() - corridorPx, plot.top, plot.centerX() + corridorPx, plot.bottom, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, width * .0008f)
        p.color = Color.argb((80 * alpha).toInt(), 255, 255, 255)
        c.drawLine(plot.centerX(), plot.top, plot.centerX(), plot.bottom, p)
        c.drawLine(plot.left, plot.centerY(), plot.right, plot.centerY(), p)
        p.style = Paint.Style.FILL
    }

    private fun drawTrace(c: Canvas, plot: RectF, trace: List<V19StrokeNode>, color: Int, stroke: Float) {
        if (trace.size < 2) return
        val xExtent = max(4.0, trace.maxOf { abs(it.xCm) } + 1.0)
        val yMin = trace.minOf { it.yCm }
        val yMax = trace.maxOf { it.yCm }.coerceAtLeast(yMin + 1.0)
        fun x(v: Double) = plot.centerX() + (v / xExtent).toFloat() * plot.width() * .48f
        fun y(v: Double) = plot.bottom - ((v - yMin) / (yMax - yMin)).toFloat() * plot.height()
        val path = Path().apply {
            moveTo(x(trace.first().xCm), y(trace.first().yCm))
            trace.drop(1).forEach { lineTo(x(it.xCm), y(it.yCm)) }
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = stroke
        p.color = color
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawFaceBars(c: Canvas, plot: RectF, trace: List<V19StrokeNode>, alpha: Float) {
        val candidates = listOf(-.62, 0.0, .62).mapNotNull { target ->
            trace.minByOrNull { abs(it.tNorm - target) }
        }
        candidates.forEach { node ->
            val face = node.faceDeg ?: return@forEach
            val xx = plot.centerX() + (node.xCm / 5.0).toFloat() * plot.width() * .42f
            val yy = plot.centerY() - node.tNorm.toFloat() * plot.height() * .42f
            val a = Math.toRadians(face)
            val half = plot.width() * .055f
            val dx = kotlin.math.cos(a).toFloat() * half
            val dy = kotlin.math.sin(a).toFloat() * half
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(2f, width * .00125f)
            p.color = Color.argb((205 * alpha).toInt(), 240, 240, 240)
            c.drawLine(xx - dx, yy - dy, xx + dx, yy + dy, p)
            p.style = Paint.Style.FILL
        }
    }
}
