package com.puttvision.screen

import org.junit.Assert.assertEquals
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
}
