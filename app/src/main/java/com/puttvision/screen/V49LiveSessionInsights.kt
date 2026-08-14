package com.puttvision.screen

/** Always derives the live view from the already-cleaned current-profile history. */
object V49LiveSessionInsights {
    fun snapshot(): V49SessionInsights = V49SessionInsightsEngine.analyze(
        V47SoloIntegrityRuntime.latestHistory?.records.orEmpty()
    )
}
