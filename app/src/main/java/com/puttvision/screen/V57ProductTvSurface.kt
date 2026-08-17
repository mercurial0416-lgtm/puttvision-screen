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

        // V131: Filament PBR is now the preferred presentation renderer.
        // It preserves the entire measurement/physics stack and falls back to the tested V129/V128
        // path automatically if Filament cannot initialize on a particular Android device.
        addView(V131FilamentScreenGolfPresentationFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional training/replay behavior stays independent of presentation.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
