package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDisplayFallbackRegressionTest {
    private fun controllerSource(): String {
        val relative = "src/main/java/com/puttvision/screen/ExternalDisplayController.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate ExternalDisplayController.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun failedFallbackShowIsNeverRetainedAsHealthyPresentation() {
        val source = controllerSource()
        val start = source.indexOf("private fun showFallback(display: Display, reason: String) {")
        val end = source.indexOf("override fun onDisplayAdded", start)
        require(start >= 0 && end > start) { "Unable to locate showFallback" }
        val fallback = source.substring(start, end)

        val show = fallback.indexOf("candidate.show()")
        val retain = fallback.indexOf("presentation = candidate")
        val catch = fallback.indexOf("catch (e: Throwable)")

        assertTrue("fallback must be shown before it is retained", show >= 0 && retain > show)
        assertTrue("failed show must bypass retained presentation assignment", catch > retain)
        assertTrue("failed candidate should be dismissed defensively", fallback.contains("candidate.dismiss()"))
        assertFalse(
            "assignment through also can retain a failed Presentation after catch",
            fallback.contains("presentation = GamePresentation(context, display, engine).also")
        )
    }
}
