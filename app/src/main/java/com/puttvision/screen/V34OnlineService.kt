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
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val V34_ENDPOINT = "https://razejagceyznnajioxgx.supabase.co/functions/v1/puttvision-online"
private const val V34_API_KEY = "sb_publishable_fjgUBLTNcWG5-f8EDFpLyw_P5wXmmFS"

private object V34SecureOnlineToken {
    private const val alias = "puttvision.online.player.v1"
    private const val prefsName = "puttvision_online_secure"

    fun exists(context: Context): Boolean = load(context) != null

    fun save(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray())
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
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

private object V34OnlineApi {
    fun post(context: Context, action: String, body: JSONObject = JSONObject(), auth: Boolean = true, done: (Result<JSONObject>) -> Unit) {
        Thread {
            val result = runCatching {
                val payload = JSONObject(body.toString()).put("action", action)
                val connection = (URL(V34_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("apikey", V34_API_KEY)
                    if (auth) {
                        val token = V34SecureOnlineToken.load(context) ?: error("온라인 프로필 없음")
                        setRequestProperty("x-pv-token", token)
                    }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                if (code !in 200..299) error(json.optString("error", "HTTP $code"))
                json
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.apply { name = "puttvision-v34-online"; isDaemon = true }.start()
    }
}

object V34WeeklyRuntime {
    private const val prefsName = "puttvision_weekly_v34"
    private const val maxQueue = 120
    @Volatile private var appContext: Context? = null
    private val flushing = AtomicBoolean(false)
    private val retryHandler = Handler(Looper.getMainLooper())

    fun install(context: Context) {
        appContext = context.applicationContext
        flush()
    }

    fun active(): Boolean {
        val context = appContext ?: return false
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("attempt_id", null) != null
    }

    fun start(context: Context, done: (Result<JSONObject>) -> Unit) {
        install(context)
        V34OnlineApi.post(context, "weekly-start") { result ->
            result.onSuccess { response ->
                val attempt = response.getJSONObject("attempt")
                val challenge = response.getJSONObject("challenge")
                val expected = challenge.optJSONObject("rules")?.optInt("shots", 18)?.coerceIn(1, 100) ?: 18
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
                    .putString("attempt_id", attempt.getString("id"))
                    .putInt("expected_shots", expected)
                    .putLong("seed", attempt.optLong("seed", challenge.optLong("seed")))
                    .putInt("shot_no", 0)
                    .apply()
                flush()
            }
            done(result)
        }
    }

    fun restore(context: Context, attempt: JSONObject?) {
        install(context)
        if (attempt == null) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.getString("attempt_id", null) == attempt.optString("id")) return
        prefs.edit()
            .putString("attempt_id", attempt.optString("id"))
            .putLong("seed", attempt.optLong("seed"))
            .putInt("shot_no", 0)
            .apply()
    }

    fun onRecord(record: ShotRecord) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val attemptId = prefs.getString("attempt_id", null) ?: return
        val expected = prefs.getInt("expected_shots", 18).coerceIn(1, 100)
        val shotNo = prefs.getInt("shot_no", 0) + 1
        if (shotNo > expected) return
        val metrics = JSONObject()
            .put("ballSpeedMps", record.metrics.ballSpeedMps)
            .put("launchAngleDeg", record.metrics.launchAngleDeg)
            .put("faceAngleDeg", record.metrics.faceAngleDeg ?: JSONObject.NULL)
            .put("pathAngleDeg", record.metrics.pathAngleDeg ?: JSONObject.NULL)
            .put("impactOffsetMm", record.metrics.impactOffsetMm ?: JSONObject.NULL)
            .put("remainingM", record.result?.distanceToCupM ?: 30.0)
            .put("holed", record.result?.holed == true)
            .put("confidence", record.metrics.confidence ?: .5)
            .put("evidenceHash", evidence(record, attemptId, shotNo))
        val item = JSONObject()
            .put("attemptId", attemptId)
            .put("clientShotId", UUID.randomUUID().toString())
            .put("shotNo", shotNo)
            .put("metrics", metrics)
        synchronized(this) {
            val queue = load(context)
            queue.put(item)
            while (queue.length() > maxQueue) queue.remove(0)
            save(context, queue)
            prefs.edit().putInt("shot_no", shotNo).apply()
        }
        flush()
    }

    fun pendingCount(): Int = appContext?.let { load(it).length() } ?: 0

    fun flush() {
        val context = appContext ?: return
        if (!flushing.compareAndSet(false, true)) return
        Thread {
            try {
                while (true) {
                    val head = synchronized(this) { load(context).optJSONObject(0) } ?: break
                    val result = blockingPost(context, head)
                    if (result == null || !result.optBoolean("ok", false)) {
                        scheduleRetry()
                        break
                    }
                    synchronized(this) {
                        val queue = load(context)
                        if (queue.optJSONObject(0)?.optString("clientShotId") == head.optString("clientShotId")) {
                            queue.remove(0)
                            save(context, queue)
                        }
                    }
                    if (result.optJSONObject("settlement")?.optString("status") == "finished") clearActive(context)
                }
            } finally {
                flushing.set(false)
                if (pendingCount() > 0) scheduleRetry()
            }
        }.apply { name = "puttvision-weekly-outbox"; isDaemon = true }.start()
    }

    private fun blockingPost(context: Context, item: JSONObject): JSONObject? = runCatching {
        val token = V34SecureOnlineToken.load(context) ?: return null
        val payload = JSONObject()
            .put("action", "weekly-submit-shot")
            .put("attemptId", item.getString("attemptId"))
            .put("clientShotId", item.getString("clientShotId"))
            .put("shotNo", item.getInt("shotNo"))
            .put("metrics", item.getJSONObject("metrics"))
        val connection = (URL(V34_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("apikey", V34_API_KEY)
            setRequestProperty("x-pv-token", token)
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) return@runCatching null
        JSONObject(text)
    }.getOrNull()

    private fun scheduleRetry() {
        retryHandler.removeCallbacksAndMessages(null)
        retryHandler.postDelayed({ flush() }, 15_000L)
    }

    private fun clearActive(context: Context) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            .remove("attempt_id").remove("expected_shots").remove("seed").remove("shot_no").apply()
    }

    private fun evidence(record: ShotRecord, attemptId: String, shotNo: Int): String {
        val raw = listOf(
            attemptId, shotNo.toString(), record.timestampMs.toString(),
            record.metrics.ballSpeedMps.toString(), record.metrics.launchAngleDeg.toString(),
            record.metrics.faceAngleDeg.toString(), record.metrics.pathAngleDeg.toString(),
            record.result?.distanceToCupM.toString(), record.result?.holed.toString()
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Synchronized private fun load(context: Context): JSONArray {
        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("queue", "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    @Synchronized private fun save(context: Context, queue: JSONArray) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().putString("queue", queue.toString()).apply()
    }
}

fun showV34OnlineHub(context: Context) {
    V34WeeklyRuntime.install(context)
    val hasProfile = V34SecureOnlineToken.exists(context)
    val items = if (hasProfile) {
        arrayOf("ONLINE LEAGUE", "주간 챌린지", "계정 이전/복구 코드 발급", "다른 기기 계정 복구")
    } else {
        arrayOf("온라인 프로필 만들기", "복구 코드로 계정 복구")
    }
    AlertDialog.Builder(context)
        .setTitle("PUTTVISION ONLINE")
        .setMessage(if (hasProfile) "경기 · 주간랭킹 · 계정복구" else "새 프로필을 만들거나 기존 계정을 복구합니다.")
        .setItems(items) { _, which ->
            if (hasProfile) when (which) {
                0 -> showV31OnlineLeagueDialog(context)
                1 -> showV34Weekly(context)
                2 -> issueRecoveryCode(context)
                else -> showRecoverAccount(context)
            } else when (which) {
                0 -> showV31OnlineLeagueDialog(context)
                else -> showRecoverAccount(context)
            }
        }
        .setNegativeButton("닫기", null)
        .show()
}

private fun issueRecoveryCode(context: Context) {
    V34OnlineApi.post(context, "create-recovery") { result ->
        result.onSuccess { response ->
            val code = response.getString("recoveryCode")
            AlertDialog.Builder(context)
                .setTitle("계정 이전 코드")
                .setMessage("$code\n\n한 번 사용하면 자동으로 새 코드로 교체됩니다. 폰 밖의 안전한 곳에 보관하세요.")
                .setPositiveButton("확인", null)
                .show()
        }.onFailure { Toast.makeText(context, "복구 코드 발급 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

private fun showRecoverAccount(context: Context) {
    val input = EditText(context).apply { hint = "XXXX-XXXX-XXXX-XXXX-XXXX"; setSingleLine(true) }
    AlertDialog.Builder(context)
        .setTitle("온라인 계정 복구")
        .setMessage("이전 기기에서 발급한 20자리 복구 코드를 입력하세요. 복구가 끝나면 기존 기기 토큰은 즉시 무효화됩니다.")
        .setView(input)
        .setPositiveButton("복구") { _, _ ->
            V34OnlineApi.post(context, "recover", JSONObject().put("recoveryCode", input.text.toString()), auth = false) { result ->
                result.onSuccess { response ->
                    V34SecureOnlineToken.save(context, response.getString("token"))
                    V31OnlineRuntime.refreshMe(context)
                    val next = response.getString("recoveryCode")
                    AlertDialog.Builder(context)
                        .setTitle("복구 완료")
                        .setMessage("계정 복구 완료. 새 복구 코드는\n\n$next\n\n입니다. 기존 코드는 폐기됐습니다.")
                        .setPositiveButton("확인", null)
                        .show()
                }.onFailure { Toast.makeText(context, "계정 복구 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        .setNegativeButton("취소", null)
        .show()
}

private fun showV34Weekly(context: Context) {
    V34OnlineApi.post(context, "weekly") { result ->
        result.onSuccess { response ->
            val challenge = response.optJSONObject("challenge")
            if (challenge == null) {
                AlertDialog.Builder(context).setTitle("WEEKLY").setMessage("현재 진행 중인 주간 챌린지가 없습니다.").setPositiveButton("확인", null).show()
                return@onSuccess
            }
            val attempt = response.optJSONObject("attempt")
            V34WeeklyRuntime.restore(context, attempt)
            val rows = response.optJSONArray("rows") ?: JSONArray()
            val ranking = (0 until minOf(15, rows.length())).joinToString("\n") { i ->
                val row = rows.getJSONObject(i)
                "#${row.optInt("rank")} ${row.optString("display_name")} · ${row.optInt("best_score")}"
            }
            val expected = challenge.optJSONObject("rules")?.optInt("shots", 18) ?: 18
            val state = if (attempt != null) "진행중 · 서버 검증 $expected 구 · 대기전송 ${V34WeeklyRuntime.pendingCount()}" else "서버 검증 $expected 구 · 샷별 자동 제출"
            AlertDialog.Builder(context)
                .setTitle(challenge.optString("title", "WEEKLY"))
                .setMessage("$state\n\n${ranking.ifBlank { "아직 기록 없음" }}")
                .setPositiveButton(if (attempt != null) "계속하기" else "도전 시작") { _, _ ->
                    if (attempt != null) {
                        Toast.makeText(context, "주간 챌린지 이어서 진행", Toast.LENGTH_SHORT).show()
                    } else {
                        V34WeeklyRuntime.start(context) { start ->
                            start.onSuccess { Toast.makeText(context, "주간 챌린지 시작 · 실제 샷이 서버에 기록됩니다", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, "시작 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
                .setNegativeButton("닫기", null)
                .show()
        }.onFailure { Toast.makeText(context, "주간 챌린지 불러오기 실패 · ${it.message}", Toast.LENGTH_LONG).show() }
    }
}
