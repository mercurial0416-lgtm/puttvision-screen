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

        // V128: one coherent commercial-style screen-golf presentation.
        // V124 remains in source as a safe OpenGL fallback and regression reference.
        addView(V128ScreenGolfWorldFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V128CommercialScreenGolfHudView(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional training/replay behavior stays independent of presentation.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
