package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayLifecycleContractTest {
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
    fun refreshDoesNotRelaunchRendererAfterControllerStop() {
        val source = sourceFile("src/main/java/com/puttvision/screen/ExternalDisplayController.kt")
        val refreshBody = Regex(
            "fun\\s+refresh\\s*\\(\\s*\\)\\s*\\{([\\s\\S]*?)val\\s+displays\\s*=",
            RegexOption.MULTILINE
        ).find(source)?.groupValues?.get(1)
            ?: error("Unable to locate ExternalDisplayController.refresh() prologue")
        val compact = refreshBody.replace(Regex("\\s+"), "")

        assertTrue(
            "refresh() must reject queued DisplayManager callbacks after stop() before querying or launching displays",
            compact.contains("if(!started)return")
        )
    }
}
