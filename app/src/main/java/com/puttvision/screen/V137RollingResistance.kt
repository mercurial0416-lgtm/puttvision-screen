package com.puttvision.screen

import kotlin.math.ln

/**
 * Speed-dependent putting rolling resistance, normalized to preserve the selected Stimp distance.
 *
 * Burritt & Henrikson (ISEA 2024) report that high-speed-camera measurements show the rolling
 * coefficient can vary by about 10% during a putt and support a nonconstant rolling-friction model.
 * We therefore use a deliberately conservative linear 10% variation across the standard Stimp
 * launch-speed range. The normalization is analytical: integrating v/a(v) from the Stimp launch
 * speed to rest produces the exact same stopping distance as the legacy constant-deceleration
 * model, so this adds realistic speed dependence without silently redefining the user's green speed.
 */
object V137RollingResistance {
    const val STIMP_LAUNCH_MPS = 1.95072 // 6.4 ft/s
    const val RELATIVE_VARIATION = 0.10

    private val normalization: Double by lazy {
        // f(u) = 1-c + 2cu, u=v/V.  For a(v)=a0*N*f(u), require
        // integral[0,1] u/(N*f(u)) du = 1/2 so the stopping distance remains V²/(2a0).
        val c = RELATIVE_VARIATION
        val a = 1.0 - c
        val b = 2.0 * c
        val integral = 1.0 / b - a / (b * b) * ln((a + b) / a)
        2.0 * integral
    }

    /** Multiplier applied to the legacy Stimp-calibrated rolling deceleration. */
    fun decelerationFactor(speedMps: Double): Double {
        val speed = speedMps.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        // Above the Stimp reference speed we allow a modest continuation of the measured trend but
        // cap it so an unusually hard putt cannot acquire an arbitrary friction penalty.
        val u = (speed / STIMP_LAUNCH_MPS).coerceIn(0.0, 1.5)
        val raw = 1.0 - RELATIVE_VARIATION + 2.0 * RELATIVE_VARIATION * u
        return (normalization * raw).coerceIn(0.82, 1.20)
    }

    /**
     * V135 computes deceleration as V_stimp²/(2*stimp). Dividing Stimp by this factor is exactly
     * equivalent to multiplying deceleration by [decelerationFactor].
     */
    fun effectiveStimp(baseStimpM: Double, speedMps: Double): Double {
        val base = baseStimpM.takeIf { it.isFinite() }?.coerceIn(1.2, 5.6) ?: 2.8
        return (base / decelerationFactor(speedMps)).coerceIn(1.0, 6.2)
    }

    /** Exposed for deterministic regression tests and diagnostics. */
    fun calibrationNormalization(): Double = normalization
}
