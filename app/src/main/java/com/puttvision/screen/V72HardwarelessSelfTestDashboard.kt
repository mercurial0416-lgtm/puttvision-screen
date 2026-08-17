package com.puttvision.screen

/** Compact aggregate for the on-phone no-hardware lab. */
data class V72HardwarelessSelfTestReport(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val failedStage: String?,
    val details: List<String>
) {
    fun shortLabel(): String = if (passed) {
        "SELFTEST PASS · $checksPassed/$checksTotal"
    } else {
        "SELFTEST FAIL · $checksPassed/$checksTotal · ${failedStage ?: "UNKNOWN"}"
    }
}

object V72HardwarelessSelfTestDashboard {
    private val matrixSpeeds = doubleArrayOf(0.45, 1.20, 2.80)
    private val matrixDirections = doubleArrayOf(-5.0, 0.0, 5.0)

    fun run(currentSpeedMps: Double, currentDirectionDeg: Double): V72HardwarelessSelfTestReport {
        val details = ArrayList<String>()
        var passed = 0
        var total = 0
        var firstFailure: String? = null

        fun record(stage: String, ok: Boolean, detail: String) {
            total++
            if (ok) passed++ else if (firstFailure == null) firstFailure = stage
            details += "$stage · ${if (ok) "PASS" else "FAIL"} · $detail"
        }

        fun recordSuite(stage: String, ok: Boolean, checksPassed: Int, checksTotal: Int, detail: String) {
            total += checksTotal
            passed += checksPassed
            if (!ok && firstFailure == null) firstFailure = stage
            details += "$stage · $checksPassed/$checksTotal · $detail"
        }

        val current = V68HardwarelessStereoRuntime.run(currentSpeedMps, currentDirectionDeg)
        record("STEREO CURRENT", current.passed, current.reason)
        for (speed in matrixSpeeds) for (direction in matrixDirections) {
            val result = V68HardwarelessStereoSelfCheck.verify(speed, direction)
            record("STEREO MATRIX ${"%.2f".format(speed)} ${"%+.1f".format(direction)}", result.passed, result.reason)
        }

        val guards = V69HardwarelessStereoGuardRuntime.run()
        recordSuite("STEREO GUARDS", guards.passed, guards.checksPassed, guards.checksTotal, guards.reason)

        val transport = V70HardwarelessTransportTimebaseRuntime.run()
        recordSuite("LAN/TIME", transport.passed, transport.checksPassed, transport.checksTotal, transport.reason)

        val provenance = V71HardwarelessProvenanceRuntime.run()
        recordSuite("PACKET BIND", provenance.passed, provenance.checksPassed, provenance.checksTotal, provenance.reason)

        val trainingResume = V74HardwarelessTrainingResumeRuntime.run()
        recordSuite("TRAIN RESUME", trainingResume.passed, trainingResume.checksPassed, trainingResume.checksTotal, trainingResume.reason)

        val memoryGuard = V78HardwarelessMemoryGuardRuntime.run()
        recordSuite("HFR MEMORY", memoryGuard.passed, memoryGuard.checksPassed, memoryGuard.checksTotal, memoryGuard.reason)

        val lifecycle = V79HardwarelessLifecycleChurnRuntime.run()
        recordSuite("LIFECYCLE", lifecycle.passed, lifecycle.checksPassed, lifecycle.checksTotal, lifecycle.reason)

        val timing = V80HardwarelessTimingDistortionRuntime.run()
        recordSuite("TIMING DISTORTION", timing.passed, timing.checksPassed, timing.checksTotal, timing.reason)

        val trainingJourney = V82HardwarelessTrainingJourneyRuntime.run()
        recordSuite("TRAIN JOURNEY", trainingJourney.passed, trainingJourney.checksPassed, trainingJourney.checksTotal, trainingJourney.reason)

        val liveVisual = V83HardwarelessLiveTrackVisualRuntime.run()
        recordSuite("LIVE TRACK UI", liveVisual.passed, liveVisual.checksPassed, liveVisual.checksTotal, liveVisual.reason)

        val playback = V84HardwarelessPlaybackRuntime.run()
        recordSuite("LIVE PLAYBACK", playback.passed, playback.checksPassed, playback.checksTotal, playback.reason)

        val updateIntegrity = V97HardwarelessUpdateIntegrityRuntime.run()
        recordSuite(
            "UPDATE INTEGRITY",
            updateIntegrity.passed,
            updateIntegrity.checksPassed,
            updateIntegrity.checksTotal,
            updateIntegrity.reason
        )

        val updateStatus = V115HardwarelessUpdateStatusRuntime.run()
        recordSuite(
            "UPDATE STATUS",
            updateStatus.passed,
            updateStatus.checksPassed,
            updateStatus.checksTotal,
            updateStatus.reason
        )

        return V72HardwarelessSelfTestReport(passed == total, passed, total, firstFailure, details.toList())
    }
}

object V72HardwarelessSelfTestRuntime {
    @Volatile private var latest: V72HardwarelessSelfTestReport? = null
    fun run(speedMps: Double, directionDeg: Double): V72HardwarelessSelfTestReport =
        V72HardwarelessSelfTestDashboard.run(speedMps, directionDeg).also { latest = it }
    fun snapshot(): V72HardwarelessSelfTestReport? = latest
    fun clear() {
        latest = null
        V68HardwarelessStereoRuntime.clear()
        V69HardwarelessStereoGuardRuntime.clear()
        V70HardwarelessTransportTimebaseRuntime.clear()
        V71HardwarelessProvenanceRuntime.clear()
        V74HardwarelessTrainingResumeRuntime.clear()
        V78HardwarelessMemoryGuardRuntime.clear()
        V79HardwarelessLifecycleChurnRuntime.clear()
        V80HardwarelessTimingDistortionRuntime.clear()
        V82HardwarelessTrainingJourneyRuntime.clear()
        V83HardwarelessLiveTrackVisualRuntime.clear()
        V84HardwarelessPlaybackRuntime.clear()
        V97HardwarelessUpdateIntegrityRuntime.clear()
        V115HardwarelessUpdateStatusRuntime.clear()
    }
}
