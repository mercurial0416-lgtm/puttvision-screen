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
        addView(V18SimulatorFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V89ScreenGolfVisualPhysicsView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V90ScreenGolfCinematicOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V51TvPolishOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V87TvVisualPolishView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V27PaceLineOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V86ScreenGolfReticleView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
