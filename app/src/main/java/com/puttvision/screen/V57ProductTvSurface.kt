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

        // V140: independent Friends public-reference putting world. V140 itself falls back to V138,
        // and V138 retains the older V133 fallback, so presentation upgrades never replace physics.
        addView(V140FriendsScreenGolfFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Measurement/training/replay remain independent of presentation and retain their existing
        // source-of-truth timing and HFR bindings.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
