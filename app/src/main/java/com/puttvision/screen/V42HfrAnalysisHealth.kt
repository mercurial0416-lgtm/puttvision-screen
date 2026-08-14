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

/** Latest successful precision-analysis health snapshot plus a bounded long-session numeric window. */
object V42HfrAnalysisHealthRuntime {
    @Volatile var latest: V42HfrAnalysisHealth? = null
        private set

    fun publish(value: V42HfrAnalysisHealth) {
        latest = value
        V43HfrHealthWindow.publish(value)
    }

    fun clear() {
        latest = null
        V43HfrHealthWindow.clear()
    }
}
