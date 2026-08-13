package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context

enum class V26ReportTile(val label:String){OVERVIEW("핵심 요약"),COACH_TREND("AI 코치 / 추세"),PUTTER_RANKING("퍼터 비교"),SHOT_DETAIL("샷 상세")}
object V26ReportPreferences{
    private const val PREF="puttvision_v26_report";@Volatile private var installed=false;@Volatile private var selected:Set<V26ReportTile> = V26ReportTile.entries.toSet()
    fun install(context:Context){if(installed)return;synchronized(this){if(installed)return;val raw=context.applicationContext.getSharedPreferences(PREF,Context.MODE_PRIVATE).getStringSet("tiles",null);selected=raw?.mapNotNull{runCatching{V26ReportTile.valueOf(it)}.getOrNull()}?.toSet()?.takeIf{it.isNotEmpty()}?:V26ReportTile.entries.toSet();installed=true}}
    fun enabled(tile:V26ReportTile)=tile in selected
    fun set(context:Context,tile:V26ReportTile,enabled:Boolean){install(context);selected=if(enabled)selected+tile else(selected-tile).ifEmpty{setOf(V26ReportTile.OVERVIEW)};context.applicationContext.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putStringSet("tiles",selected.map{it.name}.toSet()).apply()}
    fun summary()="${selected.size}/${V26ReportTile.entries.size} 섹션";fun snapshot():Set<V26ReportTile> = selected.toSet()
}
fun showV26ReportBuilderDialog(context:Context){V26ReportPreferences.install(context);val tiles=V26ReportTile.entries;val checked=tiles.map{V26ReportPreferences.enabled(it)}.toBooleanArray();AlertDialog.Builder(context).setTitle("REPORT BUILDER").setMultiChoiceItems(tiles.map{it.label}.toTypedArray(),checked){_,which,value->V26ReportPreferences.set(context,tiles[which],value)}.setPositiveButton("완료",null).show()}
