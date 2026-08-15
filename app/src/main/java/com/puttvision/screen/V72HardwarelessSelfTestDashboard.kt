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

        val current = V68HardwarelessStereoRuntime.run(currentSpeedMps, currentDirectionDeg)
        record("STEREO CURRENT", current.passed, current.reason)
        for (speed in matrixSpeeds) for (direction in matrixDirections) {
            val result = V68HardwarelessStereoSelfCheck.verify(speed, direction)
            record("STEREO MATRIX ${"%.2f".format(speed)} ${"%+.1f".format(direction)}", result.passed, result.reason)
        }

        val guards = V69HardwarelessStereoGuardRuntime.run()
        total += guards.checksTotal; passed += guards.checksPassed
        if (!guards.passed && firstFailure == null) firstFailure = "STEREO GUARDS"
        details += "STEREO GUARDS · ${guards.checksPassed}/${guards.checksTotal} · ${guards.reason}"

        val transport = V70HardwarelessTransportTimebaseRuntime.run()
        total += transport.checksTotal; passed += transport.checksPassed
        if (!transport.passed && firstFailure == null) firstFailure = "LAN/TIME"
        details += "LAN/TIME · ${transport.checksPassed}/${transport.checksTotal} · ${transport.reason}"

        val provenance = V71HardwarelessProvenanceRuntime.run()
        total += provenance.checksTotal; passed += provenance.checksPassed
        if (!provenance.passed && firstFailure == null) firstFailure = "PACKET BIND"
        details += "PACKET BIND · ${provenance.checksPassed}/${provenance.checksTotal} · ${provenance.reason}"

        val trainingResume = V74HardwarelessTrainingResumeRuntime.run()
        total += trainingResume.checksTotal; passed += trainingResume.checksPassed
        if (!trainingResume.passed && firstFailure == null) firstFailure = "TRAIN RESUME"
        details += "TRAIN RESUME · ${trainingResume.checksPassed}/${trainingResume.checksTotal} · ${trainingResume.reason}"

        val memoryGuard = V78HardwarelessMemoryGuardRuntime.run()
        total += memoryGuard.checksTotal; passed += memoryGuard.checksPassed
        if (!memoryGuard.passed && firstFailure == null) firstFailure = "HFR MEMORY"
        details += "HFR MEMORY · ${memoryGuard.checksPassed}/${memoryGuard.checksTotal} · ${memoryGuard.reason}"

        val lifecycle = V79HardwarelessLifecycleChurnRuntime.run()
        total += lifecycle.checksTotal; passed += lifecycle.checksPassed
        if (!lifecycle.passed && firstFailure == null) firstFailure = "LIFECYCLE"
        details += "LIFECYCLE · ${lifecycle.checksPassed}/${lifecycle.checksTotal} · ${lifecycle.reason}"

        val timing = V80HardwarelessTimingDistortionRuntime.run()
        total += timing.checksTotal; passed += timing.checksPassed
        if (!timing.passed && firstFailure == null) firstFailure = "TIMING DISTORTION"
        details += "TIMING DISTORTION · ${timing.checksPassed}/${timing.checksTotal} · ${timing.reason}"

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
    }
}
