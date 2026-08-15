package com.puttvision.screen

/**
 * Fault-injection companion to V68. The hardwareless lab must prove both sides of the contract:
 * valid synthetic HFR geometry reaches stereo reconstruction, while stale/mismatched frame spaces
 * are rejected before triangulation.
 */
data class V69StereoGuardSuiteResult(
    val passed: Boolean,
    val checksPassed: Int,
    val checksTotal: Int,
    val reason: String
) {
    fun shortLabel(): String = if (passed) {
        "STEREO GUARDS PASS · $checksPassed/$checksTotal"
    } else {
        "STEREO GUARDS FAIL · $checksPassed/$checksTotal · $reason"
    }
}

object V69HardwarelessStereoGuardSuite {
    private const val WIDTH = 1920
    private const val HEIGHT = 1080
    private const val FPS = 240

    fun verify(): V69StereoGuardSuiteResult {
        val signature = signature("guard-left")
        val secondSignature = signature("guard-right")
        val good = track(WIDTH, HEIGHT, FPS)
        val cases = listOf(
            "baseline accepts exact capture" to V67StereoFrameBindingGate.evaluate(good, signature).bound,
            "width mismatch rejected" to !V67StereoFrameBindingGate.evaluate(track(1280, HEIGHT, FPS), signature).bound,
            "height mismatch rejected" to !V67StereoFrameBindingGate.evaluate(track(WIDTH, 720, FPS), signature).bound,
            "fps mismatch rejected" to !V67StereoFrameBindingGate.evaluate(track(WIDTH, HEIGHT, 120), signature).bound,
            "missing shape rejected" to !V67StereoFrameBindingGate.evaluate(track(null, null, FPS), signature).bound,
            "remote pair mismatch rejected" to !V67StereoFrameBindingGate.evaluatePair(
                good,
                signature,
                track(WIDTH, HEIGHT, 120),
                secondSignature
            ).bound
        )
        val passed = cases.count { it.second }
        val firstFailure = cases.firstOrNull { !it.second }?.first
        return V69StereoGuardSuiteResult(
            passed = passed == cases.size,
            checksPassed = passed,
            checksTotal = cases.size,
            reason = firstFailure ?: "all fail-closed frame-binding guards verified"
        )
    }

    private fun signature(cameraId: String) = V59CaptureSignature(
        cameraId = cameraId,
        widthPx = WIDTH,
        heightPx = HEIGHT,
        fps = FPS,
        sensorOrientationDeg = 90,
        lensFacing = "BACK",
        captureMode = "HFR"
    )

    private fun track(width: Int?, height: Int?, fps: Int) = HfrFeatureTrack(
        fps = fps,
        impactFrame = 0,
        frames = emptyList(),
        imageWidthPx = width,
        imageHeightPx = height
    )
}

object V69HardwarelessStereoGuardRuntime {
    @Volatile private var latest: V69StereoGuardSuiteResult? = null

    fun run(): V69StereoGuardSuiteResult =
        V69HardwarelessStereoGuardSuite.verify().also { latest = it }

    fun snapshot(): V69StereoGuardSuiteResult? = latest

    fun clear() {
        latest = null
    }
}
