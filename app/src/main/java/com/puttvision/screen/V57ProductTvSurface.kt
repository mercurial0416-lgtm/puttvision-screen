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

        // V142: playability-hardened PBR path. The underlying V141 renderer retains the existing
        // V140 -> V138 -> V133 fallback chain while V142 owns render-state/target visibility rules.
        addView(V142PlayableScreenGolfFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
