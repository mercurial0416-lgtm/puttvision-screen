package com.puttvision.screen

import android.graphics.PointF

/** Projects the shared GreenRead physics path back onto the calibrated real putting mat. */
object V33ArGreenReadRuntime {
    data class Snapshot(
        val imagePoints: List<PointF>,
        val cupSpeedMps: Double,
        val ballSpeedMps: Double,
        val stability: Double
    )

    private var key = ""
    private var cached: Homography? = null
    private var lastFrameShape = ""
    private val corners = V38CornerStabilizer()
    private val path = V38PathStabilizer()

    @Synchronized
    fun snapshot(settings: GreenSettings, calibrationImagePoints: List<PointF>, frame: FrameInfo): Snapshot? {
        if (calibrationImagePoints.size != 4) {
            reset()
            return null
        }
        val frameShape = "${frame.width}x${frame.height}:${frame.rotationDegrees}"
        if (lastFrameShape.isNotEmpty() && lastFrameShape != frameShape) reset()
        lastFrameShape = frameShape

        val decision = corners.update(
            calibrationImagePoints.map { V38Point(it.x, it.y) },
            frame.width,
            frame.height
        ) ?: return null
        // During a real camera move, keep AR hidden until the new geometry repeats three times.
        if (!decision.stable) return null

        val stableCorners = decision.points.map { PointF(it.x, it.y) }
        val currentKey = buildString {
            append(frameShape)
            stableCorners.forEach {
                append(':')
                append((it.x * 2f).toInt())
                append(',')
                append((it.y * 2f).toInt())
            }
        }
        val h = if (currentKey == key) cached else {
            val half = (V16MatGeometryRuntime.widthCm / 2.0).toFloat()
            val length = V16MatGeometryRuntime.lengthCm.toFloat()
            Homography.fromPoints(
                stableCorners,
                listOf(PointF(-half, 0f), PointF(half, 0f), PointF(half, length), PointF(-half, length)),
                frame
            ).also {
                cached = it
                key = currentKey
            }
        } ?: return null

        val read = GreenReadRuntime.peekOrSchedule(settings) ?: return null
        if (!read.solverReliable) return null
        val projected = read.predictedTrail.mapNotNull { (xM, yM) ->
            val image = h.inverseMap(PointF((xM * 100.0).toFloat(), (yM * 100.0).toFloat())) ?: return@mapNotNull null
            if (!image.x.isFinite() || !image.y.isFinite()) return@mapNotNull null
            if (image.x !in -24f..(frame.width + 24f) || image.y !in -24f..(frame.height + 24f)) return@mapNotNull null
            V38Point(image.x, image.y)
        }
        if (projected.size < 2) return null

        val pathKey = "$frameShape:${GreenReadAdvisor.key(settings)}"
        val stablePath = path.update(projected, pathKey, hardReset = decision.reacquired)
        return Snapshot(
            imagePoints = stablePath.map { PointF(it.x, it.y) },
            cupSpeedMps = V27CupPaceRuntime.targetCupSpeedMps,
            ballSpeedMps = read.recommendedBallSpeedMps,
            stability = decision.stability
        )
    }

    @Synchronized
    fun reset() {
        key = ""
        cached = null
        lastFrameShape = ""
        corners.reset()
        path.reset()
    }
}
