package com.puttvision.screen

import kotlin.math.max

object V31TrainingRules {
    fun launchOk(angleDeg:Double)=kotlin.math.abs(angleDeg)<=.70
    fun weaknessOk(score:Int,holed:Boolean,remainingM:Double)=score>=80&&(holed||remainingM<=.50)
    fun distanceOk(remainingM:Double)=remainingM<=.35
    fun pressureOk(holed:Boolean)=holed
}

data class V31TrainingProgress(val running:Boolean,val finished:Boolean,val blockIndex:Int,val blockCount:Int,val shotInBlock:Int,val shotsInBlock:Int,val successesInBlock:Int,val totalShots:Int,val totalSuccesses:Int,val streak:Int,val blockTitle:String,val targetDistanceM:Double,val summary:String)

object V31TrainingSessionRuntime {
    private data class OriginalSettings(val distance:Double,val side:Double,val long:Double,val terrain:Int)
    @Volatile private var engine:GameEngine?=null
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

    fun bind(value:GameEngine){engine=value}
    @Synchronized fun start(value:V16DailyTrainingPlan):Boolean{
        val e=engine?:return false;if(value.blocks.isEmpty())return false;if(running)stop(true)
        plan=value;original=OriginalSettings(e.settings.holeDistanceM,e.settings.sideSlopePct,e.settings.longSlopePct,e.settings.terrainProfileId)
        blockIndex=0;shotInBlock=0;successesInBlock=0;totalShots=0;totalSuccesses=0;streak=0;running=true;finished=false;applyCurrentTarget();return true
    }
    @Synchronized fun stop(restore:Boolean=true){running=false;if(restore)restoreSettings()}
    @Synchronized fun onRecord(record:ShotRecord){
        if(!running)return;val p=plan?:return;val block=p.blocks.getOrNull(blockIndex)?:return;val success=evaluate(blockIndex,record)
        shotInBlock++;totalShots++;if(success){successesInBlock++;totalSuccesses++;streak++}else streak=0
        val earlyPressurePass=blockIndex==p.blocks.lastIndex&&streak>=3
        if(shotInBlock>=block.shots||earlyPressurePass){blockIndex++;shotInBlock=0;successesInBlock=0;streak=0;if(blockIndex>=p.blocks.size){running=false;finished=true;restoreSettings();return}}
        applyCurrentTarget()
    }
    fun progress():V31TrainingProgress{
        val p=plan;val maxIndex=max(0,(p?.blocks?.size?:1)-1);val block=p?.blocks?.getOrNull(blockIndex.coerceAtMost(maxIndex));val target=engine?.settings?.holeDistanceM?:block?.distanceM?:0.0
        val summary=when{finished->"완료 · 성공 $totalSuccesses/$totalShots";running&&block!=null->"${blockIndex+1}/${p!!.blocks.size} · ${shotInBlock}/${block.shots}구";else->"대기"}
        return V31TrainingProgress(running,finished,blockIndex,p?.blocks?.size?:0,shotInBlock,block?.shots?:0,successesInBlock,totalShots,totalSuccesses,streak,block?.title?:"--",target,summary)
    }
    private fun evaluate(index:Int,record:ShotRecord):Boolean=when(index){
        0->V31TrainingRules.launchOk(record.metrics.launchAngleDeg)
        1->V31TrainingRules.weaknessOk(record.strokeScore.total,record.result?.holed==true,record.result?.distanceToCupM?:9.0)
        2->V31TrainingRules.distanceOk(record.result?.distanceToCupM?:9.0)
        else->V31TrainingRules.pressureOk(record.result?.holed==true)
    }
    private fun applyCurrentTarget(){val e=engine?:return;val p=plan?:return;val b=p.blocks.getOrNull(blockIndex)?:return;e.settings.holeDistanceM=if(b.title.contains("랜덤"))randomDistance(b.distanceM,shotInBlock)else b.distanceM;e.settings.sideSlopePct=b.sideSlopePct;e.settings.longSlopePct=b.longSlopePct;e.settings.terrainProfileId=-1;GreenReadRuntime.clearRuntimeCache()}
    private fun randomDistance(base:Double,shot:Int):Double{val f=doubleArrayOf(.70,.90,1.10,.80,1.00,1.20);return(base*f[shot%f.size]).coerceIn(1.5,8.0)}
    private fun restoreSettings(){val e=engine?:return;val s=original?:return;e.settings.holeDistanceM=s.distance;e.settings.sideSlopePct=s.side;e.settings.longSlopePct=s.long;e.settings.terrainProfileId=s.terrain;GreenReadRuntime.clearRuntimeCache();original=null}
}
