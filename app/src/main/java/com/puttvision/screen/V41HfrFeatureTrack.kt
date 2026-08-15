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
    val markerAngleDeg: Double?,
    /** Raw detector coordinates in the source video frame. Optional for legacy tracks. */
    val ballXpx: Double? = null,
    val ballYpx: Double? = null,
    val heelXpx: Double? = null,
    val heelYpx: Double? = null,
    val toeXpx: Double? = null,
    val toeYpx: Double? = null
)

data class HfrFeatureTrack(
    val fps: Int,
    val impactFrame: Int,
    val frames: List<HfrFeatureFrame>,
    /** Source-image dimensions for interpreting raw pixel correspondences. */
    val imageWidthPx: Int? = null,
    val imageHeightPx: Int? = null
) {
    val ballFrames: Int get() = frames.count { it.ballXcm != null && it.ballYcm != null }
    val putterFrames: Int get() = frames.count {
        it.heelXcm != null && it.heelYcm != null && it.toeXcm != null && it.toeYcm != null
    }
    val pixelBallFrames: Int get() = frames.count { it.ballXpx != null && it.ballYpx != null }
    val pixelPutterFrames: Int get() = frames.count {
        it.heelXpx != null && it.heelYpx != null && it.toeXpx != null && it.toeYpx != null
    }
    val hasPixelFrameShape: Boolean get() =
        imageWidthPx?.let { it > 0 } == true && imageHeightPx?.let { it > 0 } == true
}

data class HfrFeatureTrackSnapshot(
    val track: HfrFeatureTrack,
    /** Physical shot/event time used for multi-camera pairing. Kept under the legacy field name. */
    val publishedAtMs: Long,
    /** When analysis actually published this compact track; used only for freshness. */
    val storedAtMs: Long = publishedAtMs,
    val timeSource: String = "LEGACY",
    val timeUncertaintyMs: Long = 1_500L
)

/** Latest compact HFR geometry for diagnostics/companion transport. No bitmaps are retained. */
object V41HfrFeatureTrackRuntime {
    private const val MAX_FRAMES = 32

    @Volatile var latest: HfrFeatureTrack? = null
        private set
    /** Legacy name: from V50 onward this is the estimated physical impact wall-clock time. */
    @Volatile var latestPublishedAtMs: Long = 0L
        private set
    @Volatile var latestStoredAtMs: Long = 0L
        private set
    @Volatile var latestTimeSource: String = "NONE"
        private set
    @Volatile var latestTimeUncertaintyMs: Long = 0L
        private set

    fun publish(track: HfrFeatureTrack, nowMs: Long = System.currentTimeMillis()) {
        val estimate = V50HfrCaptureClockRuntime.estimate(track, nowMs)
        latest = track.copy(frames = track.frames.take(MAX_FRAMES).toList())
        latestPublishedAtMs = estimate.impactAtMs
        latestStoredAtMs = nowMs
        latestTimeSource = estimate.source
        latestTimeUncertaintyMs = estimate.uncertaintyMs
    }

    fun freshSnapshot(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 1_500L): HfrFeatureTrackSnapshot? {
        val track = latest ?: return null
        val event = latestPublishedAtMs
        val stored = latestStoredAtMs
        if (event <= 0L || stored <= 0L || nowMs - stored !in 0L..maxAgeMs) return null
        return HfrFeatureTrackSnapshot(track, event, stored, latestTimeSource, latestTimeUncertaintyMs)
    }

    fun clear() {
        latest = null
        latestPublishedAtMs = 0L
        latestStoredAtMs = 0L
        latestTimeSource = "NONE"
        latestTimeUncertaintyMs = 0L
    }
}
