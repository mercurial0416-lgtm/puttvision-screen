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

        // V120: one world + one coherent presentation language. The previous decorative overlay
        // stack remains in source for rollback, but is intentionally not mounted here.
        addView(V120WorldOnlyFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V120TvRendererV2View(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V121RollMonitorOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional overlays remain independent so training/replay behavior is preserved.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
