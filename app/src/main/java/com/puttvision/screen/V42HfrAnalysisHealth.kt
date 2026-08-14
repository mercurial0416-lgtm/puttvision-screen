package com.puttvision.screen

object V42HfrCalibrationPolicy {
    const val FAST_MARKERLESS_CONFIDENCE = .90
    const val FALLBACK_MARKERLESS_CONFIDENCE = .74
    const val MARKER_SCAN_TIMEOUT_MS = 1_500L

    fun canUseFastMarkerless(confidence: Double): Boolean =
        confidence.isFinite() && confidence >= FAST_MARKERLESS_CONFIDENCE

    fun canUseFallbackMarkerless(confidence: Double): Boolean =
        confidence.isFinite() && confidence >= FALLBACK_MARKERLESS_CONFIDENCE
}

data class V42HfrAnalysisHealth(
    val calibrationMode: String,
    val calibrationMs: Long,
    val totalAnalysisMs: Long,
    val fps: Int,
    val analyzedFrames: Int,
    val ballTrackFrames: Int,
    val putterTrackFrames: Int
) {
    val label: String
        get() = "$calibrationMode · CAL ${calibrationMs}ms · TOTAL ${totalAnalysisMs}ms · ${fps}fps · TRACK $ballTrackFrames/$putterTrackFrames"
}

/** Latest successful precision-analysis snapshot. The V43 window intentionally survives per-shot clears. */
object V42HfrAnalysisHealthRuntime {
    @Volatile var latest: V42HfrAnalysisHealth? = null
        private set

    fun publish(value: V42HfrAnalysisHealth) {
        latest = value
        V43HfrHealthWindow.publish(value)
    }

    /** Called before each analysis: clear only the per-shot snapshot, not long-session history. */
    fun clear() {
        latest = null
    }

    /** Explicit session/reset hook when long-session history really should be discarded. */
    fun resetHistory() {
        latest = null
        V43HfrHealthWindow.clear()
    }
}