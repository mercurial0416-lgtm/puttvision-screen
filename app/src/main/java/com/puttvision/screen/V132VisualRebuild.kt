package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * V132 is the first deliberately art-directed TV presentation pass.
 *
 * V131 remains responsible for Filament geometry / lighting / ball motion. V132 treats that
 * renderer as the scene plate, removes the legacy debug-style HUD and adds a coherent broadcast
 * grade, atmospheric depth, restrained grass detail and a new compact simulator HUD.
 *
 * This layer is intentionally presentation-only: no measurement, calibration, HFR or physics
 * authority lives here.
 */
object V132VisualRebuildFactory {
    fun create(context: Context, game: GameEngine): View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        val base = V131FilamentScreenGolfPresentationFactory.create(context, game)
        addView(base, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        suppressLegacyHud(base)
        base.post { suppressLegacyHud(base) }
        addView(V132CourseArtOverlay(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V132PremiumHudView(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun suppressLegacyHud(root: View) {
        val name = root.javaClass.simpleName
        if (name.contains("Hud", ignoreCase = true) || name.contains("GradeOverlay", ignoreCase = true)) {
            root.visibility = View.GONE
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) suppressLegacyHud(root.getChildAt(i))
        }
    }
}

private class V132CourseArtOverlay(
    context: Context,
    private val game: GameEngine
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val greenPath = Path()
    private val fringePath = Path()
    private val trees = ArrayList<TreeSeed>()
    private val grass = ArrayList<GrassSeed>()
    private val rand = Random(132)

    private data class TreeSeed(val x: Float, val y: Float, val scale: Float, val tone: Float)
    private data class GrassSeed(val x: Float, val y: Float, val len: Float, val alpha: Int)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trees.clear()
        grass.clear()
        repeat(74) {
            trees += TreeSeed(
                x = rand.nextFloat(),
                y = rand.nextFloat(),
                scale = .62f + rand.nextFloat() * .75f,
                tone = rand.nextFloat()
            )
        }
        repeat(260) {
            grass += GrassSeed(
                x = rand.nextFloat(),
                y = rand.nextFloat(),
                len = .45f + rand.nextFloat() * .9f,
                alpha = 8 + rand.nextInt(15)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * .325f

        drawSkyGrade(canvas, w, h, horizon)
        drawDistantLandscape(canvas, w, h, horizon)
        buildGreenPaths(w, h)
        drawGreenGrade(canvas, w, h)
        drawBunkers(canvas, w, h)
        drawAtmosphere(canvas, w, h, horizon)
        drawVignette(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawSkyGrade(canvas: Canvas, w: Float, h: Float, horizon: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, horizon * 1.35f,
            intArrayOf(
                Color.argb(64, 63, 93, 110),
                Color.argb(26, 141, 166, 170),
                Color.argb(5, 228, 224, 201),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .46f, .83f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, horizon * 1.38f, paint)

        paint.shader = RadialGradient(
            w * .78f, h * .13f, max(w, h) * .31f,
            intArrayOf(Color.argb(45, 255, 238, 187), Color.argb(13, 255, 238, 187), Color.TRANSPARENT),
            floatArrayOf(0f, .43f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * .78f, h * .13f, max(w, h) * .31f, paint)
        paint.shader = null
    }

    private fun drawDistantLandscape(canvas: Canvas, w: Float, h: Float, horizon: Float) {
        paint.style = Paint.Style.FILL

        val ridge = Path().apply {
            moveTo(0f, horizon + h * .008f)
            var x = 0f
            while (x <= w + 40f) {
                val y = horizon - h * (.018f + .012f * sin(x / max(1f, w) * 14f))
                lineTo(x, y)
                x += max(18f, w / 44f)
            }
            lineTo(w, horizon + h * .07f)
            lineTo(0f, horizon + h * .07f)
            close()
        }
        paint.color = Color.argb(86, 21, 48, 35)
        canvas.drawPath(ridge, paint)

        trees.forEachIndexed { index, t ->
            val x = t.x * w
            if (x in w * .39f..w * .61f && index % 3 != 0) return@forEachIndexed
            val baseY = horizon + (t.y - .5f) * h * .032f
            val s = h * .026f * t.scale
            val trunkW = max(1.2f, s * .11f)
            paint.color = Color.argb(105, 24, 38, 29)
            canvas.drawRect(x - trunkW, baseY - s * .18f, x + trunkW, baseY + s * .55f, paint)
            val green = if (t.tone > .52f) Color.rgb(28, 58, 39) else Color.rgb(34, 66, 43)
            paint.color = Color.argb(125, Color.red(green), Color.green(green), Color.blue(green))
            canvas.drawCircle(x, baseY - s * .35f, s * .52f, paint)
            canvas.drawCircle(x - s * .32f, baseY - s * .19f, s * .38f, paint)
            canvas.drawCircle(x + s * .31f, baseY - s * .17f, s * .39f, paint)
        }
    }

    private fun buildGreenPaths(w: Float, h: Float) {
        val nearY = h * .79f
        val farY = h * .392f
        greenPath.reset()
        greenPath.moveTo(w * .105f, nearY)
        greenPath.cubicTo(w * .19f, h * .66f, w * .32f, h * .46f, w * .402f, farY)
        greenPath.cubicTo(w * .455f, h * .378f, w * .545f, h * .378f, w * .598f, farY)
        greenPath.cubicTo(w * .69f, h * .47f, w * .82f, h * .66f, w * .905f, nearY)
        greenPath.close()

        fringePath.reset()
        fringePath.moveTo(w * .086f, nearY + h * .012f)
        fringePath.cubicTo(w * .176f, h * .65f, w * .303f, h * .445f, w * .388f, farY - h * .012f)
        fringePath.cubicTo(w * .45f, h * .365f, w * .55f, h * .365f, w * .612f, farY - h * .012f)
        fringePath.cubicTo(w * .704f, h * .445f, w * .834f, h * .65f, w * .924f, nearY + h * .012f)
    }

    private fun drawGreenGrade(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(6f, h * .015f)
        paint.color = Color.argb(42, 18, 48, 24)
        canvas.drawPath(fringePath, paint)

        canvas.save()
        canvas.clipPath(greenPath)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, h * .39f, 0f, h * .82f,
            intArrayOf(
                Color.argb(58, 31, 78, 36),
                Color.argb(47, 19, 64, 29),
                Color.argb(65, 10, 43, 22)
            ),
            floatArrayOf(0f, .52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * .36f, w, h * .84f, paint)
        paint.shader = null

        // Fine, perspective-compressed turf detail. It deliberately kills the old chunky stripes.
        grass.forEach { g ->
            val y = h * (.40f + g.y * .40f)
            val p = ((y / h - .39f) / .41f).coerceIn(0f, 1f)
            val left = w * (.40f - .30f * p)
            val right = w * (.60f + .30f * p)
            val x = left + (right - left) * g.x
            val len = max(.7f, h * .0025f * g.len * (.35f + p))
            paint.color = Color.argb(g.alpha, 196, 220, 171)
            paint.strokeWidth = max(.55f, h * .0007f)
            canvas.drawLine(x, y, x + len * .22f, y - len, paint)
        }

        // Subtle mowing direction, not alternating neon slabs.
        paint.strokeWidth = max(1f, h * .0014f)
        for (i in 0..16) {
            val t = i / 16f
            val y = h * (.405f + .378f * t * t)
            val p = ((y / h - .39f) / .41f).coerceIn(0f, 1f)
            val left = w * (.40f - .29f * p)
            val right = w * (.60f + .29f * p)
            paint.color = Color.argb(if (i % 2 == 0) 10 else 6, 235, 244, 211)
            canvas.drawLine(left, y, right, y, paint)
        }
        canvas.restore()
    }

    private fun drawBunkers(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        val bunkerY = h * .49f
        val bw = w * .075f
        val bh = h * .026f
        listOf(w * .305f to bunkerY, w * .70f to bunkerY + h * .025f).forEachIndexed { idx, (cx, cy) ->
            paint.shader = RadialGradient(
                cx, cy, bw,
                intArrayOf(Color.argb(86, 228, 216, 177), Color.argb(54, 173, 160, 119), Color.TRANSPARENT),
                floatArrayOf(0f, .7f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(RectF(cx - bw, cy - bh, cx + bw, cy + bh * (1.05f + idx * .08f)), paint)
        }
        paint.shader = null
    }

    private fun drawAtmosphere(canvas: Canvas, w: Float, h: Float, horizon: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, horizon - h * .035f, 0f, horizon + h * .10f,
            intArrayOf(Color.TRANSPARENT, Color.argb(27, 220, 224, 201), Color.TRANSPARENT),
            floatArrayOf(0f, .52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, horizon - h * .04f, w, horizon + h * .11f, paint)
        paint.shader = null
    }

    private fun drawVignette(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            w * .50f, h * .48f, max(w, h) * .78f,
            intArrayOf(Color.TRANSPARENT, Color.argb(5, 0, 0, 0), Color.argb(54, 0, 0, 0)),
            floatArrayOf(0f, .72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
}

private class V132PremiumHudView(
    context: Context,
    private val game: GameEngine
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val distance = settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0
        val side = settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0
        val long = settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0
        val running = game.state?.running == true || TvInstantRollRuntime.isAnimating()

        drawTopLeft(canvas, 28f * s, 24f * s, 330f * s, 78f * s, distance, running, s)
        drawTelemetry(canvas, w - 270f * s, 24f * s, 242f * s, 150f * s, side, long, distance, s)
        drawBrand(canvas, w - 28f * s, h - 22f * s, s)
        postInvalidateOnAnimation()
    }

    private fun drawTopLeft(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        distance: Double,
        running: Boolean,
        s: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(202, 9, 16, 15)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 12f * s, 12f * s, paint)
        paint.color = if (running) Color.rgb(223, 184, 67) else Color.rgb(100, 206, 150)
        canvas.drawRoundRect(RectF(x, y, x + 4f * s, y + height), 3f * s, 3f * s, paint)

        text(canvas, "PUTTVISION  /  PRACTICE", x + 20f * s, y + 25f * s, 13f * s, Color.argb(210, 235, 240, 235), medium)
        text(canvas, if (running) "BALL IN MOTION" else "READY TO PUTT", x + 20f * s, y + 52f * s, 11f * s, Color.argb(145, 218, 226, 219), regular)
        textRight(canvas, String.format(Locale.US, "%.1f m", distance), x + width - 18f * s, y + 44f * s, 22f * s, Color.WHITE, medium)
    }

    private fun drawTelemetry(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        side: Double,
        long: Double,
        distance: Double,
        s: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(184, 8, 14, 13)
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 13f * s, 13f * s, paint)
        paint.color = Color.argb(30, 255, 255, 255)
        canvas.drawRoundRect(RectF(x, y, x + width, y + 1f * s), 1f * s, 1f * s, paint)

        text(canvas, "GREEN READ", x + 16f * s, y + 23f * s, 10f * s, Color.argb(150, 207, 219, 211), medium)
        row(canvas, x + 16f * s, y + 52f * s, width - 32f * s, "TARGET", String.format(Locale.US, "%.1f m", distance), s)
        row(canvas, x + 16f * s, y + 86f * s, width - 32f * s, "BREAK", slopeLabel(side, true), s)
        row(canvas, x + 16f * s, y + 120f * s, width - 32f * s, "GRADE", slopeLabel(long, false), s)
    }

    private fun row(canvas: Canvas, x: Float, y: Float, width: Float, label: String, value: String, s: Float) {
        text(canvas, label, x, y, 9.5f * s, Color.argb(128, 207, 219, 211), regular)
        textRight(canvas, value, x + width, y, 13f * s, Color.argb(232, 244, 247, 243), medium)
    }

    private fun slopeLabel(value: Double, lateral: Boolean): String {
        if (abs(value) < .05) return "FLAT"
        val suffix = if (lateral) {
            if (value > 0) "R" else "L"
        } else {
            if (value > 0) "UP" else "DOWN"
        }
        return String.format(Locale.US, "%.1f%% %s", abs(value), suffix)
    }

    private fun drawBrand(canvas: Canvas, right: Float, baseline: Float, s: Float) {
        textRight(canvas, "PUTTVISION · V132", right, baseline, 9f * s, Color.argb(92, 230, 237, 231), medium)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = typeface
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(value, x, y, paint)
    }

    private fun textRight(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = typeface
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, x, y, paint)
    }
}
