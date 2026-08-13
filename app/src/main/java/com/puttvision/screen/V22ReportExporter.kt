package com.puttvision.screen

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

object V22ReportExporter {
    fun share(context: Context, records: List<ShotRecord>) {
        val shots = records.takeLast(120)
        if (shots.isEmpty()) {
            android.widget.Toast.makeText(context, "공유할 샷 기록이 없습니다", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 3600_000L }?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val pdf = File(dir, "PuttVision_Report_$stamp.pdf")
            val csv = File(dir, "PuttVision_Shots_$stamp.csv")
            writePdf(pdf, shots)
            writeCsv(csv, shots)
            val authority = "${context.packageName}.fileprovider"
            val uris = arrayListOf(
                FileProvider.getUriForFile(context, authority, pdf),
                FileProvider.getUriForFile(context, authority, csv)
            )
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "PuttVision 퍼팅 리포트")
                putExtra(Intent.EXTRA_TEXT, summary(shots))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "퍼팅 리포트 공유"))
        }.onFailure {
            android.widget.Toast.makeText(context, "리포트 생성 실패 · ${it.message ?: "파일 오류"}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun summary(records: List<ShotRecord>): String {
        val avgScore = records.map { it.strokeScore.total }.average()
        val make = records.count { it.result?.holed == true } * 100.0 / records.size
        val launches = records.map { it.metrics.launchAngleDeg }
        return "PuttVision ${records.size}샷 · 평균 ${"%.1f".format(avgScore)}점 · 성공 ${"%.0f".format(make)}% · START σ ${"%.2f".format(std(launches))}°"
    }

    private fun writePdf(file: File, records: List<ShotRecord>) {
        val reportTiles = V26ReportPreferences.snapshot()
        val doc = PdfDocument()
        val pageWidth = 1240
        val pageHeight = 1754
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = 95f

        fun text(value: String, x: Float, yy: Float, size: Float, color: Int = Color.rgb(28, 34, 32), bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(value, x, yy, paint)
        }
        fun header() {
            canvas.drawColor(Color.WHITE)
            paint.color = Color.rgb(32, 148, 81)
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 18f, paint)
            text("PUTTVISION", 70f, 70f, 34f, Color.rgb(20, 28, 25), true)
            text("PERFORMANCE REPORT", 70f, 105f, 16f, Color.rgb(82, 96, 88), true)
            text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()), 930f, 88f, 14f, Color.rgb(110, 120, 114))
        }
        fun nextPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            header()
            y = 150f
        }
        header()
        y = 165f

        val avgScore = records.map { it.strokeScore.total }.average()
        val makePct = records.count { it.result?.holed == true } * 100.0 / records.size
        val launch = records.map { it.metrics.launchAngleDeg }
        val face = records.mapNotNull { it.metrics.faceAngleDeg }
        val path = records.mapNotNull { it.metrics.pathAngleDeg }
        val cupError = records.mapNotNull { it.result?.distanceToCupM }
        val compare = V20PerformanceCompare.build(records)

        if (V26ReportTile.OVERVIEW in reportTiles) {
        paint.color = Color.rgb(242, 247, 244)
        canvas.drawRoundRect(65f, 135f, 1175f, 380f, 24f, 24f, paint)
        text("${records.size}", 105f, 230f, 58f, Color.rgb(34, 153, 84), true)
        text("SHOTS", 105f, 265f, 16f, Color.rgb(95, 110, 102), true)
        text("${"%.1f".format(avgScore)}", 350f, 230f, 58f, Color.rgb(25, 34, 30), true)
        text("AVG SCORE", 350f, 265f, 16f, Color.rgb(95, 110, 102), true)
        text("${"%.0f".format(makePct)}%", 620f, 230f, 58f, Color.rgb(25, 34, 30), true)
        text("MAKE", 620f, 265f, 16f, Color.rgb(95, 110, 102), true)
        text("${"%.2f".format(std(launch))}°", 870f, 230f, 58f, Color.rgb(25, 34, 30), true)
        text("START σ", 870f, 265f, 16f, Color.rgb(95, 110, 102), true)
        text("FACE |avg| ${face.takeIf { it.isNotEmpty() }?.map(::abs)?.average()?.let { "%.2f°".format(it) } ?: "--"}", 105f, 330f, 19f)
        text("PATH |avg| ${path.takeIf { it.isNotEmpty() }?.map(::abs)?.average()?.let { "%.2f°".format(it) } ?: "--"}", 385f, 330f, 19f)
        text("CUP ERROR ${cupError.takeIf { it.isNotEmpty() }?.average()?.let { "%.2f m".format(it) } ?: "--"}", 665f, 330f, 19f)
        y = 435f
        } else { y = 165f }

        if (V26ReportTile.COACH_TREND in reportTiles) {
        text("COACH / TREND", 70f, y, 20f, Color.rgb(34, 153, 84), true); y += 42f
        text(compare.headline, 70f, y, 28f, Color.rgb(24, 31, 28), true); y += 35f
        text(compare.detail.take(110), 70f, y, 17f, Color.rgb(80, 93, 86)); y += 55f
        compare.trend?.let {
            text("최근 ${it.recentShots}구 vs 이전 ${it.baselineShots}구 · SCORE ${if (it.scoreDelta >= 0) "+" else ""}${"%.1f".format(it.scoreDelta)} · START σ ${if (it.launchStdDeltaDeg >= 0) "+" else ""}${"%.2f".format(it.launchStdDeltaDeg)}°", 70f, y, 17f, Color.rgb(57, 68, 62), true)
            y += 45f
        }

        }

        if (V26ReportTile.PUTTER_RANKING in reportTiles && compare.putters.isNotEmpty()) {
            text("PUTTER RANKING", 70f, y, 20f, Color.rgb(34, 153, 84), true); y += 38f
            compare.putters.take(5).forEachIndexed { index, row ->
                text("${index + 1}. ${row.label}", 85f, y, 19f, Color.rgb(24, 31, 28), true)
                text("${"%.1f".format(row.score)}점 · ${row.shots}구 · START σ ${"%.2f".format(row.launchStdDeg)}° · MAKE ${"%.0f".format(row.makePct)}%", 430f, y, 17f, Color.rgb(75, 87, 80))
                y += 38f
            }
            y += 20f
        }

        if (V26ReportTile.SHOT_DETAIL in reportTiles) {
        text("SHOT DETAIL", 70f, y, 20f, Color.rgb(34, 153, 84), true); y += 36f
        text("#     SCORE     BALL       START      FACE       PATH       CUP", 78f, y, 15f, Color.rgb(100, 112, 105), true); y += 30f
        records.takeLast(80).forEachIndexed { index, r ->
            if (y > pageHeight - 95f) nextPage()
            if (index % 2 == 0) {
                paint.color = Color.rgb(247, 249, 248)
                canvas.drawRect(65f, y - 23f, 1175f, y + 12f, paint)
            }
            val m = r.metrics
            val row = String.format(
                Locale.US,
                "%02d     %3d       %4.2f       %+5.2f°     %6s     %6s     %5s",
                index + 1,
                r.strokeScore.total,
                m.ballSpeedMps,
                m.launchAngleDeg,
                m.faceAngleDeg?.let { "%+.2f°".format(it) } ?: "--",
                m.pathAngleDeg?.let { "%+.2f°".format(it) } ?: "--",
                r.result?.let { if (it.holed) "IN" else "%.2fm".format(it.distanceToCupM) } ?: "--"
            )
            paint.typeface = Typeface.MONOSPACE
            text(row, 78f, y, 15f, Color.rgb(42, 51, 47))
            y += 35f
        }
        }

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun writeCsv(file: File, records: List<ShotRecord>) {
        val header = "timestamp_ms,putter,mode,target_m,score,holed,cup_error_m,ball_mps,start_deg,head_mps,face_deg,path_deg,face_to_path_deg,impact_mm,confidence\n"
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write(header)
            records.forEach { r ->
                val m = r.metrics
                fun esc(value: String?) = "\"${(value ?: "").replace("\"", "\"\"")}\""
                out.append(r.timestampMs.toString()).append(',')
                    .append(esc(r.putterProfileName)).append(',')
                    .append(esc(r.mode.label)).append(',')
                    .append(r.targetDistanceM.toString()).append(',')
                    .append(r.strokeScore.total.toString()).append(',')
                    .append((r.result?.holed == true).toString()).append(',')
                    .append(r.result?.distanceToCupM?.toString() ?: "").append(',')
                    .append(m.ballSpeedMps.toString()).append(',')
                    .append(m.launchAngleDeg.toString()).append(',')
                    .append(m.headSpeedMps?.toString() ?: "").append(',')
                    .append(m.faceAngleDeg?.toString() ?: "").append(',')
                    .append(m.pathAngleDeg?.toString() ?: "").append(',')
                    .append(m.faceToPathDeg?.toString() ?: "").append(',')
                    .append(m.impactOffsetMm?.toString() ?: "").append(',')
                    .append(m.confidence?.toString() ?: "")
                    .append('\n')
            }
        }
    }

    private fun std(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val avg = values.average()
        return sqrt(values.sumOf { (it - avg) * (it - avg) } / values.size)
    }
}
