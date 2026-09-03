package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HardwarelessGreenPresetTest {
    @Test
    fun leftAndRightBreaksMirrorSideSlope() {
        val left = HardwarelessGreenPreset.LEFT_BREAK
        val right = HardwarelessGreenPreset.RIGHT_BREAK

        assertEquals(abs(left.sideSlopePct), abs(right.sideSlopePct), 1e-9)
        assertTrue(left.sideSlopePct < 0.0)
        assertTrue(right.sideSlopePct > 0.0)
    }

    @Test
    fun uphillAndDownhillMirrorLongSlope() {
        val uphill = HardwarelessGreenPreset.UPHILL
        val downhill = HardwarelessGreenPreset.DOWNHILL

        assertEquals(abs(uphill.longSlopePct), abs(downhill.longSlopePct), 1e-9)
        assertTrue(uphill.longSlopePct > 0.0)
        assertTrue(downhill.longSlopePct < 0.0)
    }

    @Test
    fun everyDrillRemainsClearlyNonFlat() {
        HardwarelessGreenPreset.entries.forEach { preset ->
            assertTrue("${preset.name} grade must remain visible", preset.gradePct >= 1.0)
        }
    }
}
