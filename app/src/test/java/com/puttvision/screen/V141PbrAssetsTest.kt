package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class V141PbrAssetsTest {
    @Test
    fun helperIsLinkedIntoProductSources() {
        // Android/Filament API compatibility is verified by the flavor compile gates.
        // This smoke test keeps the V141 source in the unit-test compile graph explicitly.
        assertTrue(V141PbrAssets::class.java.simpleName.contains("V141"))
    }
}
