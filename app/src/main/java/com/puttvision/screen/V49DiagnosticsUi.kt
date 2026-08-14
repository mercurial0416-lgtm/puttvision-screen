package com.puttvision.screen

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object V49Diagnostics {
    fun snapshotText(nowMs:Long=System.currentTimeMillis()):String{
        val health=V47SoloIntegrityRuntime.health(nowMs);val insight=V49LiveSessionInsights.snapshot();val fusion=V37FeatureFusion.diagnostics;val hfr=V43HfrHealthWindow.summary();val failures=V45HfrFailureRuntime.summary();val companion=V16CompanionLinkRuntime.status();val stereo=V44StereoPrepRuntime.snapshot(nowMs);val training=V31TrainingSessionRuntime.progress()
        return buildString{
            appendLine("PuttVision SOLO diagnostics");appendLine("timeMs=$nowMs");appendLine("health=${health.shortLabel}")
            health.sections.forEach{appendLine("health.${it.name}=${it.status};score=${it.score};${it.detail}")}
            appendLine("hfr=${hfr.label}");appendLine("hfrFailures=${failures.label}")
            appendLine("fusion=${fusion.label};views=${fusion.activeViews};diversity=${fusion.diversityScore};drop=${fusion.droppedPackets};conf=${"%.2f".format(fusion.confidenceBefore)}->${"%.2f".format(fusion.confidenceAfter)}")
            appendLine("companion=${companion.label};role=${companion.role};peers=${companion.peers};rejected=${companion.rejected}")
            appendLine("stereo=${stereo.shortLabel};reason=${stereo.reason}");appendLine("training=${training.summary};paused=${training.paused};progress=${training.completionPct};eta=${training.estimatedRemainingMinutes}")
            appendLine("session=${insight.headline}");appendLine("session.direction=${insight.directionLabel}");appendLine("session.pace=${insight.paceLabel}");appendLine("session.confidence=${insight.confidenceLabel}");appendLine("session.fade=${insight.fadeLabel}");appendLine("session.streak=${insight.streakLabel}");appendLine("quickPlan=${insight.quickPlan.title};reason=${insight.quickPlanReason}")
            appendLine("note=SOFTWARE_DIAGNOSTICS_NOT_REAL_DEVICE_ACCURACY_CERTIFICATION")
        }
    }
    fun share(context:Context){val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"PuttVision SOLO diagnostics");putExtra(Intent.EXTRA_TEXT,snapshotText())};context.startActivity(Intent.createChooser(intent,"진단 리포트 공유"))}
    fun resetTransient(){V43HfrHealthWindow.clear();V45HfrFailureRuntime.reset();V37FeatureFusion.resetDiagnostics()}
}

/** Improvement 30: diagnostics becomes a visual subsystem board with safe action hierarchy. */
fun showV49DiagnosticsDialog(context:Context){
    val health=V47SoloIntegrityRuntime.health();val hfr=V43HfrHealthWindow.summary();val fusion=V37FeatureFusion.diagnostics;val companion=V16CompanionLinkRuntime.status();val stereo=V44StereoPrepRuntime.snapshot()
    val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(0,context.pvDp(10),0,context.pvDp(4))}

    val hero=context.v51ElevatedPanel(12).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        addView(V51ScoreRingView(context).apply{score=health.score;label="SYSTEM"},LinearLayout.LayoutParams(context.pvDp(88),context.pvDp(88)))
        addView(LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL;setPadding(context.pvDp(12),0,0,0)
            addView(context.v51StatusPill(health.grade,V51VisualPolicy.toneForScore(health.score)))
            addView(TextView(context).apply{text=health.topIssue;setTextColor(Pv.textHi);textSize=context.pvSp(11f);typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(7),0,context.pvDp(3))})
            addView(TextView(context).apply{text="재현 정보는 공유 가능 · 샷/프로필/보정 데이터는 진단 초기화로 삭제되지 않음";setTextColor(Pv.textMid);textSize=context.pvSp(7f)})
        },LinearLayout.LayoutParams(0,-2,1f))
    }
    root.addView(hero)

    fun subsystem(title:String,status:String,detail:String,tone:V51Tone):LinearLayout=context.v51ElevatedPanel(10).apply{
        val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        row.addView(TextView(context).apply{text=title;setTextColor(Pv.textHi);textSize=context.pvSp(8.5f);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f));row.addView(context.v51StatusPill(status,tone));addView(row)
        addView(TextView(context).apply{text=detail;setTextColor(Pv.textMid);textSize=context.pvSp(7.2f);setPadding(0,context.pvDp(7),0,0)})
    }
    root.addView(subsystem("HFR ANALYSIS",if(hfr.degraded)"SLOW" else if(hfr.samples==0)"NO SAMPLE" else "GOOD",hfr.label,if(hfr.degraded)V51Tone.BAD else if(hfr.samples==0)V51Tone.NEUTRAL else V51Tone.GOOD),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(10)})
    root.addView(subsystem("MULTI-PHONE FUSION",if(fusion.companionCount>0)"${fusion.companionCount+1} PHONES" else "SINGLE", "${fusion.label} · views ${fusion.activeViews} · diversity ${fusion.diversityScore} · drop ${fusion.droppedPackets}",if(fusion.droppedPackets>0)V51Tone.WARN else if(fusion.companionCount>0)V51Tone.GOOD else V51Tone.NEUTRAL),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(8)})
    root.addView(subsystem("LAN / STEREO",if(companion.peers>0)"CONNECTED" else "OFF","${companion.label} · ${stereo.shortLabel} · ${stereo.reason}",if(companion.peers>0)V51Tone.GOOD else V51Tone.NEUTRAL),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(8)})

    context.pvDialog(
        title="솔로 진단",
        content=root,
        dismissLabel="닫기",
        extraActions=listOf(
            "리포트 공유" to {runCatching{V49Diagnostics.share(context)}.onFailure{Toast.makeText(context,"공유 앱을 열 수 없습니다",Toast.LENGTH_SHORT).show()}},
            "진단만 초기화" to {V49Diagnostics.resetTransient();Toast.makeText(context,"HFR/FUSION 진단 창만 초기화됨",Toast.LENGTH_SHORT).show()}
        )
    ).show()
}
