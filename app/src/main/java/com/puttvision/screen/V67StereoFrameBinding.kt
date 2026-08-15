package com.puttvision.screen

/**
 * Binds the raw-pixel coordinate space carried by a HFR feature track to the exact capture
 * configuration whose calibration is about to consume those pixels.
 *
 * V59 proves that a calibration belongs to the current capture signature. V55 proves that a track
 * has structurally valid raw pixels. This gate closes the remaining gap between the two: pixels
 * from a different resolution/FPS capture must never be interpreted using the current signature.
 *
 * Exact equality is intentional. Crop/scale/rotation conversion belongs in V65 and must be explicit;
 * this gate never guesses a transform.
 */
data class V67FrameBindingPolicy(
    val requireExactFps: Boolean = true
)

data class V67FrameBindingResult(
    val bound: Boolean,
    val reason: String
)

object V67StereoFrameBindingGate {
    fun evaluate(
        track: HfrFeatureTrack?,
        signature: V59CaptureSignature,
        policy: V67FrameBindingPolicy = V67FrameBindingPolicy()
    ): V67FrameBindingResult {
        if (!signature.valid()) return deny("capture signature invalid")
        if (track == null) return deny("feature track missing")
        val width = track.imageWidthPx ?: return deny("track image width missing")
        val height = track.imageHeightPx ?: return deny("track image height missing")
        if (width <= 0 || height <= 0) return deny("track image shape invalid")
        if (width != signature.widthPx || height != signature.heightPx) {
            return deny("track image shape does not match active capture")
        }
        if (policy.requireExactFps && track.fps != signature.fps) {
            return deny("track fps does not match active capture")
        }
        return V67FrameBindingResult(true, "raw-pixel track bound to active capture signature")
    }

    fun evaluatePair(
        firstTrack: HfrFeatureTrack?,
        firstSignature: V59CaptureSignature,
        secondTrack: HfrFeatureTrack?,
        secondSignature: V59CaptureSignature,
        policy: V67FrameBindingPolicy = V67FrameBindingPolicy()
    ): V67FrameBindingResult {
        val first = evaluate(firstTrack, firstSignature, policy)
        if (!first.bound) return deny("first camera: ${first.reason}")
        val second = evaluate(secondTrack, secondSignature, policy)
        if (!second.bound) return deny("second camera: ${second.reason}")
        return V67FrameBindingResult(true, "both raw-pixel tracks bound to active capture signatures")
    }

    private fun deny(reason: String) = V67FrameBindingResult(false, reason)
}
