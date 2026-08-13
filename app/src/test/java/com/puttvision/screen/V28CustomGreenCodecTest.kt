package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V28CustomGreenCodecTest {
    @Test fun jsonRoundTripPreservesFiveZoneShape() {
        val profile = V22CustomGreenProfile(true, listOf(
            V22GreenNode(0.0, -1.2, .4),
            V22GreenNode(.25, .8, -.3),
            V22GreenNode(.50, 2.1, 1.0),
            V22GreenNode(.75, -2.1, -.8),
            V22GreenNode(1.0, .2, 1.6)
        ))
        val decoded = V28CustomGreenCodec.decode(V28CustomGreenCodec.encode(profile))
        requireNotNull(decoded)
        assertTrue(decoded.enabled)
        assertEquals(profile.nodes, decoded.nodes)
    }

    @Test fun shapeSignatureChangesEvenWhenMaxSlopeIsSame() {
        val a = V22CustomGreenProfile(true, listOf(
            V22GreenNode(0.0, 2.0, 0.0), V22GreenNode(.25, 0.0, 0.0),
            V22GreenNode(.50, 0.0, 1.0), V22GreenNode(.75, 0.0, 0.0), V22GreenNode(1.0, 0.0, 0.0)
        ))
        val b = V22CustomGreenProfile(true, listOf(
            V22GreenNode(0.0, 0.0, 0.0), V22GreenNode(.25, 2.0, 0.0),
            V22GreenNode(.50, 0.0, 1.0), V22GreenNode(.75, 0.0, 0.0), V22GreenNode(1.0, 0.0, 0.0)
        ))
        assertNotEquals(V28CustomGreenCodec.signature(a), V28CustomGreenCodec.signature(b))
    }

    @Test fun unsafeSlopeIsRejected() {
        val bad = """{"product":"PuttVision","schema":1,"enabled":true,"nodes":[{"at":0,"sidePct":6,"longPct":0},{"at":0.25,"sidePct":0,"longPct":0},{"at":0.5,"sidePct":0,"longPct":0},{"at":0.75,"sidePct":0,"longPct":0},{"at":1,"sidePct":0,"longPct":0}]}"""
        assertNull(V28CustomGreenCodec.decode(bad))
    }
}
