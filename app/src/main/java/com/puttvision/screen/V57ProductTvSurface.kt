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

        // V122: the canonical TV now uses an independent 3D course renderer. V18 remains only as
        // rollback/legacy source; it is not mounted in the normal TV path.
        addView(V122CourseWorldFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V120TvRendererV2View(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V121RollMonitorOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional overlays remain independent so training/replay behavior is preserved.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
