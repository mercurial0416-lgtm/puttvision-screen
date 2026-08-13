package com.puttvision.screen

import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

data class CalibrationResult(
    val homography: Homography,
    val imagePoints: List<PointF>,
    val realPointsCm: List<PointF>,
    val frameInfo: FrameInfo,
    val markerSource: String = "PV1",
    val frameQuality: FrameQualitySnapshot? = null,
    val fitPointCount: Int = 4,
    val reprojectionRmsPx: Double? = null,
    val maxReprojectionErrorPx: Double? = null,
    val lensK1: Double? = null,
    val setupAdvice: V15SetupAdvice? = null
)

class AutoCalibrator(
    private val onStatus: (String) -> Unit,
    private val onCalibrated: (CalibrationResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()
    private val busy = AtomicBoolean(false)
    private val frameQualityEstimator = CameraQualityEstimator()
    private var stableHits = 0
    private var lastSignature = ""
    private var latestFrameQuality: FrameQualitySnapshot? = null

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val media = image.image
        if (media == null) {
            busy.set(false)
            image.close()
            return
        }
        latestFrameQuality = runCatching { frameQualityEstimator.evaluate(image) }.getOrNull()
        val input = InputImage.fromMediaImage(media, 0)

        scanner.process(input)
            .addOnSuccessListener { codes ->
                val resolved = resolveCodes(codes)
                if (resolved != null) {
                    val signature = resolved.imagePoints.joinToString("|") { "${it.x.toInt()},${it.y.toInt()}" }
                    stableHits = if (similarSignature(signature, lastSignature)) stableHits + 1 else 1
                    lastSignature = signature
                    val q = latestFrameQuality?.overallScore
                    val setup = V15SetupAssistant.evaluate(image.width, image.height, resolved.imagePoints)
                    onStatus(buildString {
                        append("${resolved.source} ${resolved.fitImagePoints.size}개 · 안정화 $stableHits/3")
                        if (q != null) append(" · Q$q")
                        if (setup != null) {
                            append(" · 설치 ${setup.score}")
                            if (!setup.ready) append(" · ${setup.primaryHint}")
                        }
                    })

                    if (stableHits >= 3) {
                        val frame = FrameInfo(image.width, image.height, image.imageInfo.rotationDegrees)
                        val h = Homography.fromPoints(
                            resolved.fitImagePoints,
                            resolved.fitRealPointsCm,
                            frame
                        )
                        if (h != null) {
                            val d = h.diagnostics
                            onCalibrated(
                                CalibrationResult(
                                    homography = h,
                                    imagePoints = resolved.imagePoints,
                                    realPointsCm = resolved.realPointsCm,
                                    frameInfo = frame,
                                    markerSource = resolved.source,
                                    frameQuality = latestFrameQuality,
                                    fitPointCount = d.pointCount,
                                    reprojectionRmsPx = d.reprojectionRmsPx,
                                    maxReprojectionErrorPx = d.maxReprojectionErrorPx,
                                    lensK1 = d.lensK1,
                                    setupAdvice = setup
                                )
                            )
                            stableHits = 0
                            lastSignature = ""
                        } else {
                            stableHits = 0
                            onStatus("마커 배치가 찌그러짐 · 재인식")
                        }
                    }
                } else {
                    stableHits = 0
                    val q = latestFrameQuality?.overallScore
                    onStatus(buildString {
                        append("자동 캘 · QR ${codes.count { it.boundingBox != null }}/4+ · V16 매트폴백 ON")
                        if (q != null) append(" · Q$q")
                    })
                }
            }
            .addOnFailureListener { onStatus("QR 인식 재시도중") }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    fun close() { scanner.close() }

    private fun resolveCodes(codes: List<Barcode>): ResolvedMarkerLayout? {
        val observations = codes.mapNotNull { code ->
            val box = code.boundingBox ?: return@mapNotNull null
            V14QrObservation(
                text = code.rawValue,
                center = PointF(box.exactCenterX(), box.exactCenterY()),
                area = box.width().toLong() * box.height().toLong()
            )
        }
        return V14MarkerResolver.resolve(observations)
    }

    private fun similarSignature(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val ap = a.split("|")
        val bp = b.split("|")
        if (ap.size != bp.size || ap.size != 4) return false
        fun parse(value: String): Pair<Int, Int>? {
            val xy = value.split(",")
            if (xy.size != 2) return null
            return (xy[0].toIntOrNull() ?: return null) to (xy[1].toIntOrNull() ?: return null)
        }
        var totalMovement = 0.0
        for (i in 0 until 4) {
            val aa = parse(ap[i]) ?: return false
            val bb = parse(bp[i]) ?: return false
            totalMovement += kotlin.math.hypot((aa.first - bb.first).toDouble(), (aa.second - bb.second).toDouble())
        }
        return totalMovement / 4.0 < 18.0
    }
}
