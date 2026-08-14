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

data class HfrFeatureTrackSnapshot(
    val track: HfrFeatureTrack,
    val publishedAtMs: Long
)

/** Latest compact HFR geometry for diagnostics/companion transport. No bitmaps are retained. */
object V41HfrFeatureTrackRuntime {
    private const val MAX_FRAMES = 32

    @Volatile var latest: HfrFeatureTrack? = null
        private set
    @Volatile var latestPublishedAtMs: Long = 0L
        private set

    fun publish(track: HfrFeatureTrack, nowMs: Long = System.currentTimeMillis()) {
        latest = track.copy(frames = track.frames.take(MAX_FRAMES).toList())
        latestPublishedAtMs = nowMs
    }

    fun freshSnapshot(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 1_500L): HfrFeatureTrackSnapshot? {
        val track = latest ?: return null
        val published = latestPublishedAtMs
        if (published <= 0L || nowMs - published !in 0L..maxAgeMs) return null
        return HfrFeatureTrackSnapshot(track, published)
    }

    fun clear() {
        latest = null
        latestPublishedAtMs = 0L
    }
}
