package com.puttvision.screen

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalPolicyRegressionTest {
    @Test fun coolPhoneAllows240() {
        assertEquals(240, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 34.0).maxFps)
    }

    @Test fun warmPhoneCaps120() {
        assertEquals(120, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_MODERATE, 39.0).maxFps)
        assertEquals(120, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 41.0).maxFps)
    }

    @Test fun criticalPhoneFallsBackNormal() {
        assertEquals(0, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_CRITICAL, 44.0).maxFps)
        assertEquals(0, ThermalHfrPolicy.decide(PowerManager.THERMAL_STATUS_NONE, 46.5).maxFps)
    }
}
