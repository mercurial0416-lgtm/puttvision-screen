package com.puttvision.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class V26GreenVisualsTest {
    @Test fun flatGreenHasNoArtificialContours(){val s=GreenSettings(holeDistanceM=5.0,sideSlopePct=0.0,longSlopePct=0.0,terrainProfileId=-1);assertTrue(V26ContourEngine.build(s).isEmpty())}
    @Test fun profiledGreenProducesContours(){val s=GreenSettings(holeDistanceM=5.0,sideSlopePct=2.0,longSlopePct=1.0,terrainProfileId=3);assertTrue(V26ContourEngine.build(s).isNotEmpty())}
}
