package com.puttvision.screen

/** Hardwareless fault-injection coverage for V73 training-session resume integrity. */
data class V74TrainingResumeSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
)

object V74HardwarelessTrainingResumeSuite {
    private const val NOW_MS = 2_000_000L

    fun verify(): V74TrainingResumeSuiteResult {
        val good = state()
        fun accepted(value: V73TrainingResumeState) =
            V73TrainingResumeIntegrity.evaluate(value, NOW_MS).valid

        val checks = listOf(
            "valid resumed session accepted" to accepted(good),
            "missing completed block history rejected" to !accepted(good.copy(completedBlocks = good.completedBlocks.dropLast(1))),
            "impossible completed attempts rejected" to !accepted(good.copy(
                completedBlocks = listOf(
                    V73TrainingBlockResultState(0, 5, 4),
                    V73TrainingBlockResultState(1, 6, 2)
                )
            )),
            "active success overflow rejected" to !accepted(good.copy(shotInBlock = 2, successesInBlock = 3)),
            "stale resume rejected" to !accepted(good.copy(savedAtMs = NOW_MS - V73TrainingResumeIntegrity.MAX_RESUME_AGE_MS - 1L)),
            "running session with pause timestamp rejected" to !accepted(good.copy(pausedAtMs = NOW_MS - 5_000L)),
            "coherent paused session accepted" to accepted(good.copy(paused = true, pausedAtMs = NOW_MS - 8_000L))
        )
        val passed = checks.count { it.second }
        return V74TrainingResumeSuiteResult(
            passed = passed == checks.size,
            checksPassed = passed,
            checksTotal = checks.size,
            reason = checks.firstOrNull { !it.second }?.first ?: "training resume accept/reject guards verified"
        )
    }

    private fun state() = V73TrainingResumeState(
        blockShots = listOf(5, 5, 6, 4),
        blockIndex = 2,
        shotInBlock = 3,
        successesInBlock = 2,
        totalShots = 13,
        totalSuccesses = 8,
        streak = 1,
        paused = false,
        savedAtMs = NOW_MS - 1_000L,
        startedAtMs = NOW_MS - 300_000L,
        pausedAtMs = 0L,
        pausedAccumulatedMs = 25_000L,
        completedBlocks = listOf(
            V73TrainingBlockResultState(0, 5, 4),
            V73TrainingBlockResultState(1, 5, 2)
        )
    )
}

object V74HardwarelessTrainingResumeRuntime {
    @Volatile private var latest: V74TrainingResumeSuiteResult? = null

    fun run(): V74TrainingResumeSuiteResult = V74HardwarelessTrainingResumeSuite.verify().also { latest = it }
    fun snapshot(): V74TrainingResumeSuiteResult? = latest
    fun clear() { latest = null }
}
