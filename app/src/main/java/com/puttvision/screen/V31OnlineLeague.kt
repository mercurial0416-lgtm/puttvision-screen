package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val V31_ONLINE_ENDPOINT = "https://razejagceyznnajioxgx.supabase.co/functions/v1/puttvision-online"
private const val V31_ONLINE_KEY = "sb_publishable_fjgUBLTNcWG5-f8EDFpLyw_P5wXmmFS"

data class V31OnlinePlayer(val id:String,val name:String,val friendCode:String,val rating:Int,val wins:Int=0,val losses:Int=0,val draws:Int=0,val matches:Int=0)
data class V31OnlineRoom(val id:String,val joinCode:String,val mode:String,val status:String,val maxPlayers:Int)

private class V31OnlineTokenStore(private val context:Context){
    private val alias="puttvision.online.player.v1";private val prefs=context.getSharedPreferences("puttvision_online_secure",Context.MODE_PRIVATE)
    fun save(token:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val out=c.doFinal(token.toByteArray());prefs.edit().putString("token",Base64.encodeToString(out,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(c.iv,Base64.NO_WRAP)).apply()}
    fun load():String?=runCatching{val a=prefs.getString("token",null)?:return null;val iv=prefs.getString("iv",null)?:return null;val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));String(c.doFinal(Base64.decode(a,Base64.NO_WRAP)),Charsets.UTF_8)}.getOrNull()
    fun clear(){prefs.edit().clear().apply()}
    private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey(alias,null)as?SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generateKey()}}
}

object V31OnlineRuntime{
    @Volatile var player:V31OnlinePlayer?=null;private set
    @Volatile var activeRoom:V31OnlineRoom?=null;private set
    private fun token(context:Context)=V31OnlineTokenStore(context.applicationContext).load()
    fun statusLabel(context:Context):String=player?.let{"${it.name} · R${it.rating}"}?:if(token(context)!=null)"온라인 연결됨" else "프로필 만들기"
    fun register(context:Context,name:String,done:(Result<V31OnlinePlayer>)->Unit){post(context,"register",JSONObject().put("displayName",name),false){r->runCatching{val t=r.getString("token");V31OnlineTokenStore(context.applicationContext).save(t);parsePlayer(r.getJSONObject("player")).also{player=it}}.let(done)}}
    fun refreshMe(context:Context,done:(Result<V31OnlinePlayer>)->Unit={}){post(context,"me",JSONObject(),true){r->runCatching{parsePlayer(r.getJSONObject("player")).also{player=it}}.let(done)}}
    fun leaderboard(context:Context,done:(Result<List<JSONObject>>)->Unit){post(context,"leaderboard",JSONObject().put("limit",50),true){r->runCatching{val a=r.getJSONArray("rows");(0 until a.length()).map{a.getJSONObject(it)}}.let(done)}}
    fun friendRequest(context:Context,code:String,done:(Result<Unit>)->Unit){post(context,"friend-request",JSONObject().put("friendCode",code.uppercase()),true){r->if(r.optBoolean("ok"))done(Result.success(Unit))else done(Result.failure(Exception(r.optString("error","failed"))))}}
    fun friendList(context:Context,done:(Result<JSONObject>)->Unit){post(context,"friend-list",JSONObject(),true){done(Result.success(it))}}
    fun createRoom(context:Context,done:(Result<V31OnlineRoom>)->Unit){post(context,"create-room",JSONObject().put("mode","stroke").put("maxPlayers",4),true){r->runCatching{parseRoom(r.getJSONObject("room")).also{activeRoom=it}}.let(done)}}
    fun joinRoom(context:Context,code:String,done:(Result<V31OnlineRoom>)->Unit){post(context,"join-room",JSONObject().put("joinCode",code.uppercase()),true){r->runCatching{parseRoom(r.getJSONObject("room")).also{activeRoom=it}}.let(done)}}
    fun room(context:Context,done:(Result<JSONObject>)->Unit){val id=activeRoom?.id?:return done(Result.failure(Exception("room 없음")));post(context,"room",JSONObject().put("roomId",id),true){done(Result.success(it))}}
    fun weekly(context:Context,done:(Result<JSONObject>)->Unit){post(context,"weekly",JSONObject(),true){done(Result.success(it))}}
    private fun post(context:Context,action:String,payload:JSONObject,auth:Boolean,done:(JSONObject)->Unit){Thread{val result=runCatching{val body=JSONObject(payload.toString()).put("action",action);val c=(URL(V31_ONLINE_ENDPOINT).openConnection()as HttpURLConnection).apply{requestMethod="POST";connectTimeout=7000;readTimeout=9000;doOutput=true;setRequestProperty("content-type","application/json");setRequestProperty("apikey",V31_ONLINE_KEY);if(auth){val t=token(context)?:error("온라인 프로필 없음");setRequestProperty("x-pv-token",t)}};c.outputStream.use{it.write(body.toString().toByteArray())};val code=c.responseCode;val text=(if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()};val j=JSONObject(text);if(code !in 200..299)error(j.optString("error","HTTP $code"));j};android.os.Handler(android.os.Looper.getMainLooper()).post{result.onSuccess(done).onFailure{Toast.makeText(context,"온라인: ${it.message}",Toast.LENGTH_LONG).show()}}}.start()}
    private fun parsePlayer(j:JSONObject)=V31OnlinePlayer(j.getString("id"),j.getString("display_name"),j.getString("friend_code"),j.optInt("rating",1000),j.optInt("wins"),j.optInt("losses"),j.optInt("draws"),j.optInt("matches"))
    private fun parseRoom(j:JSONObject)=V31OnlineRoom(j.getString("id"),j.getString("join_code"),j.optString("mode","stroke"),j.optString("status","waiting"),j.optInt("max_players",4))
}

