package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class V132VisualRebuildTest {
    @Test
    fun v132PresentationClassIsPackaged() {
        assertTrue(V132VisualRebuildFactory::class.java.name.contains("V132VisualRebuildFactory"))
    }
}
