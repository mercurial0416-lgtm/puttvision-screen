package com.puttvision.screen

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repairs the offline-cold-start hole in ONLINE LEAGUE recovery.
 *
 * V36 resumes/heartbeats once the in-memory player is known, but the first
 * refreshMe() can legitimately fail when the app is launched without network.
 * This watchdog retries only until identity is restored, then gets out of the way.
 */
object V37OnlineColdStartRecovery {
    private const val prefsName = "puttvision_online_secure"
    private const val retryMs = 15_000L

    private val installed = AtomicBoolean(false)
    private val requestRunning = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appContext: Context? = null

    private val tick = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            if (V31OnlineRuntime.player == null && hasStoredToken(context)) {
                retryIdentity(context)
            }
            main.postDelayed(this, retryMs)
        }
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        main.post(tick)
    }

    private fun retryIdentity(context: Context) {
        if (!requestRunning.compareAndSet(false, true)) return
        V31OnlineRuntime.refreshMe(context) { me ->
            if (me.isSuccess) {
                V31OnlineRuntime.resume(context) { resume ->
                    if (resume.isSuccess && V31OnlineRuntime.activeMatchId != null) {
                        V36OnlinePresenceRuntime.forceRefresh(context)
                    }
                    requestRunning.set(false)
                }
            } else {
                requestRunning.set(false)
            }
        }
    }

    private fun hasStoredToken(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return !prefs.getString("token", null).isNullOrBlank() && !prefs.getString("iv", null).isNullOrBlank()
    }
}
