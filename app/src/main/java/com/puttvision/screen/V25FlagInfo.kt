package com.puttvision.screen

import java.util.Locale
import kotlin.math.hypot

/** V25 flag-side live target information. All user-visible values stay in metres. */
data class V25FlagInfo(
    val remainingDistanceM: Double,
    val heightDeltaM: Double
) {
    val distanceLabel: String get() = String.format(Locale.US, "남은거리 %.2f m", remainingDistanceM.coerceAtLeast(0.0))
    val heightLabel: String get() = String.format(Locale.US, "높이 %+.2f m", heightDeltaM)
}

object V25FlagInfoRuntime {
    fun current(engine: GameEngine): V25FlagInfo {
        val settings = engine.settings
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state)
        val start = V26BallStartRuntime.current(settings)
        val ballX = display?.first ?: state?.x ?: start.first
        val ballY = display?.second ?: state?.y ?: start.second
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
