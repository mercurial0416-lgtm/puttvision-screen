package com.puttvision.screen

/**
 * Lightweight LAB-only green reading presets. These values only configure the existing
 * authoritative GameEngine/GreenTerrain/GreenReadAdvisor pipeline; they do not alter physics.
 */
internal enum class HardwarelessGreenPreset(
    val label: String,
    val sideSlopePct: Double,
    val longSlopePct: Double
) {
    LEFT_BREAK("LEFT ↙", -1.35, -0.45),
    RIGHT_BREAK("RIGHT ↘", 1.35, -0.45),
    UPHILL("UPHILL ↑", 0.35, 1.35),
    DOWNHILL("DOWNHILL ↓", -0.35, -1.35);

    val gradePct: Double
        get() = kotlin.math.hypot(sideSlopePct, longSlopePct)

    val directionLabel: String
        get() = when (this) {
            LEFT_BREAK -> "BREAK LEFT"
            RIGHT_BREAK -> "BREAK RIGHT"
            UPHILL -> "UPHILL"
            DOWNHILL -> "DOWNHILL"
        }
}
