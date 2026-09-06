package com.puttvision.screen

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityRendererBridgeTest {
    @After
    fun tearDown() {
        UnityRendererBridge.installSenderForTests(null)
        UnityRendererBridge.enabled = false
    }

    @Test
    fun shotProtocolPreservesMeasuredMetricsAndGreenContext() {
        val metrics = ShotMetrics(
            ballSpeedMps = 1.82,
            launchAngleDeg = -0.65,
            headSpeedMps = 0.94,
            faceAngleDeg = -0.30,
            pathAngleDeg = 0.18,
            faceToPathDeg = -0.48,
            smash = 1.94,
            impactOffsetMm = 2.4,
            measuredAtNs = 123456789L,
            confidence = 0.91,
        )
        val settings = GreenSettings(
            stimpMeters = 3.15,
            holeDistanceM = 4.2,
            sideSlopePct = 1.4,
            longSlopePct = -0.7,
            terrainProfileId = 7,
            flagstickIn = true,
            grainDirectionDeg = 12.0,
            grainStrength01 = 0.22,
        )

        val json = JSONObject(UnityRendererProtocol.shotJson(metrics, settings, 0.25, -0.4))

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(1.82, json.getDouble("ballSpeedMps"), 1e-9)
        assertEquals(-0.65, json.getDouble("launchDirectionDeg"), 1e-9)
        assertEquals(0.25, json.getDouble("startXM"), 1e-9)
        assertEquals(-0.4, json.getDouble("startYM"), 1e-9)
        assertEquals(4.2, json.getDouble("holeDistanceM"), 1e-9)
        assertEquals(7, json.getInt("terrainProfileId"))
        assertTrue(json.getBoolean("flagstickIn"))
        assertEquals(15, json.getInt("validityFlags"))
    }

    @Test
    fun physicsProtocolPublishesAuthoritativeSixDofStateWithoutTrailCopies() {
        val state = SimState(
            x = -0.32,
            y = 3.75,
            vx = 0.11,
            vy = 0.78,
            running = true,
            elapsed = 1.25,
            holed = false,
            trail = mutableListOf(0.0 to 0.0, -0.1 to 1.0),
            ballCenterZM = 0.02135,
            vz = 0.02,
            orientationW = 0.9238795,
            orientationX = 0.3826834,
            orientationY = 0.0,
            orientationZ = 0.0,
            surfaceNormalZ = 1.0,
            v135SlipSpeedMps = 0.04,
            cupContacts = 1,
        )

        val json = JSONObject(UnityRendererProtocol.physicsFrameJson(state))

        assertEquals(-0.32, json.getDouble("xM"), 1e-9)
        assertEquals(3.75, json.getDouble("yM"), 1e-9)
        assertEquals(0.02135, json.getDouble("centerZM"), 1e-9)
        assertEquals(0.9238795, json.getDouble("orientationW"), 1e-7)
        assertEquals(1, json.getInt("cupContacts"))
        assertTrue(json.getBoolean("running"))
        assertFalse(json.has("trail"))
    }

    @Test
    fun bridgeUsesStableUnityMessageSurface() {
        val sent = mutableListOf<Triple<String, String, String>>()
        UnityRendererBridge.installSenderForTests { gameObject, method, payload ->
            sent += Triple(gameObject, method, payload)
        }

        val published = UnityRendererBridge.publishPhysicsFrame(
            SimState(ballCenterZM = 0.02135, orientationW = 1.0)
        )

        assertTrue(published)
        assertEquals(1, sent.size)
        assertEquals("PuttTelemetryReceiver", sent.single().first)
        assertEquals("OnPhysicsFrameJson", sent.single().second)
        assertEquals(1, JSONObject(sent.single().third).getInt("schemaVersion"))
    }
}
