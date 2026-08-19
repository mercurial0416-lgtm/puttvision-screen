package com.puttvision.screen

import android.content.Context
import android.view.View

/**
 * V142 is the playability hardening layer over the V141 PBR renderer.
 *
 * The rendered ball must follow the authoritative physics state.  V29's instant-roll handoff is
 * a one-frame presentation bridge captured immediately before the simulation ticker starts; using
 * it as the permanent render position freezes the visible ball while physics continues underneath.
 */
object V142RenderContract {
    fun authoritativeBallPosition(
        stateX: Double?,
        stateY: Double?,
        startX: Double,
        startY: Double
    ): Pair<Double, Double> =
        if (stateX != null && stateY != null && stateX.isFinite() && stateY.isFinite()) {
            stateX to stateY
        } else {
            startX to startY
        }

    /** A target pin is presentation-only when the physical flagstick setting is OUT. */
    fun showTargetPin(): Boolean = true
}

object V142PlayableScreenGolfFactory {
    fun create(context: Context, game: GameEngine): View =
        V141FriendsPbrScreenGolfFactory.create(context, game)
}
