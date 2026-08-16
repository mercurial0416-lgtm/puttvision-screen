package com.puttvision.screen

import java.util.ArrayDeque

enum class V45HfrFailureReason {
    FILE_INVALID,
    API_UNSUPPORTED,
    VIDEO_METADATA,
    CALIBRATION,
    BALL_ORIGIN,
    IMPACT_NOT_FOUND,
    KINEMATICS,
    FEATURE_TRACK_INTEGRITY,
    DECODE_EXCEPTION
}

data class V45HfrFailure(
    val reason: V45HfrFailureReason,
    val phase: String,
    val elapsedMs: Long,
    val fps: Int = 0,
    val frameCount: Int = 0,
    val detail: String = ""
) {
    val label: String
        get() = buildString {
            append("HFR FAIL · ${reason.name} · $phase · ${elapsedMs.coerceAtLeast(0L)}ms")
            if (fps > 0) append(" · ${fps}fps")
            if (frameCount > 0) append(" · ${frameCount}f")
            if (detail.isNotBlank()) append(" · $detail")
        }
}

data class V45HfrFailureSummary(
    val samples: Int,
    val topReason: V45HfrFailureReason?,
    val topReasonCount: Int
) {
    val label: String
        get() = if (samples == 0) "HFR FAILURES · 없음"
        else "HFR FAILURES · n=$samples · ${topReason?.name ?: "UNKNOWN"} $topReasonCount"
}

/** Bounded numeric/reason-only failure history. No images or videos are retained. */
object V45HfrFailureRuntime {
    private const val MAX_SAMPLES = 24
    private val history = ArrayDeque<V45HfrFailure>()

    @Volatile var latest: V45HfrFailure? = null
        private set

    @Synchronized
    fun publish(value: V45HfrFailure) {
        latest = value
        history.addLast(value)
        while (history.size > MAX_SAMPLES) history.removeFirst()
    }

    fun recordSuccess() {
        latest = null
    }

    @Synchronized
    fun summary(): V45HfrFailureSummary {
        val snapshot = history.toList()
        if (snapshot.isEmpty()) return V45HfrFailureSummary(0, null, 0)
        val counts = snapshot.groupingBy { it.reason }.eachCount()
        val top = counts.maxByOrNull { it.value }
        return V45HfrFailureSummary(snapshot.size, top?.key, top?.value ?: 0)
    }

    @Synchronized
    fun reset() {
        latest = null
        history.clear()
    }
}

/** Keeps decoded HFR bitmaps below a predictable heap/native-memory envelope. */
object V45HfrFrameCachePolicy {
    const val BATCH_SIZE = 4
    const val MAX_ITEMS = 12
    const val MAX_BYTES = 48L * 1024L * 1024L

    /** Invalid accounting is treated as over-budget instead of silently disabling eviction. */
    fun shouldEvict(itemCount: Int, totalBytes: Long): Boolean =
        itemCount < 0 || totalBytes < 0L || itemCount > MAX_ITEMS || totalBytes > MAX_BYTES

    /**
     * Estimates ARGB memory without allowing Long overflow to wrap a huge allocation negative/small.
     * Invalid negative dimensions retain the previous zero-byte behavior; arithmetic overflow
     * saturates to Long.MAX_VALUE so shouldEvict() deterministically rejects it.
     */
    fun estimatedArgbBytes(width: Int, height: Int, count: Int): Long {
        if (width < 0 || height < 0 || count < 0) return 0L
        return runCatching {
            Math.multiplyExact(
                Math.multiplyExact(
                    Math.multiplyExact(width.toLong(), height.toLong()),
                    4L
                ),
                count.toLong()
            )
        }.getOrElse { Long.MAX_VALUE }
    }
}
