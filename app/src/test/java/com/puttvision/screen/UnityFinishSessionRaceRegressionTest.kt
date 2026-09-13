package com.puttvision.screen

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnityFinishSessionRaceRegressionTest {
    private fun runtimeSource(): String {
        val candidates = listOf(
            File("src/main/java/com/puttvision/screen/UnityTvRuntime.kt"),
            File("app/src/main/java/com/puttvision/screen/UnityTvRuntime.kt")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("UnityTvRuntime.kt not found from ${File(".").absolutePath}")
    }

    @Test
    fun launchIdentitySnapshotCannotAliasNextHdmiSession() {
        val guard = UnityLaunchSessionGuard()
        val oldSession = guard.begin(displayId = 7)
        val retired = guard.currentIdentity()

        assertEquals(UnityLaunchIdentity(7, oldSession), retired)
        guard.clear()
        assertNull(guard.currentIdentity())

        val newSession = guard.begin(displayId = 7)
        assertNotEquals(oldSession, newSession)
        assertNotEquals(retired, guard.currentIdentity())
    }

    @Test
    fun finishOnlyTargetsActivityOwnedByRetiredLaunch() {
        val source = runtimeSource()
        val finishStart = source.indexOf("fun finishCurrent()")
        assertTrue("finishCurrent must exist", finishStart >= 0)
        val finish = source.substring(finishStart)

        val snapshot = finish.indexOf("val identity = launchSessions.currentIdentity()")
        val clearSession = finish.indexOf("launchSessions.clear()", startIndex = snapshot)
        val displayMatch = finish.indexOf(
            "getIntExtra(DISPLAY_ID_EXTRA, Int.MIN_VALUE) == retiredLaunch.displayId",
            startIndex = clearSession
        )
        val sessionMatch = finish.indexOf(
            "getLongExtra(LAUNCH_SESSION_EXTRA, Long.MIN_VALUE) == retiredLaunch.sessionId",
            startIndex = displayMatch
        )
        val ownershipGate = finish.indexOf("belongsToRetiredLaunch", startIndex = sessionMatch)
        val finishActivity = finish.indexOf("activity.finish()", startIndex = ownershipGate)

        assertTrue("retired launch identity must be captured before clearing it", snapshot >= 0 && clearSession > snapshot)
        assertTrue("activity display must match the retired launch", displayMatch > clearSession)
        assertTrue("activity session must match the retired launch", sessionMatch > displayMatch)
        assertTrue("ownership gate must protect Activity.finish", ownershipGate > sessionMatch)
        assertTrue("only the owned retired Activity may be finished", finishActivity > ownershipGate)
    }
}
