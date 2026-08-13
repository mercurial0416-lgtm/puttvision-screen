package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class V27ReplayGeometryTest {
    @Test fun rightAngleIsNinetyDegrees() {
        val v = V27NormPoint(.5f, .5f)
        val a = V27NormPoint(.8f, .5f)
        val b = V27NormPoint(.5f, .2f)
        assertEquals(90.0, V27ReplayGeometry.angleDeg(v, a, b), 1e-4)
    }
}
