package com.puttvision.screen

import java.util.ArrayDeque

/**
 * Bounded in-process history for the no-hardware regression dashboard.
 *
 * A transient failure must not disappear just because the next synthetic shot passes. This history
 * keeps only compact counters/stage names (no frames, videos, metrics or physical accuracy claims).
 */
data class V75SelfTestHistoryEntry(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val failedStage: String?,
    val recordedAtMs: Long
)

data class V75SelfTestHistorySummary(
    val samples: Int,
    val failures: Int,
    val consecutivePasses: Int,
    val lastFailureStage: String?,
    val lastFailureSamplesAgo: Int?
) {
    fun warningLabel(): String? = if (failures <= 0) null else
        "HIST FAIL $failures · ${lastFailureStage ?: "UNKNOWN"} · ${lastFailureSamplesAgo ?: 0} run ago"
}

object V75HardwarelessSelfTestHistoryRuntime {
    const val MAX_SAMPLES = 20
    private val history = ArrayDeque<V75SelfTestHistoryEntry>()

    @Synchronized
    fun record(report: V72HardwarelessSelfTestReport, recordedAtMs: Long = System.currentTimeMillis()) {
        history.addLast(
            V75SelfTestHistoryEntry(
                passed = report.passed,
                checksPassed = report.checksPassed,
                checksTotal = report.checksTotal,
                failedStage = report.failedStage,
                recordedAtMs = recordedAtMs.coerceAtLeast(0L)
            )
        )
        while (history.size > MAX_SAMPLES) history.removeFirst()
    }

    @Synchronized
    fun summary(): V75SelfTestHistorySummary {
        val snapshot = history.toList()
        if (snapshot.isEmpty()) return V75SelfTestHistorySummary(0, 0, 0, null, null)
        val failures = snapshot.count { !it.passed }
        var consecutivePasses = 0
        for (entry in snapshot.asReversed()) {
            if (!entry.passed) break
            consecutivePasses++
        }
        val lastFailureIndex = snapshot.indexOfLast { !it.passed }
        return V75SelfTestHistorySummary(
            samples = snapshot.size,
            failures = failures,
            consecutivePasses = consecutivePasses,
            lastFailureStage = snapshot.getOrNull(lastFailureIndex)?.failedStage,
            lastFailureSamplesAgo = lastFailureIndex.takeIf { it >= 0 }?.let { snapshot.lastIndex - it }
        )
    }

    @Synchronized
    fun reset() {
        history.clear()
    }

    @Synchronized
    internal fun sizeForTest(): Int = history.size
}
