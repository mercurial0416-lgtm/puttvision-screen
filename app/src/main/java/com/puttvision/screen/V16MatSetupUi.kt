package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

object V16MatGeometryRuntime {
    @Volatile var widthCm: Double = 50.0
    @Volatile var lengthCm: Double = 300.0
    @Volatile var markerlessEnabled: Boolean = true
}

class V16MatGeometryStore(context: Context) {
    private val prefs = context.getSharedPreferences("v16_mat_geometry", Context.MODE_PRIVATE)

    init { sync() }

    fun save(widthCm: Double, lengthCm: Double, markerless: Boolean = true) {
        prefs.edit()
            .putFloat("widthCm", widthCm.toFloat())
            .putFloat("lengthCm", lengthCm.toFloat())
            .putBoolean("markerless", markerless)
            .apply()
        sync()
    }

    fun reset() {
        prefs.edit().clear().apply()
        sync()
    }

    private fun sync() {
        V16MatGeometryRuntime.widthCm = prefs.getFloat("widthCm", 50f).toDouble().coerceIn(20.0, 300.0)
        V16MatGeometryRuntime.lengthCm = prefs.getFloat("lengthCm", 300f).toDouble().coerceIn(50.0, 1000.0)
        V16MatGeometryRuntime.markerlessEnabled = prefs.getBoolean("markerless", true)
    }
}

fun showV16MatSetupDialog(context: Context, manager: MatCalibrationManager) {
    val store = V16MatGeometryStore(context)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.pvDp(18), context.pvDp(8), context.pvDp(18), context.pvDp(4))
    }
    val width = EditText(context).apply {
        hint = "매트 폭(cm)"
        setText("%.0f".format(V16MatGeometryRuntime.widthCm))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true)
    }
    val length = EditText(context).apply {
        hint = "매트 길이(cm)"
        setText("%.0f".format(V16MatGeometryRuntime.lengthCm))
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine(true)
    }
    root.addView(width, LinearLayout.LayoutParams(-1, context.pvDp(48)))
    root.addView(length, LinearLayout.LayoutParams(-1, context.pvDp(48)))

    val message = buildString {
        append("QR은 가장 정확한 기준이고, V16은 저장한 매트 크기와 영상의 매트 윤곽을 이용해 HFR에서 QR 없는 보정도 시도합니다.\n")
        append("현재 매트 속도: ${manager.statusLabel()}\n\n")
        append("처음 한 번 QR로 맞춘 뒤 같은 거치 위치를 쓰는 것을 권장합니다.")
    }

    val dialog = AlertDialog.Builder(context)
        .setTitle("매트 자동 인식")
        .setMessage(message)
        .setView(root)
        .setNegativeButton("취소", null)
        .setNeutralButton("속도 다시 측정") { _, _ -> manager.reset() }
        .setPositiveButton("저장", null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val w = width.text.toString().toDoubleOrNull()
            val l = length.text.toString().toDoubleOrNull()
            if (w == null || w !in 20.0..300.0 || l == null || l !in 50.0..1000.0) {
                Toast.makeText(context, "매트 폭 20~300cm / 길이 50~1000cm", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.save(w, l, true)
            Toast.makeText(context, "매트 ${"%.0f".format(w)} × ${"%.0f".format(l)}cm 저장 · QR 없는 HFR 보정 ON", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
    }
    dialog.show()
}
