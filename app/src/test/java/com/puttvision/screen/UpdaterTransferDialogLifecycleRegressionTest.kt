package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTransferDialogLifecycleRegressionTest {
    private fun updaterSource(): String {
        val relative = "src/main/java/com/puttvision/screen/AppUpdater.kt"
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate AppUpdater.kt from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun transferDialogIsDismissedAcrossSuccessFailureAndClosePaths() {
        val source = updaterSource()

        val showStart = source.indexOf("private fun showTransferDialog(versionName: String)")
        val showEnd = source.indexOf("private fun updateTransferDialog(message: String)", showStart)
        require(showStart >= 0 && showEnd > showStart) { "Unable to locate transfer dialog show flow" }
        val show = source.substring(showStart, showEnd)
        assertTrue(
            "opening a transfer dialog must dismiss any stale instance first",
            show.indexOf("dismissTransferDialog()") >= 0 &&
                show.indexOf("dismissTransferDialog()") < show.indexOf("transferDialog =")
        )

        val successMarker = source.indexOf("failedApk = null")
        val installMarker = source.indexOf("install(targetApk, info)", successMarker)
        require(successMarker >= 0 && installMarker > successMarker) { "Unable to locate successful installer handoff" }
        val success = source.substring(successMarker, installMarker + "install(targetApk, info)".length)
        val successDismiss = success.indexOf("dismissTransferDialog()")
        val successInstall = success.indexOf("install(targetApk, info)")
        assertTrue(
            "successful downloads must dismiss progress UI before launching the package installer",
            successDismiss >= 0 && successDismiss < successInstall
        )

        val catchStart = source.indexOf("} catch (t: Throwable) {", successMarker)
        val finallyStart = source.indexOf("} finally {", catchStart)
        require(catchStart >= 0 && finallyStart > catchStart) { "Unable to locate updater failure flow" }
        val failure = source.substring(catchStart, finallyStart)
        val failureDismiss = failure.indexOf("dismissTransferDialog()")
        val failureDialog = failure.indexOf("pvMessageDialog(\"업데이트 실패\"")
        assertTrue(
            "failed downloads must dismiss progress UI before showing the failure dialog",
            failureDismiss >= 0 && failureDialog > failureDismiss
        )

        val dismissStart = source.indexOf("private fun dismissTransferDialog()")
        val dismissEnd = source.indexOf("private fun openHttpStream", dismissStart)
        require(dismissStart >= 0 && dismissEnd > dismissStart) { "Unable to locate transfer dialog dismissal" }
        val dismiss = source.substring(dismissStart, dismissEnd)
        assertTrue(
            "dismissal must clear the retained dialog reference before touching the window",
            dismiss.indexOf("transferDialog = null") >= 0 &&
                dismiss.indexOf("transferDialog = null") < dismiss.indexOf("dialog.dismiss()")
        )

        val closeStart = source.indexOf("fun close() {")
        require(closeStart >= 0) { "Unable to locate AppUpdater.close" }
        val close = source.substring(closeStart)
        assertTrue(
            "closing the updater must dismiss any transfer dialog on the UI thread",
            close.contains("onUi { dismissTransferDialog() }")
        )
        assertTrue(
            "executor shutdown must happen after transfer UI cleanup is scheduled",
            close.indexOf("onUi { dismissTransferDialog() }") < close.indexOf("executor.shutdownNow()")
        )
    }
}
