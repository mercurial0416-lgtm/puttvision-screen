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

        // V141: sampled PBR turf + textured scenery renderer. V141 falls back to V140, then the
        // existing V138/V133 chain, while measurement and physics remain independent.
        addView(V141FriendsPbrScreenGolfFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
