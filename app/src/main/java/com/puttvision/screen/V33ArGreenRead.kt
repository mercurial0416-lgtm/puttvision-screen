package com.puttvision.screen

import android.graphics.PointF
import android.os.SystemClock

/** Projects the physics/GreenRead world path back onto the calibrated real putting mat. */
object V33ArGreenReadRuntime {
    data class Snapshot(
        val imagePoints: List<PointF>,
        val cupSpeedMps: Double,
        val ballSpeedMps: Double,
        val updatedAtMs: Long
    )

    @Volatile private var homography: Homography? = null
    @Volatile private var frameInfo: FrameInfo? = null
    @Volatile private var updatedAtMs: Long = 0L

    fun updateCalibration(h: Homography, frame: FrameInfo) {
        homography = h
        frameInfo = frame
        updatedAtMs = SystemClock.uptimeMillis()
    }

    fun snapshot(settings: GreenSettings): Snapshot? {
        if (SystemClock.uptimeMillis() - updatedAtMs > 450L) return null
        val h = homography ?: return null
        val frame = frameInfo ?: return null
        val read = GreenReadRuntime.peekOrSchedule(settings) ?: return null
        if (!read.solverReliable) return null

        val points = read.predictedTrail.mapNotNull { (xM, yM) ->
            val image = h.inverseMap(PointF((xM * 100.0).toFloat(), (yM * 100.0).toFloat())) ?: return@mapNotNull null
            if (!image.x.isFinite() || !image.y.isFinite()) return@mapNotNull null
            if (image.x < -24f || image.x > frame.width + 24f || image.y < -24f || image.y > frame.height + 24f) return@mapNotNull null
            image
        }
        if (points.size < 2) return null
        return Snapshot(points, V27CupPaceRuntime.targetCupSpeedMps, read.recommendedBallSpeedMps, updatedAtMs)
    }
}
