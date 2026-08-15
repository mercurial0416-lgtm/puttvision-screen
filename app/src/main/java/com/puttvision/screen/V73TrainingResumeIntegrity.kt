package com.puttvision.screen

/**
 * Pure fail-closed validation for persisted 15-minute training progress.
 *
 * SharedPreferences/JSON parsing stays in V31, while this object verifies the relationships that
 * must still be true after process death before the runtime is allowed to resume a session.
 */
data class V73TrainingBlockResultState(
    val blockIndex: Int,
    val attempts: Int,
    val successes: Int
)

data class V73TrainingResumeState(
    val blockShots: List<Int>,
    val blockIndex: Int,
    val shotInBlock: Int,
    val successesInBlock: Int,
    val totalShots: Int,
    val totalSuccesses: Int,
    val streak: Int,
    val paused: Boolean,
    val savedAtMs: Long,
    val startedAtMs: Long,
    val pausedAtMs: Long,
    val pausedAccumulatedMs: Long,
    val completedBlocks: List<V73TrainingBlockResultState>
)

data class V73TrainingResumeDecision(
    val valid: Boolean,
    val reason: String
)

object V73TrainingResumeIntegrity {
    const val MAX_RESUME_AGE_MS = 6L * 60L * 60L * 1000L
    private const val MAX_FUTURE_MS = 2_000L

    fun evaluate(state: V73TrainingResumeState, nowMs: Long): V73TrainingResumeDecision {
        fun deny(reason: String) = V73TrainingResumeDecision(false, reason)
        if (nowMs <= 0L) return deny("current time invalid")
        if (state.blockShots.isEmpty() || state.blockShots.any { it !in 1..100 }) return deny("training block shots invalid")
        if (state.blockIndex !in state.blockShots.indices) return deny("active block index invalid")
        val currentShots = state.blockShots[state.blockIndex]
        if (state.shotInBlock !in 0..currentShots) return deny("active block attempt count invalid")
        if (state.successesInBlock !in 0..state.shotInBlock) return deny("active block successes invalid")
        if (state.totalShots < 0 || state.totalSuccesses !in 0..state.totalShots) return deny("session totals invalid")
        if (state.totalSuccesses < state.successesInBlock) return deny("session successes below active block successes")
        if (state.streak !in 0..state.successesInBlock) return deny("active streak invalid")

        val age = nowMs - state.savedAtMs
        if (state.savedAtMs <= 0L || age !in -MAX_FUTURE_MS..MAX_RESUME_AGE_MS) return deny("saved training state stale or future")
        if (state.startedAtMs <= 0L || state.startedAtMs > state.savedAtMs + MAX_FUTURE_MS) return deny("training start timestamp invalid")
        if (state.pausedAccumulatedMs < 0L) return deny("paused duration invalid")
        val lifetime = (state.savedAtMs - state.startedAtMs).coerceAtLeast(0L)
        if (state.pausedAccumulatedMs > lifetime + MAX_FUTURE_MS) return deny("paused duration exceeds session lifetime")
        if (state.paused) {
            if (state.pausedAtMs <= 0L || state.pausedAtMs < state.startedAtMs || state.pausedAtMs > state.savedAtMs + MAX_FUTURE_MS) {
                return deny("paused session timestamp invalid")
            }
        } else if (state.pausedAtMs != 0L) {
            return deny("running session retained paused timestamp")
        }

        // V31 appends exactly one BlockResult whenever it advances past a block. A resume state
        // claiming to be on block N therefore needs exactly N preceding results, in order.
        if (state.completedBlocks.size != state.blockIndex) return deny("completed block result count does not match active block")
        state.completedBlocks.forEachIndexed { expectedIndex, result ->
            if (result.blockIndex != expectedIndex) return deny("completed block order invalid")
            val scheduled = state.blockShots.getOrNull(result.blockIndex) ?: return deny("completed block index invalid")
            if (result.attempts !in 0..scheduled) return deny("completed block attempts invalid")
            if (result.successes !in 0..result.attempts) return deny("completed block successes invalid")
        }

        return V73TrainingResumeDecision(true, "training resume state internally consistent")
    }
}
