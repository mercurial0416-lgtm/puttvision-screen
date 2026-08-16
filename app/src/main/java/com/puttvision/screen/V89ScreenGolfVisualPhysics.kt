package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class V89VisualPhysicsPlan(
    val speedMps: Double,
    val spinDegrees: Float,
    val blurStrength: Float,
    val blurSamples: Int,
    val shadowStretch: Float,
    val highlightStrength: Float,
    val focusStrength: Float,
    val cometLengthM: Double,
    val dimpleAlpha: Int
)

object V89VisualPhysicsPlanner {
    const val BALL_RADIUS_M = 0.02135
    private const val BALL_CIRCUMFERENCE_M = 2.0 * PI * BALL_RADIUS_M

    fun plan(speedMps: Double, travelledM: Double): V89VisualPhysicsPlan {
        val speed = speedMps.takeIf { it.isFinite() }?.coerceIn(0.0, 5.0) ?: 0.0
        val travel = travelledM.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val spin = ((travel / BALL_CIRCUMFERENCE_M) * 360.0 % 360.0).toFloat()
        val normalized = (speed / 2.2).coerceIn(0.0, 1.0).toFloat()
        return V89VisualPhysicsPlan(
            speedMps = speed,
            spinDegrees = spin,
            blurStrength = normalized,
            blurSamples = when {
                speed < .16 -> 0
                speed < .65 -> 2
                speed < 1.35 -> 3
                else -> 4
            },
            shadowStretch = 1f + normalized * .34f,
            highlightStrength = .55f + normalized * .35f,
            focusStrength = (normalized * .62f).coerceIn(0f, .62f),
            cometLengthM = (.025 + speed * .055).coerceIn(.025, .24),
            dimpleAlpha = (52 + 58 * (1f - normalized * .35f)).roundToInt().coerceIn(42, 110)
        )
    }
}

