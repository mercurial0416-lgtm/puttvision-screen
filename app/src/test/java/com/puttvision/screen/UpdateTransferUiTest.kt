package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTransferUiTest {
    @Test
    fun firstProgressSamplePublishesImmediately() {
        assertTrue(UpdateTransferUi.shouldPublish(-1L, 0L))
    }

    @Test
    fun progressUpdatesAreThrottledToAvoidUiFlooding() {
        val step = UpdateTransferUi.PUBLISH_STEP_BYTES
        assertFalse(UpdateTransferUi.shouldPublish(10L * step, 10L * step + step - 1L))
        assertTrue(UpdateTransferUi.shouldPublish(10L * step, 11L * step))
    }

    @Test
    fun negativeTransferCountersNeverPublish() {
        assertFalse(UpdateTransferUi.shouldPublish(-1L, -1L))
    }

    @Test
    fun downloadMessageUsesHumanReadableMib() {
        assertEquals("다운로드 중 · 0.0 MB", UpdateTransferUi.downloadMessage(-1L))
        assertEquals("다운로드 중 · 12.5 MB", UpdateTransferUi.downloadMessage(13_107_200L))
    }
}
