package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V73TrainingResumeIntegrityTest {
    private val now = 1_000_000L

    private fun state(
        blockIndex: Int = 2,
        shotInBlock: Int = 3,
        successesInBlock: Int = 2,
        totalShots: Int = 13,
        totalSuccesses: Int = 8,
        streak: Int = 1,
        paused: Boolean = false,
        savedAtMs: Long = now - 1_000L,
        startedAtMs: Long = now - 300_000L,
        pausedAtMs: Long = 0L,
        pausedAccumulatedMs: Long = 25_000L,
        completedBlocks: List<V73TrainingBlockResultState> = listOf(
            V73TrainingBlockResultState(0, 5, 4),
            V73TrainingBlockResultState(1, 5, 2)
        )
    ) = V73TrainingResumeState(
        blockShots = listOf(5, 5, 6, 4),
        blockIndex = blockIndex,
        shotInBlock = shotInBlock,
        successesInBlock = successesInBlock,
        totalShots = totalShots,
        totalSuccesses = totalSuccesses,
        streak = streak,
        paused = paused,
        savedAtMs = savedAtMs,
        startedAtMs = startedAtMs,
        pausedAtMs = pausedAtMs,
        pausedAccumulatedMs = pausedAccumulatedMs,
        completedBlocks = completedBlocks
    )

    @Test fun validMidSessionStateResumes() {
        assertTrue(V73TrainingResumeIntegrity.evaluate(state(), now).valid)
    }

    @Test fun completedBlockHistoryMustMatchActiveBlock() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(
            state(completedBlocks = listOf(V73TrainingBlockResultState(0, 5, 4))), now
        ).valid)
    }

    @Test fun completedBlockHistoryMustRemainSequential() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(
            state(completedBlocks = listOf(
                V73TrainingBlockResultState(0, 5, 4),
                V73TrainingBlockResultState(2, 5, 2)
            )), now
        ).valid)
    }

    @Test fun corruptedCompletedBlockCountersFailClosed() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(
            state(completedBlocks = listOf(
                V73TrainingBlockResultState(0, 6, 4),
                V73TrainingBlockResultState(1, 5, 2)
            )), now
        ).valid)
        assertFalse(V73TrainingResumeIntegrity.evaluate(
            state(completedBlocks = listOf(
                V73TrainingBlockResultState(0, 5, 6),
                V73TrainingBlockResultState(1, 5, 2)
            )), now
        ).valid)
    }

    @Test fun activeCountersCannotContradictEachOther() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(shotInBlock = 2, successesInBlock = 3), now).valid)
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(totalShots = 4, totalSuccesses = 5), now).valid)
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(successesInBlock = 2, streak = 3), now).valid)
    }

    @Test fun staleOrFutureStateFailsClosed() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(
            state(savedAtMs = now - V73TrainingResumeIntegrity.MAX_RESUME_AGE_MS - 1L), now
        ).valid)
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(savedAtMs = now + 2_001L), now).valid)
    }

    @Test fun pauseTimestampsMustBeCoherent() {
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(paused = true, pausedAtMs = 0L), now).valid)
        assertFalse(V73TrainingResumeIntegrity.evaluate(state(paused = false, pausedAtMs = now - 5_000L), now).valid)
        assertTrue(V73TrainingResumeIntegrity.evaluate(
            state(paused = true, pausedAtMs = now - 10_000L), now
        ).valid)
    }
}
