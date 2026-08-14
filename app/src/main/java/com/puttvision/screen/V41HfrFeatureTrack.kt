package com.puttvision.screen

data class HfrFeatureFrame(
    val frame: Int,
    val timeFromImpactMs: Double,
    val ballXcm: Double?,
    val ballYcm: Double?,
    val heelXcm: Double?,
    val heelYcm: Double?,
    val toeXcm: Double?,
    val toeYcm: Double?,
    val markerAngleDeg: Double?
)

data class HfrFeatureTrack(
    val fps: Int,
    val impactFrame: Int,
    val frames: List<HfrFeatureFrame>
) {
    val ballFrames: Int get() = frames.count { it.ballXcm != null && it.ballYcm != null }
    val putterFrames: Int get() = frames.count {
        it.heelXcm != null && it.heelYcm != null && it.toeXcm != null && it.toeYcm != null
    }
}

/** Latest compact HFR geometry for diagnostics and future stereo correspondence. No bitmaps are retained. */
object V41HfrFeatureTrackRuntime {
    private const val MAX_FRAMES = 32

    @Volatile var latest: HfrFeatureTrack? = null
        private set

    fun publish(track: HfrFeatureTrack) {
        latest = track.copy(frames = track.frames.take(MAX_FRAMES).toList())
    }

    fun clear() {
        latest = null
    }
}
