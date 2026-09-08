package com.puttvision.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityLaunchSessionGuardTest {
    @Test
    fun staleSessionCannotMatchSameDisplayAfterReconnect() {
        val guard = UnityLaunchSessionGuard()
        val first = guard.begin(42)
        assertTrue(guard.matches(42, first))

        guard.clear()
        val second = guard.begin(42)

        assertNotEquals(first, second)
        assertFalse(guard.matches(42, first))
        assertTrue(guard.matches(42, second))
        assertTrue(guard.matchesDisplay(42))
    }

    @Test
    fun staleLaunchFailureCannotClearCurrentLaunch() {
        val guard = UnityLaunchSessionGuard()
        val first = guard.begin(7)
        val second = guard.begin(7)

        guard.clearIf(7, first)
        assertTrue(guard.matches(7, second))

        guard.clearIf(7, second)
        assertFalse(guard.matchesDisplay(7))
    }

    @Test
    fun terminalFailureInvalidatesSameLaunchReadyCallback() {
        val guard = UnityLaunchSessionGuard()
        val session = guard.begin(12)
        assertTrue(guard.matches(12, session))

        guard.clearIf(12, session)

        assertFalse(guard.matches(12, session))
        assertFalse(guard.matchesDisplay(12))
    }
}
