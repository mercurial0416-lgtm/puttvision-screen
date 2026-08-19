package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel-ratio calibration from the publicly visible Friends Screen putting still published by
 * Kakao VX. Only rendered output is used as reference; no private assets, binaries or settings.
 */
object V140FriendsReference {
    const val ADDRESS_FOV_DEG = 38.2f
    const val ADDRESS_EYE_Z_M = 0.72f
    const val BALL_SCREEN_Y01 = 0.735f
    const val FLAG_SCREEN_Y01 = 0.490f
    const val WAIT_SCREEN_Y01 = 0.828f
    const val CUP_HERO_TRIGGER_M = 0.64
    const val PRE_CUP_TRIGGER_M = 1.55
}

object V140FriendsCameraPlanner {
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
        val cupDistance = hypot(bx, by - d)
        val live = state?.running == true || TvInstantRollRuntime.isAnimating()
        val cupAction = state?.cupPhase == V134CupPhase.RIM ||
            state?.cupPhase == V134CupPhase.DROP ||
            state?.cupPhase == V134CupPhase.SETTLED

        // Keep the cup almost front-on during the lip/drop moment, matching the restrained simulator
        // presentation rather than V138's oblique broadcast shot.
        if (cupAction || (live && cupDistance < V140FriendsReference.CUP_HERO_TRIGGER_M)) {
            return V133CameraFrame(
                eyeX = .24f,
                eyeY = (d - .96).toFloat(),
                eyeZ = .31f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.026f,
                fovDeg = 31.5f
            )
        }

        if (live && cupDistance < V140FriendsReference.PRE_CUP_TRIGGER_M) {
            return V133CameraFrame(
                eyeX = .32f,
                eyeY = (d - 2.05).toFloat(),
                eyeZ = .68f,
                lookX = (bx * .035).toFloat(),
                lookY = d.toFloat(),
                lookZ = .018f,
                fovDeg = 34.5f
            )
        }

        if (result != null && (result.holed || result.lipOut || result.distanceToCupM < .70)) {
            return V133CameraFrame(
                eyeX = .28f,
                eyeY = (d - 1.42).toFloat(),
                eyeZ = .50f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.012f,
                fovDeg = 33.0f
            )
        }

        if (live) {
            val progress = ((by - sy) / max(.25, d - sy)).coerceIn(0.0, 1.0)
            // Friends' putting camera reads as target-locked, not a free chase. Move much less than
            // V138 and keep the pin near the optical axis as the ball advances.
            val back = max(2.20, min(3.55, d * .31))
            val eyeY = by - back * (1.0 - .28 * progress)
            return V133CameraFrame(
                eyeX = (sx * .025 + bx * .020).toFloat(),
                eyeY = eyeY.toFloat(),
                eyeZ = (.78 + .055 * (1.0 - progress)).toFloat(),
                lookX = (bx * .025).toFloat(),
                lookY = min(d, by + max(2.15, d * .22)).toFloat(),
                lookZ = .060f,
                fovDeg = (37.8 - 1.2 * progress).toFloat()
            )
        }

        // Public still: low, symmetrical one-point perspective with the ball low-center and flag
        // centered vertically around the middle of frame.
        return V133CameraFrame(
            eyeX = (sx * .025).toFloat(),
            eyeY = (sy - max(2.18, d * .305)).toFloat(),
            eyeZ = (V140FriendsReference.ADDRESS_EYE_Z_M + min(.045, d * .002)).toFloat(),
            lookX = 0f,
            lookY = (d * .742).toFloat(),
            lookZ = .078f,
            fovDeg = V140FriendsReference.ADDRESS_FOV_DEG
        )
    }
}

class V140FriendsCameraSmoother {
    private var current: V133CameraFrame? = null

    fun reset(frame: V133CameraFrame? = null) { current = frame }

    fun step(target: V133CameraFrame, cupAction: Boolean): V133CameraFrame {
        val c = current ?: target.also { current = it }
        val a = if (cupAction) .18f else .060f
        val fovA = if (cupAction) .14f else .045f
        fun l(v: Float, t: Float, alpha: Float) = v + (t - v) * alpha
        return V133CameraFrame(
            eyeX = l(c.eyeX, target.eyeX, a),
            eyeY = l(c.eyeY, target.eyeY, a),
            eyeZ = l(c.eyeZ, target.eyeZ, a),
            lookX = l(c.lookX, target.lookX, a),
            lookY = l(c.lookY, target.lookY, a),
            lookZ = l(c.lookZ, target.lookZ, a),
            fovDeg = l(c.fovDeg, target.fovDeg, fovA)
        ).also { current = it }
    }
}
