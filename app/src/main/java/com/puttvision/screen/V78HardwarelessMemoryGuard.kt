package com.puttvision.screen

/** Deterministic no-hardware checks for the decoded HFR frame-cache memory guard. */
data class V78HardwarelessMemoryGuardReport(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V78HardwarelessMemoryGuardSuite {
    const val CHECKS_TOTAL = 7

    fun run(): V78HardwarelessMemoryGuardReport {
        var passed = 0
        var firstFailure: String? = null

        fun check(name: String, ok: Boolean) {
            if (ok) passed++ else if (firstFailure == null) firstFailure = name
        }

        val six1080p = V45HfrFrameCachePolicy.estimatedArgbBytes(1920, 1080, 6)
        val twentyFour1080p = V45HfrFrameCachePolicy.estimatedArgbBytes(1920, 1080, 24)
        val overflow = V45HfrFrameCachePolicy.estimatedArgbBytes(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)

        check("normal cache under budget", !V45HfrFrameCachePolicy.shouldEvict(6, six1080p))
        check("large cache over budget", V45HfrFrameCachePolicy.shouldEvict(24, twentyFour1080p))
        check("negative item count rejected", V45HfrFrameCachePolicy.shouldEvict(-1, 0L))
        check("negative byte count rejected", V45HfrFrameCachePolicy.shouldEvict(1, -1L))
        check("overflow saturates", overflow == Long.MAX_VALUE)
        check("overflow estimate evicted", V45HfrFrameCachePolicy.shouldEvict(1, overflow))
        check("exact byte boundary allowed", !V45HfrFrameCachePolicy.shouldEvict(1, V45HfrFrameCachePolicy.MAX_BYTES))

        val ok = passed == CHECKS_TOTAL
        return V78HardwarelessMemoryGuardReport(
            passed = ok,
            checksPassed = passed,
            checksTotal = CHECKS_TOTAL,
            reason = if (ok) "HFR cache accounting fail-closed and bounded" else firstFailure ?: "memory guard failure"
        )
    }
}

object V78HardwarelessMemoryGuardRuntime {
    @Volatile private var latest: V78HardwarelessMemoryGuardReport? = null

    fun run(): V78HardwarelessMemoryGuardReport =
        V78HardwarelessMemoryGuardSuite.run().also { latest = it }

    fun snapshot(): V78HardwarelessMemoryGuardReport? = latest

    fun clear() {
        latest = null
    }
}
