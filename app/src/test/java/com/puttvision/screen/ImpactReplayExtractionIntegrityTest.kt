package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImpactReplayExtractionIntegrityTest {
    @Test fun exactDecodedImpactFrameIsAccepted() {
        assertEquals(
            2,
            ImpactReplayExtractionIntegrity.exactImpactIndex(
                extractedSourceFrameIndices = listOf(94, 97, 100, 103, 106),
                sourceImpactFrame = 100
            )
        )
    }

    @Test fun missingImpactFrameFailsClosedInsteadOfPromotingNeighbor() {
        assertNull(
            ImpactReplayExtractionIntegrity.exactImpactIndex(
                extractedSourceFrameIndices = listOf(94, 97, 103, 106),
                sourceImpactFrame = 100
            )
        )
    }

    @Test fun malformedSourceFrameProvenanceFailsClosed() {
        assertNull(
            ImpactReplayExtractionIntegrity.exactImpactIndex(
                extractedSourceFrameIndices = listOf(94, 100, 100, 106),
                sourceImpactFrame = 100
            )
        )
        assertNull(
            ImpactReplayExtractionIntegrity.exactImpactIndex(
                extractedSourceFrameIndices = listOf(100, 97, 103),
                sourceImpactFrame = 100
            )
        )
    }
}
