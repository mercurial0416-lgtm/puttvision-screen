package com.puttvision.screen

/**
 * Hardwareless lifecycle churn checks for stateful regression runtimes.
 *
 * Each check exercises the same pattern a test-screen close/re-entry causes: publish state, clear it,
 * verify no stale snapshot survives, then run again and require a fresh passing snapshot. This is a
 * software lifecycle invariant only; it does not claim physical-device accuracy.
 */
data class V79LifecycleChurnReport(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V79HardwarelessLifecycleChurnRuntime {
    @Volatile private var latest: V79LifecycleChurnReport? = null

    fun run(): V79LifecycleChurnReport {
        var passed = 0
        var total = 0
        val failures = ArrayList<String>()

        fun check(name: String, block: () -> Boolean) {
            total++
            val ok = runCatching(block).getOrDefault(false)
            if (ok) passed++ else failures += name
        }

        check("STEREO CLEAR/RERUN") {
            V68HardwarelessStereoRuntime.clear()
            val cleared = V68HardwarelessStereoRuntime.snapshot() == null
            val rerun = V68HardwarelessStereoRuntime.run(1.20, 0.0)
            cleared && rerun.passed && V68HardwarelessStereoRuntime.snapshot() === rerun
        }
        check("STEREO GUARDS CLEAR/RERUN") {
            V69HardwarelessStereoGuardRuntime.clear()
            val cleared = V69HardwarelessStereoGuardRuntime.snapshot() == null
            val rerun = V69HardwarelessStereoGuardRuntime.run()
            cleared && rerun.passed && V69HardwarelessStereoGuardRuntime.snapshot() === rerun
        }
        check("LAN/TIME CLEAR/RERUN") {
            V70HardwarelessTransportTimebaseRuntime.clear()
            val cleared = V70HardwarelessTransportTimebaseRuntime.snapshot() == null
            val rerun = V70HardwarelessTransportTimebaseRuntime.run()
            cleared && rerun.passed && V70HardwarelessTransportTimebaseRuntime.snapshot() === rerun
        }
        check("PACKET BIND CLEAR/RERUN") {
            V71HardwarelessProvenanceRuntime.clear()
            val cleared = V71HardwarelessProvenanceRuntime.snapshot() == null
            val rerun = V71HardwarelessProvenanceRuntime.run()
            cleared && rerun.passed && V71HardwarelessProvenanceRuntime.snapshot() === rerun
        }
        check("TRAIN RESUME CLEAR/RERUN") {
            V74HardwarelessTrainingResumeRuntime.clear()
            val cleared = V74HardwarelessTrainingResumeRuntime.snapshot() == null
            val rerun = V74HardwarelessTrainingResumeRuntime.run()
            cleared && rerun.passed && V74HardwarelessTrainingResumeRuntime.snapshot() === rerun
        }
        check("HFR MEMORY CLEAR/RERUN") {
            V78HardwarelessMemoryGuardRuntime.clear()
            val cleared = V78HardwarelessMemoryGuardRuntime.snapshot() == null
            val rerun = V78HardwarelessMemoryGuardRuntime.run()
            cleared && rerun.passed && V78HardwarelessMemoryGuardRuntime.snapshot() === rerun
        }
        check("ALL RUNTIMES REPOPULATED") {
            V68HardwarelessStereoRuntime.snapshot() != null &&
                V69HardwarelessStereoGuardRuntime.snapshot() != null &&
                V70HardwarelessTransportTimebaseRuntime.snapshot() != null &&
                V71HardwarelessProvenanceRuntime.snapshot() != null &&
                V74HardwarelessTrainingResumeRuntime.snapshot() != null &&
                V78HardwarelessMemoryGuardRuntime.snapshot() != null
        }

        return V79LifecycleChurnReport(
            passed = passed == total,
            checksPassed = passed,
            checksTotal = total,
            reason = if (failures.isEmpty()) "clear/rerun lifecycle clean" else failures.joinToString(", ")
        ).also { latest = it }
    }

    fun snapshot(): V79LifecycleChurnReport? = latest

    fun clear() {
        latest = null
    }
}