fun showV31OnlineLeagueDialog(context:Context){
    val tokenExists=V31OnlineRuntime.statusLabel(context)!="프로필 만들기"
    if(!tokenExists){showV31Register(context);return}
    V31OnlineRuntime.refreshMe(context){showV31OnlineHome(context)}
}
private fun showV31Register(context:Context){val input=EditText(context).apply{hint="닉네임 2~20자";setSingleLine(true)};AlertDialog.Builder(context).setTitle("PUTTVISION ONLINE").setMessage("기기 전용 온라인 프로필을 만듭니다. 비밀번호 없이 이 폰의 암호화 토큰으로 로그인합니다.").setView(input).setPositiveButton("만들기"){_,_->V31OnlineRuntime.register(context,input.text.toString()){r->r.onSuccess{showV31OnlineHome(context)}.onFailure{Toast.makeText(context,it.message,Toast.LENGTH_LONG).show()}}}.setNegativeButton("취소",null).show()}
private fun showV31OnlineHome(context:Context){val p=V31OnlineRuntime.player?:return;val items=arrayOf("글로벌 랭킹","친구 코드로 추가","친구 목록","방 만들기","방 코드로 입장","현재 방","주간 챌린지");AlertDialog.Builder(context).setTitle("${p.name} · R${p.rating}").setMessage("FRIEND ${p.friendCode} · ${p.wins}승 ${p.losses}패 · ${p.matches}게임").setItems(items){_,i->when(i){0->showV31Leaderboard(context);1->showV31FriendAdd(context);2->showV31Friends(context);3->V31OnlineRuntime.createRoom(context){it.onSuccess{r->Toast.makeText(context,"방 ${r.joinCode} 생성",Toast.LENGTH_LONG).show();showV31Room(context)}};4->showV31Join(context);5->showV31Room(context);else->showV31Weekly(context)}}.setNegativeButton("닫기",null).show()}
private fun showV31Leaderboard(context:Context){V31OnlineRuntime.leaderboard(context){r->r.onSuccess{rows->val text=rows.take(30).joinToString("\n"){"#${it.optInt("rank")}  ${it.optString("display_name")}  R${it.optInt("rating")}"};AlertDialog.Builder(context).setTitle("GLOBAL RANKING").setMessage(text.ifBlank{"아직 기록 없음"}).setPositiveButton("확인",null).show()}}}
private fun showV31FriendAdd(context:Context){val e=EditText(context).apply{hint="친구코드 8자리";setSingleLine(true)};AlertDialog.Builder(context).setTitle("친구 추가").setView(e).setPositiveButton("요청"){_,_->V31OnlineRuntime.friendRequest(context,e.text.toString()){r->Toast.makeText(context,if(r.isSuccess)"친구 요청 보냄" else "실패",Toast.LENGTH_SHORT).show()}}.setNegativeButton("취소",null).show()}
private fun showV31Friends(context:Context){V31OnlineRuntime.friendList(context){r->r.onSuccess{j->val a=j.optJSONArray("players")?:JSONArray();val lines=(0 until a.length()).map{a.getJSONObject(it)}.joinToString("\n"){"${it.optString("display_name")} · ${it.optString("friend_code")} · R${it.optInt("rating")}"};AlertDialog.Builder(context).setTitle("FRIENDS").setMessage(lines.ifBlank{"친구 없음"}).setPositiveButton("확인",null).show()}}}
private fun showV31Join(context:Context){val e=EditText(context).apply{hint="방 코드 6자리";setSingleLine(true)};AlertDialog.Builder(context).setTitle("온라인 방 입장").setView(e).setPositiveButton("입장"){_,_->V31OnlineRuntime.joinRoom(context,e.text.toString()){it.onSuccess{showV31Room(context)}}}.setNegativeButton("취소",null).show()}
private fun showV31Room(context:Context){V31OnlineRuntime.room(context){r->r.onSuccess{j->val room=j.getJSONObject("room");val players=j.optJSONArray("players")?:JSONArray();val map=mutableMapOf<String,String>();for(i in 0 until players.length()){val p=players.getJSONObject(i);map[p.getString("id")]=p.getString("display_name")};val members=j.optJSONArray("members")?:JSONArray();val lines=(0 until members.length()).joinToString("\n"){val m=members.getJSONObject(it);"${m.optInt("seat")}. ${map[m.optString("player_id")]?:"PLAYER"} ${if(m.optBoolean("ready"))"READY" else "WAIT"}"};AlertDialog.Builder(context).setTitle("ROOM ${room.optString("join_code")}").setMessage(lines).setPositiveButton("확인",null).show()}}}
private fun showV31Weekly(context:Context){V31OnlineRuntime.weekly(context){r->r.onSuccess{j->val c=j.optJSONObject("challenge");val rows=j.optJSONArray("rows")?:JSONArray();val rank=(0 until minOf(20,rows.length())).joinToString("\n"){val x=rows.getJSONObject(it);"#${x.optInt("rank")} ${x.optString("display_name")} · ${x.optInt("best_score")}"};AlertDialog.Builder(context).setTitle(c?.optString("title")?:"WEEKLY").setMessage((c?.optString("mode")?:"진행중 챌린지 없음")+"\n\n"+rank).setPositiveButton("확인",null).show()}}}
