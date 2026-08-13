package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context

/** Device-safe list dialog: avoids mixing single-choice and multi-choice AlertDialog modes. */
fun showV26GreenVisualSettingsDialog(context: Context) {
    V26GreenVisualRuntime.install(context)
    val modes = V26GreenVisualMode.entries
    val labels = modes.map { mode ->
        val mark = if (mode == V26GreenVisualRuntime.mode) "✓" else "  "
        "$mark ${mode.label}"
    } + listOf("${if (V26GreenVisualRuntime.swingGuide) "✓" else "  "} 추천 속도 스윙가이드")
    AlertDialog.Builder(context)
        .setTitle("GREEN VISUALS")
        .setItems(labels.toTypedArray()) { dialog, which ->
            if (which < modes.size) V26GreenVisualRuntime.setMode(context, modes[which])
            else V26GreenVisualRuntime.setSwingGuide(context, !V26GreenVisualRuntime.swingGuide)
            dialog.dismiss()
        }
        .setNegativeButton("닫기", null)
        .show()
}
