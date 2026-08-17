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

        // V133: every course visual is rendered in native Filament world space.
        // The failed V132 camera-agnostic Canvas course overlay is intentionally bypassed.
        addView(V133FilamentCourseFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))

        // Functional training/replay behavior stays independent of presentation.
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
    }
}
