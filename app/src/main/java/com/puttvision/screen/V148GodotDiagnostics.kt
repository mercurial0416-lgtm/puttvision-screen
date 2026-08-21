package com.puttvision.screen

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.godotengine.godot.GodotActivity
import java.io.File
import java.io.FileOutputStream

/**
 * V148 hardwareless crash journal.
 *
 * SharedPreferences.apply() can lose the last write when the dedicated Godot process dies in
 * native code.  This tiny journal is synchronously fsync'd so the coordinator process can read the
 * exact last stage after SIGSEGV/SIGABRT without requiring adb/logcat from the user.
 */
object V148GodotCrashJournal {
    data class Stage(val atMs: Long, val mode: String, val stage: String)

    private const val FILE_NAME = "v148_godot_stage.txt"

    fun write(context: Context, mode: String, stage: String) {
        val payload = "${System.currentTimeMillis()}|$mode|$stage\n".toByteArray(Charsets.UTF_8)
        runCatching {
            FileOutputStream(File(context.filesDir, FILE_NAME), false).use { out ->
                out.write(payload)
                out.flush()
                out.fd.sync()
            }
        }
    }

    fun read(context: Context): Stage? = runCatching {
        val line = File(context.filesDir, FILE_NAME).readText(Charsets.UTF_8).trim()
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) null else Stage(parts[0].toLong(), parts[1], parts[2])
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}

/**
 * Existing MainActivity continues launching this class.  V148 turns it into a native coordinator
 * instead of a GodotActivity so a Godot native crash can never take the diagnostic screen down.
 * It first runs the exact V143 scene with no Android plugin/GameEngine.  Only if that stays alive
 * for four seconds does it launch the full SIM LAB in a second isolated process.
 */
class V144HardwarelessGodotActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private var childStarted = false
    private var currentMode = ""
    private var childLaunchedAtMs = 0L
    private var evaluating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(buildUi())
        V148GodotCrashJournal.clear(this)
        handler.postDelayed({ launchSmoke() }, 300L)
    }

    override fun onResume() {
        super.onResume()
        if (childStarted && !evaluating) {
            evaluating = true
            handler.postDelayed({ evaluateReturnedChild(0) }, 550L)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun launchSmoke() {
        currentMode = "smoke"
        childStarted = true
        evaluating = false
        childLaunchedAtMs = System.currentTimeMillis()
        V148GodotCrashJournal.write(this, "smoke", "launch-requested")
        status.text = "1/2  GODOT 단독 부팅"
        detail.text = "플러그인 · GameEngine · 물리 pump 없이 V143 씬만 확인 중"
        startActivity(Intent(this, V148GodotSmokeActivity::class.java))
    }

    private fun launchFull() {
        currentMode = "full"
        childStarted = true
        evaluating = false
        childLaunchedAtMs = System.currentTimeMillis()
        V148GodotCrashJournal.write(this, "full", "launch-requested")
        status.text = "2/2  전체 SIM LAB"
        detail.text = "Godot 단독 부팅 통과 · V143 bridge + 실제 GameEngine 연결"
        startActivity(Intent(this, V148HardwarelessFullActivity::class.java))
    }

    private fun evaluateReturnedChild(attempt: Int) {
        val stage = V148GodotCrashJournal.read(this)
        val exit = latestExit(currentMode)
        val cleanStage = when (currentMode) {
            "smoke" -> stage?.mode == "smoke" && (stage.stage == "smoke-stable" || stage.stage == "smoke-finished")
            else -> stage?.mode == "full" && (stage.stage == "full-user-close" || stage.stage == "full-finished")
        }

        if (currentMode == "smoke" && cleanStage) {
            evaluating = false
            childStarted = false
            status.text = "GODOT 단독 부팅  PASS"
            detail.text = "씬/렌더러 단독 경로 정상 · 전체 bridge 경로로 자동 전환"
            handler.postDelayed({ launchFull() }, 500L)
            return
        }

        if (currentMode == "full" && cleanStage) {
            evaluating = false
            childStarted = false
            status.text = "전체 SIM LAB  PASS"
            detail.text = "Godot + bridge + GameEngine 경로가 정상 종료됨"
            return
        }

        if (exit == null && attempt < 6) {
            handler.postDelayed({ evaluateReturnedChild(attempt + 1) }, 350L)
            return
        }

        evaluating = false
        childStarted = false
        val stageText = stage?.let { "${it.mode} · ${it.stage}" } ?: "stage 기록 없음"
        val exitText = exit?.let {
            "${reasonName(it.reason)} · status=${it.status}" +
                (it.description?.takeIf(String::isNotBlank)?.let { d -> "\n$d" } ?: "")
        } ?: "Android exit record 없음"
        status.text = if (currentMode == "smoke") "GODOT 단독 경로에서 종료됨" else "전체 SIM LAB 경로에서 종료됨"
        detail.text = "LAST  $stageText\nEXIT  $exitText"
    }

    private fun latestExit(mode: String): ApplicationExitInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val suffix = if (mode == "smoke") ":godot_smoke" else ":hardwareless_full"
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching {
            manager.getHistoricalProcessExitReasons(packageName, 0, 30)
                .asSequence()
                .filter { it.processName == packageName + suffix }
                .filter { it.timestamp >= childLaunchedAtMs - 1500L }
                .maxByOrNull { it.timestamp }
        }.getOrNull()
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "RESOURCE_LIMIT"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(28), dp(32), dp(28))
            setBackgroundColor(Color.rgb(4, 8, 10))
        }
        root.addView(TextView(this).apply {
            text = "PUTTVISION · GODOT DIAGNOSTIC"
            setTextColor(Color.rgb(105, 239, 176))
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "진단 준비 중"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(status)
        detail = TextView(this).apply {
            text = "Godot 단독 → 전체 SIM LAB 순서로 자동 검사합니다"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(18))
        }
        root.addView(detail)
        root.addView(Button(this).apply {
            text = "닫기"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(24, 34, 39))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(180), dp(48)))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Pure engine/scene smoke test: intentionally no V143 plugin, GameEngine or bridge pump. */
class V148GodotSmokeActivity : GodotActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var stable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        V148GodotCrashJournal.write(this, "smoke", "onCreate-before-godot")
        super.onCreate(savedInstanceState)
        V148GodotCrashJournal.write(this, "smoke", "onCreate-after-godot")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val badge = TextView(this).apply {
            text = "GODOT SMOKE · NO PLUGIN · NO PHYSICS"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(210, 4, 8, 10))
            textSize = 11f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        addContentView(badge, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(10)
            marginStart = dp(10)
        })
    }

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        V148GodotCrashJournal.write(this, "smoke", "godot-ready")
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                stable = true
                V148GodotCrashJournal.write(this, "smoke", "smoke-stable")
                finish()
            }
        }, 4000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (stable) V148GodotCrashJournal.write(this, "smoke", "smoke-finished")
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
