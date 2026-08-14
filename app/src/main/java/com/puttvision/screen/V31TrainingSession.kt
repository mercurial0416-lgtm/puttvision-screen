package com.puttvision.screen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

object V31TrainingRules {
    fun launchOk(angleDeg:Double)=kotlin.math.abs(angleDeg)<=.70
    fun weaknessOk(score:Int,holed:Boolean,remainingM:Double)=score>=80&&(holed||remainingM<=.50)
    fun distanceOk(remainingM:Double)=remainingM<=.35
    fun pressureOk(holed:Boolean)=holed
}

data class V44TrainingResumeCounters(
    val blockIndex:Int,
    val shotInBlock:Int,
    val successesInBlock:Int,
    val totalShots:Int,
    val totalSuccesses:Int,
    val streak:Int
)

object V44TrainingResumePolicy {
    const val MAX_RESUME_AGE_MS = 21_600_000L
    fun sanitize(
        blocks:List<V16TrainingBlock>, blockIndex:Int, shotInBlock:Int, successesInBlock:Int,
        totalShots:Int, totalSuccesses:Int, streak:Int
    ):V44TrainingResumeCounters? {
        if(blocks.isEmpty()) return null
        val bi=blockIndex.coerceIn(0,blocks.lastIndex)
        val maxShots=blocks[bi].shots.coerceAtLeast(1)
        val si=shotInBlock.coerceIn(0,maxShots-1)
        val sb=successesInBlock.coerceIn(0,si)
        val ts=totalShots.coerceAtLeast(si)
        val tss=totalSuccesses.coerceIn(0,ts)
        val st=streak.coerceIn(0,si)
        return V44TrainingResumeCounters(bi,si,sb,ts,tss,st)
    }
    fun fresh(savedAtMs:Long,nowMs:Long):Boolean=(nowMs-savedAtMs) in 0..MAX_RESUME_AGE_MS
}

data class V31TrainingProgress(val running:Boolean,val finished:Boolean,val blockIndex:Int,val blockCount:Int,val shotInBlock:Int,val shotsInBlock:Int,val successesInBlock:Int,val totalShots:Int,val totalSuccesses:Int,val streak:Int,val blockTitle:String,val targetDistanceM:Double,val summary:String)

object V31TrainingSessionRuntime {
    private data class OriginalSettings(val distance:Double,val side:Double,val long:Double,val terrain:Int)
    @Volatile private var engine:GameEngine?=null
    @Volatile private var context:Context?=null
    @Volatile private var plan:V16DailyTrainingPlan?=null
    @Volatile private var blockIndex=0
    @Volatile private var shotInBlock=0
    @Volatile private var successesInBlock=0
    @Volatile private var totalShots=0
    @Volatile private var totalSuccesses=0
    @Volatile private var streak=0
    @Volatile private var running=false
    @Volatile private var finished=false
    private var original:OriginalSettings?=null
    private var restored=false

