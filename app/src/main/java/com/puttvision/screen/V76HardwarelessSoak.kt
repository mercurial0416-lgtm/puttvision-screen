package com.puttvision.screen

/** Deterministic long-run regression exercise for the no-hardware path. */
data class V76HardwarelessSoakReport(
    val requestedRuns: Int,
    val completedRuns: Int,
    val passedRuns: Int,
    val firstFailureRun: Int?,
    val firstFailureStage: String?,
    val minChecksPerRun: Int,
    val maxChecksPerRun: Int,
    val maxHistorySamples: Int,
    val finalHistorySamples: Int,
    val finalHistoryFailures: Int,
    val finalConsecutivePasses: Int,
    val passed: Boolean
) {
    fun shortLabel(): String = if (passed) {
        "SOAK PASS · $completedRuns/$requestedRuns · ${minChecksPerRun}x"
    } else {
        "SOAK FAIL · ${firstFailureRun ?: completedRuns}/$requestedRuns · ${firstFailureStage ?: "UNKNOWN"}"
    }
}

object V76HardwarelessSoak {
    const val DEFAULT_RUNS = 240
    const val EXPECTED_CHECKS_PER_RUN = 80

    private val speeds = doubleArrayOf(0.35, 0.45, 0.75, 1.20, 1.80, 2.80, 3.20)
    private val directions = doubleArrayOf(-7.0, -5.0, -2.5, 0.0, 2.5, 5.0, 7.0)

    fun run(runs: Int = DEFAULT_RUNS): V76HardwarelessSoakReport {
        require(runs in 1..5_000) { "soak runs must be 1..5000" }
        V72HardwarelessSelfTestRuntime.clear()
        V75HardwarelessSelfTestHistoryRuntime.reset()

        var completed = 0
        var passedRuns = 0
        var firstFailureRun: Int? = null
        var firstFailureStage: String? = null
        var minChecks = Int.MAX_VALUE
        var maxChecks = 0
        var maxHistory = 0

        repeat(runs) { index ->
            val speed = speeds[index % speeds.size]
            val direction = directions[(index * 3 + index / speeds.size) % directions.size]
            val report = V72HardwarelessSelfTestRuntime.run(speed, direction)
            V75HardwarelessSelfTestHistoryRuntime.record(report, recordedAtMs = 1_000_000L + index)
            completed++
            minChecks = minOf(minChecks, report.checksTotal)
            maxChecks = maxOf(maxChecks, report.checksTotal)
            val runPassed = report.passed && report.checksPassed == report.checksTotal && report.checksTotal == EXPECTED_CHECKS_PER_RUN
            if (runPassed) passedRuns++ else if (firstFailureRun == null) {
                firstFailureRun = index + 1
                firstFailureStage = when {
                    report.checksTotal != EXPECTED_CHECKS_PER_RUN -> "CHECK COUNT ${report.checksTotal}"
                    !report.passed -> report.failedStage ?: "SELFTEST"
                    else -> "CHECK TOTAL MISMATCH"
                }
            }
            val history = V75HardwarelessSelfTestHistoryRuntime.summary()
            maxHistory = maxOf(maxHistory, history.samples)
            if (history.samples > V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES && firstFailureRun == null) {
                firstFailureRun = index + 1
                firstFailureStage = "HISTORY BOUND"
            }
        }

        val history = V75HardwarelessSelfTestHistoryRuntime.summary()
        val bounded = maxHistory <= V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES && history.samples <= V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES
        val passed = firstFailureRun == null && passedRuns == runs && bounded &&
            minChecks == EXPECTED_CHECKS_PER_RUN && maxChecks == EXPECTED_CHECKS_PER_RUN &&
            history.failures == 0 && history.consecutivePasses == minOf(runs, V75HardwarelessSelfTestHistoryRuntime.MAX_SAMPLES)

        return V76HardwarelessSoakReport(
            requestedRuns = runs,
            completedRuns = completed,
            passedRuns = passedRuns,
            firstFailureRun = firstFailureRun,
            firstFailureStage = firstFailureStage,
            minChecksPerRun = minChecks,
            maxChecksPerRun = maxChecks,
            maxHistorySamples = maxHistory,
            finalHistorySamples = history.samples,
            finalHistoryFailures = history.failures,
            finalConsecutivePasses = history.consecutivePasses,
            passed = passed
        )
    }
}
