package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V42CompanionSyncPolicyTest {
    @Test fun missingSyncAlwaysRefreshes() {
        assertTrue(V42CompanionSyncPolicy.shouldRefresh(0L, 10_000L, null))
    }

    @Test fun freshSyncDoesNotRefreshEveryShot() {
        val sync = V28ClockSync(offsetMs = 3L, rttMs = 20L)
        assertFalse(V42CompanionSyncPolicy.shouldRefresh(100_000L, 120_000L, sync))
    }

    @Test fun longSessionRefreshesClockPeriodically() {
        val sync = V28ClockSync(offsetMs = 3L, rttMs = 20L)
        assertTrue(
            V42CompanionSyncPolicy.shouldRefresh(
                100_000L,
                100_000L + V42CompanionSyncPolicy.REFRESH_INTERVAL_MS,
                sync
            )
        )
    }

    @Test fun backwardClockJumpForcesResync() {
        val sync = V28ClockSync(offsetMs = 3L, rttMs = 20L)
        assertTrue(V42CompanionSyncPolicy.shouldRefresh(100_000L, 99_000L, sync))
    }

    @Test fun pathologicalLanRttIsRejected() {
        assertTrue(V42CompanionSyncPolicy.acceptable(V28ClockSync(0L, 35L)))
        assertFalse(
            V42CompanionSyncPolicy.acceptable(
                V28ClockSync(0L, V42CompanionSyncPolicy.MAX_ACCEPTABLE_RTT_MS + 1L)
            )
        )
    }
}
