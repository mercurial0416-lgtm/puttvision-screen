package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class V25FlagInfoTest {
    @Test
    fun distanceAlwaysUsesMetersWithTwoDecimals() {
        assertEquals("남은거리 0.01 m", V25FlagInfo(0.01, 0.0).distanceLabel)
        assertEquals("남은거리 0.42 m", V25FlagInfo(0.42, 0.0).distanceLabel)
        assertEquals("남은거리 5.00 m", V25FlagInfo(5.0, 0.0).distanceLabel)
    }

    @Test
    fun heightAlwaysUsesSignedMetersWithTwoDecimals() {
        assertEquals("높이 +0.03 m", V25FlagInfo(1.0, 0.03).heightLabel)
        assertEquals("높이 -0.02 m", V25FlagInfo(1.0, -0.02).heightLabel)
        assertEquals("높이 +0.00 m", V25FlagInfo(1.0, 0.0).heightLabel)
    }
}