    @Synchronized fun bind(value:GameEngine){engine=value;restore()}
    @Synchronized fun install(value:Context){context=value.applicationContext;restore()}
    @Synchronized fun start(value:V16DailyTrainingPlan):Boolean{val e=engine?:return false;if(value.blocks.isEmpty())return false;if(running)stop(true);plan=value;original=OriginalSettings(e.settings.holeDistanceM,e.settings.sideSlopePct,e.settings.longSlopePct,e.settings.terrainProfileId);blockIndex=0;shotInBlock=0;successesInBlock=0;totalShots=0;totalSuccesses=0;streak=0;running=true;finished=false;applyCurrentTarget();save();return true}
    @Synchronized fun stop(restore:Boolean=true){running=false;finished=false;if(restore)restoreSettings();clear()}
    @Synchronized fun onRecord(record:ShotRecord){if(!running)return;val p=plan?:return;val block=p.blocks.getOrNull(blockIndex)?:return;val success=evaluate(blockIndex,record);shotInBlock++;totalShots++;if(success){successesInBlock++;totalSuccesses++;streak++}else streak=0;val earlyPressurePass=blockIndex==p.blocks.lastIndex&&streak>=3;if(shotInBlock>=block.shots||earlyPressurePass){blockIndex++;shotInBlock=0;successesInBlock=0;streak=0;if(blockIndex>=p.blocks.size){running=false;finished=true;restoreSettings();clear();return}};applyCurrentTarget();save()}
    fun progress():V31TrainingProgress{val p=plan;val maxIndex=max(0,(p?.blocks?.size?:1)-1);val block=p?.blocks?.getOrNull(blockIndex.coerceAtMost(maxIndex));val target=engine?.settings?.holeDistanceM?:block?.distanceM?:0.0;val summary=when{finished->"완료 · 성공 $totalSuccesses/$totalShots";running&&block!=null->"${blockIndex+1}/${p!!.blocks.size} · ${shotInBlock}/${block.shots}구";else->"대기"};return V31TrainingProgress(running,finished,blockIndex,p?.blocks?.size?:0,shotInBlock,block?.shots?:0,successesInBlock,totalShots,totalSuccesses,streak,block?.title?:"--",target,summary)}
    private fun evaluate(index:Int,record:ShotRecord):Boolean=when(index){0->V31TrainingRules.launchOk(record.metrics.launchAngleDeg);1->V31TrainingRules.weaknessOk(record.strokeScore.total,record.result?.holed==true,record.result?.distanceToCupM?:9.0);2->V31TrainingRules.distanceOk(record.result?.distanceToCupM?:9.0);else->V31TrainingRules.pressureOk(record.result?.holed==true)}
    private fun applyCurrentTarget(){val e=engine?:return;val p=plan?:return;val b=p.blocks.getOrNull(blockIndex)?:return;e.settings.holeDistanceM=if(b.title.contains("랜덤"))randomDistance(b.distanceM,shotInBlock)else b.distanceM;e.settings.sideSlopePct=b.sideSlopePct;e.settings.longSlopePct=b.longSlopePct;e.settings.terrainProfileId=-1;GreenReadRuntime.clearRuntimeCache()}
    private fun randomDistance(base:Double,shot:Int):Double{val f=doubleArrayOf(.70,.90,1.10,.80,1.00,1.20);return(base*f[shot%f.size]).coerceIn(1.5,8.0)}
    private fun restoreSettings(){val e=engine?:return;val s=original?:return;e.settings.holeDistanceM=s.distance;e.settings.sideSlopePct=s.side;e.settings.longSlopePct=s.long;e.settings.terrainProfileId=s.terrain;GreenReadRuntime.clearRuntimeCache();original=null}
    private fun prefs()=context?.getSharedPreferences("v31_training_session",Context.MODE_PRIVATE)
    private fun save(){val p=plan?:return;val s=original?:return;val blocks=JSONArray();p.blocks.forEach{b->blocks.put(JSONObject().put("t",b.title).put("n",b.shots).put("d",b.distanceM).put("s",b.sideSlopePct).put("l",b.longSlopePct).put("r",b.successRule))};val j=JSONObject().put("ts",System.currentTimeMillis()).put("bi",blockIndex).put("si",shotInBlock).put("sb",successesInBlock).put("tsn",totalShots).put("tss",totalSuccesses).put("st",streak).put("p",JSONObject().put("t",p.title).put("m",p.estimatedMinutes).put("r",p.reason).put("b",blocks)).put("o",JSONObject().put("d",s.distance).put("s",s.side).put("l",s.long).put("t",s.terrain));prefs()?.edit()?.putString("state",j.toString())?.apply()}
    private fun clear(){prefs()?.edit()?.remove("state")?.apply()}
    private fun restore(){
        if(restored||engine==null||context==null)return
        restored=true
        val raw=prefs()?.getString("state",null)?:return
        val parsed=runCatching{
            val j=JSONObject(raw)
            require(V44TrainingResumePolicy.fresh(j.getLong("ts"),System.currentTimeMillis()))
            val pj=j.getJSONObject("p")
            val a=pj.getJSONArray("b")
            val blocks=(0 until a.length()).map{i->a.getJSONObject(i).let{b->V16TrainingBlock(b.getString("t"),b.getInt("n"),b.getDouble("d"),b.getDouble("s"),b.getDouble("l"),b.getString("r"))}}
            require(blocks.isNotEmpty()&&blocks.all{it.shots>0&&it.distanceM.isFinite()&&it.sideSlopePct.isFinite()&&it.longSlopePct.isFinite()})
            val counters=requireNotNull(V44TrainingResumePolicy.sanitize(blocks,j.getInt("bi"),j.getInt("si"),j.getInt("sb"),j.getInt("tsn"),j.getInt("tss"),j.getInt("st")))
            val restoredPlan=V16DailyTrainingPlan(pj.getString("t"),pj.getInt("m").coerceAtLeast(1),blocks,pj.getString("r"))
            val o=j.getJSONObject("o")
            val restoredOriginal=OriginalSettings(o.getDouble("d"),o.getDouble("s"),o.getDouble("l"),o.getInt("t"))
            require(restoredOriginal.distance.isFinite()&&restoredOriginal.side.isFinite()&&restoredOriginal.long.isFinite())
            Triple(restoredPlan,restoredOriginal,counters)
        }.getOrNull()
        if(parsed==null){clear();return}
        val (restoredPlan,restoredOriginal,counters)=parsed
        plan=restoredPlan
        original=restoredOriginal
        blockIndex=counters.blockIndex
        shotInBlock=counters.shotInBlock
        successesInBlock=counters.successesInBlock
        totalShots=counters.totalShots
        totalSuccesses=counters.totalSuccesses
        streak=counters.streak
        running=true
        finished=false
        applyCurrentTarget()
        save()
    }
}
