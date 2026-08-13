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

/**
 * V23 calibration policy:
 * 1) saved mat geometry + stable visual mat quad is the normal/default path,
 * 2) QR remains a precision/recovery assist and may finish first when visible,
 * 3) weak markerless detections are never promoted merely to avoid QR.
 */
class AutoCalibrator(
    private val onStatus: (String) -> Unit,
    private val onCalibrated: (CalibrationResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()
    private val busy = AtomicBoolean(false)
    private val frameQualityEstimator = CameraQualityEstimator()
    private val matStability = V23MarkerlessStability(requiredHits = 4)
    private var qrStableHits = 0
    private var lastQrSignature = ""
    private var latestFrameQuality: FrameQualitySnapshot? = null
    private var markerlessStatus = "매트 자동 인식 준비"
    private var lastDeliveryNs = 0L
    private var frameSerial = 0L
    private var lastDeliveredFrameSerial = -1L

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

        val serial = ++frameSerial

        latestFrameQuality = runCatching { frameQualityEstimator.evaluate(image) }.getOrNull()
        val markerless = if (V16MatGeometryRuntime.markerlessEnabled) {
            runCatching { V23YuvMatDetector.detect(image) }.getOrNull()
        } else null
        processMarkerless(image, markerless, serial)

        // Keep QR running as an optional precision/recovery path. We intentionally retain raw-frame
        // coordinates here because the homography and normal analyzer both consume sensor pixels.
        val input = InputImage.fromMediaImage(media, 0)
        scanner.process(input)
            .addOnSuccessListener { codes -> processQr(image, codes, serial) }
            .addOnFailureListener {
                onStatus(if (V16MatGeometryRuntime.markerlessEnabled) markerlessStatus else "QR 인식 재시도중")
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    fun close() { scanner.close() }

    private fun processMarkerless(image: ImageProxy, detection: V23MarkerlessDetection?, serial: Long) {
        if (!V16MatGeometryRuntime.markerlessEnabled) {
            matStability.reset()
            markerlessStatus = "QR 보정 모드"
            return
        }
        val q = latestFrameQuality?.overallScore
        if (detection == null) {
            matStability.miss()
            markerlessStatus = buildString {
                append("매트 찾는 중")
                if (q != null) append(" · Q$q")
                append(" · QR 보조 가능")
            }
            onStatus(markerlessStatus)
            return
        }

        val hits = matStability.update(detection, image.width, image.height)
        val setup = V15SetupAssistant.evaluate(image.width, image.height, detection.cornersPx)
        val confidencePct = (detection.confidence * 100.0).toInt().coerceIn(0, 99)
        markerlessStatus = buildString {
            append("매트 자동캘 $confidencePct")
            append(" · 안정화 ${hits}/4")
            if (q != null) append(" · Q$q")
            if (setup != null && !setup.ready) append(" · ${setup.primaryHint}")
            append(" · QR 선택")
        }
        onStatus(markerlessStatus)

        val frameGood = q == null || q >= 48
        val setupGood = setup == null || setup.score >= 68
        if (!frameGood || !setupGood || !matStability.ready(detection)) return

        val frame = FrameInfo(image.width, image.height, image.imageInfo.rotationDegrees)
        val h = detection.homography(frame) ?: return
        val d = h.diagnostics
        val result = CalibrationResult(
            homography = h,
            imagePoints = detection.fitImagePoints(),
            realPointsCm = detection.realPointsCm(),
            frameInfo = frame,
            markerSource = "${detection.source}_V23",
            frameQuality = latestFrameQuality,
            fitPointCount = d.pointCount,
            reprojectionRmsPx = d.reprojectionRmsPx,
            maxReprojectionErrorPx = d.maxReprojectionErrorPx,
            lensK1 = d.lensK1,
            setupAdvice = setup
        )
        if (deliver(result, serial)) {
            matStability.reset()
            qrStableHits = 0
            lastQrSignature = ""
            markerlessStatus = "매트 자동캘 완료 · QR 불필요"
            onStatus(markerlessStatus)
        }
    }

    private fun processQr(image: ImageProxy, codes: List<Barcode>, serial: Long) {
        val resolved = resolveCodes(codes)
        if (resolved == null) {
            qrStableHits = 0
            lastQrSignature = ""
            val count = codes.count { it.boundingBox != null }
            onStatus(
                if (V16MatGeometryRuntime.markerlessEnabled) {
                    "$markerlessStatus · QR $count/4+"
                } else {
                    "QR 자동캘 · $count/4+"
                }
            )
            return
        }

        val signature = resolved.imagePoints.joinToString("|") { "${it.x.toInt()},${it.y.toInt()}" }
        qrStableHits = if (similarSignature(signature, lastQrSignature)) qrStableHits + 1 else 1
        lastQrSignature = signature
        val q = latestFrameQuality?.overallScore
        val setup = V15SetupAssistant.evaluate(image.width, image.height, resolved.imagePoints)
        onStatus(buildString {
            append("QR 정밀보정 · ${resolved.source} ${resolved.fitImagePoints.size}개 · ${qrStableHits}/3")
            if (q != null) append(" · Q$q")
            if (setup != null && !setup.ready) append(" · ${setup.primaryHint}")
        })

        if (qrStableHits < 3) return
        val frame = FrameInfo(image.width, image.height, image.imageInfo.rotationDegrees)
        val h = Homography.fromPoints(resolved.fitImagePoints, resolved.fitRealPointsCm, frame)
        if (h == null) {
            qrStableHits = 0
            onStatus("QR 배치가 찌그러짐 · 매트 자동캘 계속")
            return
        }
        val d = h.diagnostics
        val result = CalibrationResult(
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
        if (deliver(result, serial)) {
            qrStableHits = 0
            lastQrSignature = ""
            matStability.reset()
        }
    }

    @Synchronized
    private fun deliver(result: CalibrationResult, serial: Long): Boolean {
        val now = System.nanoTime()
        // Markerless and QR share the same ImageProxy. A slow QR callback must never overwrite a
        // markerless result from that exact frame, even if ML processing takes longer than cooldown.
        if (lastDeliveredFrameSerial == serial) return false
        // Cross-frame cooldown still allows a fresh attempt if ProductCalibrationQuality rejects
        // the previous candidate while preventing rapid oscillation between two valid sources.
        if (lastDeliveryNs != 0L && now - lastDeliveryNs < 350_000_000L) return false
        lastDeliveredFrameSerial = serial
        lastDeliveryNs = now
        onCalibrated(result)
        return true
    }

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