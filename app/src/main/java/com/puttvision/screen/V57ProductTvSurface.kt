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

        // V132: art-directed simulator presentation on top of the V131 Filament scene.
        // Measurement, calibration, HFR and physics remain untouched; V131 still owns the 3D plate
        // and retains its V129/V128 initialization fallback path.
        addView(V132VisualRebuildFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional training/replay behavior stays independent of presentation.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
