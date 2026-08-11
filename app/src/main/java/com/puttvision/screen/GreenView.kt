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
    private var lastNs = 0L
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.nanoTime()

        if (lastNs == 0L) {
            lastNs = now
        }

        // Physics is ticked by MainActivity so the simulation keeps running
        // even when no external TV Presentation is connected.
        lastNs = now

        drawBackground(canvas)
        drawGreen(canvas, now)
        drawMainHud(canvas)
        drawScoreCard(canvas)
        drawHeatmap(canvas)

        postInvalidateOnAnimation()
    }

    private fun drawBackground(c: Canvas) {
        val horizon = height * 0.19f

        p.shader = LinearGradient(
            0f,
            0f,
            0f,
            horizon,
            Color.rgb(19, 41, 38),
            Color.rgb(7, 19, 15),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, width.toFloat(), horizon, p)
        p.shader = null

        // Distant course silhouette for a more screen-golf / stadium feel.
        val hill = Path().apply {
            moveTo(0f, horizon)
            lineTo(width * 0.00f, horizon * 0.86f)
            lineTo(width * 0.13f, horizon * 0.56f)
            lineTo(width * 0.27f, horizon * 0.78f)
            lineTo(width * 0.42f, horizon * 0.45f)
            lineTo(width * 0.58f, horizon * 0.72f)
            lineTo(width * 0.76f, horizon * 0.50f)
            lineTo(width.toFloat(), horizon * 0.80f)
            lineTo(width.toFloat(), horizon)
            close()
        }
        p.color = Color.rgb(11, 48, 31)
        c.drawPath(hill, p)

        p.shader = LinearGradient(
            0f,
            horizon,
            0f,
            height.toFloat(),
            Color.rgb(8, 50, 31),
            Color.rgb(2, 12, 8),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, horizon, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawGreen(
        c: Canvas,
        nowNs: Long
    ) {
        val margin = width * 0.035f
        val top = height * 0.145f
        val bottom = height * 0.965f
        val greenLeft = margin
        val greenRight = width - margin

        rect.set(
            greenLeft,
            top,
            greenRight,
            bottom
        )

        p.shader = LinearGradient(
            greenLeft,
            top,
            greenRight,
            bottom,
            Color.rgb(43, 151, 82),
            Color.rgb(10, 78, 45),
            Shader.TileMode.CLAMP
        )

        c.drawRoundRect(
            rect,
            34f,
            34f,
            p
        )

        p.shader = null

        // Mowing stripes.
        p.color = Color.argb(
            19,
            255,
            255,
            255
        )

        val stripeH =
            (bottom - top) / 16f

        for (i in 0 until 16 step 2) {
            c.drawRect(
                greenLeft,
                top + i * stripeH,
                greenRight,
                top + (i + 1) * stripeH,
                p
            )
        }

        val settings = engine.settings
        val maxY =
            max(
                settings.holeDistanceM * 1.28,
                3.0
            )

        fun sx(x: Double): Float {
            val range =
                max(
                    1.2,
                    settings.holeDistanceM * 0.19
                )

            return (
                width / 2.0 +
                    (x / range) *
                    (greenRight - greenLeft) /
                    2.0
                ).toFloat()
        }

        fun sy(y: Double): Float {
            val t =
                (y / maxY)
                    .coerceIn(
                        -0.08,
                        1.08
                    )

            return (
                bottom -
                    t *
                    (bottom - top)
                ).toFloat()
        }

        drawContourGrid(
            c,
            greenLeft,
            greenRight,
            top,
            bottom,
            nowNs
        )

        // Physical center line.
        p.color =
            Color.argb(
                90,
                255,
                255,
                255
            )

        p.strokeWidth = 3f

        c.drawLine(
            sx(0.0),
            sy(0.0),
            sx(0.0),
            sy(settings.holeDistanceM),
            p
        )

        // Green read / aim advisor.
        val read =
            GreenReadAdvisor.read(settings)

        val aimX =
            read.aimOffsetCm / 100.0

        p.style =
            Paint.Style.STROKE

        p.strokeWidth = 4f
        p.pathEffect =
            DashPathEffect(
                floatArrayOf(
                    18f,
                    13f
                ),
                0f
            )

        p.color =
            Color.argb(
                210,
                255,
                224,
                92
            )

        c.drawLine(
            sx(0.0),
            sy(0.0),
            sx(aimX),
            sy(settings.holeDistanceM),
            p
        )

        p.pathEffect = null
        p.style = Paint.Style.FILL

        // Hole + flag.
        val hx = sx(0.0)
        val hy =
            sy(
                settings.holeDistanceM
            )

        p.color =
            Color.rgb(
                7,
                18,
                12
            )

        c.drawOval(
            RectF(
                hx - 13f,
                hy - 5f,
                hx + 13f,
                hy + 5f
            ),
            p
        )

        p.color = Color.WHITE

        c.drawRect(
            hx - 2f,
            hy - 105f,
            hx + 2f,
            hy,
            p
        )

        val flag =
            Path().apply {
                moveTo(
                    hx + 2f,
                    hy - 105f
                )

                lineTo(
                    hx + 49f,
                    hy - 88f
                )

                lineTo(
                    hx + 2f,
                    hy - 72f
                )

                close()
            }

        p.color =
            Color.rgb(
                235,
                66,
                70
            )

        c.drawPath(
            flag,
            p
        )

        // Ball trail and ball.
        engine.state?.let { state ->
            if (state.trail.size >= 2) {
                val trail =
                    Path()

                trail.moveTo(
                    sx(
                        state.trail
                            .first()
                            .first
                    ),
                    sy(
                        state.trail
                            .first()
                            .second
                    )
                )

                state.trail
                    .drop(1)
                    .forEach {
                        trail.lineTo(
                            sx(it.first),
                            sy(it.second)
                        )
                    }

                p.style =
                    Paint.Style.STROKE

                p.strokeWidth = 7f

                p.color =
                    Color.argb(
                        220,
                        231,
                        255,
                        240
                    )

                c.drawPath(
                    trail,
                    p
                )

                p.style =
                    Paint.Style.FILL
            }

            val bx = sx(state.x)
            val by = sy(state.y)

            p.color =
                Color.argb(
                    85,
                    0,
                    0,
                    0
                )

            c.drawOval(
                RectF(
                    bx - 15f,
                    by + 8f,
                    bx + 15f,
                    by + 18f
                ),
                p
            )

            p.color = Color.WHITE

            c.drawCircle(
                bx,
                by,
                12f,
                p
            )
        } ?: run {
            p.color = Color.WHITE

            c.drawCircle(
                sx(0.0),
                sy(0.0),
                12f,
                p
            )
        }

        // Green reading label.
        p.color =
            Color.argb(
                220,
                255,
                255,
                255
            )

        p.textSize =
            max(
                23f,
                width * 0.016f
            )

        val breakDirection =
            when {
                read.estimatedBreakCm > 2.0 ->
                    "우 ${"%.0f".format(abs(read.estimatedBreakCm))}cm"

                read.estimatedBreakCm < -2.0 ->
                    "좌 ${"%.0f".format(abs(read.estimatedBreakCm))}cm"

                else ->
                    "거의 직선"
            }

        c.drawText(
            "GREEN READ  $breakDirection  ·  ${read.paceHint}",
            greenLeft + 30f,
            top + 45f,
            p
        )
    }

    private fun drawContourGrid(
        c: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        nowNs: Long
    ) {
        val settings = engine.settings
        val side = settings.sideSlopePct
        val long = settings.longSlopePct

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.color =
            Color.argb(
                70,
                224,
                255,
                235
            )

        val rows = 9
        val cols = 7

        for (row in 1 until rows) {
            val y =
                top +
                    (bottom - top) *
                    row /
                    rows

            c.drawLine(
                left + 12f,
                y,
                right - 12f,
                y,
                p
            )
        }

        for (col in 1 until cols) {
            val x =
                left +
                    (right - left) *
                    col /
                    cols

            c.drawLine(
                x,
                top + 12f,
                x,
                bottom - 12f,
                p
            )
        }

        p.style = Paint.Style.FILL

        // Animated flowing dots like a screen-golf green reader.
        val phase =
            (
                nowNs /
                    1_000_000_000.0 *
                    0.65
                ) % 1.0

        val slopeMagnitude =
            hypot(
                side,
                long
            )

        if (slopeMagnitude < 0.08) {
            return
        }

        val vx =
            side /
                slopeMagnitude

        // +longSlope = downhill toward target, which is visually upward.
        val vy =
            -long /
                slopeMagnitude

        p.color =
            Color.argb(
                205,
                169,
                255,
                198
            )

        for (row in 1 until rows) {
            for (col in 1 until cols) {
                val bx =
                    left +
                        (right - left) *
                        col /
                        cols

                val by =
                    top +
                        (bottom - top) *
                        row /
                        rows

                val travel =
                    (phase * 34.0)
                        .toFloat()

                val x =
                    bx +
                        vx.toFloat() *
                        travel

                val y =
                    by +
                        vy.toFloat() *
                        travel

                c.drawCircle(
                    x,
                    y,
                    4.5f,
                    p
                )
            }
        }
    }

    private fun drawMainHud(
        c: Canvas
    ) {
        val settings = engine.settings
        val shot = engine.currentShot
        val result = engine.lastResult
        val game = engine.gameModes.status

        val hudW = width * 0.35f

        rect.set(
            width - hudW - 24f,
            24f,
            width - 24f,
            height * 0.34f
        )

        p.color =
            Color.argb(
                220,
                3,
                15,
                10
            )

        c.drawRoundRect(
            rect,
            24f,
            24f,
            p
        )

        var y =
            rect.top + 43f

        val x =
            rect.left + 26f

        p.color =
            Color.rgb(
                137,
                247,
                176
            )

        p.textSize =
            max(
                27f,
                width * 0.022f
            )

        p.typeface =
            Typeface.DEFAULT_BOLD

        c.drawText(
            "PUTTVISION SCREEN",
            x,
            y,
            p
        )

        p.typeface =
            Typeface.DEFAULT

        y += 38f

        p.color = Color.WHITE
        p.textSize =
            max(
                20f,
                width * 0.0155f
            )

        val gameText =
            when {
                game.totalHoles > 0 ->
                    "${game.mode.label}  ${game.hole}/${game.totalHoles}H"

                else ->
                    game.mode.label
            }

        c.drawText(
            "$gameText   SCORE ${game.gameScore}",
            x,
            y,
            p
        )

        y += 31f

        c.drawText(
            "거리 ${"%.1f".format(settings.holeDistanceM)}m   Green ${"%.1f".format(settings.stimpMeters)}m",
            x,
            y,
            p
        )

        y += 31f

        c.drawText(
            "경사 LR ${"%+.1f".format(settings.sideSlopePct)}%   FB ${"%+.1f".format(settings.longSlopePct)}%",
            x,
            y,
            p
        )

        if (shot != null) {
            y += 36f

            c.drawText(
                "BALL ${"%.2f".format(shot.ballSpeedMps)}m/s   LAUNCH ${"%+.2f".format(shot.launchAngleDeg)}°",
                x,
                y,
                p
            )

            y += 30f

            c.drawText(
                "HEAD ${shot.headSpeedMps?.let { "%.2f".format(it) } ?: "--"}m/s   FACE ${shot.faceAngleDeg?.let { "%+.2f".format(it) } ?: "--"}°",
                x,
                y,
                p
            )

            y += 30f

            c.drawText(
                "PATH ${shot.pathAngleDeg?.let { "%+.2f".format(it) } ?: "--"}°   F2P ${shot.faceToPathDeg?.let { "%+.2f".format(it) } ?: "--"}°",
                x,
                y,
                p
            )

            y += 30f

            c.drawText(
                "TEMPO ${shot.tempoRatio?.let { "%.2f:1".format(it) } ?: "--"}   BS ${shot.backswingLengthCm?.let { "%.1fcm".format(it) } ?: "--"}",
                x,
                y,
                p
            )
        }

        result?.let {
            y += 40f

            p.typeface =
                Typeface.DEFAULT_BOLD

            p.textSize =
                max(
                    29f,
                    width * 0.022f
                )

            p.color =
                if (it.holed) {
                    Color.rgb(
                        255,
                        223,
                        88
                    )
                } else {
                    Color.WHITE
                }

            val text =
                if (it.holed) {
                    "HOLE IN"
                } else {
                    "컵까지 ${"%.0f".format(it.distanceToCupM * 100)}cm"
                }

            c.drawText(
                text,
                x,
                y,
                p
            )

            p.typeface =
                Typeface.DEFAULT
        }

        engine.matStimpEstimateM?.let {
            p.color =
                Color.rgb(
                    175,
                    220,
                    255
                )

            p.textSize =
                max(
                    17f,
                    width * 0.0135f
                )

            c.drawText(
                "MAT AUTO-CAL ≈ ${"%.2f".format(it)}m",
                x,
                rect.bottom - 18f,
                p
            )
        }
    }

    private fun drawScoreCard(
        c: Canvas
    ) {
        val score =
            engine.strokeScore ?: return

        val coach =
            engine.coachFeedback

        val left = 24f
        val top = 24f
        val cardW =
            width * 0.31f
        val bottom =
            height * 0.31f

        rect.set(
            left,
            top,
            left + cardW,
            bottom
        )

        p.color =
            Color.argb(
                220,
                3,
                15,
                10
            )

        c.drawRoundRect(
            rect,
            24f,
            24f,
            p
        )

        p.color =
            Color.rgb(
                255,
                217,
                78
            )

        p.typeface =
            Typeface.DEFAULT_BOLD

        p.textSize =
            max(
                39f,
                width * 0.032f
            )

        c.drawText(
            "${score.total}",
            rect.left + 24f,
            rect.top + 58f,
            p
        )

        p.textSize =
            max(
                18f,
                width * 0.014f
            )

        c.drawText(
            "PERFECT STROKE",
            rect.left + 98f,
            rect.top + 51f,
            p
        )

        p.typeface =
            Typeface.DEFAULT

        var y =
            rect.top + 95f

        p.color = Color.WHITE

        val lines =
            listOf(
                "Face ${score.face}   Path ${score.path}   Tempo ${score.tempo}",
                "Impact ${score.impact}   Distance ${score.distance}   Repeat ${score.consistency}"
            )

        for (line in lines) {
            c.drawText(
                line,
                rect.left + 24f,
                y,
                p
            )

            y += 29f
        }

        coach?.let {
            y += 12f

            p.color =
                Color.rgb(
                    137,
                    247,
                    176
                )

            p.typeface =
                Typeface.DEFAULT_BOLD

            c.drawText(
                it.headline,
                rect.left + 24f,
                y,
                p
            )

            p.typeface =
                Typeface.DEFAULT

            y += 27f

            p.color =
                Color.rgb(
                    220,
                    228,
                    223
                )

            val detail =
                if (it.detail.length > 37) {
                    it.detail.take(37) + "…"
                } else {
                    it.detail
                }

            c.drawText(
                detail,
                rect.left + 24f,
                y,
                p
            )
        }
    }

    private fun drawHeatmap(
        c: Canvas
    ) {
        val records =
            engine.recentRecords
                .filter {
                    it.result != null &&
                        it.targetDistanceM > 0.0
                }
                .takeLast(30)

        if (records.isEmpty()) {
            return
        }

        val panelW =
            width * 0.25f

        val panelH =
            height * 0.29f

        val left = 24f
        val bottom =
            height - 24f

        val top =
            bottom - panelH

        rect.set(
            left,
            top,
            left + panelW,
            bottom
        )

        p.color =
            Color.argb(
                208,
                3,
                15,
                10
            )

        c.drawRoundRect(
            rect,
            24f,
            24f,
            p
        )

        p.color = Color.WHITE
        p.textSize =
            max(
                18f,
                width * 0.014f
            )

        p.typeface =
            Typeface.DEFAULT_BOLD

        c.drawText(
            "SHOT DISPERSION · ${records.size}",
            rect.left + 22f,
            rect.top + 34f,
            p
        )

        p.typeface =
            Typeface.DEFAULT

        val cx =
            rect.centerX()

        val cy =
            rect.top +
                rect.height() * 0.58f

        val plotW =
            rect.width() * 0.78f

        val plotH =
            rect.height() * 0.55f

        p.color =
            Color.argb(
                120,
                255,
                255,
                255
            )

        p.strokeWidth = 2f

        c.drawLine(
            cx - plotW / 2f,
            cy,
            cx + plotW / 2f,
            cy,
            p
        )

        c.drawLine(
            cx,
            cy - plotH / 2f,
            cx,
            cy + plotH / 2f,
            p
        )

        // 50cm radius circle.
        p.style =
            Paint.Style.STROKE

        p.color =
            Color.argb(
                90,
                255,
                255,
                255
            )

        c.drawCircle(
            cx,
            cy,
            min(plotW, plotH) * 0.25f,
            p
        )

        p.style =
            Paint.Style.FILL

        for (record in records) {
            val r =
                record.result ?: continue

            val xError =
                r.finishX

            val yError =
                r.finishY -
                    record.targetDistanceM

            // Plot window: +/- 1m.
            val px =
                cx +
                    (
                        xError
                            .coerceIn(-1.0, 1.0) /
                            1.0 *
                            plotW /
                            2.0
                        ).toFloat()

            val py =
                cy -
                    (
                        yError
                            .coerceIn(-1.0, 1.0) /
                            1.0 *
                            plotH /
                            2.0
                        ).toFloat()

            p.color =
                if (r.holed) {
                    Color.rgb(
                        255,
                        220,
                        78
                    )
                } else {
                    Color.argb(
                        220,
                        144,
                        233,
                        172
                    )
                }

            c.drawCircle(
                px,
                py,
                if (r.holed) 7f else 5f,
                p
            )
        }

        val made =
            records.count {
                it.result?.holed == true
            }

        p.color =
            Color.rgb(
                215,
                226,
                219
            )

        p.textSize =
            max(
                16f,
                width * 0.0125f
            )

        c.drawText(
            "IN ${made}/${records.size}   GOLD = HOLED",
            rect.left + 22f,
            rect.bottom - 18f,
            p
        )
    }
}
