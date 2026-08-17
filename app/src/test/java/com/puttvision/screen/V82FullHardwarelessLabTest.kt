package com.puttvision.screen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V82FullHardwarelessLabTest {
    @Test fun fullTrainingJourneyCoversPauseResumeCompletionAndWeakestBlock() {
        val result = V82HardwarelessTrainingJourney.verify()
        assertTrue(result.reason, result.passed)
        assertEquals(9, result.checksTotal)
        assertEquals(9, result.checksPassed)
        assertEquals(1, result.weakestBlockIndex)
    }

    @Test fun diagnosticsExportIsPortableAndExplicitlyNonAccuracyClaiming() {
        V72HardwarelessSelfTestRuntime.clear()
        V75HardwarelessSelfTestHistoryRuntime.reset()
        val self = V72HardwarelessSelfTestRuntime.run(1.2, 0.0)
        V75HardwarelessSelfTestHistoryRuntime.record(self, 1234L)
        val verdict = V81LabVerdictEngine.snapshot()
        val overlay = V81LiveTrackProjector.from(track())
        val raw = V82HardwarelessDiagnosticsExport.toJson(verdict, overlay = overlay, generatedAtMs = 2222L)
        val json = JSONObject(raw)
        assertEquals(1, json.getInt("schema"))
        assertEquals("synthetic-regression-only", json.getString("scope"))
        assertFalse(json.getBoolean("realDeviceAccuracyClaim"))
        assertTrue(json.getJSONObject("lab").getBoolean("passed"))
        assertEquals(self.checksTotal, json.getJSONObject("lab").getInt("selfTestTotal"))
        assertTrue(json.getJSONObject("lab").getInt("selfTestTotal") >= V76HardwarelessSoak.MIN_EXPECTED_CHECKS_PER_RUN)
        assertTrue(json.getJSONObject("liveTrack").getBoolean("ready"))
    }

    @Test fun replayTimelineProducesBallTrailPutterGhostsAndImpactState() {
        val overlay = V81LiveTrackProjector.from(track())
        assertTrue(overlay.ready)
        val times = V82LiveTrackReplay.timeline(overlay)
        assertTrue(times.isNotEmpty())
        val before = V82LiveTrackReplay.slice(overlay, -4.0)
        assertTrue(before.ready)
        assertFalse(before.impactReached)
        val atImpact = V82LiveTrackReplay.slice(overlay, 0.0)
        assertTrue(atImpact.impactReached)
        assertTrue(atImpact.ballTrail.isNotEmpty())
        assertTrue(atImpact.putterGhosts.isNotEmpty())
        assertNotNull(atImpact.currentBall)
        assertNotNull(atImpact.currentPutter)
    }

    @Test fun replayFailsClosedForBadTimingOrUnreadyOverlay() {
        val overlay = V81LiveTrackProjector.from(track())
        assertFalse(V82LiveTrackReplay.slice(overlay, Double.NaN).ready)
        val unready = V81LiveTrackProjector.from(track().copy(imageWidthPx = null))
        assertFalse(V82LiveTrackReplay.slice(unready, 0.0).ready)
    }

    private fun track(): HfrFeatureTrack {
        val impact = 10
        val frames = (0 until 7).map { i ->
            val frame = impact - 2 + i
            val t = (frame - impact) * (1000.0 / 240.0)
            HfrFeatureFrame(
                frame = frame,
                timeFromImpactMs = t,
                ballXcm = 0.0,
                ballYcm = i * 0.5,
                heelXcm = 0.0,
                heelYcm = 0.0,
                toeXcm = 0.0,
                toeYcm = 0.0,
                markerAngleDeg = null,
                ballXpx = 900.0 + i * 8.0,
                ballYpx = 600.0 - i * 10.0,
                heelXpx = 820.0 + i * 4.0,
                heelYpx = 690.0 - i * 3.0,
                toeXpx = 900.0 + i * 4.0,
                toeYpx = 690.0 - i * 3.0
            )
        }
        return HfrFeatureTrack(240, impact, frames, 1920, 1080)
    }
}
