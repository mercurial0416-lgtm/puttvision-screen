package com.puttvision.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V40AccuracyCiFixturesTest {
    private fun sample(
        id: String = "shot-001",
        profileKey: String? = "Galaxy S25|CAM:0|FPS:240|SIZE:1920x1080|API:36",
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
        profileKey = profileKey,
        refBall = refBall,
        refLaunch = refLaunch,
        refHead = 1.22,
        refFace = refFace,
        refPath = refPath
    )

    private fun readySamples(): List<ValidationSample> =
        (0 until V40AccuracyCiFixtures.MIN_CI_SHOTS).map { i -> sample(id = "shot-${"%03d".format(i)}") }

    @Test fun referenceCsvMatchesProductionGateSchemaAndTolerances() {
        val csv = V40AccuracyCiFixtures.referenceCsv(listOf(sample()))
        val lines = csv.trim().lines()
        assertEquals(
            "id,profile_key,ball_speed_mps,ball_tol_mps,launch_deg,launch_tol_deg,face_deg,face_tol_deg,path_deg,path_tol_deg",
            lines[0]
        )
        assertEquals(
            "shot-001,Galaxy S25|CAM:0|FPS:240|SIZE:1920x1080|API:36,1.500000,0.080000,0.250000,0.350000,-0.400000,0.550000,0.600000,0.650000",
            lines[1]
        )
    }

    @Test fun missingReferenceMetricLeavesBothValueAndToleranceBlank() {
        val row = V40AccuracyCiFixtures.referenceCsv(
            listOf(sample(refFace = null, refPath = null))
        ).trim().lines()[1].split(',')
        assertEquals("", row[6])
        assertEquals("", row[7])
        assertEquals("", row[8])
        assertEquals("", row[9])
    }

    @Test fun measuredCsvUsesSameStableShotIdProfileAndKeepsOptionalBlanks() {
        val csv = V40AccuracyCiFixtures.measuredCsv(
            listOf(sample(id = "fixture-42", measuredFace = null, measuredPath = null))
        )
        val lines = csv.trim().lines()
        assertEquals("id,profile_key,ball_speed_mps,launch_deg,face_deg,path_deg", lines[0])
        assertEquals(
            "fixture-42,Galaxy S25|CAM:0|FPS:240|SIZE:1920x1080|API:36,1.480000,0.300000,,",
            lines[1]
        )
    }

    @Test fun csvEscapesUnexpectedIdsAndProfilesWithoutChangingMetricColumns() {
        val csv = V40AccuracyCiFixtures.measuredCsv(
            listOf(sample(id = "shot,\"A\"", profileKey = "phone,\"240\""))
        )
        val row = csv.trim().lines()[1]
        assertTrue(row.startsWith("\"shot,\"\"A\"\"\",\"phone,\"\"240\"\"\",1.480000,0.300000"))
    }

    @Test fun officialFixtureRequiresAtLeastTwentyCompleteShots() {
        assertNotNull(V40AccuracyCiFixtures.readinessIssue(readySamples().dropLast(1)))
        assertNull(V40AccuracyCiFixtures.readinessIssue(readySamples()))
    }

    @Test fun missingFaceCoverageBlocksOfficialFixture() {
        val samples = readySamples().mapIndexed { index, value ->
            if (index == 0) sample(id = value.id, refFace = null) else value
        }
        val issue = V40AccuracyCiFixtures.readinessIssue(samples)
        assertTrue(issue?.contains("FACE") == true)
    }

    @Test fun duplicateShotIdsBlockOfficialFixture() {
        val samples = readySamples().toMutableList()
        samples[samples.lastIndex] = sample(id = samples.first().id)
        assertTrue(V40AccuracyCiFixtures.readinessIssue(samples)?.contains("ID") == true)
    }

    @Test fun missingCaptureProfileBlocksOfficialFixture() {
        val samples = readySamples().toMutableList()
        samples[0] = sample(id = samples[0].id, profileKey = null)
        assertTrue(V40AccuracyCiFixtures.readinessIssue(samples)?.contains("프로필") == true)
    }

    @Test fun underSampledSecondCaptureProfileCannotHideBehindGlobalTwentyShots() {
        val base = readySamples().toMutableList()
        repeat(V40AccuracyCiFixtures.MIN_PROFILE_SHOTS - 1) { i ->
            base[base.lastIndex - i] = sample(
                id = base[base.lastIndex - i].id,
                profileKey = "Galaxy S25|CAM:0|FPS:120|SIZE:1920x1080|API:36"
            )
        }
        val issue = V40AccuracyCiFixtures.readinessIssue(base)
        assertTrue(issue?.contains("8샷 미만") == true)
    }

    @Test fun twoCaptureProfilesPassWhenBothHaveEnoughCompleteShots() {
        val samples = readySamples().mapIndexed { index, value ->
            if (index < 10) value else sample(
                id = value.id,
                profileKey = "Galaxy S25|CAM:0|FPS:120|SIZE:1920x1080|API:36"
            )
        }
        assertNull(V40AccuracyCiFixtures.readinessIssue(samples))
    }
}
