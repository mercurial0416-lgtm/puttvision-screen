package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V44SoloHardeningTest {
    @Test fun cornerOrderingSurvivesDetectorPermutation() {
        val ref=listOf(V38Point(10f,10f),V38Point(90f,10f),V38Point(90f,90f),V38Point(10f,90f))
        val flipped=listOf(ref[2],ref[0],ref[3],ref[1])
        assertEquals(ref,V44CornerOrdering.align(ref,flipped))
    }

    @Test fun cornerStabilizerDoesNotReacquireOnOrderFlip() {
        val s=V38CornerStabilizer()
        val ref=listOf(V38Point(10f,10f),V38Point(90f,10f),V38Point(90f,90f),V38Point(10f,90f))
        s.update(ref,100,100)
        val d=requireNotNull(s.update(listOf(ref[3],ref[2],ref[1],ref[0]),100,100))
        assertFalse(d.reacquired)
        assertTrue(d.stable)
        assertTrue(d.stability>.9)
    }

    @Test fun trainingResumeCountersAreBoundedToCurrentBlock() {
        val blocks=listOf(V16TrainingBlock("A",5,2.0,0.0,0.0,"x"))
        val c=requireNotNull(V44TrainingResumePolicy.sanitize(blocks,99,99,88,2,99,77))
        assertEquals(0,c.blockIndex)
        assertEquals(4,c.shotInBlock)
        assertEquals(4,c.successesInBlock)
        assertEquals(4,c.totalShots)
        assertEquals(4,c.totalSuccesses)
        assertEquals(4,c.streak)
    }

    @Test fun trainingResumeRejectsClockSkewAndStaleSessions() {
        assertTrue(V44TrainingResumePolicy.fresh(10_000L,20_000L))
        assertFalse(V44TrainingResumePolicy.fresh(20_001L,20_000L))
        assertFalse(V44TrainingResumePolicy.fresh(0L,V44TrainingResumePolicy.MAX_RESUME_AGE_MS+1L))
    }
}
