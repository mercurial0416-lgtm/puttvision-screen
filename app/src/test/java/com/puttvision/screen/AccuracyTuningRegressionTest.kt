package com.puttvision.screen

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyTuningRegressionTest {
    @Test
    fun learnsBoundedDeviceBiasFromReferenceShots() {
        val samples = (0 until 12).map { i ->
            val refBall = 1.20 + i * 0.03
            val refHead = 0.90 + i * 0.02
            val refLaunch = -0.40 + i * 0.07
            val refFace = -0.25 + i * 0.05
            val refPath = -0.15 + i * 0.04
            ValidationSample(
                id = i.toString(),
                timestampMs = i.toLong(),
                measuredBall = refBall / 1.04,
                measuredLaunch = refLaunch + 0.32,
                measuredHead = refHead / 0.97,
                measuredFace = refFace - 0.22,
                measuredPath = refPath + 0.18,
                confidence = 0.94,
                refBall = refBall,
                refLaunch = refLaunch,
                refHead = refHead,
                refFace = refFace,
                refPath = refPath
            )
        }

        val model = AccuracyModelCalculator.derive(samples)
        assertNotNull(model)
        model!!
        assertTrue(model.sampleCount == 12)
        assertTrue(model.improvementPct > 40.0)
        assertTrue(model.ballScale in 1.03..1.05)
        assertTrue(model.launchOffsetDeg in -0.34..-0.30)

        val raw = ShotMetrics(
            ballSpeedMps = 1.50 / 1.04,
            launchAngleDeg = 0.50 + 0.32,
            headSpeedMps = 1.05 / 0.97,
            faceAngleDeg = 0.30 - 0.22,
            pathAngleDeg = 0.20 + 0.18,
            faceToPathDeg = null,
            smash = null,
            impactOffsetMm = 0.0,
            measuredAtNs = 1L,
            confidence = 0.95
        )
        val corrected = AccuracyModelCalculator.apply(model, raw)
        assertTrue(kotlin.math.abs(corrected.ballSpeedMps - 1.50) < 0.02)
        assertTrue(kotlin.math.abs(corrected.launchAngleDeg - 0.50) < 0.03)
        assertTrue(kotlin.math.abs((corrected.headSpeedMps ?: 0.0) - 1.05) < 0.02)
        assertTrue(kotlin.math.abs((corrected.faceAngleDeg ?: 0.0) - 0.30) < 0.03)
        assertTrue(kotlin.math.abs((corrected.pathAngleDeg ?: 0.0) - 0.20) < 0.03)
    }

    @Test
    fun refusesToBuildModelFromTooFewReferenceShots() {
        val samples = (0 until 7).map { i ->
            ValidationSample(
                id = i.toString(), timestampMs = i.toLong(), measuredBall = 1.0,
                measuredLaunch = 0.0, measuredHead = null, measuredFace = null,
                measuredPath = null, confidence = 0.9, refBall = 1.02
            )
        }
        assertTrue(AccuracyModelCalculator.derive(samples) == null)
    }
}
