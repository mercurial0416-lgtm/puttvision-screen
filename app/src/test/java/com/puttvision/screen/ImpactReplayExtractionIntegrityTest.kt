package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun replayRequiresEveryPlannedTemporalSideThatExists() {
        val planned = listOf(94, 97, 100, 103, 106)

        assertTrue(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = planned,
                extractedSourceFrameIndices = listOf(97, 100, 106),
                sourceImpactFrame = 100
            )
        )
        assertFalse(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = planned,
                extractedSourceFrameIndices = listOf(100, 103, 106),
                sourceImpactFrame = 100
            )
        )
        assertFalse(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = planned,
                extractedSourceFrameIndices = listOf(94, 97, 100),
                sourceImpactFrame = 100
            )
        )
    }

    @Test fun clipBoundaryDoesNotInventMissingTemporalSideRequirement() {
        assertTrue(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = listOf(0, 3, 6, 9),
                extractedSourceFrameIndices = listOf(0, 6, 9),
                sourceImpactFrame = 0
            )
        )
        assertTrue(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = listOf(90, 93, 96, 99),
                extractedSourceFrameIndices = listOf(90, 96, 99),
                sourceImpactFrame = 99
            )
        )
    }

    @Test fun malformedTemporalContextProvenanceFailsClosed() {
        assertFalse(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = listOf(94, 100, 100, 106),
                extractedSourceFrameIndices = listOf(94, 100, 106),
                sourceImpactFrame = 100
            )
        )
        assertFalse(
            ImpactReplayExtractionIntegrity.hasRequiredTemporalContext(
                plannedSourceFrameIndices = listOf(94, 100, 106),
                extractedSourceFrameIndices = listOf(94, 106),
                sourceImpactFrame = 100
            )
        )
    }

    @Test fun sourceFrameProvenanceDrivesReplayTiming() {
        assertEquals(
            -25.0,
            ImpactReplayExtractionIntegrity.relativeTimeMs(
                frameIndex = 1,
                frameCount = 5,
                fps = 240,
                impactIndex = 2,
                sourceFrameIndices = listOf(94, 97, 100, 103, 106),
                sourceImpactFrame = 100
            )!!,
            0.0001
        )
    }

    @Test fun legacyReplayWithoutProvenanceCanUseOrdinalTiming() {
        assertEquals(
            -1000.0 / 240.0,
            ImpactReplayExtractionIntegrity.relativeTimeMs(
                frameIndex = 1,
                frameCount = 5,
                fps = 240,
                impactIndex = 2,
                sourceFrameIndices = emptyList(),
                sourceImpactFrame = null
            )!!,
            0.0001
        )
    }

    @Test fun partialOrMisalignedTimingProvenanceFailsClosed() {
        assertNull(
            ImpactReplayExtractionIntegrity.relativeTimeMs(
                frameIndex = 1,
                frameCount = 5,
                fps = 240,
                impactIndex = 2,
                sourceFrameIndices = listOf(94, 97, 100, 103),
                sourceImpactFrame = 100
            )
        )
        assertNull(
            ImpactReplayExtractionIntegrity.relativeTimeMs(
                frameIndex = 1,
                frameCount = 5,
                fps = 240,
                impactIndex = 2,
                sourceFrameIndices = listOf(94, 97, 100, 103, 106),
                sourceImpactFrame = null
            )
        )
        assertNull(
            ImpactReplayExtractionIntegrity.relativeTimeMs(
                frameIndex = 1,
                frameCount = 5,
                fps = 240,
                impactIndex = 2,
                sourceFrameIndices = listOf(94, 97, 101, 103, 106),
                sourceImpactFrame = 100
            )
        )
    }
}
