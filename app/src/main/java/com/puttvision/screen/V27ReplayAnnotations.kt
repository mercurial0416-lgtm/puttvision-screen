package com.puttvision.screen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

enum class V27ReplayTool { NONE, LINE, CIRCLE, ANGLE }

data class V27NormPoint(val x: Float, val y: Float)

sealed interface V27ReplayMark {
    data class Line(val a: V27NormPoint, val b: V27NormPoint) : V27ReplayMark
    data class Circle(val center: V27NormPoint, val edge: V27NormPoint) : V27ReplayMark
    data class Angle(val vertex: V27NormPoint, val a: V27NormPoint, val b: V27NormPoint) : V27ReplayMark
}

object V27ReplayGeometry {
    fun angleDeg(vertex: V27NormPoint, a: V27NormPoint, b: V27NormPoint): Double {
        val ax = (a.x - vertex.x).toDouble()
        val ay = (a.y - vertex.y).toDouble()
        val bx = (b.x - vertex.x).toDouble()
        val by = (b.y - vertex.y).toDouble()
        val al = hypot(ax, ay)
        val bl = hypot(bx, by)
        if (al < 1e-9 || bl < 1e-9) return 0.0
        val dot = ((ax * bx + ay * by) / (al * bl)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot))
    }
}

class V27ReplayAnnotationSession {
    val marks = mutableListOf<V27ReplayMark>()
    var tool: V27ReplayTool = V27ReplayTool.NONE
    private var down: V27NormPoint? = null
    private var preview: V27NormPoint? = null
    private val anglePoints = mutableListOf<V27NormPoint>()

    fun clear() {
        marks.clear()
        down = null
        preview = null
        anglePoints.clear()
    }

    fun undo() {
        if (anglePoints.isNotEmpty()) anglePoints.removeAt(anglePoints.lastIndex)
        else if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex)
    }

    fun setTool(value: V27ReplayTool) {
        tool = value
        down = null
        preview = null
        anglePoints.clear()
    }

    fun handle(event: MotionEvent, media: RectF): Boolean {
        if (tool == V27ReplayTool.NONE || !media.contains(event.x, event.y)) return false
        val p = normalize(event.x, event.y, media)
        when (tool) {
            V27ReplayTool.LINE, V27ReplayTool.CIRCLE -> when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { down = p; preview = p }
                MotionEvent.ACTION_MOVE -> preview = p
                MotionEvent.ACTION_UP -> {
                    val start = down
                    if (start != null && hypot((p.x - start.x).toDouble(), (p.y - start.y).toDouble()) > .012) {
                        marks += if (tool == V27ReplayTool.LINE) V27ReplayMark.Line(start, p)
                        else V27ReplayMark.Circle(start, p)
                    }
                    down = null
                    preview = null
                }
            }
            V27ReplayTool.ANGLE -> if (event.actionMasked == MotionEvent.ACTION_UP) {
                anglePoints += p
                if (anglePoints.size == 3) {
                    marks += V27ReplayMark.Angle(anglePoints[0], anglePoints[1], anglePoints[2])
                    anglePoints.clear()
                }
            }
            else -> Unit
        }
        return true
    }

    fun draw(canvas: Canvas, media: RectF, paint: Paint, viewWidth: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = max(2.4f, viewWidth * .0020f)
        paint.color = Color.argb(235, 255, 210, 72)
        marks.forEach { mark ->
            when (mark) {
                is V27ReplayMark.Line -> canvas.drawLine(sx(mark.a, media), sy(mark.a, media), sx(mark.b, media), sy(mark.b, media), paint)
                is V27ReplayMark.Circle -> {
                    val cx = sx(mark.center, media); val cy = sy(mark.center, media)
                    val ex = sx(mark.edge, media); val ey = sy(mark.edge, media)
                    canvas.drawCircle(cx, cy, hypot((ex - cx).toDouble(), (ey - cy).toDouble()).toFloat(), paint)
                }
                is V27ReplayMark.Angle -> drawAngle(canvas, media, paint, mark, viewWidth)
            }
        }
        val start = down
        val end = preview
        if (start != null && end != null) {
            paint.color = Color.argb(150, 255, 235, 140)
            if (tool == V27ReplayTool.LINE) canvas.drawLine(sx(start, media), sy(start, media), sx(end, media), sy(end, media), paint)
            if (tool == V27ReplayTool.CIRCLE) {
                val cx = sx(start, media); val cy = sy(start, media)
                val ex = sx(end, media); val ey = sy(end, media)
                canvas.drawCircle(cx, cy, hypot((ex - cx).toDouble(), (ey - cy).toDouble()).toFloat(), paint)
            }
        }
        if (tool == V27ReplayTool.ANGLE && anglePoints.isNotEmpty()) {
            paint.color = Color.argb(190, 255, 235, 140)
            val v = anglePoints.first()
            anglePoints.drop(1).forEach { point -> canvas.drawLine(sx(v, media), sy(v, media), sx(point, media), sy(point, media), paint) }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawAngle(canvas: Canvas, media: RectF, paint: Paint, mark: V27ReplayMark.Angle, viewWidth: Float) {
        val vx = sx(mark.vertex, media); val vy = sy(mark.vertex, media)
        canvas.drawLine(vx, vy, sx(mark.a, media), sy(mark.a, media), paint)
        canvas.drawLine(vx, vy, sx(mark.b, media), sy(mark.b, media), paint)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paint.textSize = max(10f, viewWidth * .0080f)
        paint.color = Color.WHITE
        canvas.drawText("%.1f°".format(V27ReplayGeometry.angleDeg(mark.vertex, mark.a, mark.b)), vx + viewWidth * .008f, vy - viewWidth * .006f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(235, 255, 210, 72)
    }

    private fun normalize(x: Float, y: Float, media: RectF) = V27NormPoint(
        ((x - media.left) / media.width()).coerceIn(0f, 1f),
        ((y - media.top) / media.height()).coerceIn(0f, 1f)
    )
    private fun sx(p: V27NormPoint, media: RectF) = media.left + p.x * media.width()
    private fun sy(p: V27NormPoint, media: RectF) = media.top + p.y * media.height()
}
