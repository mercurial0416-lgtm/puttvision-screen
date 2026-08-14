package com.puttvision.screen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Durable online delivery. A measured shot is committed locally before any network call.
 * clientShotId is stable across retries and the server owns the duplicate guard.
 */
object V33OnlineOutbox {
    private const val endpoint = "https://razejagceyznnajioxgx.supabase.co/functions/v1/puttvision-online"
    private const val apiKey = "sb_publishable_fjgUBLTNcWG5-f8EDFpLyw_P5wXmmFS"
    private const val prefsName = "puttvision_online_outbox_v33"
    private const val securePrefs = "puttvision_online_secure"
    private const val keyAlias = "puttvision.online.player.v1"
    private const val maxQueue = 96

    @Volatile private var appContext: Context? = null
    private val flushing = AtomicBoolean(false)
    private val retryHandler = Handler(Looper.getMainLooper())

    fun install(context: Context) {
        appContext = context.applicationContext
        flush()
    }

    fun pendingCount(): Int = load().length()

    fun onRecord(record: ShotRecord) {
        val context = appContext ?: return
        val matchId = V31OnlineRuntime.activeMatchId ?: return
        val shotNo = nextShotNo(context, matchId)
        val clientShotId = UUID.randomUUID().toString()
        val metrics = JSONObject()
            .put("ballSpeedMps", record.metrics.ballSpeedMps)
            .put("launchAngleDeg", record.metrics.launchAngleDeg)
            .put("faceAngleDeg", record.metrics.faceAngleDeg ?: JSONObject.NULL)
            .put("pathAngleDeg", record.metrics.pathAngleDeg ?: JSONObject.NULL)
            .put("impactOffsetMm", record.metrics.impactOffsetMm ?: JSONObject.NULL)
            .put("remainingM", record.result?.distanceToCupM ?: 30.0)
            .put("holed", record.result?.holed == true)
            .put("strokeScore", record.strokeScore.total)
            .put("confidence", record.metrics.confidence ?: .5)
            .put("evidenceHash", evidence(record, matchId, shotNo))
        val item = JSONObject()
            .put("matchId", matchId)
            .put("clientShotId", clientShotId)
            .put("shotNo", shotNo)
            .put("metrics", metrics)
            .put("createdAtMs", System.currentTimeMillis())
            .put("attempts", 0)
        synchronized(this) {
            val q = load()
            q.put(item)
            while (q.length() > maxQueue) removeAt(q, 0)
            save(q)
        }
        flush()
    }

    fun flush() {
        val context = appContext ?: return
        if (!flushing.compareAndSet(false, true)) return
        Thread {
            try {
                val token = readOnlineToken(context) ?: return@Thread
                while (true) {
                    val item = synchronized(this) { load().optJSONObject(0) } ?: break
                    val ok = send(token, item)
                    if (!ok) {
                        synchronized(this) {
                            val q = load()
                            q.optJSONObject(0)?.let { head ->
                                if (head.optString("clientShotId") == item.optString("clientShotId")) {
                                    head.put("attempts", head.optInt("attempts") + 1)
                                    save(q)
                                }
                            }
                        }
                        scheduleRetry()
                        break
                    }
                    synchronized(this) {
                        val q = load()
                        if (q.optJSONObject(0)?.optString("clientShotId") == item.optString("clientShotId")) {
                            removeAt(q, 0)
                            save(q)
                        }
                    }
                }
            } finally {
                flushing.set(false)
                if (pendingCount() > 0) scheduleRetry()
            }
        }.apply { name = "puttvision-online-outbox"; isDaemon = true }.start()
    }

    private fun scheduleRetry() {
        retryHandler.removeCallbacksAndMessages(null)
        retryHandler.postDelayed({ flush() }, 15_000L)
    }

    private fun send(token: String, item: JSONObject): Boolean = runCatching {
        val body = JSONObject()
            .put("action", "submit-shot")
            .put("matchId", item.getString("matchId"))
            .put("clientShotId", item.getString("clientShotId"))
            .put("shotNo", item.getInt("shotNo"))
            .put("metrics", item.getJSONObject("metrics"))
        val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7000
            readTimeout = 9000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("apikey", apiKey)
            setRequestProperty("x-pv-token", token)
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use { it.readText() }
        if (code !in 200..299) return@runCatching false
        JSONObject(text).optBoolean("ok", false)
    }.getOrDefault(false)

    private fun nextShotNo(context: Context, matchId: String): Int {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val key = "shot_no_$matchId"
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return next
    }

    private fun evidence(record: ShotRecord, matchId: String, shotNo: Int): String {
        val raw = listOf(
            matchId,
            shotNo.toString(),
            record.timestampMs.toString(),
            record.metrics.ballSpeedMps.toString(),
            record.metrics.launchAngleDeg.toString(),
            record.metrics.faceAngleDeg.toString(),
            record.metrics.pathAngleDeg.toString(),
            record.result?.distanceToCupM.toString(),
            record.result?.holed.toString(),
            record.strokeScore.total.toString()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun readOnlineToken(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(securePrefs, Context.MODE_PRIVATE)
        val encrypted = prefs.getString("token", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(keyAlias, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized private fun load(): JSONArray {
        val context = appContext ?: return JSONArray()
        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("queue", "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    @Synchronized private fun save(q: JSONArray) {
        val context = appContext ?: return
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString("queue", q.toString()).apply()
    }

    private fun removeAt(array: JSONArray, index: Int) {
        if (index in 0 until array.length()) array.remove(index)
    }
}
