package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * External TV / simulator renderer.
 * Rendering only: it reads GameEngine state but never mutates game/camera logic.
 */
class GreenView(
    context: Context,
    private val engine: GameEngine
) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = false
        isFocusable = false
        keepScreenOn = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCourse(canvas)
        drawGreen(canvas)
        drawBrandRail(canvas)
        drawShotTelemetry(canvas)
        drawResult(canvas)
        postInvalidateOnAnimation()
    }

    private fun drawCourse(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * .305f

        p.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(Color.rgb(12, 27, 36), Color.rgb(33, 66, 75), Color.rgb(73, 107, 108)),
            floatArrayOf(0f, .58f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null

        p.color = Color.argb(28, 240, 246, 247)
        repeat(6) { i ->
            val cx = w * (.10f + i * .17f)
            val cy = horizon * (.18f + (i % 2) * .14f)
            c.drawOval(RectF(cx - w * .085f, cy - h * .018f, cx + w * .085f, cy + h * .018f), p)
        }

        p.color = Color.rgb(22, 60, 39)
        val treeBase = horizon + h * .055f
        repeat(30) { i ->
            val x = w * i / 29f
            val r = w * (.012f + (i % 5) * .0022f)
            c.drawCircle(x, treeBase - r * .55f, r, p)
            c.drawRect(x - r * .08f, treeBase - r * .1f, x + r * .08f, treeBase + r * .65f, p)
        }

        p.shader = LinearGradient(
            0f, horizon, 0f, h,
            Color.rgb(43, 104, 49), Color.rgb(18, 59, 35), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null

        p.shader = LinearGradient(
            0f, h * .58f, 0f, h,
            Color.TRANSPARENT, Color.argb(126, 3, 7, 7), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * .58f, w, h, p)
        p.shader = null
    }

    private fun drawGreen(c: Canvas) {
        val settings = engine.settings
        val w = width.toFloat()
        val h = height.toFloat()
        val horizonY = h * .355f
        val bottomY = h * .955f
        val centerX = w * .54f

        val greenShape = Path().apply {
            moveTo(w * .265f, horizonY)
            lineTo(w * .815f, horizonY)
            lineTo(w * .965f, bottomY)
            lineTo(w * .075f, bottomY)
            close()
        }

        p.shader = LinearGradient(
            0f, horizonY, 0f, bottomY,
            intArrayOf(Color.rgb(78, 154, 61), Color.rgb(69, 140, 50), Color.rgb(50, 111, 43)),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(greenShape, p)
        p.shader = null

        val maxY = max(settings.holeDistanceM * 1.28, 3.0)
        fun sy(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            return bottomY - (bottomY - horizonY) * t
        }
        fun halfWidthAt(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return w * (.175f + .265f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = sy(y)
            val sideRange = max(1.15, settings.holeDistanceM * .20)
            return centerX + (x / sideRange).toFloat() * halfWidthAt(yp)
        }

        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT

        // Perspective putting grid. Spacing adapts to the selected distance so
        // short-putt screens stay precise while long putts do not become noisy.
        val gridStepM = when {
            maxY <= 6.0 -> 0.5
            maxY <= 12.0 -> 1.0
            else -> 2.0
        }
        val gridSideRange = max(1.15, settings.holeDistanceM * .20)
        var gridY = gridStepM
        while (gridY < maxY) {
            val major = kotlin.math.abs(gridY - kotlin.math.round(gridY)) < 0.02
            val yp = sy(gridY)
            val hw = halfWidthAt(yp)
            p.strokeWidth = if (major) max(1.4f, w * .0009f) else max(.7f, w * .00045f)
            p.color = if (major) Color.argb(84, 231, 255, 235) else Color.argb(38, 183, 238, 199)
            c.drawLine(centerX - hw, yp, centerX + hw, yp, p)

            if (major && gridY <= settings.holeDistanceM + 1.0) {
                p.style = Paint.Style.FILL
                p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                p.textSize = max(9f, w * .0067f)
                p.color = Color.argb(150, 235, 244, 238)
                c.drawText("${"%.0f".format(gridY)}m", centerX - hw + w * .008f, yp - h * .006f, p)
                p.style = Paint.Style.STROKE
            }
            gridY += gridStepM
        }

        for (lane in -5..5) {
            val frac = lane / 5.0
            val path = Path()
            for (step in 0..36) {
                val yM = maxY * step / 36.0
                val xM = gridSideRange * frac
                val px = sx(xM, yM)
                val py = sy(yM)
                if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            p.strokeWidth = if (lane == 0) max(1.5f, w * .0010f) else max(.7f, w * .00045f)
            p.color = if (lane == 0) Color.argb(96, 239, 255, 241) else Color.argb(38, 183, 238, 199)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL

        val read = GreenReadAdvisor.read(settings)
        val holeY = settings.holeDistanceM
        val aimX = read.aimOffsetCm / 100.0
        val guide = Path().apply {
            moveTo(sx(0.0, 0.0), sy(0.0))
            val midY = holeY * .56
            val curveX = aimX * .55 + settings.sideSlopePct * .012
            cubicTo(
                sx(curveX * .16, holeY * .18), sy(holeY * .18),
                sx(curveX, midY), sy(midY),
                sx(aimX, holeY), sy(holeY)
            )
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(3f, w * .0021f)
        p.color = Color.argb(188, 238, 247, 239)
        c.drawPath(guide, p)

        engine.state?.trail?.takeIf { it.size >= 2 }?.let { trail ->
            val actual = Path().apply {
                moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))
                trail.drop(1).forEach { point -> lineTo(sx(point.first, point.second), sy(point.second)) }
            }
            p.strokeWidth = max(4f, w * .0028f)
            p.color = Color.argb(238, 255, 210, 88)
            c.drawPath(actual, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL

        val hx = sx(0.0, holeY)
        val hy = sy(holeY)
        p.color = Color.argb(125, 0, 0, 0)
        c.drawOval(RectF(hx - w * .010f, hy - h * .0035f, hx + w * .010f, hy + h * .005f), p)
        p.color = Color.WHITE
        c.drawRect(hx - 1.7f, hy - h * .12f, hx + 1.7f, hy, p)
        val flag = Path().apply {
            moveTo(hx + 2f, hy - h * .12f)
            lineTo(hx + w * .035f, hy - h * .103f)
            lineTo(hx + 2f, hy - h * .082f)
            close()
        }
        p.color = Pv.amber
        c.drawPath(flag, p)

        val state = engine.state
        val bx = if (state != null) sx(state.x, state.y) else sx(0.0, 0.0)
        val by = if (state != null) sy(state.y) else sy(0.0)
        p.color = Color.argb(72, 0, 0, 0)
        c.drawOval(RectF(bx - w * .012f, by + h * .008f, bx + w * .012f, by + h * .017f), p)
        p.color = Color.WHITE
        c.drawCircle(bx, by, max(8f, w * .0065f), p)

        drawAimReadout(c, read)
    }

    private fun drawAimReadout(c: Canvas, read: GreenRead) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .695f
        val right = w * .968f
        val top = h * .185f
        val bottom = h * .315f

        rect.set(left, top, right, bottom)
        p.color = Color.argb(228, 6, 10, 13)
        c.drawRoundRect(rect, h * .018f, h * .018f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(145, 67, 91, 78)
        c.drawRoundRect(rect, h * .018f, h * .018f, p)
        p.style = Paint.Style.FILL

        val pad = w * .014f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0078f)
        p.color = Pv.primary
        c.drawText("추천 에임 · GREEN READ", left + pad, top + h * .029f, p)

        val main = if (read.aimSideLabel == "센터") {
            "센터 조준"
        } else {
            "${read.aimSideLabel}  ${"%.1f".format(read.cupCount)}컵"
        }
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = max(24f, w * .019f)
        p.color = Pv.textHi
        c.drawText(main, left + pad, top + h * .071f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0075f)
        p.color = Pv.textMid
        val head = if (read.aimSideLabel == "센터") "퍼터헤드 0.0개" else "퍼터헤드 ${"%.1f".format(read.putterHeadCount)}개"
        c.drawText("$head  ·  ${read.paceHint}", left + pad, top + h * .102f, p)

        p.typeface = Typeface.MONOSPACE
        p.textSize = max(8f, w * .0064f)
        p.color = Pv.textLo
        val breakText = "경사 ${if (read.effectiveSideSlopePct >= 0) "+" else ""}${"%.1f".format(read.effectiveSideSlopePct)}%  ·  종경사 ${if (read.effectiveLongSlopePct >= 0) "+" else ""}${"%.1f".format(read.effectiveLongSlopePct)}%"
        c.drawText(breakText, left + pad, bottom - h * .012f, p)
    }

    private fun drawBrandRail(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val game = engine.gameModes.status
        val settings = engine.settings

        val left = w * .032f
        val top = h * .032f
        val right = w * .968f
        val bottom = h * .155f
        rect.set(left, top, right, bottom)
        p.color = Color.argb(220, 7, 11, 14)
        c.drawRoundRect(rect, h * .020f, h * .020f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(140, 55, 68, 77)
        c.drawRoundRect(rect, h * .020f, h * .020f, p)
        p.style = Paint.Style.FILL

        val padX = w * .016f
        val labelY = top + h * .033f
        val valueY = top + h * .083f

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(19f, w * .015f)
        p.color = Pv.textHi
        c.drawText("PUTTVISION", left + padX, labelY + h * .013f, p)
        p.textSize = max(11f, w * .0085f)
        p.color = Pv.primary
        c.drawText("SCREEN", left + padX, valueY + h * .010f, p)

        fun metric(x: Float, label: String, value: String, accent: Int = Pv.textHi) {
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(10f, w * .0078f)
            p.color = Pv.textLo
            c.drawText(label, x, labelY, p)
            p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            p.textSize = max(23f, w * .018f)
            p.color = accent
            c.drawText(value, x, valueY, p)
        }

        metric(left + w * .185f, "TARGET", "${"%.1f".format(settings.holeDistanceM)} m", Pv.primary)
        metric(left + w * .335f, "GREEN", "${"%.1f".format(settings.stimpMeters)}")
        metric(left + w * .455f, "BREAK", if (settings.sideSlopePct >= 0) "R ${"%.1f".format(abs(settings.sideSlopePct))}%" else "L ${"%.1f".format(abs(settings.sideSlopePct))}%")
        metric(left + w * .595f, "GRADE", if (settings.longSlopePct >= 0) "+${"%.1f".format(settings.longSlopePct)}%" else "${"%.1f".format(settings.longSlopePct)}%")

        p.textAlign = Paint.Align.RIGHT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(11f, w * .0085f)
        p.color = Pv.textLo
        c.drawText("${game.mode.label.uppercase()}  ·  PLAYER ${game.activePlayer}/${game.playerCount}", right - padX, labelY, p)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(22f, w * .017f)
        p.color = Pv.textHi
        val rightValue = if (game.totalHoles > 0) "HOLE ${game.hole}  ·  ${game.gameScore}" else "${game.gameScore}"
        c.drawText(rightValue, right - padX, valueY, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    private fun drawShotTelemetry(c: Canvas) {
        val shot = engine.currentShot ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .032f
        val bottom = h * .955f
        val top = h * .825f
        val right = w * .61f

        rect.set(left, top, right, bottom)
        p.color = Color.argb(214, 7, 11, 14)
        c.drawRoundRect(rect, h * .018f, h * .018f, p)

        val pad = w * .014f
        val columns = listOf(
            Triple("BALL SPEED", shot.ballSpeedMps?.let { "%.2f".format(it) } ?: "--", "m/s"),
            Triple("HEAD SPEED", shot.headSpeedMps?.let { "%.2f".format(it) } ?: "--", "m/s"),
            Triple("FACE", shot.faceAngleDeg?.let { "%+.2f".format(it) } ?: "--", "°"),
            Triple("PATH", shot.pathAngleDeg?.let { "%+.2f".format(it) } ?: "--", "°")
        )
        val colW = (right - left - pad * 2f) / columns.size
        columns.forEachIndexed { index, item ->
            val x = left + pad + colW * index
            p.color = Pv.textLo
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(9f, w * .0072f)
            c.drawText(item.first, x, top + h * .036f, p)
            p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            p.textSize = max(23f, w * .018f)
            p.color = if (index == 0) Pv.primary else Pv.textHi
            c.drawText(item.second, x, top + h * .087f, p)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(9f, w * .0068f)
            p.color = Pv.textLo
            c.drawText(item.third, x + colW * .63f, top + h * .087f, p)
        }
    }

    private fun drawResult(c: Canvas) {
        val result = engine.lastResult ?: return
        val score = engine.strokeScore
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * .665f
        val right = w * .968f
        val top = h * .745f
        val bottom = h * .955f

        rect.set(left, top, right, bottom)
        p.color = Color.argb(232, 7, 11, 14)
        c.drawRoundRect(rect, h * .020f, h * .020f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = if (result.holed) Color.argb(170, 246, 190, 74) else Color.argb(135, 78, 209, 121)
        c.drawRoundRect(rect, h * .020f, h * .020f, p)
        p.style = Paint.Style.FILL

        val x = left + w * .018f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(11f, w * .0085f)
        p.color = if (result.holed) Pv.amber else Pv.primary
        c.drawText(if (result.holed) "HOLED" else "SHOT RESULT", x, top + h * .042f, p)

        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(38f, w * .030f)
        p.color = Pv.textHi
        val main = if (result.holed) "IN" else "${"%.2f".format(result.distanceToCupM)} m"
        c.drawText(main, x, top + h * .112f, p)

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(13f, w * .010f)
        val side = when {
            result.finishX > .03 -> "RIGHT"
            result.finishX < -.03 -> "LEFT"
            else -> "CENTER"
        }
        p.color = Pv.textMid
        c.drawText("$side  ·  ${score?.let { "SCORE ${it.total}" } ?: "ANALYZING"}", x, top + h * .158f, p)
    }
}
