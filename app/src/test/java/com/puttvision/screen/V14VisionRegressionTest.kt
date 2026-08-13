package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V14VisionRegressionTest {
    @Test fun robustFitRejectsOneBadCentroid() {
        val speed = 1.50
        val angle = Math.toRadians(2.0)
        val points = (0..14).map { i ->
            val t = i * .006
            val x = Math.sin(angle) * speed * t * 100.0
            val y = Math.cos(angle) * speed * t * 100.0
            V14TimedPoint(t, if (i == 7) x + 2.8 else x, if (i == 7) y - 2.2 else y)
        }
        val fit = V14RobustKinematics.fit(points)
        assertNotNull(fit)
        assertEquals(1.50, fit!!.speedMps, .08)
        assertEquals(2.0, fit.launchAngleDeg, .45)
    }

    @Test fun markedBallRollChannelCanFindTransition() {
        val fps = 240
        val v = 1.4
        val r = .02135
        val target = v / r
        var angle = 0.0
        val samples = (0..24).map { i ->
            val omega = target * if (i < 6) (i / 6.0) else 1.0
            angle += Math.toDegrees(omega / fps)
            V14BallRollAnalyzer.MarkerSample(i, i * .6, angle % 360.0)
        }
        val roll = V14BallRollAnalyzer.analyze(samples, fps, v)
        assertNotNull(roll)
        assertTrue(roll!!.markedBall)
        assertTrue((roll.spinRpm ?: 0.0) > 200.0)
        assertTrue((roll.rollStartDistanceCm ?: 99.0) < 10.0)
    }

    @Test fun empiricalP95RequiresRealReferenceVolume() {
        val samples = (0 until 24).map { i ->
            ValidationSample("$i", i.toLong(), 1.40 + (i%3-.0)*.001, .20, 1.0, .1, .1, .95, "P", refBall=1.42, refLaunch=.25, refHead=1.02, refFace=.15, refPath=.12)
        }
        val m = ShotMetrics(1.4,.2,1.0,.1,.1,0.0,1.4,0.0,0L)
        val out = V14EmpiricalUncertainty.apply(m,samples,"P",null)
        assertTrue(out.uncertainty?.basis?.startsWith("LAB P95") == true)
    }
}