/** Visual-only screen-golf physics. Never mutates measured metrics or GreenPhysics state. */
class V89ScreenGolfVisualPhysicsView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballRect = RectF()
    private val shadowRect = RectF()
    private var trackedState: SimState? = null
    private var lastWorldX = Double.NaN
    private var lastWorldY = Double.NaN
    private var travelledM = 0.0
    private var seenResult: SimResult? = null
    private var resultSeenAtMs = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val settings = engine.settings
        val state = engine.state
        updateTravel(state)
        updateResultPulse()

        val start = V26BallStartRuntime.current(settings)
        val animated = TvInstantRollRuntime.displayPosition(state)
        val x = animated?.first ?: state?.x ?: start.first
        val y = animated?.second ?: state?.y ?: start.second
        if (!x.isFinite() || !y.isFinite()) return

        val groundZ = GreenTerrain.effectiveHeightAt(settings, x, y)
        if (!groundZ.isFinite()) return
        val center = V25FlagProjectionRuntime.project(x, y, groundZ + V89VisualPhysicsPlanner.BALL_RADIUS_M)
            ?: run { schedule(state); return }
        val ground = V25FlagProjectionRuntime.project(x, y, groundZ + .001)
            ?: run { schedule(state); return }
        val side = V25FlagProjectionRuntime.project(
            x + V89VisualPhysicsPlanner.BALL_RADIUS_M,
            y,
            groundZ + V89VisualPhysicsPlanner.BALL_RADIUS_M
        )
        val projectedRadius = side?.let { hypot((it.x - center.x).toDouble(), (it.y - center.y).toDouble()).toFloat() }
        val unit = min(width, height).toFloat()
        val radius = (projectedRadius ?: unit * .0105f).coerceIn(unit * .0052f, unit * .024f)

        val vx = state?.vx?.takeIf { it.isFinite() } ?: 0.0
        val vy = state?.vy?.takeIf { it.isFinite() } ?: 0.0
        val plan = V89VisualPhysicsPlanner.plan(hypot(vx, vy), travelledM)

        drawBroadcastFocus(canvas, center.x, center.y, radius, plan)
        drawVelocityComet(canvas, settings, x, y, vx, vy, radius, plan)
        drawRollingGhosts(canvas, settings, x, y, vx, vy, radius, plan)
        drawContactShadow(canvas, ground.x, ground.y, radius, plan)
        drawCupDepth(canvas, settings)
        drawBall(canvas, center.x, center.y, radius, plan)
        drawCupInteraction(canvas, settings)

        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val pulseAlive = resultSeenAtMs > 0L && SystemClock.uptimeMillis() - resultSeenAtMs < 1450L
        postInvalidateDelayed(if (running || pulseAlive) 16L else 120L)
    }

    private fun updateTravel(state: SimState?) {
        if (state !== trackedState) {
            trackedState = state
            travelledM = 0.0
            lastWorldX = state?.x ?: Double.NaN
            lastWorldY = state?.y ?: Double.NaN
            return
        }
        val x = state?.x ?: return
        val y = state.y
        if (!x.isFinite() || !y.isFinite()) return
        if (lastWorldX.isFinite() && lastWorldY.isFinite()) {
            val d = hypot(x - lastWorldX, y - lastWorldY)
            if (d.isFinite() && d in 0.0..0.40) travelledM += d
        }
        lastWorldX = x
        lastWorldY = y
    }

    private fun updateResultPulse() {
        val result = engine.lastResult
        if (result !== seenResult) {
            seenResult = result
            resultSeenAtMs = if (result != null) SystemClock.uptimeMillis() else 0L
        }
    }

    private fun drawBroadcastFocus(c: Canvas, cx: Float, cy: Float, radius: Float, plan: V89VisualPhysicsPlan) {
        if (plan.focusStrength <= .01f) return
        val focusR = max(radius * 11f, min(width, height) * .20f)
        p.shader = RadialGradient(
            cx, cy, focusR,
            intArrayOf(Color.TRANSPARENT, Color.argb((34f * plan.focusStrength).roundToInt(), 0, 0, 0), Color.argb((92f * plan.focusStrength).roundToInt(), 0, 0, 0)),
            floatArrayOf(0f, .60f, 1f), Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.shader = null
    }

    private fun drawVelocityComet(
        c: Canvas,
        settings: GreenSettings,
        x: Double,
        y: Double,
        vx: Double,
        vy: Double,
        radius: Float,
        plan: V89VisualPhysicsPlan
    ) {
        if (plan.speedMps < .18) return
        val speed = plan.speedMps.coerceAtLeast(.01)
        val nx = vx / speed
        val ny = vy / speed
        val headZ = GreenTerrain.effectiveHeightAt(settings, x, y) + V89VisualPhysicsPlanner.BALL_RADIUS_M
        val head = V25FlagProjectionRuntime.project(x, y, headZ) ?: return
        val tailX = x - nx * plan.cometLengthM
        val tailY = y - ny * plan.cometLengthM
        val tailZ = GreenTerrain.effectiveHeightAt(settings, tailX, tailY) + V89VisualPhysicsPlanner.BALL_RADIUS_M
        val tail = V25FlagProjectionRuntime.project(tailX, tailY, tailZ) ?: return
        p.shader = android.graphics.LinearGradient(
            tail.x, tail.y, head.x, head.y,
            Color.TRANSPARENT,
            Color.argb((110f * plan.blurStrength).roundToInt().coerceIn(18, 110), 236, 250, 255),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1.5f, radius * (.40f + plan.blurStrength * .42f))
        c.drawLine(tail.x, tail.y, head.x, head.y, p)
        p.shader = null
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawRollingGhosts(c: Canvas, settings: GreenSettings, x: Double, y: Double, vx: Double, vy: Double, radius: Float, plan: V89VisualPhysicsPlan) {
        if (plan.blurSamples <= 0 || plan.speedMps <= .01) return
        val speed = plan.speedMps.coerceAtLeast(.01)
        val nx = vx / speed
        val ny = vy / speed
        for (i in plan.blurSamples downTo 1) {
            val behindM = (.012 + i * .012) * (1.0 + plan.blurStrength * 1.6)
            val px = x - nx * behindM
            val py = y - ny * behindM
            val pz = GreenTerrain.effectiveHeightAt(settings, px, py)
            if (!pz.isFinite()) continue
            val sp = V25FlagProjectionRuntime.project(px, py, pz + V89VisualPhysicsPlanner.BALL_RADIUS_M) ?: continue
            val t = i.toFloat() / (plan.blurSamples + 1).toFloat()
            p.style = Paint.Style.FILL
            p.color = Color.argb((42f * plan.blurStrength * (1f - t * .55f)).roundToInt().coerceIn(0, 48), 244, 250, 255)
            c.drawCircle(sp.x, sp.y, radius * (1f - t * .16f), p)
        }
    }

    private fun drawContactShadow(c: Canvas, cx: Float, cy: Float, radius: Float, plan: V89VisualPhysicsPlan) {
        val rx = radius * 1.18f * plan.shadowStretch
        val ry = radius * .42f
        shadowRect.set(cx - rx, cy - ry * .45f, cx + rx, cy + ry * 1.55f)
        p.shader = RadialGradient(cx, cy + ry * .25f, rx,
            intArrayOf(Color.argb(112, 0, 0, 0), Color.argb(38, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, .54f, 1f), Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawOval(shadowRect, p)
        p.shader = null
    }

    private fun drawCupDepth(c: Canvas, settings: GreenSettings) {
        val y = settings.holeDistanceM
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, y)
        val cup = V25FlagProjectionRuntime.project(0.0, y, z + .002) ?: return
        val base = min(width, height) * .0105f
        p.style = Paint.Style.FILL
        p.color = Color.argb(120, 5, 8, 8)
        c.drawOval(RectF(cup.x - base * 1.35f, cup.y - base * .50f, cup.x + base * 1.35f, cup.y + base * .62f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.2f, base * .10f)
        p.color = Color.argb(150, 232, 238, 232)
        c.drawArc(RectF(cup.x - base * 1.38f, cup.y - base * .53f, cup.x + base * 1.38f, cup.y + base * .60f), 188f, 164f, false, p)
        p.style = Paint.Style.FILL
    }

    private fun drawBall(c: Canvas, cx: Float, cy: Float, radius: Float, plan: V89VisualPhysicsPlan) {
        p.style = Paint.Style.FILL
        p.color = Color.argb((24f + 28f * plan.blurStrength).roundToInt(), 210, 244, 220)
        c.drawCircle(cx, cy + radius * .10f, radius * 1.20f, p)

        ballRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        p.shader = RadialGradient(cx - radius * .34f, cy - radius * .42f, radius * 1.35f,
            intArrayOf(Color.WHITE, Color.rgb(246, 249, 250), Color.rgb(188, 198, 202), Color.rgb(92, 102, 108)),
            floatArrayOf(0f, .36f, .78f, 1f), Shader.TileMode.CLAMP)
        c.drawOval(ballRect, p)
        p.shader = null

        c.save()
        c.rotate(plan.spinDegrees, cx, cy)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.1f, radius * .075f)
        p.color = Color.argb(108, 66, 74, 78)
        c.drawOval(RectF(cx - radius * .72f, cy - radius * .23f, cx + radius * .72f, cy + radius * .23f), p)
        p.color = Color.argb(72, 100, 108, 112)
        c.drawArc(RectF(cx - radius * .24f, cy - radius * .78f, cx + radius * .24f, cy + radius * .78f), -78f, 156f, false, p)
        drawDimples(c, cx, cy, radius, plan)
        c.restore()

        p.style = Paint.Style.FILL
        p.color = Color.argb((130f * plan.highlightStrength).roundToInt().coerceIn(80, 160), 255, 255, 255)
        c.drawCircle(cx - radius * .32f, cy - radius * .38f, radius * .16f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, radius * .055f)
        p.color = Color.argb(95, 26, 32, 35)
        c.drawOval(ballRect, p)
        p.style = Paint.Style.FILL
    }

    private fun drawDimples(c: Canvas, cx: Float, cy: Float, radius: Float, plan: V89VisualPhysicsPlan) {
        if (radius < 8f) return
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(.8f, radius * .032f)
        p.color = Color.argb(plan.dimpleAlpha, 82, 92, 96)
        val rings = intArrayOf(6, 9)
        val rr = floatArrayOf(.34f, .62f)
        for (r in rings.indices) {
            for (i in 0 until rings[r]) {
                val a = 2.0 * PI * i / rings[r] + r * .34
                val dx = cos(a).toFloat() * radius * rr[r]
                val dy = sin(a).toFloat() * radius * rr[r] * .70f
                c.drawCircle(cx + dx, cy + dy, max(1f, radius * .052f), p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawCupInteraction(c: Canvas, settings: GreenSettings) {
        val result = seenResult ?: return
        val age = SystemClock.uptimeMillis() - resultSeenAtMs
        if (age !in 0L..1450L) return
        val y = settings.holeDistanceM
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, y)
        val cup = V25FlagProjectionRuntime.project(0.0, y, z + .003) ?: return
        val t = (age / 1450f).coerceIn(0f, 1f)
        val grow = 1f + t * 2.4f
        val base = min(width, height) * .018f
        val alpha = ((1f - t) * if (result.holed) 190f else 135f).roundToInt().coerceIn(0, 195)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, base * .12f)
        p.color = if (result.holed) Color.argb(alpha, 105, 245, 177) else Color.argb(alpha, 255, 192, 76)
        c.drawCircle(cup.x, cup.y, base * grow, p)
        if (result.holed) {
            p.strokeWidth = max(1f, base * .07f)
            p.color = Color.argb((alpha * .66f).roundToInt(), 225, 255, 242)
            c.drawCircle(cup.x, cup.y, base * (.58f + grow * .60f), p)
        } else if (result.lipOut) {
            p.strokeWidth = max(2f, base * .16f)
            p.color = Color.argb(alpha, 255, 148, 56)
            c.drawArc(RectF(cup.x - base * grow, cup.y - base * grow, cup.x + base * grow, cup.y + base * grow), -35f, 105f + 160f * t, false, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun schedule(state: SimState?) {
        postInvalidateDelayed(if (state?.running == true) 16L else 120L)
    }
}
