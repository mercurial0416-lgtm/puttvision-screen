package com.puttvision.screen

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * V150 fallback watchdog for the isolated EMPTY/SCENE Godot probes.
 *
 * Godot 4.7.0 can render normally on the real device without invoking
 * GodotActivity.onGodotSetupCompleted(). The V149 probe completion timer lived only behind that
 * callback, so a healthy probe could remain on-screen forever. This provider is instantiated in
 * each probe process before its Activity and arms a lifecycle-based timer that is independent of
 * Godot callbacks. If the process survives six seconds, the probe is considered stable and is
 * returned to the native coordinator. A native crash before the deadline still kills the process
 * and is attributed by ApplicationExitInfo exactly as before.
 */
class V150GodotProbeWatchdogProvider : ContentProvider() {
    private val handler = Handler(Looper.getMainLooper())
    private var armed = false

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (armed) return
                val mode = when (activity) {
                    is V148GodotSmokeActivity -> "empty"
                    is V149GodotSceneSmokeActivity -> "scene"
                    else -> return
                }
                armed = true
                handler.postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        V148GodotCrashJournal.write(activity, mode, "$mode-stable")
                        activity.finish()
                    }
                }, 6000L)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}