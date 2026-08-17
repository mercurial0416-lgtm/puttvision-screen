package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V24AdaptiveTvQualityTest {
    private val light = 1
    private val severe = 3

    @Test fun severeHeatOverridesForcedHighQuality() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.HIGH,
            thermalStatus = severe,
            highFrameRateActive = false,
            cameraQuality = 100,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.PERFORMANCE, d.tier)
        assertEquals("발열 보호", d.reason)
    }

    @Test fun lightHeatDoesNotOverrideForcedHighQuality() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.HIGH,
            thermalStatus = light,
            highFrameRateActive = false,
            cameraQuality = 100,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.HIGH, d.tier)
    }

    @Test fun autoPrioritizesHfrBeforeCosmetics() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.AUTO,
            thermalStatus = null,
            highFrameRateActive = true,
            cameraQuality = 100,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.PERFORMANCE, d.tier)
        assertEquals("HFR 카메라 우선", d.reason)
    }

    @Test fun autoUsesBalancedTierForLightHeat() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.AUTO,
            thermalStatus = light,
            highFrameRateActive = false,
            cameraQuality = 100,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.BALANCED, d.tier)
    }

    @Test fun autoProtectsWeakCameraQuality() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.AUTO,
            thermalStatus = null,
            highFrameRateActive = false,
            cameraQuality = 71,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.BALANCED, d.tier)
        assertEquals("카메라 품질 우선", d.reason)
    }

    @Test fun performanceModeStaysPerformanceWithoutThermalSignal() {
        val d = V24TvQualityPlanner.decide(
            V24TvQualityMode.PERFORMANCE,
            thermalStatus = null,
            highFrameRateActive = false,
            cameraQuality = null,
            thermalLight = light,
            thermalSevere = severe
        )
        assertEquals(V24RenderTier.PERFORMANCE, d.tier)
    }

    @Test fun autoSafetyDowngradeIsImmediate() {
        assertFalse(V24TvTierStabilityPlanner.isUpgrade(V24RenderTier.HIGH, V24RenderTier.PERFORMANCE))
        assertEquals(
            V24RenderTier.PERFORMANCE,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.AUTO,
                currentTier = V24RenderTier.HIGH,
                candidateTier = V24RenderTier.PERFORMANCE,
                candidateStableMs = 0L
            )
        )
    }

    @Test fun autoCosmeticUpgradeWaitsForStableWindow() {
        assertTrue(V24TvTierStabilityPlanner.isUpgrade(V24RenderTier.PERFORMANCE, V24RenderTier.HIGH))
        assertEquals(
            V24RenderTier.PERFORMANCE,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.AUTO,
                currentTier = V24RenderTier.PERFORMANCE,
                candidateTier = V24RenderTier.HIGH,
                candidateStableMs = V24TvTierStabilityPlanner.AUTO_UPGRADE_HOLD_MS - 1L
            )
        )
    }

    @Test fun autoCosmeticUpgradeAppliesAfterStableWindow() {
        assertEquals(
            V24RenderTier.HIGH,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.AUTO,
                currentTier = V24RenderTier.PERFORMANCE,
                candidateTier = V24RenderTier.HIGH,
                candidateStableMs = V24TvTierStabilityPlanner.AUTO_UPGRADE_HOLD_MS
            )
        )
    }

    @Test fun autoIntermediateUpgradeIsAlsoHeld() {
        assertEquals(
            V24RenderTier.PERFORMANCE,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.AUTO,
                currentTier = V24RenderTier.PERFORMANCE,
                candidateTier = V24RenderTier.BALANCED,
                candidateStableMs = 400L
            )
        )
    }

    @Test fun forcedModesDoNotWaitForCosmeticHysteresis() {
        assertEquals(
            V24RenderTier.HIGH,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.HIGH,
                currentTier = V24RenderTier.PERFORMANCE,
                candidateTier = V24RenderTier.HIGH,
                candidateStableMs = 0L
            )
        )
    }

    @Test fun negativeStableDurationCannotBypassHold() {
        assertEquals(
            V24RenderTier.BALANCED,
            V24TvTierStabilityPlanner.resolve(
                V24TvQualityMode.AUTO,
                currentTier = V24RenderTier.BALANCED,
                candidateTier = V24RenderTier.HIGH,
                candidateStableMs = -1L
            )
        )
    }
}
