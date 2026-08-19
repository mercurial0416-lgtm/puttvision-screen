package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max

/**
 * V142 is the playability hardening layer over the V141 PBR renderer.
 *
 * V29 instant-roll is useful only while HFR analysis is still finishing. Once GameEngine owns a
 * BallState, the TV must render that state every frame; leaving the handoff snapshot alive freezes
 * the visible ball while V135-V137 physics continues underneath.
 */
object V142RenderContract {
    fun authoritativeBallPosition(
        stateX: Double?,
        stateY: Double?,
        startX: Double,
        startY: Double
    ): Pair<Double, Double> =
        if (stateX != null && stateY != null && stateX.isFinite() && stateY.isFinite()) {
            stateX to stateY
        } else {
            startX to startY
        }

    /** A target pin is presentation-only when the physical flagstick setting is OUT. */
    fun showTargetPin(): Boolean = true
}

object V142PlayableScreenGolfFactory {
    fun create(context: Context, game: GameEngine): View = V142PlayableStage(context, game)
}

/**
 * Keeps the existing V141 PBR world but removes the stale handoff from the render path as soon as
 * authoritative physics exists. This intentionally does not change the physical flagstick setting.
 */
private class V142PlayableStage(context: Context, private val game: GameEngine) : FrameLayout(context) {
    private val targetOverlay = V142TargetOverlay(context, game)
    private var active = false

    private val sync = object : Runnable {
        override fun run() {
            if (!active) return
            // Before launch, instant-roll remains available to hide HFR latency. From the first
            // authoritative BallState onward it must never override the real simulated position.
            if (game.state != null) TvInstantRollRuntime.clear()
            targetOverlay.invalidate()
            postOnAnimation(this)
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(
            V141FriendsPbrScreenGolfFactory.create(context, game),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(targetOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!active) {
            active = true
            postOnAnimation(sync)
        }
    }

    override fun onDetachedFromWindow() {
        active = false
        removeCallbacks(sync)
        super.onDetachedFromWindow()
    }
}

/**
 * A clean-room presentation marker for the address screen. It makes the actual target immediately
 * legible even when the physical flagstick is configured OUT. The physical cup / flag collision
 * remains entirely in V135/V136 and is not changed by this view.
 */
private class V142TargetOverlay(context: Context, private val game: GameEngine) : View(context) {
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(225, 238, 241, 233)
    }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 4, 7, 5)
    }
    private val poleShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(90, 0, 0, 0)
    }
    private val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(245, 246, 240)
    }
    private val flag = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(210, 34, 27)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!V142RenderContract.showTargetPin()) return

        // During a shot the real 3D cup camera owns framing. The overlay exists to make the
        // pre-putt target unmistakable, which is where the supplied real-device capture failed.
        if (game.state != null) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        val cupX = w * .5f
        val cupY = h * .425f
        val cupRx = max(7f, w * .0055f)
        val cupRy = max(2.8f, cupRx * .30f)
        val poleTop = h * .205f
        val poleWidth = max(2.0f, w * .00115f)

        // Contact shadow + high-contrast cup mouth.
        hole.color = Color.argb(75, 0, 0, 0)
        canvas.drawOval(cupX - cupRx * 1.55f, cupY + cupRy * .25f, cupX + cupRx * 1.55f, cupY + cupRy * 2.0f, hole)
        hole.color = Color.argb(238, 3, 6, 4)
        canvas.drawOval(cupX - cupRx, cupY - cupRy, cupX + cupRx, cupY + cupRy, hole)
        rim.strokeWidth = max(1.5f, poleWidth * .70f)
        canvas.drawOval(cupX - cupRx, cupY - cupRy, cupX + cupRx, cupY + cupRy, rim)

        poleShadow.strokeWidth = poleWidth * 2.3f
        canvas.drawLine(cupX + poleWidth, cupY, cupX + poleWidth, poleTop, poleShadow)
        pole.strokeWidth = poleWidth
        canvas.drawLine(cupX, cupY, cupX, poleTop, pole)

        val flagW = max(26f, w * .027f)
        val flagH = max(15f, h * .030f)
        val p = Path().apply {
            moveTo(cupX, poleTop)
            lineTo(cupX + flagW, poleTop + flagH * .34f)
            lineTo(cupX, poleTop + flagH)
            close()
        }
        canvas.drawPath(p, flag)
    }
}
