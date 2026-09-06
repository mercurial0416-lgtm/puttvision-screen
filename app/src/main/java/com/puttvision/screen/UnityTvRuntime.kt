package com.puttvision.screen

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.Display

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

    @Volatile
    var setupComplete: Boolean = false
        private set

    @Volatile
    var lastFailure: String? = null
        private set

    @Volatile
    private var requestedDisplayId: Int? = null

    fun isAvailable(): Boolean = runCatching {
        Class.forName(UNITY_ACTIVITY)
        Class.forName(UNITY_PLAYER)
    }.isSuccess

    fun launch(context: Context, display: Display): Boolean {
        if (!isAvailable()) return false
        setupComplete = false
        lastFailure = null
        requestedDisplayId = display.displayId

        return runCatching {
            val activityClass = Class.forName(UNITY_ACTIVITY)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
            val intent = Intent(context, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("pv_display_id", display.displayId)
            }
            context.startActivity(intent, options.toBundle())
            UnityRendererBridge.enableIfRuntimeAvailable()
            true
        }.getOrElse { throwable ->
            lastFailure = throwable.message ?: throwable.javaClass.simpleName
            setupComplete = false
            requestedDisplayId = null
            false
        }
    }

    fun isReadyOn(displayId: Int): Boolean =
        setupComplete && requestedDisplayId == displayId

    /**
     * Unity activities can finish and report readiness asynchronously. Bind the callback to the
     * display id carried by the launch Intent so a late callback from a disconnected/old HDMI task
     * cannot mark a newer display launch ready.
     */
    @JvmStatic
    fun onUnityReady(displayId: Int) {
        if (requestedDisplayId != displayId) return
        setupComplete = true
        lastFailure = null
        UnityRendererBridge.enableIfRuntimeAvailable()
    }

    @JvmStatic
    fun onUnityFailure(displayId: Int, message: String?) {
        if (requestedDisplayId != displayId) return
        setupComplete = false
        lastFailure = message?.takeIf { it.isNotBlank() } ?: "Unity renderer failure"
    }

    fun finishCurrent() {
        setupComplete = false
        requestedDisplayId = null
        UnityRendererBridge.enabled = false

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
