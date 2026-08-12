package com.puttvision.screen

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalHysteresisV12Test {
    private fun raw(fps: Int, temp: Double) = ThermalHfrDecision(
        maxFps = fps,
        label = "raw",
        detail = "raw",
        thermalStatus = PowerManager.THERMAL_STATUS_NONE,
        batteryTempC = temp
    )

    @Test fun warmThrottleIsImmediateButRecoveryWaits() {
        val h = ThermalHfrHysteresis()
        assertEquals(120, h.update(raw(120, 41.0), 1_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 2_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 61_000L).maxFps)
        assertEquals(240, h.update(raw(240, 38.0), 62_100L).maxFps)
    }

    @Test fun hotModeRecoversThrough120First() {
        val h = ThermalHfrHysteresis()
        assertEquals(0, h.update(raw(0, 47.0), 1_000L).maxFps)
        assertEquals(0, h.update(raw(240, 38.0), 2_000L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 32_100L).maxFps)
        assertEquals(120, h.update(raw(240, 38.0), 33_000L).maxFps)
    }
}
