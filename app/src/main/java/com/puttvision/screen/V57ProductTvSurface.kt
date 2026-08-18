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

        // V138: dedicated commercial screen-golf Filament world. The renderer itself falls back to
        // V133 if a device cannot initialize the heavier presentation path.
        addView(V138CommercialScreenGolfFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Measurement/training/replay remain independent of presentation and retain their existing
        // source-of-truth timing and HFR bindings.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
