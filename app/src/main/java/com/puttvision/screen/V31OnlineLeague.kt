package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.EditText
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

data class V31OnlinePlayer(
    val id: String,
    val name: String,
    val friendCode: String,
    val rating: Int,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val matches: Int = 0
)

data class V31OnlineRoom(
    val id: String,
    val joinCode: String,
    val mode: String,
    val status: String,
    val maxPlayers: Int
)

private class V31OnlineTokenStore(private val context: Context) {
    private val alias = "puttvision.online.player.v1"
    private val prefs = context.getSharedPreferences("puttvision_online_secure", Context.MODE_PRIVATE)

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.doFinal(token.toByteArray())
        prefs.edit()
            .putString("token", Base64.encodeToString(out, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = runCatching {
        val encrypted = prefs.getString("token", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}

object V31OnlineRuntime {
    @Volatile var player: V31OnlinePlayer? = null
        private set
    @Volatile var activeRoom: V31OnlineRoom? = null
        private set
    @Volatile var activeMatchId: String? = null
        private set

    private fun token(context: Context) = V31OnlineTokenStore(context.applicationContext).load()

    fun statusLabel(context: Context): String = player?.let { "${it.name} · R${it.rating}" }
        ?: if (token(context) != null) "온라인 연결됨" else "프로필 만들기"

    fun register(context: Context, name: String, done: (Result<V31OnlinePlayer>) -> Unit) {
        post(context, "register", JSONObject().put("displayName", name), false) { result ->
            result.mapCatching { response ->
                V31OnlineTokenStore(context.applicationContext).save(response.getString("token"))
                parsePlayer(response.getJSONObject("player")).also { player = it }
            }.let(done)
        }
    }

    fun refreshMe(context: Context, done: (Result<V31OnlinePlayer>) -> Unit = {}) {
        post(context, "me", JSONObject(), true) { result ->
            result.mapCatching { response -> parsePlayer(response.getJSONObject("player")).also { player = it } }.let(done)
        }
    }

    fun resume(context: Context, done: (Result<JSONObject>) -> Unit = {}) {
        post(context, "resume", JSONObject(), true) { result ->
            result.mapCatching { response ->
                response.optJSONObject("room")?.let { activeRoom = parseRoom(it) }
                    ?: run { activeRoom = null }
                response.optJSONObject("match")?.takeIf { it.optString("status") == "active" }
                    ?.optString("id")?.takeIf { it.isNotBlank() }?.let { activeMatchId = it }
                    ?: run { activeMatchId = null }
                response
            }.let(done)
        }
    }

    fun leaderboard(context: Context, done: (Result<List<JSONObject>>) -> Unit) {
        post(context, "leaderboard", JSONObject().put("limit", 50), true) { result ->
            result.mapCatching { response ->
                val rows = response.getJSONArray("rows")
                (0 until rows.length()).map { rows.getJSONObject(it) }
            }.let(done)
        }
    }

    fun friendRequest(context: Context, code: String, done: (Result<Unit>) -> Unit) {
        post(context, "friend-request", JSONObject().put("friendCode", code.uppercase()), true) { result ->
            result.mapCatching { response -> if (!response.optBoolean("ok")) error(response.optString("error", "failed")) }.let(done)
        }
    }

    fun friendList(context: Context, done: (Result<JSONObject>) -> Unit) =
        post(context, "friend-list", JSONObject(), true, done)

    fun friendRespond(context: Context, id: String, accept: Boolean, done: (Result<Unit>) -> Unit) {
        post(context, "friend-respond", JSONObject().put("id", id).put("accept", accept), true) { result ->
            result.mapCatching { response -> if (!response.optBoolean("ok")) error(response.optString("error", "failed")) }.let(done)
        }
    }

    fun createRoom(context: Context, done: (Result<V31OnlineRoom>) -> Unit) {
        post(context, "create-room", JSONObject().put("mode", "stroke").put("maxPlayers", 4), true) { result ->
            result.mapCatching { response -> parseRoom(response.getJSONObject("room")).also { activeRoom = it; activeMatchId = null } }.let(done)
        }
    }

    fun joinRoom(context: Context, code: String, done: (Result<V31OnlineRoom>) -> Unit) {
        post(context, "join-room", JSONObject().put("joinCode", code.uppercase()), true) { result ->
            result.mapCatching { response -> parseRoom(response.getJSONObject("room")).also { activeRoom = it; activeMatchId = null } }.let(done)
        }
    }

    fun room(context: Context, done: (Result<JSONObject>) -> Unit) {
        val id = activeRoom?.id ?: return done(Result.failure(Exception("room 없음")))
        post(context, "room", JSONObject().put("roomId", id), true) { result ->
            result.mapCatching { response ->
                response.optJSONObject("room")?.let { activeRoom = parseRoom(it) }
                val match = response.optJSONObject("match")
                if (match?.optString("status") == "active") {
                    activeMatchId = match.optString("id").takeIf { it.isNotBlank() }
                } else if (match?.optString("status") == "finished") {
                    activeMatchId = null
                }
                response
            }.let(done)
        }
    }

    fun setReady(context: Context, ready: Boolean, done: (Result<Unit>) -> Unit) {
        val id = activeRoom?.id ?: return done(Result.failure(Exception("room 없음")))
        post(context, "ready", JSONObject().put("roomId", id).put("ready", ready), true) { result ->
            result.mapCatching { response -> if (!response.optBoolean("ok")) error(response.optString("error", "failed")) }.let(done)
        }
    }

    fun startMatch(context: Context, done: (Result<String>) -> Unit) {
        val id = activeRoom?.id ?: return done(Result.failure(Exception("room 없음")))
        post(context, "start-match", JSONObject().put("roomId", id), true) { result ->
            result.mapCatching { response -> response.getJSONObject("match").getString("id").also { activeMatchId = it } }.let(done)
        }
    }

    fun weekly(context: Context, done: (Result<JSONObject>) -> Unit) = post(context, "weekly", JSONObject(), true, done)

    private fun post(
        context: Context,
        action: String,
        payload: JSONObject,
        auth: Boolean,
        done: (Result<JSONObject>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val body = JSONObject(payload.toString()).put("action", action)
                val connection = (URL(V31_ONLINE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7_000
                    readTimeout = 9_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("apikey", V31_ONLINE_KEY)
                    if (auth) {
                        val onlineToken = token(context) ?: error("온라인 프로필 없음")
                        setRequestProperty("x-pv-token", onlineToken)
                    }
                }
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    .bufferedReader().use { it.readText() }
                val response = JSONObject(text)
                if (code !in 200..299) error(response.optString("error", "HTTP $code"))
                response
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.apply { name = "puttvision-online-$action"; isDaemon = true }.start()
    }

    private fun parsePlayer(j: JSONObject) = V31OnlinePlayer(
        j.getString("id"), j.getString("display_name"), j.getString("friend_code"), j.optInt("rating", 1000),
        j.optInt("wins"), j.optInt("losses"), j.optInt("draws"), j.optInt("matches")
    )

    private fun parseRoom(j: JSONObject) = V31OnlineRoom(
        j.getString("id"), j.getString("join_code"), j.optString("mode", "stroke"),
        j.optString("status", "waiting"), j.optInt("max_players", 4)
    )
}

fun showV31OnlineLeagueDialog(context: Context) {
    if (V31OnlineRuntime.statusLabel(context) == "프로필 만들기") {
        showV31Register(context)
        return
    }
    V31OnlineRuntime.refreshMe(context) { me ->
        me.onSuccess {
            V31OnlineRuntime.resume(context) { showV31OnlineHome(context) }
        }.onFailure { Toast.makeText(context, "온라인 연결 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

private fun showV31Register(context: Context) {
    val input = EditText(context).apply { hint = "닉네임 2~20자"; setSingleLine(true) }
    AlertDialog.Builder(context)
        .setTitle("PUTTVISION ONLINE")
        .setMessage("온라인 프로필을 만듭니다. 생성 후 ONLINE 메뉴에서 계정 이전 코드를 발급해 두세요.")
        .setView(input)
        .setPositiveButton("만들기") { _, _ ->
            V31OnlineRuntime.register(context, input.text.toString()) { it.onSuccess { showV34OnlineHub(context) } }
        }
        .setNegativeButton("취소", null)
        .show()
}

private fun showV31OnlineHome(context: Context) {
    val p = V31OnlineRuntime.player ?: return
    val roomLabel = V31OnlineRuntime.activeRoom?.let { "현재 방 ${it.joinCode}" } ?: "현재 방"
    val items = arrayOf("글로벌 랭킹", "친구 코드로 추가", "친구/요청 관리", "방 만들기", "방 코드로 입장", roomLabel, "주간 챌린지")
    AlertDialog.Builder(context)
        .setTitle("${p.name} · R${p.rating}")
        .setMessage("FRIEND ${p.friendCode} · ${p.wins}승 ${p.losses}패 ${p.draws}무 · ${p.matches}게임")
        .setItems(items) { _, i ->
            when (i) {
                0 -> showV31Leaderboard(context)
                1 -> showV31FriendAdd(context)
                2 -> showV31Friends(context)
                3 -> V31OnlineRuntime.createRoom(context) { it.onSuccess { showV31Room(context) }.onFailure { e -> toastOnlineError(context, e) } }
                4 -> showV31Join(context)
                5 -> if (V31OnlineRuntime.activeRoom != null) showV31Room(context) else V31OnlineRuntime.resume(context) { r -> if (r.isSuccess && V31OnlineRuntime.activeRoom != null) showV31Room(context) else Toast.makeText(context, "참가 중인 방 없음", Toast.LENGTH_SHORT).show() }
                else -> showV34OnlineHub(context)
            }
        }
        .setNegativeButton("닫기", null)
        .show()
}

private fun showV31Leaderboard(context: Context) {
    V31OnlineRuntime.leaderboard(context) { result ->
        result.onSuccess { rows ->
            AlertDialog.Builder(context).setTitle("GLOBAL RANKING")
                .setMessage(rows.take(30).joinToString("\n") { r -> "#${r.optInt("rank")}  ${r.optString("display_name")}  R${r.optInt("rating")}" }.ifBlank { "아직 기록 없음" })
                .setPositiveButton("확인", null).show()
        }.onFailure { toastOnlineError(context, it) }
    }
}

private fun showV31FriendAdd(context: Context) {
    val input = EditText(context).apply { hint = "친구코드 8자리"; setSingleLine(true) }
    AlertDialog.Builder(context).setTitle("친구 추가").setView(input)
        .setPositiveButton("요청") { _, _ ->
            V31OnlineRuntime.friendRequest(context, input.text.toString()) {
                Toast.makeText(context, if (it.isSuccess) "친구 요청 보냄" else "실패 · ${it.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("취소", null).show()
}

private fun showV31Friends(context: Context) {
    V31OnlineRuntime.friendList(context) { result ->
        result.onSuccess { j ->
            val friendships = j.optJSONArray("friendships") ?: JSONArray()
            val players = j.optJSONArray("players") ?: JSONArray()
            val names = mutableMapOf<String, JSONObject>()
            for (i in 0 until players.length()) {
                val p = players.getJSONObject(i)
                names[p.optString("id")] = p
            }
            val me = V31OnlineRuntime.player?.id.orEmpty()
            if (friendships.length() == 0) {
                AlertDialog.Builder(context).setTitle("FRIENDS").setMessage("친구/요청 없음").setPositiveButton("확인", null).show()
                return@onSuccess
            }
            val rows = (0 until friendships.length()).map { i ->
                val f = friendships.getJSONObject(i)
                val otherId = if (f.optString("requester_id") == me) f.optString("addressee_id") else f.optString("requester_id")
                val p = names[otherId]
                val incoming = f.optString("addressee_id") == me && f.optString("status") == "pending"
                Triple(f, p, when {
                    incoming -> "받은 요청"
                    f.optString("status") == "pending" -> "보낸 요청"
                    f.optString("status") == "accepted" -> "친구"
                    else -> f.optString("status")
                })
            }
            AlertDialog.Builder(context).setTitle("FRIENDS")
                .setItems(rows.map { (_, p, label) -> "$label · ${p?.optString("display_name") ?: "PLAYER"} · R${p?.optInt("rating") ?: 0}" }.toTypedArray()) { _, idx ->
                    val (f, p, _) = rows[idx]
                    if (f.optString("addressee_id") == me && f.optString("status") == "pending") {
                        AlertDialog.Builder(context).setTitle(p?.optString("display_name") ?: "친구 요청")
                            .setMessage("친구 요청을 수락할까요?")
                            .setPositiveButton("수락") { _, _ -> V31OnlineRuntime.friendRespond(context, f.getString("id"), true) { showV31Friends(context) } }
                            .setNegativeButton("거절") { _, _ -> V31OnlineRuntime.friendRespond(context, f.getString("id"), false) { showV31Friends(context) } }
                            .show()
                    }
                }.setNegativeButton("닫기", null).show()
        }.onFailure { toastOnlineError(context, it) }
    }
}

private fun showV31Join(context: Context) {
    val input = EditText(context).apply { hint = "방 코드 6자리"; setSingleLine(true) }
    AlertDialog.Builder(context).setTitle("온라인 방 입장").setView(input)
        .setPositiveButton("입장") { _, _ ->
            V31OnlineRuntime.joinRoom(context, input.text.toString()) {
                it.onSuccess { showV31Room(context) }.onFailure { e -> toastOnlineError(context, e) }
            }
        }.setNegativeButton("취소", null).show()
}

private fun showV31Room(context: Context) {
    val handler = Handler(Looper.getMainLooper())
    val dialog = AlertDialog.Builder(context)
        .setTitle("ONLINE ROOM")
        .setMessage("방 상태 동기화 중…")
        .setPositiveButton("READY", null)
        .setNegativeButton("닫기", null)
        .create()

    var myReady = false
    var waiting = true
    var requestRunning = false

    fun refresh() {
        if (!dialog.isShowing || requestRunning) return
        requestRunning = true
        V31OnlineRuntime.room(context) { result ->
            requestRunning = false
            result.onSuccess { j ->
                if (!dialog.isShowing) return@onSuccess
                val room = j.getJSONObject("room")
                val players = j.optJSONArray("players") ?: JSONArray()
                val names = mutableMapOf<String, String>()
                for (i in 0 until players.length()) {
                    val p = players.getJSONObject(i)
                    names[p.getString("id")] = p.getString("display_name")
                }
                val members = j.optJSONArray("members") ?: JSONArray()
                val me = V31OnlineRuntime.player?.id.orEmpty()
                myReady = false
                val lines = (0 until members.length()).joinToString("\n") { i ->
                    val member = members.getJSONObject(i)
                    if (member.optString("player_id") == me) myReady = member.optBoolean("ready")
                    "${member.optInt("seat")}. ${names[member.optString("player_id")] ?: "PLAYER"} ${if (member.optBoolean("ready")) "READY" else "WAIT"}"
                }
                waiting = room.optString("status") == "waiting"
                val match = j.optJSONObject("match")
                val matchLine = match?.let { "\n\nMATCH ${it.optString("id").take(8)} · ${it.optString("status").uppercase()}" }.orEmpty()
                dialog.setTitle("ROOM ${room.optString("join_code")} · ${room.optString("status").uppercase()}")
                dialog.setMessage(lines + matchLine + if (waiting) "\n\n전원 READY 시 서버가 자동 시작" else "")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    text = when {
                        waiting && myReady -> "READY 해제"
                        waiting -> "READY"
                        match?.optString("status") == "active" -> "경기 진행중"
                        else -> "완료"
                    }
                    isEnabled = waiting
                }
            }.onFailure {
                if (dialog.isShowing) dialog.setMessage("동기화 실패 · ${it.message}\n자동 재시도 중…")
            }
            if (dialog.isShowing) handler.postDelayed({ refresh() }, 1_500L)
        }
    }

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!waiting) return@setOnClickListener
            V31OnlineRuntime.setReady(context, !myReady) { result ->
                result.onFailure { toastOnlineError(context, it) }
                handler.postDelayed({ refresh() }, 150L)
            }
        }
        refresh()
    }
    dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
    dialog.show()
}

private fun toastOnlineError(context: Context, error: Throwable) {
    Toast.makeText(context, "온라인: ${error.message ?: "오류"}", Toast.LENGTH_LONG).show()
}
