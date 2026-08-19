package com.puttvision.screen

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Clean-room presentation calibration derived only from publicly visible Friends Screen putting
 * imagery. No private code/assets/settings are used here.
 */
object V139FriendsReference {
    const val ADDRESS_FOV_DEG = 41.5f
    const val ADDRESS_EYE_Z_M = 1.04f
    const val BALL_SCREEN_Y01 = 0.79f
    const val FLAG_SCREEN_Y01 = 0.48f
    const val CUP_HERO_TRIGGER_M = 0.72
    const val PRE_CUP_TRIGGER_M = 1.85
}

object V139FriendsCameraPlanner {
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

        // Public putting imagery keeps the target flag visually central and the horizon high. The
        // hero camera remains low enough to show rim motion without becoming a dramatic TV closeup.
        if (cupAction || (live && cupDistance < V139FriendsReference.CUP_HERO_TRIGGER_M)) {
            return V133CameraFrame(
                eyeX = .38f,
                eyeY = (d - 1.18).toFloat(),
                eyeZ = .42f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.020f,
                fovDeg = 34.0f
            )
        }

        if (live && cupDistance < V139FriendsReference.PRE_CUP_TRIGGER_M) {
            return V133CameraFrame(
                eyeX = .54f,
                eyeY = (d - 2.38).toFloat(),
                eyeZ = .86f,
                lookX = (bx * .05).toFloat(),
                lookY = d.toFloat(),
                lookZ = .022f,
                fovDeg = 36.0f
            )
        }

        if (result != null && (result.holed || result.lipOut || result.distanceToCupM < .80)) {
            return V133CameraFrame(
                eyeX = .46f,
                eyeY = (d - 1.75).toFloat(),
                eyeZ = .66f,
                lookX = 0f,
                lookY = d.toFloat(),
                lookZ = -.010f,
                fovDeg = 35.0f
            )
        }

        if (live) {
            val progress = ((by - sy) / max(.25, d - sy)).coerceIn(0.0, 1.0)
            val back = max(2.65, min(4.65, d * .36))
            return V133CameraFrame(
                eyeX = (bx * .035 + sx * .07).toFloat(),
                eyeY = (by - back).toFloat(),
                eyeZ = (1.02 + .10 * (1.0 - progress)).toFloat(),
                lookX = (bx * .05).toFloat(),
                lookY = min(d, by + max(2.45, d * .24)).toFloat(),
                lookZ = .060f,
                fovDeg = (39.0 - 1.5 * progress).toFloat()
            )
        }

        // Address: symmetrical and target-centric, deliberately less cinematic than V138.
        return V133CameraFrame(
            eyeX = (sx * .06).toFloat(),
            eyeY = (sy - max(2.65, d * .38)).toFloat(),
            eyeZ = (V139FriendsReference.ADDRESS_EYE_Z_M + min(.10, d * .004)).toFloat(),
            lookX = 0f,
            lookY = (d * .69).toFloat(),
            lookZ = .105f,
            fovDeg = V139FriendsReference.ADDRESS_FOV_DEG
        )
    }
}

/** More stable than the V138 broadcast camera; public Friends imagery reads like a simulator view. */
class V139FriendsCameraSmoother {
    private var current: V133CameraFrame? = null

    fun reset(frame: V133CameraFrame? = null) { current = frame }

    fun step(target: V133CameraFrame, cupAction: Boolean): V133CameraFrame {
        val c = current ?: target.also { current = it }
        val a = if (cupAction) .17f else .075f
        val fovA = if (cupAction) .13f else .055f
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
