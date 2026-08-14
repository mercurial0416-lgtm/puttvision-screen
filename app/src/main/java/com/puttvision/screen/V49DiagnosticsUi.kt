package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object V49Diagnostics {
    /** Feature 29: one shareable snapshot captures the exact transient state needed for a bug report. */
    fun snapshotText(nowMs: Long = System.currentTimeMillis()): String {
        val health = V47SoloIntegrityRuntime.health(nowMs)
        val insight = V49SessionInsightsRuntime.snapshot
        val fusion = V37FeatureFusion.diagnostics
        val hfr = V43HfrHealthWindow.summary()
        val failures = V45HfrFailureRuntime.summary()
        val companion = V16CompanionLinkRuntime.status()
        val stereo = V44StereoPrepRuntime.snapshot(nowMs)
        val training = V31TrainingSessionRuntime.progress()
        return buildString {
            appendLine("PuttVision SOLO diagnostics")
            appendLine("timeMs=$nowMs")
            appendLine("health=${health.shortLabel}")
            health.sections.forEach { appendLine("health.${it.name}=${it.status};score=${it.score};${it.detail}") }
            appendLine("hfr=${hfr.label}")
            appendLine("hfrFailures=${failures.label}")
            appendLine("fusion=${fusion.label};views=${fusion.activeViews};diversity=${fusion.diversityScore};drop=${fusion.droppedPackets};conf=${"%.2f".format(fusion.confidenceBefore)}->${"%.2f".format(fusion.confidenceAfter)}")
            appendLine("companion=${companion.label};role=${companion.role};peers=${companion.peers};rejected=${companion.rejected}")
            appendLine("stereo=${stereo.shortLabel};reason=${stereo.reason}")
            appendLine("training=${training.summary};paused=${training.paused};progress=${training.completionPct};eta=${training.estimatedRemainingMinutes}")
            appendLine("session=${insight.headline}")
            appendLine("session.direction=${insight.directionLabel}")
            appendLine("session.pace=${insight.paceLabel}")
            appendLine("session.confidence=${insight.confidenceLabel}")
            appendLine("session.fade=${insight.fadeLabel}")
            appendLine("session.streak=${insight.streakLabel}")
            appendLine("quickPlan=${insight.quickPlan.title};reason=${insight.quickPlanReason}")
            appendLine("note=SOFTWARE_DIAGNOSTICS_NOT_REAL_DEVICE_ACCURACY_CERTIFICATION")
        }
    }

    fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PuttVision SOLO diagnostics")
            putExtra(Intent.EXTRA_TEXT, snapshotText())
        }
        context.startActivity(Intent.createChooser(intent, "진단 리포트 공유"))
    }

    /** Feature 30: clears transient diagnostic windows without deleting shots, profiles, calibration or training. */
    fun resetTransient() {
        V43HfrHealthWindow.clear()
        V45HfrFailureRuntime.reset()
        V37FeatureFusion.resetDiagnostics()
    }
}

fun showV49DiagnosticsDialog(context: Context) {
    val health = V47SoloIntegrityRuntime.health()
    val hfr = V43HfrHealthWindow.summary()
    val fusion = V37FeatureFusion.diagnostics
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(6))
    }
    fun add(text: String, strong: Boolean = false, accent: Boolean = false) {
        root.addView(TextView(context).apply {
            this.text = text
            textSize = context.pvSp(if (strong) 9.5f else 8f)
            setTextColor(if (accent) Pv.primary else if (strong) Pv.textHi else Pv.textMid)
            if (strong) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, context.pvDp(3), 0, context.pvDp(3))
        })
    }
    add("SOLO DIAGNOSTICS", true, true)
    add(health.shortLabel, true)
    add(hfr.label)
    add("FUSION ${fusion.label}")
    add("샷/프로필/보정/훈련 기록은 초기화 버튼으로 삭제되지 않습니다.")

    AlertDialog.Builder(context)
        .setTitle("진단")
        .setView(root)
        .setPositiveButton("리포트 공유") { _, _ ->
            runCatching { V49Diagnostics.share(context) }
                .onFailure { Toast.makeText(context, "공유 앱을 열 수 없습니다", Toast.LENGTH_SHORT).show() }
        }
        .setNeutralButton("진단만 초기화") { _, _ ->
            V49Diagnostics.resetTransient()
            Toast.makeText(context, "HFR/FUSION 진단 창만 초기화됨", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("닫기", null)
        .show()
}
