package com.puttvision.screen

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Improvement 28: training controls become a stateful instrument panel with progress and action hierarchy. */
fun showV49TrainingControlDialog(context: Context) {
    val p = V31TrainingSessionRuntime.progress()
    val root = LinearLayout(context).apply { orientation=LinearLayout.VERTICAL;setPadding(0,context.pvDp(10),0,context.pvDp(4)) }

    val hero=context.v51ElevatedPanel(12).apply {
        val top=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val tone=when{p.finished->V51Tone.GOOD;p.paused->V51Tone.WARN;p.running->V51Tone.GOOD;else->V51Tone.NEUTRAL}
        top.addView(context.v51StatusPill(when{p.finished->"COMPLETE";p.paused->"PAUSED";p.running->"LIVE TRAINING";else->"READY"},tone))
        top.addView(TextView(context).apply{text=if(p.running||p.finished)"${p.completionPct}%" else "15 MIN";setTextColor(v51ToneColor(tone));textSize=context.pvSp(18f);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);gravity=Gravity.END},LinearLayout.LayoutParams(0,-2,1f))
        addView(top)
        addView(TextView(context).apply{text=when{p.running||p.finished->p.blockTitle.ifBlank{p.summary};else->p.lastCompletedSummary?:"오늘의 훈련을 시작할 준비가 됐습니다."};setTextColor(Pv.textHi);textSize=context.pvSp(13f);typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(9),0,context.pvDp(3))})
        if(p.running||p.finished)addView(TextView(context).apply{text="BLOCK ${(p.blockIndex+1).coerceAtMost(p.blockCount)}/${p.blockCount} · SHOT ${p.shotInBlock}/${p.shotsInBlock} · STREAK ${p.streak}";setTextColor(Pv.textMid);textSize=context.pvSp(7.6f);typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD)})
    }
    root.addView(hero)

    if(p.running||p.finished){
        root.addView(context.v51Meter("TOTAL PROGRESS",p.completionPct,V51Tone.GOOD,"전체 성공 ${p.totalSuccesses}/${p.totalShots} · ETA ${p.estimatedRemainingMinutes}분"),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(12)})
        root.addView(context.v51Meter("CURRENT BLOCK HIT",p.blockSuccessPct,if(p.blockSuccessPct>=70)V51Tone.GOOD else V51Tone.WARN,"${p.blockTitle} · target ${"%.1f".format(p.targetDistanceM)}m"),LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(10)})
    }
    p.lastCompletedSummary?.let{summary->
        root.addView(context.v51ElevatedPanel(10).apply{
            addView(TextView(context).apply{text="LAST COMPLETE";setTextColor(Pv.textLo);textSize=context.pvSp(6.8f);typeface=Typeface.DEFAULT_BOLD;letterSpacing=.08f})
            addView(TextView(context).apply{text=summary;setTextColor(Pv.textHi);textSize=context.pvSp(8.3f);typeface=Typeface.DEFAULT_BOLD;setPadding(0,context.pvDp(5),0,0)})
        },LinearLayout.LayoutParams(-1,-2).apply{topMargin=context.pvDp(10)})
    }

    val actions:List<Pair<String,()->Unit>>
    val dismissLabel:String
    val dismissAction:(()->Unit)?
    when{
        p.running&&p.paused->{
            actions=listOf(
                "재개" to { toast(context,V31TrainingSessionRuntime.resume(),"훈련 재개","재개할 훈련이 없습니다") },
                "블록 재시작" to { toast(context,V31TrainingSessionRuntime.restartCurrentBlock(),"현재 블록 재시작","재시작 실패") }
            );dismissLabel="종료";dismissAction={V31TrainingSessionRuntime.stop(true)}
        }
        p.running->{
            actions=listOf(
                "일시정지" to { toast(context,V31TrainingSessionRuntime.pause(),"훈련 일시정지","일시정지 실패") },
                "건너뛰기" to { toast(context,V31TrainingSessionRuntime.skipCurrentBlock(),"다음 블록으로 이동","건너뛰기 실패") }
            );dismissLabel="종료";dismissAction={V31TrainingSessionRuntime.stop(true)}
        }
        else->{actions=listOf("최약 블록 재훈련" to {toast(context,V31TrainingSessionRuntime.retryWeakestBlock(),"약점 재훈련 시작","재훈련할 완료 기록이 없습니다")});dismissLabel="닫기";dismissAction=null}
    }
    context.pvDialog("훈련 제어",root,dismissLabel=dismissLabel,extraActions=actions,onDismissTap=dismissAction).show()
}

private fun toast(context:Context,ok:Boolean,yes:String,no:String){Toast.makeText(context,if(ok)yes else no,Toast.LENGTH_SHORT).show()}
