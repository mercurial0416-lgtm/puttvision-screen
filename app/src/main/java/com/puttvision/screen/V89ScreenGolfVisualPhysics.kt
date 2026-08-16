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
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure visual-physics policy. It never modifies measured metrics or GreenPhysics state.
 * The screen effect is derived from physical speed and travelled distance so animation
 * does not change when the TV render cadence changes.
 */
data class V89VisualPhysicsPlan(
    val speedMps: Double,
    val spinDegrees: Float,
    val blurStrength: Float,
    val blurSamples: Int,
    val shadowStretch: Float,
    val highlightStrength: Float
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
            highlightStrength = .55f + normalized * .35f
        )
    }
}

/**
 * Screen-golf visual physics layer shared by the physical TV, phone TV preview and
 * hardwareless TV surface. World position comes from the same GameEngine/GreenPhysics
 * state as the simulator. This layer only renders contact/rotation/motion cues.
 */
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

        val virtualStart = V26BallStartRuntime.current(settings)
        val animated = TvInstantRollRuntime.displayPosition(state)
        val x = animated?.first ?: state?.x ?: virtualStart.first
        val y = animated?.second ?: state?.y ?: virtualStart.second
        if (!x.isFinite() || !y.isFinite()) return

        val groundZ = GreenTerrain.effectiveHeightAt(settings, x, y)
        if (!groundZ.isFinite()) return
        val center = V25FlagProjectionRuntime.project(x, y, groundZ + V89VisualPhysicsPlanner.BALL_RADIUS_M)
            ?: scheduleAndReturn(state)
        val ground = V25FlagProjectionRuntime.project(x, y, groundZ + .001)
            ?: scheduleAndReturn(state)
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
        val speed = hypot(vx, vy)
        val plan = V89VisualPhysicsPlanner.plan(speed, travelledM)

        drawRollingGhosts(canvas, settings, x, y, groundZ, vx, vy, radius, plan)
        drawContactShadow(canvas, ground.x, ground.y, radius, plan)
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
            // A display/state reset must not spin the ball several revolutions in one frame.
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

    private fun drawRollingGhosts(
        c: Canvas,
        settings: GreenSettings,
        x: Double,
        y: Double,
        z: Double,
        vx: Double,
        vy: Double,
        radius: Float,
        plan: V89VisualPhysicsPlan
    ) {
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
        p.shader = RadialGradient(
            cx,
            cy + ry * .25f,
            rx,
            intArrayOf(Color.argb(105, 0, 0, 0), Color.argb(35, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, .54f, 1f),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        c.drawOval(shadowRect, p)
        p.shader = null
    }

    private fun drawBall(c: Canvas, cx: Float, cy: Float, radius: Float, plan: V89VisualPhysicsPlan) {
        // Soft contact halo makes the ball feel seated on the green instead of floating.
        p.style = Paint.Style.FILL
        p.color = Color.argb((24 + 28 * plan.blurStrength).roundToInt(), 210, 244, 220)
        c.drawCircle(cx, cy + radius * .10f, radius * 1.20f, p)

        ballRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        p.shader = RadialGradient(
            cx - radius * .34f,
            cy - radius * .42f,
            radius * 1.35f,
            intArrayOf(Color.WHITE, Color.rgb(242, 246, 247), Color.rgb(174, 184, 188), Color.rgb(102, 111, 116)),
            floatArrayOf(0f, .38f, .78f, 1f),
            Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.FILL
        c.drawOval(ballRect, p)
        p.shader = null

        // A distance-driven seam gives real rolling rotation without tying spin to FPS.
        c.save()
        c.rotate(plan.spinDegrees, cx, cy)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.1f, radius * .075f)
        p.color = Color.argb(105, 70, 79, 82)
        c.drawOval(RectF(cx - radius * .72f, cy - radius * .23f, cx + radius * .72f, cy + radius * .23f), p)
        p.color = Color.argb(72, 103, 112, 116)
        c.drawArc(RectF(cx - radius * .24f, cy - radius * .78f, cx + radius * .24f, cy + radius * .78f), -78f, 156f, false, p)
        c.restore()

        // Specular glint strengthens slightly with speed, like a broadcast/simulator ball shader.
        p.style = Paint.Style.FILL
        p.color = Color.argb((130f * plan.highlightStrength).roundToInt().coerceIn(80, 160), 255, 255, 255)
        c.drawCircle(cx - radius * .32f, cy - radius * .38f, radius * .16f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1f, radius * .055f)
        p.color = Color.argb(90, 30, 36, 38)
        c.drawOval(ballRect, p)
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
        val alpha = ((1f - t) * if (result.holed) 185f else 125f).roundToInt().coerceIn(0, 190)

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, base * .12f)
        p.color = if (result.holed) Color.argb(alpha, 105, 245, 177) else Color.argb(alpha, 255, 192, 76)
        c.drawCircle(cup.x, cup.y, base * grow, p)
        if (result.holed) {
            p.strokeWidth = max(1f, base * .07f)
            p.color = Color.argb((alpha * .66f).roundToInt(), 225, 255, 242)
            c.drawCircle(cup.x, cup.y, base * (.58f + grow * .60f), p)
        } else if (result.lipOut) {
            val sweep = 105f + 160f * t
            p.strokeWidth = max(2f, base * .16f)
            p.color = Color.argb(alpha, 255, 148, 56)
            c.drawArc(RectF(cup.x - base * grow, cup.y - base * grow, cup.x + base * grow, cup.y + base * grow), -35f, sweep, false, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun scheduleAndReturn(state: SimState?) {
        postInvalidateDelayed(if (state?.running == true) 16L else 120L)
    }
}
