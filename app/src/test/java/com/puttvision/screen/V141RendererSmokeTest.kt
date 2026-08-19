package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V141RendererSmokeTest {
    @Test
    fun activeRendererFactoryIsPresent() {
        assertEquals("V141FriendsPbrScreenGolfFactory", V141FriendsPbrScreenGolfFactory::class.java.simpleName)
        assertTrue(V141PbrAssets::class.java.simpleName.startsWith("V141"))
        assertTrue(V141SceneryAssets::class.java.simpleName.startsWith("V141"))
    }
}
