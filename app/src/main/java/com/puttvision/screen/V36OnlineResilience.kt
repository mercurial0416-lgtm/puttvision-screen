package com.puttvision.screen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val V36_ENDPOINT = "https://razejagceyznnajioxgx.supabase.co/functions/v1/puttvision-online"
private const val V39_STATE_ENDPOINT = "https://razejagceyznnajioxgx.supabase.co/functions/v1/puttvision-match-state"
private const val V36_API_KEY = "sb_publishable_fjgUBLTNcWG5-f8EDFpLyw_P5wXmmFS"

data class V36OnlinePlayerState(
    val id: String,
    val name: String,
    val shotNo: Int,
    val remainingM: Double?,
    val online: Boolean,
    val ratingBefore: Int?,
    val ratingAfter: Int?,
    val forfeited: Boolean,
    val forfeitReason: String?
) {
    val ratingDelta: Int? get() = if (ratingBefore != null && ratingAfter != null) ratingAfter - ratingBefore else null
}

data class V36OnlineSnapshot(
    val matchId: String,
    val status: String,
    val players: List<V36OnlinePlayerState>,
    val updatedAtMs: Long,
    val settlementReason: String? = null
) {
    fun me(): V36OnlinePlayerState? = V31OnlineRuntime.player?.id?.let { id -> players.firstOrNull { it.id == id } }
    fun opponent(): V36OnlinePlayerState? = V31OnlineRuntime.player?.id?.let { id -> players.firstOrNull { it.id != id } }
    fun finished(): Boolean = status == "finished"
}

object V36OnlinePresenceRuntime {
    private const val securePrefs = "puttvision_online_secure"
    private const val keyAlias = "puttvision.online.player.v1"
    private const val heartbeatMs = 12_000L
    private const val stateSyncMs = 2_000L
    private const val finishedHoldMs = 45_000L

