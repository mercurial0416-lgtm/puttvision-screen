package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V40AccuracyCiFixturesTest {
    private fun sample(
        id: String = "shot-001",
        refBall: Double? = 1.5,
        refLaunch: Double? = .25,
        refFace: Double? = -.4,
        refPath: Double? = .6,
        measuredFace: Double? = -.35,
        measuredPath: Double? = .55
    ) = ValidationSample(
        id = id,
        timestampMs = 1_700_000_000_000L,
        measuredBall = 1.48,
        measuredLaunch = .30,
        measuredHead = 1.2,
        measuredFace = measuredFace,
        measuredPath = measuredPath,
        confidence = .91,
        profileKey = "galaxy-s25-240",
        refBall = refBall,
        refLaunch = refLaunch,
        refHead = 1.22,
        refFace = refFace,
        refPath = refPath
    )

    @Test fun referenceCsvMatchesProductionGateSchemaAndTolerances() {
        val csv = V40AccuracyCiFixtures.referenceCsv(listOf(sample()))
        val lines = csv.trim().lines()
        assertEquals(
            "id,ball_speed_mps,ball_tol_mps,launch_deg,launch_tol_deg,face_deg,face_tol_deg,path_deg,path_tol_deg",
            lines[0]
        )
        assertEquals(
            "shot-001,1.500000,0.080000,0.250000,0.350000,-0.400000,0.550000,0.600000,0.650000",
            lines[1]
        )
    }

    @Test fun missingReferenceMetricLeavesBothValueAndToleranceBlank() {
        val row = V40AccuracyCiFixtures.referenceCsv(
            listOf(sample(refFace = null, refPath = null))
        ).trim().lines()[1].split(',')
        assertEquals("", row[5])
        assertEquals("", row[6])
        assertEquals("", row[7])
        assertEquals("", row[8])
    }

    @Test fun measuredCsvUsesSameStableShotIdAndKeepsOptionalBlanks() {
        val csv = V40AccuracyCiFixtures.measuredCsv(
            listOf(sample(id = "fixture-42", measuredFace = null, measuredPath = null))
        )
        val lines = csv.trim().lines()
        assertEquals("id,ball_speed_mps,launch_deg,face_deg,path_deg", lines[0])
        assertEquals("fixture-42,1.480000,0.300000,,", lines[1])
    }

    @Test fun csvEscapesUnexpectedIdsWithoutChangingMetricColumns() {
        val csv = V40AccuracyCiFixtures.measuredCsv(listOf(sample(id = "shot,\"A\"")))
        val row = csv.trim().lines()[1]
        assertTrue(row.startsWith("\"shot,\"\"A\"\"\",1.480000,0.300000"))
    }
}