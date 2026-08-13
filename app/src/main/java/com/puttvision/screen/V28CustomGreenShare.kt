package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object V28CustomGreenCodec {
    fun encode(profile: V22CustomGreenProfile): String = JSONObject().apply {
        put("product", "PuttVision")
        put("schema", 1)
        put("enabled", profile.enabled)
        put("nodes", JSONArray().apply {
            profile.nodes.forEach { n -> put(JSONObject().apply { put("at", n.at); put("sidePct", n.sidePct); put("longPct", n.longPct) }) }
        })
    }.toString(2)

    fun decode(raw: String): V22CustomGreenProfile? = runCatching {
        val root = JSONObject(raw.trim())
        require(root.optString("product") == "PuttVision" && root.optInt("schema") == 1)
        val arr = root.getJSONArray("nodes")
        require(arr.length() == 5)
        val expected = doubleArrayOf(0.0, .25, .50, .75, 1.0)
        val nodes = (0 until 5).map { i ->
            val j = arr.getJSONObject(i); val at = j.getDouble("at"); val side = j.getDouble("sidePct"); val long = j.getDouble("longPct")
            require(at.isFinite() && kotlin.math.abs(at - expected[i]) <= .002)
            require(side.isFinite() && side in -5.0..5.0 && long.isFinite() && long in -5.0..5.0)
            V22GreenNode(expected[i], side, long)
        }
        V22CustomGreenProfile(root.optBoolean("enabled", true), nodes)
    }.getOrNull()

    fun signature(profile: V22CustomGreenProfile): Int {
        var h = if (profile.enabled) 17 else 31
        profile.nodes.forEach { n -> h = 31*h + (n.at*1000).toInt(); h = 31*h + (n.sidePct*1000).toInt(); h = 31*h + (n.longPct*1000).toInt() }
        return h
    }
}

object V28CustomGreenShare {
    fun show(context: Context) {
        AlertDialog.Builder(context).setTitle("커스텀 그린 공유").setMessage("현재 5존 그린을 JSON으로 공유하거나 다른 PuttVision 그린을 불러옵니다.")
            .setPositiveButton("공유") { _, _ -> share(context) }.setNeutralButton("불러오기") { _, _ -> showImport(context) }.setNegativeButton("닫기", null).show()
    }

    fun share(context: Context) = runCatching {
        val json = V28CustomGreenCodec.encode(V22CustomGreenRuntime.profile)
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "PuttVision_CustomGreen_${System.currentTimeMillis()}.json").apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="application/json"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, json); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "커스텀 그린 공유"))
    }.onFailure { Toast.makeText(context, "그린 공유 실패", Toast.LENGTH_LONG).show() }.let { Unit }

    private fun showImport(context: Context) {
        val input = EditText(context).apply { hint="PuttVision 커스텀 그린 JSON 붙여넣기"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines=8 }
        AlertDialog.Builder(context).setTitle("커스텀 그린 불러오기").setView(input).setPositiveButton("적용") { _, _ ->
            val p = V28CustomGreenCodec.decode(input.text.toString())
            if (p == null) Toast.makeText(context, "올바른 그린 JSON이 아닙니다", Toast.LENGTH_LONG).show()
            else { V22CustomGreenRuntime.save(context, p.enabled, p.nodes); Toast.makeText(context, "그린 불러오기 완료", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("취소", null).show()
    }
}