    @Volatile private var appContext: Context? = null
    @Volatile private var latest: V36OnlineSnapshot? = null
    private val installed = AtomicBoolean(false)
    private val heartbeatRunning = AtomicBoolean(false)
    private val syncRunning = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    private val heartbeatTick = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            val matchId = V31OnlineRuntime.activeMatchId
            if (matchId != null) heartbeat(context, matchId)
            else if (V31OnlineRuntime.player != null) {
                V31OnlineRuntime.resume(context) { result ->
                    if (result.isSuccess) V31OnlineRuntime.activeMatchId?.let { heartbeat(context, it) }
                }
            }
            main.postDelayed(this, heartbeatMs)
        }
    }

    private val syncTick = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            val matchId = V31OnlineRuntime.activeMatchId ?: latest?.takeIf { !it.finished() }?.matchId
            if (matchId != null) syncState(context, matchId)
            main.postDelayed(this, stateSyncMs)
        }
    }

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        if (hasToken(context)) {
            V31OnlineRuntime.refreshMe(context) { me ->
                if (me.isSuccess) {
                    V31OnlineRuntime.resume(context) { resume ->
                        if (resume.isSuccess) V31OnlineRuntime.activeMatchId?.let {
                            heartbeat(context, it)
                            syncState(context, it)
                        }
                    }
                }
            }
        }
        main.post(heartbeatTick)
        main.post(syncTick)
    }

    fun snapshot(): V36OnlineSnapshot? {
        val value = latest ?: return null
        if (value.finished() && System.currentTimeMillis() - value.updatedAtMs > finishedHoldMs) {
            latest = null
            return null
        }
        return value
    }

    fun forceRefresh(context: Context, done: (Result<V36OnlineSnapshot>) -> Unit = {}) {
        val id = V31OnlineRuntime.activeMatchId
            ?: latest?.matchId
            ?: return done(Result.failure(IllegalStateException("진행 중인 온라인 경기 없음")))
        request(context, "match-state", JSONObject().put("matchId", id)) { result ->
            val mapped = result.mapCatching(::parse)
            mapped.onSuccess(::accept)
            done(mapped)
        }
    }

    fun forfeit(context: Context, done: (Result<V36OnlineSnapshot>) -> Unit) {
        val id = V31OnlineRuntime.activeMatchId
            ?: return done(Result.failure(IllegalStateException("진행 중인 온라인 경기 없음")))
        request(context, "forfeit", JSONObject().put("matchId", id)) { result ->
            result.onFailure { done(Result.failure(it)); return@request }
            request(context, "match-state", JSONObject().put("matchId", id)) { state ->
                val mapped = state.mapCatching(::parse)
                mapped.onSuccess(::accept)
                done(mapped)
            }
        }
    }

    private fun heartbeat(context: Context, matchId: String) {
        if (!heartbeatRunning.compareAndSet(false, true)) return
        request(context, "heartbeat", JSONObject().put("matchId", matchId)) { result ->
            heartbeatRunning.set(false)
            result.mapCatching(::parse).onSuccess(::accept)
        }
    }

    private fun syncState(context: Context, matchId: String) {
        if (!syncRunning.compareAndSet(false, true)) return
        request(context, "match-state", JSONObject().put("matchId", matchId)) { result ->
            syncRunning.set(false)
            result.mapCatching(::parse).onSuccess(::accept)
        }
    }

    private fun accept(snapshot: V36OnlineSnapshot) {
        latest = snapshot
        if (snapshot.finished()) {
            val context = appContext ?: return
            V31OnlineRuntime.refreshMe(context) {
                V31OnlineRuntime.resume(context)
            }
        }
    }

    private fun parse(response: JSONObject): V36OnlineSnapshot {
        val match = response.optJSONObject("match") ?: error("match_missing")
        val rawPlayers = response.optJSONArray("players")
        val players = buildList {
            if (rawPlayers != null) for (i in 0 until rawPlayers.length()) {
                val p = rawPlayers.getJSONObject(i)
                add(
                    V36OnlinePlayerState(
                        id = p.optString("player_id"),
                        name = p.optString("display_name", "PLAYER"),
                        shotNo = p.optInt("shot_no", 0),
                        remainingM = if (p.isNull("remaining_m") || !p.has("remaining_m")) null else p.optDouble("remaining_m"),
                        online = p.optBoolean("online", false),
                        ratingBefore = if (p.isNull("rating_before") || !p.has("rating_before")) null else p.optInt("rating_before"),
                        ratingAfter = if (p.isNull("rating_after") || !p.has("rating_after")) null else p.optInt("rating_after"),
                        forfeited = !p.isNull("forfeited_at") && p.optString("forfeited_at").isNotBlank(),
                        forfeitReason = p.optString("forfeit_reason").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        val settlement = response.optJSONObject("settlement")
        return V36OnlineSnapshot(
            matchId = match.getString("id"),
            status = match.optString("status", "active"),
            players = players,
            updatedAtMs = System.currentTimeMillis(),
            settlementReason = settlement?.optString("reason")?.takeIf { it.isNotBlank() }
                ?: match.optJSONObject("settings")?.optString("finish_reason")?.takeIf { it.isNotBlank() }
        )
    }

    private fun request(
        context: Context,
        action: String,
        body: JSONObject,
        done: (Result<JSONObject>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val token = readToken(context) ?: error("온라인 프로필 없음")
                val payload = JSONObject(body.toString()).put("action", action)
                val endpoint = if (action == "match-state") V39_STATE_ENDPOINT else V36_ENDPOINT
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = if (action == "match-state") 3_500 else 7_000
                    readTimeout = if (action == "match-state") 5_000 else 10_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("apikey", V36_API_KEY)
                    setRequestProperty("x-pv-token", token)
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (code !in 200..299) error(json.optString("error", "HTTP $code"))
                json
            }
            main.post { done(result) }
        }.apply { name = "puttvision-online-presence-$action"; isDaemon = true }.start()
    }

    private fun hasToken(context: Context): Boolean = readToken(context) != null

    private fun readToken(context: Context): String? = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(securePrefs, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("token", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(keyAlias, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}
