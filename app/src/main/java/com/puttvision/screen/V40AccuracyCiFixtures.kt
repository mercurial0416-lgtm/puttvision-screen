package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Exports the exact two CSV files consumed by ci/v20_accuracy_gate.py. */
object V40AccuracyCiFixtures {
    private const val ballToleranceMps = .08
    private const val launchToleranceDeg = .35
    private const val faceToleranceDeg = .55
    private const val pathToleranceDeg = .65

    fun export(activity: Activity, lab: AccuracyValidationLab): Result<Int> = runCatching {
        val matched = lab.matched()
        require(matched.isNotEmpty()) { "기준장비 값과 매칭된 샷이 없습니다" }

        val dir = File(activity.cacheDir, "exports/v20-ci").apply { mkdirs() }
        val reference = File(dir, "v20_reference.csv")
        val measured = File(dir, "v20_measured.csv")

        reference.bufferedWriter().use { out ->
            out.appendLine("id,ball_speed_mps,ball_tol_mps,launch_deg,launch_tol_deg,face_deg,face_tol_deg,path_deg,path_tol_deg")
            matched.forEach { s ->
                out.appendLine(listOf(
                    s.id,
                    number(s.refBall), if (s.refBall != null) ballToleranceMps else "",
                    number(s.refLaunch), if (s.refLaunch != null) launchToleranceDeg else "",
                    number(s.refFace), if (s.refFace != null) faceToleranceDeg else "",
                    number(s.refPath), if (s.refPath != null) pathToleranceDeg else ""
                ).joinToString(","))
            }
        }

        measured.bufferedWriter().use { out ->
            out.appendLine("id,ball_speed_mps,launch_deg,face_deg,path_deg")
            matched.forEach { s ->
                out.appendLine(listOf(
                    s.id,
                    number(s.measuredBall),
                    number(s.measuredLaunch),
                    number(s.measuredFace),
                    number(s.measuredPath)
                ).joinToString(","))
            }
        }

        sharePair(activity, reference, measured, matched.size)
        matched.size
    }

    fun show(activity: Activity) {
        val lab = AccuracyValidationLab(activity)
        val matched = lab.matched().size
        val total = lab.all().size
        val readiness = when {
            matched >= 30 -> "충분한 샷 수 · 배포 회귀 기준으로 사용 권장"
            matched >= 20 -> "LAB P95 READY · CI 기준값으로 사용 가능"
            matched > 0 -> "내보내기 가능 · 20샷 이상 권장"
            else -> "먼저 ACCURACY LAB에서 기준장비 값을 매칭하세요"
        }
        AlertDialog.Builder(activity)
            .setTitle("REAL DEVICE CI FIXTURES")
            .setMessage(
                "측정 $total 샷 · 기준값 매칭 $matched 샷\n\n" +
                    "$readiness\n\n" +
                    "내보내면 CI가 그대로 읽는 v20_reference.csv + v20_measured.csv 두 파일이 생성됩니다. " +
                    "기본 허용오차: BALL ±0.08 m/s · START ±0.35° · FACE ±0.55° · PATH ±0.65°."
            )
            .setPositiveButton("CI 2파일 내보내기") { _, _ ->
                export(activity, lab)
                    .onFailure { android.widget.Toast.makeText(activity, it.message ?: "내보내기 실패", android.widget.Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun sharePair(activity: Activity, reference: File, measured: File, count: Int) {
        val authority = "${activity.packageName}.fileprovider"
        val uris = arrayListOf<Uri>(
            FileProvider.getUriForFile(activity, authority, reference),
            FileProvider.getUriForFile(activity, authority, measured)
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "PuttVision real-device accuracy fixtures · $count shots")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, "PuttVision CI 정확도 2파일 공유"))
    }

    private fun number(value: Double?): Any = value?.takeIf { it.isFinite() }?.let { "%.6f".format(java.util.Locale.US, it) } ?: ""
}
