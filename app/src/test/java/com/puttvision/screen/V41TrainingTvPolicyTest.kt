package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V41TrainingTvPolicyTest {
    private fun progress(running: Boolean = false, finished: Boolean = false) = V31TrainingProgress(
        running = running,
        finished = finished,
        blockIndex = 0,
        blockCount = 4,
        shotInBlock = 2,
        shotsInBlock = 8,
        successesInBlock = 1,
        totalShots = 2,
        totalSuccesses = 1,
        streak = 1,
        blockTitle = "워밍업",
        targetDistanceM = 1.5,
        summary = "대기"
    )

    @Test fun layoutScalesAcrossCommonTvResolutions() {
        val hd = V41TrainingTvPolicy.layout(1280, 720)
        val fhd = V41TrainingTvPolicy.layout(1920, 1080)
        val uhd = V41TrainingTvPolicy.layout(3840, 2160)
        assertTrue(hd.width < fhd.width)
        assertTrue(uhd.width > fhd.width)
        assertEquals(1f, fhd.scale, .001f)
        assertEquals(2f, uhd.scale, .001f)
        assertTrue(hd.width <= 1280f * .42f)
    }

    @Test fun activeTrainingRefreshesFastButFinishedAndIdleDoNotSpin() {
        assertEquals(250L, V41TrainingTvPolicy.refreshDelayMs(progress(running = true)))
        assertEquals(1_500L, V41TrainingTvPolicy.refreshDelayMs(progress(finished = true)))
        assertEquals(1_000L, V41TrainingTvPolicy.refreshDelayMs(progress()))
    }
}
