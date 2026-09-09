package com.puttvision.screen

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.Display

/** Keeps asynchronous Unity callbacks bound to the exact launch that created them. */
internal class UnityLaunchSessionGuard {
    private var nextSessionId = 0L
    private var activeDisplayId: Int? = null
    private var activeSessionId: Long? = null

    @Synchronized
    fun begin(displayId: Int): Long {
        nextSessionId = if (nextSessionId == Long.MAX_VALUE) 1L else nextSessionId + 1L
        activeDisplayId = displayId
        activeSessionId = nextSessionId
        return nextSessionId
    }

    @Synchronized
    fun matches(displayId: Int, sessionId: Long): Boolean =
        activeDisplayId == displayId && activeSessionId == sessionId

    @Synchronized
    fun matchesDisplay(displayId: Int): Boolean = activeDisplayId == displayId

    @Synchronized
    fun clearIf(displayId: Int, sessionId: Long) {
        if (matches(displayId, sessionId)) {
            activeDisplayId = null
            activeSessionId = null
        }
    }

    @Synchronized
    fun clear() {
        activeDisplayId = null
        activeSessionId = null
    }
}

/**
 * Reflection-only lifecycle wrapper around Unity as a Library.
 *
 * Keeping all Unity symbols behind reflection means ordinary Android builds still compile and run
 * when `unity-export/unityLibrary` has not been generated. When the library is present, Unity 6 is
 * exported with the classic Activity entry point and this object launches UnityPlayerActivity on
 * the HDMI/DeX presentation display.
 */
object UnityTvRuntime {
    private const val UNITY_ACTIVITY = "com.unity3d.player.UnityPlayerActivity"
    private const val UNITY_PLAYER = "com.unity3d.player.UnityPlayer"
    private const val DISPLAY_ID_EXTRA = "pv_display_id"
    private const val LAUNCH_SESSION_EXTRA = "pv_launch_session"

    private val stateLock = Any()
    private val launchSessions = UnityLaunchSessionGuard()

    @Volatile
    var setupComplete: Boolean = false
        private set

    @Volatile
    var lastFailure: String? = null
        private set

    fun isAvailable(): Boolean = runCatching {
        Class.forName(UNITY_ACTIVITY)
        Class.forName(UNITY_PLAYER)
    }.isSuccess

    fun launch(context: Context, display: Display): Boolean {
        if (!isAvailable()) return false
        val displayId = display.displayId
        val launchSession = synchronized(stateLock) {
            setupComplete = false
            lastFailure = null
            launchSessions.begin(displayId)
        }

        return runCatching {
            val activityClass = Class.forName(UNITY_ACTIVITY)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            val intent = Intent(context, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(DISPLAY_ID_EXTRA, displayId)
                putExtra(LAUNCH_SESSION_EXTRA, launchSession)
            }
            context.startActivity(intent, options.toBundle())
            UnityRendererBridge.enableIfRuntimeAvailable()
            true
        }.getOrElse { throwable ->
            synchronized(stateLock) {
                if (launchSessions.matches(displayId, launchSession)) {
                    lastFailure = throwable.message ?: throwable.javaClass.simpleName
                    setupComplete = false
                    launchSessions.clearIf(displayId, launchSession)
                }
            }
            false
        }
    }

    fun isReadyOn(displayId: Int): Boolean = synchronized(stateLock) {
        setupComplete && launchSessions.matchesDisplay(displayId)
    }

    /**
     * Unity activities finish and report readiness asynchronously. Both display id and launch
     * session must match so a late callback from an old HDMI task cannot bless a same-id reconnect.
     */
    @JvmStatic
    fun onUnityReady(displayId: Int, launchSession: Long) {
        synchronized(stateLock) {
            if (!launchSessions.matches(displayId, launchSession)) return
            setupComplete = true
            lastFailure = null
            UnityRendererBridge.enableIfRuntimeAvailable()
        }
    }

    @JvmStatic
    fun onUnityFailure(displayId: Int, launchSession: Long, message: String?) {
        synchronized(stateLock) {
            if (!launchSessions.matches(displayId, launchSession)) return
            setupComplete = false
            lastFailure = message?.takeIf { it.isNotBlank() } ?: "Unity renderer failure"
            UnityRendererBridge.enabled = false
            launchSessions.clearIf(displayId, launchSession)
        }
    }

    fun finishCurrent() {
        synchronized(stateLock) {
            setupComplete = false
            launchSessions.clear()
            UnityRendererBridge.enabled = false
        }

        // UnityPlayerActivity owns Unity's lifecycle. Finishing the Activity is intentionally used
        // instead of UnityPlayer.quit(), because Unity documents quit as terminating the hosting
        // process. If a future Unity version hides currentActivity, this safely becomes a no-op and
        // the system will reclaim the old task when its display disappears.
        runCatching {
            val playerClass = Class.forName(UNITY_PLAYER)
            val activity = sequenceOf("currentActivity", "mCurrentActivity")
                .mapNotNull { name ->
                    runCatching {
                        val field = playerClass.getDeclaredField(name).apply { isAccessible = true }
                        field.get(null) as? Activity
                    }.getOrNull()
                }
                .firstOrNull()

            if (activity != null && activity.javaClass.name.contains("UnityPlayer")) {
                activity.runOnUiThread {
                    runCatching { activity.finish() }
                }
            }
        }
    }
}
