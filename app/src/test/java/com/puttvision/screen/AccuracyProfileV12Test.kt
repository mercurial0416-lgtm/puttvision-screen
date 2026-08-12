package com.puttvision.screen

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyProfileV12Test {
    @Test fun captureModesGetDifferentStableKeys() {
        val a = AccuracyProfileKey.build("Samsung S25", "0", 240, "1920x1080", 36)
        val b = AccuracyProfileKey.build("Samsung S25", "0", 120, "1920x1080", 36)
        val c = AccuracyProfileKey.build("Samsung S25", "0", 240, "1280x720", 36)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(AccuracyProfileKey.slot(a), AccuracyProfileKey.slot(b))
        assertTrue(AccuracyProfileKey.slot(a).startsWith("model_"))
    }
}
