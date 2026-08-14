package com.puttvision.screen

import kotlin.math.min

data class V41TrainingTvLayout(
    val scale: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Pure layout/refresh policy so the TV HUD behaves consistently from 720p through 4K. */
object V41TrainingTvPolicy {
    fun layout(viewWidth: Int, viewHeight: Int): V41TrainingTvLayout {
        val w = viewWidth.coerceAtLeast(1).toFloat()
        val h = viewHeight.coerceAtLeast(1).toFloat()
        val scale = min(w / 1920f, h / 1080f).coerceIn(.65f, 2.0f)
        val margin = 24f * scale
        val cardWidth = min(390f * scale, w * .46f)
        val cardHeight = 168f * scale
        return V41TrainingTvLayout(scale, margin, margin, cardWidth, cardHeight)
    }

    fun refreshDelayMs(progress: V31TrainingProgress): Long = when {
        progress.paused -> 1_000L
        progress.running -> 250L
        progress.finished -> 1_500L
        else -> 1_000L
    }
}
