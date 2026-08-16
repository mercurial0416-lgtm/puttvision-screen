package com.puttvision.screen

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout

/**
 * Canonical TV surface shared by the physical presentation, on-phone TV preview,
 * and no-hardware lab. Keeping the composition here prevents those three surfaces
 * from silently drifting apart as product HUD layers evolve.
 */
object V57ProductTvSurface {
    fun create(
        context: Context,
        engine: GameEngine,
        includeImpactReplay: Boolean = true
    ): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(V18SimulatorFactory.create(context, engine), FrameLayout.LayoutParams(-1, -1))
        // Visual-only physics FX: ball roll/spin/contact/cup reaction from the same engine state.
        addView(V89ScreenGolfVisualPhysicsView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V51TvPolishOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V87TvVisualPolishView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V27PaceLineOverlay(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V86ScreenGolfReticleView(context, engine), FrameLayout.LayoutParams(-1, -1))
        addView(V31TrainingTvOverlay(context), FrameLayout.LayoutParams(-1, -1))
        if (includeImpactReplay) {
            addView(TvImpactReplayView(context), FrameLayout.LayoutParams(-1, -1))
        }
    }
}
