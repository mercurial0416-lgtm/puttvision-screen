package com.puttvision.screen

import kotlin.math.hypot

/** V25 flag-side live target information. All user-visible values stay in metres. */
data class V25FlagInfo(
    val remainingDistanceM: Double,
    val heightDeltaM: Double
) {
    val distanceLabel: String get() = "남은거리 %.2f m".format(remainingDistanceM.coerceAtLeast(0.0))
    val heightLabel: String get() = "높이 %+.2f m".format(heightDeltaM)
}

object V25FlagInfoRuntime {
    fun current(engine: GameEngine): V25FlagInfo {
        val settings = engine.settings
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
        val ballX = display?.first ?: state?.x ?: 0.0
        val ballY = display?.second ?: state?.y ?: 0.0
        val cupX = 0.0
        val cupY = settings.holeDistanceM
        val remaining = hypot(cupX - ballX, cupY - ballY)
        val ballZ = GreenTerrain.effectiveHeightAt(settings, ballX, ballY)
        val cupZ = GreenTerrain.effectiveHeightAt(settings, cupX, cupY)
        return V25FlagInfo(
            remainingDistanceM = remaining,
            heightDeltaM = cupZ - ballZ
        )
    }
}
