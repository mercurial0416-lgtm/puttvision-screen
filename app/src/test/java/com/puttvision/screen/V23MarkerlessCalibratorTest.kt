package com.puttvision.screen

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

class V23MarkerlessCalibratorTest {

    @Test fun perspectiveMatWithNoiseFitsStableQuad() {
        val w = 120
        val h = 96
        val mask = BooleanArray(w * h)
        val topY = 14
        val bottomY = 91
        for (y in topY..bottomY) {
            val t = (y - topY).toDouble() / (bottomY - topY)
            val roll = (y - 50) * .035
            val left = (43.0 - 20.0 * t + roll).roundToInt()
            val right = (77.0 + 20.0 * t + roll).roundToInt()
            for (x in left..right) if (x in 0 until w) mask[y * w + x] = true
        }
        val random = Random(23)
        // Punch sparse holes and add disconnected floor noise. The component/robust fit should ignore both.
        repeat(260) {
            val x = random.nextInt(w)
            val y = random.nextInt(h)
            if (mask[y * w + x] && random.nextBoolean()) mask[y * w + x] = false
            else if (y < 60 && random.nextDouble() < .25) mask[y * w + x] = true
        }

        val quad = V23MatQuadFitter.fit(V23MaskGrid(w, h, mask, "TEST"))
        assertNotNull(quad)
        quad!!
        assertTrue("confidence=${quad.confidence}", quad.confidence >= .78)
        assertTrue(quad.coverage in .15..0.60)
        assertTrue(abs(quad.tl.y - topY) < 8.0)
        assertTrue(abs(quad.bl.y - bottomY) < 8.0)
        assertTrue(quad.br.x - quad.bl.x > quad.tr.x - quad.tl.x)
        assertTrue(quad.tl.x < quad.tr.x && quad.bl.x < quad.br.x)
    }

    @Test fun fullWidthFloorPatchIsRejectedAsNotAMat() {
        val w = 120
        val h = 96
        val mask = BooleanArray(w * h)
        for (y in 48 until h) for (x in 0 until w) mask[y * w + x] = true
        val quad = V23MatQuadFitter.fit(V23MaskGrid(w, h, mask, "FLOOR"))
        assertNull(quad)
    }

    @Test fun smallDisconnectedClutterIsRejected() {
        val w = 120
        val h = 96
        val mask = BooleanArray(w * h)
        for (y in 65..82 step 4) for (x in 20..105 step 11) mask[y * w + x] = true
        val quad = V23MatQuadFitter.fit(V23MaskGrid(w, h, mask, "CLUTTER"))
        assertNull(quad)
    }
}