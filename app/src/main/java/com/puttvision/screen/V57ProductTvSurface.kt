package com.puttvision.screen

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout

object V57ProductTvSurface {
    fun create(
        context: Context,
        engine: GameEngine,
        includeImpactReplay: Boolean = true
    ): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)

        // V124: one bright screen-golf world and one simulator-style information hierarchy.
        // Older V120/V121/V122 visuals remain in source only for rollback and regression history.
        addView(V124ScreenGolfWorldFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V124ScreenGolfHudView(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional training/replay behavior stays independent of presentation.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
