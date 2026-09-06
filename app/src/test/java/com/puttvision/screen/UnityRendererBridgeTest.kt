package com.puttvision.screen

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

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
    fun surfaceGridCarriesFiniteNativeTerrainTruthAtBoundedSize() {
        val settings = GreenSettings(
            holeDistanceM = 5.0,
            sideSlopePct = 1.25,
            longSlopePct = -0.5,
            terrainProfileId = 8,
        )
        val json = JSONObject(UnityRendererProtocol.surfaceGridJson(settings, 0.2, -0.1))
        val width = json.getInt("width")
        val height = json.getInt("height")
        val bytes = Base64.getDecoder().decode(json.getString("heightF32LeBase64"))

        assertEquals(UnityRendererProtocol.SURFACE_WIDTH, width)
        assertEquals(UnityRendererProtocol.SURFACE_HEIGHT, height)
        assertEquals(width * height * 4, bytes.size)
        assertTrue(json.getDouble("minXM") < json.getDouble("maxXM"))
        assertTrue(json.getDouble("minYM") < json.getDouble("maxYM"))

        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        repeat(width * height) {
            assertTrue("surface sample $it must be finite", floats.get(it).isFinite())
        }
    }

    @Test
    fun bridgeUsesStableUnityMessageSurfaces() {
        val sent = mutableListOf<Triple<String, String, String>>()
        UnityRendererBridge.installSenderForTests { gameObject, method, payload ->
            sent += Triple(gameObject, method, payload)
        }

        assertTrue(UnityRendererBridge.publishSurfaceGrid(GreenSettings(), 0.0, 0.0))
        assertTrue(UnityRendererBridge.publishPhysicsFrame(SimState(ballCenterZM = 0.02135, orientationW = 1.0)))

        assertEquals(2, sent.size)
        assertTrue(sent.all { it.first == "PuttTelemetryReceiver" })
        assertEquals("OnSurfaceGridJson", sent[0].second)
        assertEquals("OnPhysicsFrameJson", sent[1].second)
        assertEquals(1, JSONObject(sent[0].third).getInt("schemaVersion"))
        assertEquals(1, JSONObject(sent[1].third).getInt("schemaVersion"))
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
            trail = mutableListOf(0.0 to 0.0, -0.1 to 1.0),
            ballCenterZM = 0.02135,
            vz = 0.02,
            orientationW = 0.9238795,
            orientationX = 0.3826834,
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
}
