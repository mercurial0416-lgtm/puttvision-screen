package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V128CommercialScreenGolfRenderTest {
    @Test
    fun highTierKeepsMoreGeometryThanPerformanceTier() {
        val high = V128WorldPlanner.plan(V24RenderTier.HIGH, 5.0)
        val low = V128WorldPlanner.plan(V24RenderTier.PERFORMANCE, 5.0)
        assertTrue(high.greenCols > low.greenCols)
        assertTrue(high.greenRows > low.greenRows)
        assertTrue(high.treeCount > low.treeCount)
        assertTrue(high.movingFrameMs < low.movingFrameMs)
    }

    @Test
    fun visualBallScaleStaysBoundedAndGrowsNearCup() {
        val start = V128WorldPlanner.visualBallRadius(0.0)
        val nearCup = V128WorldPlanner.visualBallRadius(1.0)
        assertTrue(start >= .026f)
        assertTrue(nearCup <= .038f)
        assertTrue(nearCup > start)
        assertEquals(.026f, V128WorldPlanner.visualBallRadius(Double.NaN), 0.0001f)
    }

    @Test
    fun addressCameraLooksForwardFromBehindBall() {
        val camera = V128ScreenGolfCameraPlanner.plan(
            distanceM = 5.0,
            startX = 0.0,
            startY = 0.0,
            ballX = 0.0,
            ballY = 0.0,
            running = false,
            result = null
        )
        assertTrue(camera.eyeY < 0f)
        assertTrue(camera.lookY > 0f)
        assertTrue(camera.eyeZ > .8f)
        assertTrue(camera.fovDeg in 40f..50f)
    }

    @Test
    fun runningCameraTracksPhysicsForward() {
        val early = V128ScreenGolfCameraPlanner.plan(8.0, 0.0, 0.0, .1, 1.0, true, null)
        val late = V128ScreenGolfCameraPlanner.plan(8.0, 0.0, 0.0, .2, 6.5, true, null)
        assertTrue(late.eyeY > early.eyeY)
        assertTrue(late.lookY > early.lookY)
        assertTrue(late.fovDeg <= early.fovDeg)
    }

    @Test
    fun cupResultUsesTighterCamera() {
        val result = SimResult(
            holed = true,
            finishX = 0.0,
            finishY = 5.0,
            distanceToCupM = 0.0,
            elapsedSec = 2.0
        )
        val camera = V128ScreenGolfCameraPlanner.plan(5.0, 0.0, 0.0, 0.0, 5.0, false, result)
        assertTrue(camera.fovDeg < 42f)
        assertTrue(camera.eyeY > 3.0f)
        assertTrue(camera.lookY >= 5.0f)
    }

    @Test
    fun hudRefreshesAtMotionRateWithoutPermanentAnimation() {
        val moving = V128HudPlanner.plan(running = true, hasShot = true, hasResult = false, resultAgeMs = 0L)
        val idle = V128HudPlanner.plan(running = false, hasShot = false, hasResult = false, resultAgeMs = Long.MAX_VALUE)
        val result = V128HudPlanner.plan(running = false, hasShot = true, hasResult = true, resultAgeMs = 500L)
        assertEquals(16L, moving.refreshMs)
        assertTrue(idle.refreshMs >= 100L)
        assertTrue(result.showResult)
        assertTrue(result.refreshMs < idle.refreshMs)
    }
}
