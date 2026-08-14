package com.puttvision.screen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Exports the exact two CSV files consumed by ci/v20_accuracy_gate.py. */
object V40AccuracyCiFixtures {
    internal const val MIN_CI_SHOTS = 20
    private const val ballToleranceMps = .08
    private const val launchToleranceDeg = .35
    private const val faceToleranceDeg = .55
    private const val pathToleranceDeg = .65

    internal fun referenceCsv(samples: List<ValidationSample>): String = buildString {
        appendLine("id,ball_speed_mps,ball_tol_mps,launch_deg,launch_tol_deg,face_deg,face_tol_deg,path_deg,path_tol_deg")
        samples.forEach { s ->
            appendLine(listOf(
                csvCell(s.id),
                number(s.refBall), if (s.refBall != null) number(ballToleranceMps) else "",
                number(s.refLaunch), if (s.refLaunch != null) number(launchToleranceDeg) else "",
                number(s.refFace), if (s.refFace != null) number(faceToleranceDeg) else "",
                number(s.refPath), if (s.refPath != null) number(pathToleranceDeg) else ""
            ).joinToString(","))
        }
    }

    internal fun measuredCsv(samples: List<ValidationSample>): String = buildString {
        appendLine("id,ball_speed_mps,launch_deg,face_deg,path_deg")
        samples.forEach { s ->
            appendLine(listOf(
                csvCell(s.id),
                number(s.measuredBall),
                number(s.measuredLaunch),
                number(s.measuredFace),
                number(s.measuredPath)
            ).joinToString(","))
        }
    }

    internal fun readinessIssue(samples: List<ValidationSample>): String? {
        if (samples.size < MIN_CI_SHOTS) {
            return "CI 기준 데이터는 최소 ${MIN_CI_SHOTS}샷이 필요합니다 · 현재 ${samples.size}샷"
        }
        val uniqueIds = samples.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
        if (uniqueIds.size != samples.size) return "샷 ID가 비어 있거나 중복되었습니다"

        fun coverage(label: String, ref: (ValidationSample) -> Double?, measured: (ValidationSample) -> Double?): String? {
            val count = samples.count { s ->
                ref(s)?.isFinite() == true && measured(s)?.isFinite() == true
            }
            return if (count < MIN_CI_SHOTS) "$label 기준/측정 매칭이 ${MIN_CI_SHOTS}개 필요합니다 · 현재 ${count}개" else null
        }
        return coverage("BALL", { it.refBall }, { it.measuredBall })
            ?: coverage("START", { it.refLaunch }, { it.measuredLaunch })
            ?: coverage("FACE", { it.refFace }, { it.measuredFace })
            ?: coverage("PATH", { it.refPath }, { it.measuredPath })
    }

    fun export(activity: Activity, lab: AccuracyValidationLab): Result<Int> = runCatching {
        val matched = lab.matched()
        require(matched.isNotEmpty()) { "기준장비 값과 매칭된 샷이 없습니다" }
        readinessIssue(matched)?.let { error(it) }

        val dir = File(activity.cacheDir, "exports/v20-ci").apply { mkdirs() }
        val reference = File(dir, "v20_reference.csv")
        val measured = File(dir, "v20_measured.csv")
        reference.writeText(referenceCsv(matched))
        measured.writeText(measuredCsv(matched))

        sharePair(activity, reference, measured, matched.size)
        matched.size
    }

    fun show(activity: Activity) {
        val lab = AccuracyValidationLab(activity)
        val samples = lab.matched()
        val matched = samples.size
        val total = lab.all().size
        val issue = readinessIssue(samples)
        val readiness = when {
            issue == null && matched >= 30 -> "충분한 샷 수 · 배포 회귀 기준으로 사용 권장"
            issue == null -> "CI READY · BALL/START/FACE/PATH 각 ${MIN_CI_SHOTS}개 이상"
            matched > 0 -> issue
            else -> "먼저 ACCURACY LAB에서 기준장비 값을 매칭하세요"
        }
        AlertDialog.Builder(activity)
            .setTitle("REAL DEVICE CI FIXTURES")
            .setMessage(
                "측정 $total 샷 · 기준값 매칭 $matched 샷\n\n" +
                    "$readiness\n\n" +
                    "CI용 공식 2파일은 최소 ${MIN_CI_SHOTS}개의 완전한 BALL / START / FACE / PATH 페어가 있어야 생성됩니다. " +
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

    private fun number(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { "%.6f".format(Locale.US, it) } ?: ""

    private fun csvCell(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
