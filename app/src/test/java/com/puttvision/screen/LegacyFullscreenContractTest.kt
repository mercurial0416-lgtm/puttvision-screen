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

    private fun productionSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen"),
            File("app/src/main/java/com/puttvision/screen"),
            File("../app/src/main/java/com/puttvision/screen")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Unable to locate production Kotlin source root from ${File(".").absolutePath}")
    }

    @Test
    fun productionCodeHasNoLegacyFullscreenAssignments() {
        val assignment = Regex("\\.systemUiVisibility\\s*=")
        val sourceRoot = productionSourceRoot()
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val count = assignment.findAll(file.readText()).count()
                if (count == 0) null else file.relativeTo(sourceRoot).invariantSeparatorsPath to count
            }
            .toList()

        assertEquals(
            "Do not reintroduce deprecated systemUiVisibility assignments into production code",
            emptyList<Pair<String, Int>>(),
            offenders
        )
    }

    private fun assertModernImmersive(source: String) {
        val compact = source.replace(Regex("\\s+"), "")
        assertFalse(
            "Do not use deprecated systemUiVisibility for immersive mode",
            Regex("\\.systemUiVisibility\\s*=").containsMatchIn(source)
        )
        assertTrue(compact.contains("WindowCompat.setDecorFitsSystemWindows(window,false)"))
        assertTrue(compact.contains("WindowInsetsControllerCompat(window,window.decorView)"))
        assertTrue(compact.contains("hide(WindowInsetsCompat.Type.systemBars())"))
        assertTrue(compact.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"))
    }

    @Test
    fun productionMainActivityUsesInsetsControllerForImmersiveMode() {
        assertModernImmersive(sourceFile("src/main/java/com/puttvision/screen/MainActivity.kt"))
    }

    @Test
    fun hardwarelessGodotActivityUsesInsetsControllerForImmersiveMode() {
        assertModernImmersive(sourceFile("src/main/java/com/puttvision/screen/V144HardwarelessGodotActivity.kt"))
    }
}
