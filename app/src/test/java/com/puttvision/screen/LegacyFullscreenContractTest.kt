package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFullscreenContractTest {
    private fun sourceFile(modulePath: String): String {
        val candidates = listOf(
            File(modulePath),
            File("app/$modulePath"),
            File("../app/$modulePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to locate $modulePath from ${File(".").absolutePath}")
    }

    @Test
    fun legacyFullscreenIsConfinedToHardwarelessGodotActivity() {
        val hardwareless = sourceFile("src/main/java/com/puttvision/screen/V144HardwarelessGodotActivity.kt")
        val mainActivity = sourceFile("src/main/java/com/puttvision/screen/MainActivity.kt")

        assertEquals(
            "Keep the deprecated systemUiVisibility migration surface confined to one LAB activity",
            1,
            Regex("\\.systemUiVisibility\\s*=").findAll(hardwareless).count()
        )
        assertFalse(
            "Do not reintroduce deprecated systemUiVisibility into MainActivity",
            Regex("\\.systemUiVisibility\\s*=").containsMatchIn(mainActivity)
        )
    }

    @Test
    fun productionMainActivityUsesInsetsControllerForImmersiveMode() {
        val source = sourceFile("src/main/java/com/puttvision/screen/MainActivity.kt")
        val compact = source.replace(Regex("\\s+"), "")

        assertTrue(compact.contains("WindowCompat.setDecorFitsSystemWindows(window,false)"))
        assertTrue(compact.contains("WindowInsetsControllerCompat(window,window.decorView)"))
        assertTrue(compact.contains("hide(WindowInsetsCompat.Type.systemBars())"))
        assertTrue(compact.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"))
    }
}
