package com.puttvision.screen

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Improvement 29: health becomes a scorecard with severity bars instead of a long diagnostic paragraph. */
fun showV47SoloHealthDialog(context: Context) {
    val snapshot=V47SoloIntegrityRuntime.health()
    val root=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(0,context.pvDp(10),0,context.pvDp(4))}

    val hero=context.v51ElevatedPanel(12).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        addView(V51ScoreRingView(context).apply{score=snapshot.score;label=snapshot.grade},LinearLayout.LayoutParams(context.pvDp(96),context.pvDp(96)))
        addView(LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL;setPadding(context.pvDp(12),0,0,0)
            addView(context.v51StatusPill(if(snapshot.insufficientData)"MORE DATA" else "SYSTEM ${snapshot.grade}",if(snapshot.insufficientData)V51Tone.WARN else V51VisualPolicy.toneForScore(snapshot.score)))
            addView(TextView(context).apply{text=snapshot.topIssue;setTextColor(Pv.textHi);textSize=context.pvSp(12f);typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(7),0,context.pvDp(3))})
            addView(TextView(context).apply{text=if(snapshot.insufficientData)"표본이 부족해 READY 판정을 보류합니다." else "샷·기록·HFR·데이터 커버리지 소프트웨어 상태";setTextColor(Pv.textMid);textSize=context.pvSp(7.3f)})
        },LinearLayout.LayoutParams(0,-2,1f))
    }
    root.addView(hero)

    root.addView(TextView(context).apply{text="SUBSYSTEMS";setTextColor(Pv.textLo);textSize=context.pvSp(6.8f);typeface=Typeface.DEFAULT_BOLD;letterSpacing=.10f;setPadding(context.pvDp(2),context.pvDp(13),0,context.pvDp(5))})
    snapshot.sections.forEach{section->
        val tone=V51VisualPolicy.toneForScore(section.score)
        root.addView(context.v51ElevatedPanel(9).apply{
            val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(context).apply{text=section.name;setTextColor(Pv.textHi);textSize=context.pvSp(8.2f);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))
            row.addView(context.v51StatusPill("${section.status}${if(section.optional)" · OPT" else ""}",V51VisualPolicy.toneForStatus(section.status)))
            addView(row)
            addView(context.v51Meter("HEALTH",section.score,tone,section.detail),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(7)})
        },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=context.pvDp(7)})
    }

    root.addView(context.v51ElevatedPanel(10).apply{
        addView(TextView(context).apply{text="NEXT ACTION";setTextColor(Pv.primary);textSize=context.pvSp(7f);typeface=Typeface.DEFAULT_BOLD;letterSpacing=.08f})
        snapshot.nextActions.forEachIndexed{index,action->addView(TextView(context).apply{text="${index+1}. $action";setTextColor(if(index==0)Pv.textHi else Pv.textMid);textSize=context.pvSp(7.8f);if(index==0)typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(5),0,0)})}
    })
    root.addView(TextView(context).apply{text="※ SOFTWARE HEALTH ONLY · 실제 기준장비 정확도 인증을 대신하지 않습니다.";setTextColor(Pv.textLo);textSize=context.pvSp(6.3f);setPadding(context.pvDp(2),context.pvDp(10),0,0)})

    context.pvDialog("솔로 시스템 상태",root).show()
}
