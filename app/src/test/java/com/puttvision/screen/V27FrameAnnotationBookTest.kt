package com.puttvision.screen

import org.junit.Assert.*
import org.junit.Test

class V27FrameAnnotationBookTest {
    @Test fun framesKeepIndependentMarks() {
        val book=V27FrameAnnotationBook(); val s=V27ReplayAnnotationSession()
        s.marks += V27ReplayMark.Line(V27NormPoint(.1f,.2f),V27NormPoint(.8f,.7f)); book.save(3,s)
        s.clear(); book.load(4,s); assertTrue(s.marks.isEmpty())
        book.load(3,s); assertEquals(1,s.marks.size); assertEquals(setOf(3),book.annotatedFrames())
    }
}
