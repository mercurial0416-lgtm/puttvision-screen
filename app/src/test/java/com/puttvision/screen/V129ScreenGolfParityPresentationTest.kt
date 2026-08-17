package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V129ScreenGolfParityPresentationTest {
    @Test
    fun addressKeepsFullReadAndIdleCadence() {
        val p = V129PresentationPlanner.plan(
            width = 1920,
            height = 1080,
            running = false,
            progressRaw = 0.0,
            distanceToCupRaw = 5.0,
            result = null,
            resultAgeMsRaw = Long.MAX_VALUE,
            hasShot = false
        )
        assertEquals(V129PresentationPhase.ADDRESS, p.phase)
        assertTrue(p.showHolePlate)
        assertTrue(p.showMiniMap)
        assertTrue(p.showConditions)
        assertTrue(p.showGreenRead)
        assertEquals(120L, p.refreshMs)
    }

    @Test
    fun rollingReducesChromeAndRunsAtMotionCadence() {
        val p = V129PresentationPlanner.plan(1920, 1080, true, .42, 2.9, null, 0L, true)
        assertEquals(V129PresentationPhase.ROLL, p.phase)
        assertTrue(p.showShotMetrics)
        assertFalse(p.showConditions)
        assertTrue(p.showCenterStatus)
        assertTrue(p.chromeAlpha < 180)
        assertEquals(16L, p.refreshMs)
    }

    @Test
    fun cupApproachClearsThePlayfield() {
        val p = V129PresentationPlanner.plan(1920, 1080, true, .84, .62, null, 0L, true)
        assertEquals(V129PresentationPhase.CUP_APPROACH, p.phase)
        assertFalse(p.showMiniMap)
        assertFalse(p.showShotMetrics)
        assertFalse(p.showGreenRead)
        assertTrue(p.cupFocus > .5f)
        assertEquals(16L, p.refreshMs)
    }

    @Test
    fun holedResultGetsBoundedWhiteFlashAndResultCard() {
        val result = SimResult(true, 0.0, 5.0, 0.0, 2.0)
        val early = V129PresentationPlanner.plan(1920, 1080, false, 1.0, 0.0, result, 80L, true)
        val old = V129PresentationPlanner.plan(1920, 1080, false, 1.0, 0.0, result, 4000L, true)
        assertEquals(V129PresentationPhase.HOLED, early.phase)
        assertTrue(early.showResultCard)
        assertTrue(early.resultFlash in 0f..1f)
        assertTrue(early.resultFlash > old.resultFlash)
        assertEquals(0f, old.resultFlash, .0001f)
        assertEquals(120L, old.refreshMs)
    }

    @Test
    fun lipOutUsesSeparateResultPhase() {
        val result = SimResult(false, .08, 5.05, .10, 2.2, lipOut = true, cupContacts = 1)
        val p = V129PresentationPlanner.plan(1920, 1080, false, 1.0, .10, result, 120L, true)
        assertEquals(V129PresentationPhase.LIP_OUT, p.phase)
        assertTrue(p.resultFlash <= .72f)
        assertTrue(p.cupFocus > 0f)
        assertTrue(p.showResultCard)
    }

    @Test
    fun malformedProgressFailsSafeToNormalRoll() {
        val p = V129PresentationPlanner.plan(1920, 1080, true, Double.NaN, Double.NaN, null, -50L, true)
        assertEquals(V129PresentationPhase.ROLL, p.phase)
        assertTrue(p.resultFlash.isFinite())
        assertTrue(p.cupFocus.isFinite())
        assertEquals(16L, p.refreshMs)
    }

    @Test
    fun narrowScreenUsesCompactSafeFrameAndHidesMiniMap() {
        val p = V129PresentationPlanner.plan(1080, 1920, false, 0.0, 5.0, null, 0L, false)
        assertTrue(p.safe.compact)
        assertFalse(p.showMiniMap)
        assertTrue(p.safe.left > 0f)
        assertTrue(p.safe.right < 1080f)
        assertTrue(p.safe.bottom < 1920f)
    }

    @Test
    fun ultrawideKeepsAllHudInsideSafeMargins() {
        val s = V129PresentationPlanner.safeFrame(2560, 1080)
        assertFalse(s.compact)
        assertTrue(s.left > 0f)
        assertTrue(s.top > 0f)
        assertTrue(s.right < 2560f)
        assertTrue(s.bottom < 1080f)
        assertTrue(s.right > s.left)
        assertTrue(s.bottom > s.top)
    }
}
