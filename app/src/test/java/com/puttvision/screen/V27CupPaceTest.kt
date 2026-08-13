package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V27CupPaceTest {
    @Test fun pacePresetsAreStrictlyIncreasing() {
        val v=V27CupPace.entries.map{it.targetCupSpeedMps}; assertEquals(v.sorted(),v); assertEquals(v.size,v.distinct().size)
    }
    @Test fun greenReadKeyIncludesPace() {
        val s=GreenSettings(holeDistanceM=4.0); val a=GreenReadAdvisor.key(s,.25); val b=GreenReadAdvisor.key(s,.85); assertNotEquals(a.pace100,b.pace100)
    }
}
