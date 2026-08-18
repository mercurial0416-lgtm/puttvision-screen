package com.puttvision.screen

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Commercial screen-golf presentation camera.
 *
 * The physics state remains authoritative. This only chooses and smooths the broadcast camera so
 * the TV feels like a dedicated simulator instead of a debug camera attached to the ball.
 */
object V138CommercialCameraPlanner {
    fun target(
        distanceMRaw: Double,
        startXRaw: Double,
        startYRaw: Double,
        ballXRaw: Double,
        ballYRaw: Double,
        state: SimState?,
        result: SimResult?
    ): V133CameraFrame {
        val d = distanceMRaw.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val sx = startXRaw.takeIf { it.isFinite() } ?: 0.0
        val sy = startYRaw.takeIf { it.isFinite() } ?: 0.0
        val bx = ballXRaw.takeIf { it.isFinite() } ?: sx
        val by = ballYRaw.takeIf { it.isFinite() } ?: sy
        val cupDistance = kotlin.math.hypot(bx, by - d)
        val live = state?.running == true || TvInstantRollRuntime.isAnimating()
        val cupAction = state?.cupPhase == V134CupPhase.RIM ||
            state?.cupPhase == V134CupPhase.DROP ||
            state?.cupPhase == V134CupPhase.SETTLED

        // Lip / drop is the hero moment. Hold a low 3/4 cup camera so rim ride and vertical drop are
        // actually visible instead of being hidden by a high chase view.
        if (cupAction || (live && cupDistance < .42)) {
            return V133CameraFrame(
                eyeX = .54f,
                eyeY = (d - .82).toFloat(),
                eyeZ = .29f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.018f,
                fovDeg = 29.0f
            )
        }

        // Once the ball is inside roughly 1.6 m, transition to a broadcast cup camera before impact.
        if (live && cupDistance < 1.65) {
            val lateral = (bx * .10 + .78).coerceIn(.58, .92)
            return V133CameraFrame(
                eyeX = lateral.toFloat(),
                eyeY = (d - 2.05).toFloat(),
                eyeZ = .78f,
                lookX = (bx * .08).toFloat(),
                lookY = d.toFloat(),
                lookZ = .018f,
                fovDeg = 32.0f
            )
        }

        if (result != null && (result.holed || result.lipOut || result.distanceToCupM < .75)) {
            return V133CameraFrame(
                eyeX = .74f,
                eyeY = (d - 1.65).toFloat(),
                eyeZ = .64f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.01f,
                fovDeg = 31.0f
            )
        }

        if (live) {
            val progress = ((by - sy) / max(.25, d - sy)).coerceIn(0.0, 1.0)
            val chaseBack = max(2.9, min(5.8, d * .42))
            val lift = 1.12 + .26 * (1.0 - progress)
            return V133CameraFrame(
                eyeX = (sx * .18 + bx * .06).toFloat(),
                eyeY = (by - chaseBack).toFloat(),
                eyeZ = lift.toFloat(),
                lookX = (bx * .13).toFloat(),
                lookY = min(d, by + max(2.8, d * .28)).toFloat(),
                lookZ = .052f,
                fovDeg = (35.5 - progress * 2.0).toFloat()
            )
        }

        // Address view: wider/higher than V133 and aligned down the intended line like a simulator.
        return V133CameraFrame(
            eyeX = (sx * .20).toFloat(),
            eyeY = (sy - max(4.4, d * .58)).toFloat(),
            eyeZ = (1.48 + min(.30, d * .010)).toFloat(),
            lookX = (sx * .08).toFloat(),
            lookY = min(d, sy + max(4.0, d * .72)).toFloat(),
            lookZ = .040f,
            fovDeg = 36.0f
        )
    }
}

/** Exponential-ish frame smoother with stronger smoothing for camera position than FOV. */
class V138CameraSmoother {
    private var current: V133CameraFrame? = null

    fun reset(frame: V133CameraFrame? = null) { current = frame }

    fun step(target: V133CameraFrame, heroCupAction: Boolean): V133CameraFrame {
        val c = current ?: target.also { current = it }
        // Cup action needs to arrive quickly enough to show the lip, but still must not snap.
        val a = if (heroCupAction) .20f else .105f
        val fovA = if (heroCupAction) .16f else .075f
        fun l(v: Float, t: Float, alpha: Float) = v + (t - v) * alpha
        val next = V133CameraFrame(
            eyeX = l(c.eyeX, target.eyeX, a),
            eyeY = l(c.eyeY, target.eyeY, a),
            eyeZ = l(c.eyeZ, target.eyeZ, a),
            lookX = l(c.lookX, target.lookX, a),
            lookY = l(c.lookY, target.lookY, a),
            lookZ = l(c.lookZ, target.lookZ, a),
            fovDeg = l(c.fovDeg, target.fovDeg, fovA)
        )
        current = next
        return next
    }

    fun isSettledNear(target: V133CameraFrame): Boolean {
        val c = current ?: return false
        return abs(c.eyeX - target.eyeX) + abs(c.eyeY - target.eyeY) + abs(c.eyeZ - target.eyeZ) < .04f
    }
}
