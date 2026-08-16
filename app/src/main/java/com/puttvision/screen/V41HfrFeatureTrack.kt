package com.puttvision.screen

import kotlin.math.abs

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

/**
 * Guards the compact HFR track's source-frame/time provenance before it can become the runtime
 * snapshot used by stereo pairing, diagnostics, replay overlays, or companion transport.
 */
object V93HfrFeatureTrackIntegrity {
    private const val MAX_TIME_ERROR_MS = 0.75

    fun isValid(track: HfrFeatureTrack): Boolean {
        if (track.fps <= 0 || track.impactFrame < 0 || track.frames.isEmpty()) return false
        if (track.frames.zipWithNext().any { (a, b) -> b.frame <= a.frame }) return false
        if (track.frames.none { it.frame == track.impactFrame }) return false

        val width = track.imageWidthPx
        val height = track.imageHeightPx
        if ((width == null) != (height == null)) return false
        if (width != null && (width <= 0 || height == null || height <= 0)) return false

        for (frame in track.frames) {
            if (!frame.timeFromImpactMs.isFinite()) return false
            val expectedMs = (frame.frame - track.impactFrame) * 1000.0 / track.fps.toDouble()
            if (abs(frame.timeFromImpactMs - expectedMs) > MAX_TIME_ERROR_MS) return false
            if (!validPair(frame.ballXcm, frame.ballYcm)) return false
            if (!validPair(frame.ballXpx, frame.ballYpx)) return false
            if (!validPair(frame.heelXcm, frame.heelYcm) || !validPair(frame.toeXcm, frame.toeYcm)) return false
            if (!validPair(frame.heelXpx, frame.heelYpx) || !validPair(frame.toeXpx, frame.toeYpx)) return false
            if ((frame.heelXcm == null) != (frame.toeXcm == null)) return false
            if ((frame.heelXpx == null) != (frame.toeXpx == null)) return false
            if (frame.markerAngleDeg?.isFinite() == false) return false

            val hasPixelCoordinates = frame.ballXpx != null || frame.heelXpx != null || frame.toeXpx != null
            if (hasPixelCoordinates && (width == null || height == null)) return false
            if (width != null && height != null) {
                if (!insideFrame(frame.ballXpx, frame.ballYpx, width, height)) return false
                if (!insideFrame(frame.heelXpx, frame.heelYpx, width, height)) return false
                if (!insideFrame(frame.toeXpx, frame.toeYpx, width, height)) return false
            }
        }
        return true
    }

    private fun validPair(x: Double?, y: Double?): Boolean {
        if ((x == null) != (y == null)) return false
        return x?.isFinite() != false && y?.isFinite() != false
    }

    private fun insideFrame(x: Double?, y: Double?, width: Int, height: Int): Boolean {
        if (x == null && y == null) return true
        if (x == null || y == null) return false
        return x >= 0.0 && x < width.toDouble() && y >= 0.0 && y < height.toDouble()
    }
}

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
    /**
     * Most recent rejected producer write. The last valid geometry is retained for diagnostics, but
     * freshSnapshot() must not expose it after a newer invalid capture attempt or consumers could
     * accidentally bind the previous stroke's HFR evidence to the current stroke.
     */
    @Volatile var latestRejectedAtMs: Long = 0L
        private set

    @Synchronized
    fun publish(track: HfrFeatureTrack, nowMs: Long = System.currentTimeMillis()): Boolean {
        // Validate every producer frame before truncating. Otherwise malformed provenance outside the
        // retained runtime window could be silently discarded and the shortened track would look valid.
        if (!V93HfrFeatureTrackIntegrity.isValid(track)) {
            latestRejectedAtMs = maxOf(latestRejectedAtMs, nowMs)
            return false
        }

        val compactTrack = compactAroundImpact(track)
        val estimate = V50HfrCaptureClockRuntime.estimate(compactTrack, nowMs)
        latest = compactTrack
        latestPublishedAtMs = estimate.impactAtMs
        latestStoredAtMs = nowMs
        latestTimeSource = estimate.source
        latestTimeUncertaintyMs = estimate.uncertaintyMs
        latestRejectedAtMs = 0L
        return true
    }

    private fun compactAroundImpact(track: HfrFeatureTrack): HfrFeatureTrack {
        if (track.frames.size <= MAX_FRAMES) return track.copy(frames = track.frames.toList())

        val impactIndex = track.frames.indexOfFirst { it.frame == track.impactFrame }
        if (impactIndex < 0) return track.copy(frames = track.frames.take(MAX_FRAMES).toList())

        val framesBeforeImpact = (MAX_FRAMES - 1) / 2
        var start = (impactIndex - framesBeforeImpact).coerceAtLeast(0)
        var endExclusive = (start + MAX_FRAMES).coerceAtMost(track.frames.size)
        start = (endExclusive - MAX_FRAMES).coerceAtLeast(0)
        endExclusive = (start + MAX_FRAMES).coerceAtMost(track.frames.size)

        return track.copy(frames = track.frames.subList(start, endExclusive).toList())
    }

    @Synchronized
    fun freshSnapshot(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 1_500L): HfrFeatureTrackSnapshot? {
        if (maxAgeMs < 0L) return null
        val track = latest ?: return null
        val event = latestPublishedAtMs
        val stored = latestStoredAtMs
        val rejected = latestRejectedAtMs
        if (rejected > 0L && rejected >= stored) return null
        if (event <= 0L || stored <= 0L || nowMs - stored !in 0L..maxAgeMs) return null
        return HfrFeatureTrackSnapshot(track, event, stored, latestTimeSource, latestTimeUncertaintyMs)
    }

    @Synchronized
    fun clear() {
        latest = null
        latestPublishedAtMs = 0L
        latestStoredAtMs = 0L
        latestTimeSource = "NONE"
        latestTimeUncertaintyMs = 0L
        latestRejectedAtMs = 0L
    }
}
