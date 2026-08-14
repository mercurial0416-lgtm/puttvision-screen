package com.puttvision.screen

/** Always derives the live view from the already-cleaned current-profile, current-session history. */
object V49LiveSessionInsights {
    fun snapshot(nowMs: Long = System.currentTimeMillis()): V49SessionInsights {
        val clean = V47SoloIntegrityRuntime.latestHistory?.records.orEmpty()
        return V49SessionInsightsEngine.analyze(V49SessionWindow.current(clean, nowMs))
    }
}
