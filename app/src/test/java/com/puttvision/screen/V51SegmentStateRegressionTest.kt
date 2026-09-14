package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V51SegmentStateRegressionTest {
    @Test fun activeBlockSeparatesFinishedCurrentAndPendingSegments() {
        assertEquals(
            listOf(-1, -1, 0, 1, 1),
            V51VisualPolicy.segmentStates(blockIndex = 2, blockCount = 5, finished = false)
        )
    }

    @Test fun firstBlockLeavesOnlyFirstSegmentActive() {
        assertEquals(
            listOf(0, 1, 1),
            V51VisualPolicy.segmentStates(blockIndex = 0, blockCount = 3, finished = false)
        )
    }

    @Test fun finishedSessionMarksEverySegmentComplete() {
        assertEquals(
            listOf(-1, -1, -1, -1),
            V51VisualPolicy.segmentStates(blockIndex = 1, blockCount = 4, finished = true)
        )
    }

    @Test fun nonPositiveBlockCountProducesNoHudSegments() {
        assertTrue(V51VisualPolicy.segmentStates(0, 0, finished = false).isEmpty())
        assertTrue(V51VisualPolicy.segmentStates(0, -1, finished = false).isEmpty())
    }

    @Test fun preStartIndexKeepsEverySegmentPending() {
        assertEquals(
            listOf(1, 1, 1),
            V51VisualPolicy.segmentStates(blockIndex = -1, blockCount = 3, finished = false)
        )
    }

    @Test fun indexPastLastBlockTreatsEverySegmentAsComplete() {
        assertEquals(
            listOf(-1, -1, -1),
            V51VisualPolicy.segmentStates(blockIndex = 3, blockCount = 3, finished = false)
        )
        assertEquals(
            listOf(-1, -1, -1),
            V51VisualPolicy.segmentStates(blockIndex = Int.MAX_VALUE, blockCount = 3, finished = false)
        )
    }
}
