package com.puttvision.screen

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class GreenView(
    context: Context,
    private val engine: GameEngine
) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        keepScreenOn = true
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSkyAndCourse(canvas)
        drawPerspectiveGreen(canvas)
        drawTopHud(canvas)
        drawCornerCards(canvas)
        postInvalidateOnAnimation()
    }

    private fun drawSkyAndCourse(c: Canvas) {
        val horizon = height * 0.31f

        p.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            Color.rgb(18, 43, 58),
            Color.rgb(81, 126, 137),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), horizon, p)
        p.shader = null

        // Soft cloud bands for a photo-like screen-golf backdrop.
        p.color = Color.argb(38, 236, 241, 247)
        for (i in 0 until 8) {
            val cx = width * (0.08f + i * 0.14f)
            val cy = horizon * (0.20f + (i % 3) * 0.13f)
            c.drawOval(RectF(cx - width * .055f, cy - 15f, cx + width * .055f, cy + 15f), p)
        }

        // Tree line.
        p.color = Color.rgb(37, 91, 45)
        val base = horizon + height * .045f
        for (i in 0..24) {
            val x = width * i / 24f
            val r = width * (0.018f + (i % 4) * .003f)
            c.drawCircle(x, base - r * .6f, r, p)
            c.drawRect(x - r * .12f, base - r * .3f, x + r * .12f, base + r * .6f, p)
        }

        p.shader = LinearGradient(
            0f, horizon, 0f, height.toFloat(),
            Color.rgb(74, 144, 58),
            Color.rgb(34, 91, 38),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawPerspectiveGreen(c: Canvas) {
        val settings = engine.settings
        val horizonY = height * 0.37f
        val bottomY = height * 0.91f
        val centerX = width * 0.56f

        val green = Path().apply {
            moveTo(width * 0.26f, horizonY)
            lineTo(width * 0.84f, horizonY)
            lineTo(width * 0.96f, bottomY)
            lineTo(width * 0.07f, bottomY)
            close()
        }
        p.shader = LinearGradient(
            0f, horizonY, 0f, bottomY,
            Color.rgb(91, 170, 48),
            Color.rgb(70, 142, 37),
            Shader.TileMode.CLAMP
        )
        c.drawPath(green, p)
        p.shader = null

        val maxY = max(settings.holeDistanceM * 1.30, 3.0)
        fun sy(y: Double): Float {
            val t = (y / maxY).coerceIn(0.0, 1.0).toFloat()
            // Target is farther away, so visually higher.
            return bottomY - (bottomY - horizonY) * t
        }
        fun halfWidthAtY(yPix: Float): Float {
            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
            return width * (0.19f + 0.25f * t)
        }
        fun sx(x: Double, y: Double): Float {
            val yp = sy(y)
            val sideRange = max(1.2, settings.holeDistanceM * .20)
            return centerX + (x / sideRange).toFloat() * halfWidthAtY(yp)
        }

        // Perspective grid, converging toward the flag.
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, width * .0007f)
        p.color = Color.argb(92, 210, 244, 195)
        for (i in -5..5) {
            val frac = i / 5f
            val xBottom = centerX + frac * halfWidthAtY(bottomY)
            val xTop = centerX + frac * halfWidthAtY(horizonY)
            c.drawLine(xBottom, bottomY, xTop, horizonY, p)
        }
        for (i in 1..10) {
            val t = i / 10f
            val y = bottomY - (bottomY - horizonY) * t.pow(1.45f)
            val hw = halfWidthAtY(y)
            c.drawLine(centerX - hw, y, centerX + hw, y, p)
        }
        p.style = Paint.Style.FILL

        val holeY = settings.holeDistanceM
        val hx = sx(0.0, holeY)
        val hy = sy(holeY)

        // Suggested break path (the big white preview curve).
        val read = GreenReadAdvisor.read(settings)
        val aimX = read.aimOffsetCm / 100.0
        val path = Path().apply {
            moveTo(sx(0.0, 0.0), sy(0.0))
            val midY = holeY * .55
            val curveX = aimX * .55 + settings.sideSlopePct * .012
            cubicTo(
                sx(curveX * .20, holeY * .22), sy(holeY * .22),
                sx(curveX, midY), sy(midY),
                sx(aimX, holeY), sy(holeY)
            )
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(5f, width * .004f)
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb(215, 241, 255, 236)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT

        // Actual trail overlays the guide when the ball is moving.
        engine.state?.trail?.takeIf { it.size >= 2 }?.let { trail ->
            val actual = Path().apply {
                moveTo(sx(trail.first().first, trail.first().second), sy(trail.first().second))
                trail.drop(1).forEach { pt -> lineTo(sx(pt.first, pt.second), sy(pt.second)) }
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(4f, width * .003f)
            p.color = Color.argb(230, 255, 234, 126)
            c.drawPath(actual, p)
            p.style = Paint.Style.FILL
        }

        // Cup and flag.
        p.color = Color.argb(140, 0, 0, 0)
        c.drawOval(RectF(hx - 15f, hy - 4f, hx + 15f, hy + 5f), p)
        p.color = Color.WHITE
        c.drawRect(hx - 2f, hy - height * .115f, hx + 2f, hy, p)
        val flag = Path().apply {
            moveTo(hx + 2f, hy - height * .115f)
            lineTo(hx + width * .032f, hy - height * .098f)
            lineTo(hx + 2f, hy - height * .080f)
            close()
        }
        p.color = Color.rgb(245, 219, 62)
        c.drawPath(flag, p)

        // Ball shadow + ball.
        val state = engine.state
        val bx = if (state != null) sx(state.x, state.y) else sx(0.0, 0.0)
        val by = if (state != null) sy(state.y) else sy(0.0)
        p.color = Color.argb(72, 0, 0, 0)
        c.drawOval(RectF(bx - 22f, by + 10f, bx + 22f, by + 20f), p)
        p.color = Color.WHITE
        c.drawCircle(bx, by, max(9f, width * .007f), p)
    }

    private fun drawTopHud(c: Canvas) {
        val settings = engine.settings
        val top = height * .035f
        val h = height * .145f
        val start = width * .035f
        val totalW = width * .54f
        val gap = width * .006f
        val widths = floatArrayOf(.22f, .22f, .28f, .28f)
        val labels = arrayOf("HOLE", "GREEN SPEED", "좌우 경사", "오르막/내리막")
        val values = arrayOf(
            "${"%.1f".format(settings.holeDistanceM)} m",
            "${"%.1f".format(settings.stimpMeters)}",
            if (settings.sideSlopePct >= 0) "R ${"%.1f".format(abs(settings.sideSlopePct))}%" else "L ${"%.1f".format(abs(settings.sideSlopePct))}%",
            if (settings.longSlopePct >= 0) "▲ ${"%.1f".format(abs(settings.longSlopePct))}%" else "▼ ${"%.1f".format(abs(settings.longSlopePct))}%"
        )

        var x = start
        widths.forEachIndexed { i, frac ->
            val w = totalW * frac - gap
            rect.set(x, top, x + w, top + h)
            p.color = Color.argb(238, 13, 17, 22)
            c.drawRoundRect(rect, 16f, 16f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f
            p.color = Pv.line
            c.drawRoundRect(rect, 16f, 16f, p)
            p.style = Paint.Style.FILL
            p.color = Pv.textMid
            p.textSize = max(13f, width * .010f)
            p.typeface = Typeface.DEFAULT
            c.drawText(labels[i], x + 16f, top + h * .32f, p)
            p.color = if (i == 0) Pv.primary else Pv.textHi
            p.textSize = max(25f, width * .022f)
            p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(values[i], x + 16f, top + h * .73f, p)
            x += w + gap
        }

        p.color = Pv.textHi
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.RIGHT
        p.textSize = max(23f, width * .019f)
        c.drawText("PUTTVISION", width * .965f, top + h * .38f, p)
        p.color = Pv.primary
        c.drawText("SCREEN", width * .965f, top + h * .66f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    private fun drawCornerCards(c: Canvas) {
        val game = engine.gameModes.status
        val result = engine.lastResult

        // Bottom-left player/shot card.
        rect.set(width * .035f, height * .79f, width * .16f, height * .91f)
        p.color = Color.argb(232, 13, 17, 22)
        c.drawRoundRect(rect, 16f, 16f, p)
        p.color = Pv.textMid
        p.textSize = max(13f, width * .010f)
        c.drawText("PLAYER ${game.activePlayer}/${game.playerCount}", rect.left + 15f, rect.top + 25f, p)
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(20f, width * .015f)
        val shotNo = if (game.totalHoles > 0) "HOLE ${game.hole} · ${game.gameScore}" else "${game.mode.label} · ${game.gameScore}"
        c.drawText(shotNo, rect.left + 15f, rect.top + 54f, p)
        p.typeface = Typeface.DEFAULT

        if (result != null) {
            rect.set(width * .79f, height * .70f, width * .965f, height * .91f)
            p.color = Color.argb(238, 13, 17, 22)
            c.drawRoundRect(rect, 18f, 18f, p)
            p.color = Pv.textMid
            p.textSize = max(14f, width * .011f)
            c.drawText(if (result.holed) "RESULT" else "컵까지", rect.left + 18f, rect.top + 28f, p)
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = max(32f, width * .028f)
            val main = if (result.holed) "HOLE IN" else "${"%.2f".format(result.distanceToCupM)} m"
            c.drawText(main, rect.left + 18f, rect.top + 72f, p)
            p.textSize = max(17f, width * .013f)
            p.color = if (result.holed) Pv.amber else Pv.primary
            val side = when {
                result.finishX > 0.03 -> "RIGHT"
                result.finishX < -0.03 -> "LEFT"
                else -> "CENTER"
            }
            c.drawText(side, rect.left + 18f, rect.bottom - 18f, p)
            p.typeface = Typeface.DEFAULT
        }
    }
}
