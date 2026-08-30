package com.puttvision.screen

import java.util.Locale

/** Pure formatting/throttling policy for the updater transfer UI. */
object UpdateTransferUi {
    const val PUBLISH_STEP_BYTES = 2L * 1024L * 1024L

    fun shouldPublish(lastPublishedBytes: Long, downloadedBytes: Long): Boolean {
        if (downloadedBytes < 0L) return false
        if (lastPublishedBytes < 0L) return true
        return downloadedBytes - lastPublishedBytes >= PUBLISH_STEP_BYTES
    }

    fun downloadMessage(downloadedBytes: Long): String {
        val safeBytes = downloadedBytes.coerceAtLeast(0L)
        val mib = safeBytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "다운로드 중 · %.1f MB", mib)
    }
}
