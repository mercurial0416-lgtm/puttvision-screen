package com.puttvision.screen

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

fun showV16DeviceCalibrationDialog(context: Context) {
    val message = buildString {
        append("현재: ${V16DeviceAutoCalibrationRuntime.statusLabel()}\n\n")
        append("240/120fps HFR 샷에서 얻은 raw 볼스피드와 매트 감속 보정 임팩트 스피드를 비교해 이 폰 전용 보정계수를 자동 학습합니다.\n\n")
        append("최소 5개의 신뢰도 높은 HFR 샷이 쌓인 뒤에만 30/60fps 폴백 측정에 작은 보정을 적용합니다. HFR 값 자체에는 이 값을 다시 적용하지 않습니다.")
    }
    AlertDialog.Builder(context)
        .setTitle("기기 정밀 자동보정")
        .setMessage(message)
        .setNegativeButton("닫기", null)
        .setPositiveButton("다시 학습") { _, _ ->
            V16DeviceAutoCalibrationRuntime.reset()
            Toast.makeText(context, "기기 보정 초기화 · 다음 HFR 샷부터 다시 학습", Toast.LENGTH_SHORT).show()
        }
        .show()
}
