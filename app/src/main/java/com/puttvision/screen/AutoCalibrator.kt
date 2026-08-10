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
    val markerSource: String = "PV1"
)

class AutoCalibrator(
    private val onStatus: (String) -> Unit,
    private val onCalibrated: (CalibrationResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner =
        BarcodeScanning.getClient()

    private val busy =
        AtomicBoolean(false)

    private var stableHits = 0
    private var lastSignature = ""

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

        // Raw-buffer orientation so camera-analysis and calibration coordinates
        // share the same coordinate system.
        val input =
            InputImage.fromMediaImage(
                media,
                0
            )

        scanner.process(input)
            .addOnSuccessListener { codes ->
                val resolved =
                    resolveCodes(
                        codes
                    )

                if (resolved != null) {
                    val signature =
                        resolved.imagePoints
                            .joinToString("|") {
                                "${it.x.toInt()},${it.y.toInt()}"
                            }

                    stableHits =
                        if (
                            similarSignature(
                                signature,
                                lastSignature
                            )
                        ) {
                            stableHits + 1
                        } else {
                            1
                        }

                    lastSignature =
                        signature

                    onStatus(
                        "${resolved.source} 4개 · 안정화 $stableHits/3"
                    )

                    if (stableHits >= 3) {
                        val h =
                            Homography.fromFourPoints(
                                resolved.imagePoints,
                                resolved.realPointsCm
                            )

                        if (h != null) {
                            onCalibrated(
                                CalibrationResult(
                                    homography = h,
                                    imagePoints =
                                        resolved.imagePoints,
                                    realPointsCm =
                                        resolved.realPointsCm,
                                    frameInfo =
                                        FrameInfo(
                                            image.width,
                                            image.height,
                                            image.imageInfo.rotationDegrees
                                        ),
                                    markerSource =
                                        resolved.source
                                )
                            )
                        } else {
                            stableHits = 0

                            onStatus(
                                "마커 배치가 찌그러짐 · 재인식"
                            )
                        }
                    }
                } else {
                    stableHits = 0

                    onStatus(
                        "자동 캘 · QR 마커 ${codes.count { it.boundingBox != null }}/4"
                    )
                }
            }
            .addOnFailureListener {
                onStatus(
                    "QR 인식 재시도중"
                )
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    fun close() {
        scanner.close()
    }

    private data class Marker(
        val role: String,
        val image: PointF,
        val realCm: PointF
    )

    private fun resolveCodes(
        codes: List<Barcode>
    ): ResolvedMarkerLayout? {
        // Preferred path: PuttVision QR markers carrying exact physical coords.
        val parsed =
            codes.mapNotNull(
                ::parsePvMarker
            ).associateBy {
                it.role
            }

        val needed =
            listOf(
                "BL",
                "BR",
                "TR",
                "TL"
            )

        if (
            needed.all {
                parsed.containsKey(it)
            }
        ) {
            val markers =
                needed.map {
                    parsed.getValue(it)
                }

            return ResolvedMarkerLayout(
                imagePoints =
                    markers.map {
                        it.image
                    },
                realPointsCm =
                    markers.map {
                        it.realCm
                    },
                source =
                    "PV1"
            )
        }

        // v0.4 fallback: four ordinary purchasable QR stickers can be used.
        // Put their centers at 45cm left-right and 100cm front-back.
        // We select the four visually largest QR codes if extras are visible.
        val generic =
            codes.mapNotNull { code ->
                val box =
                    code.boundingBox
                        ?: return@mapNotNull null

                val center =
                    PointF(
                        box.exactCenterX(),
                        box.exactCenterY()
                    )

                val area =
                    box.width()
                        .toLong() *
                        box.height()
                        .toLong()

                Triple(
                    center,
                    area,
                    code.rawValue ?: ""
                )
            }
                .sortedByDescending {
                    it.second
                }
                .take(4)
                .map {
                    it.first
                }

        if (generic.size == 4) {
            return MarkerLayoutResolver.fromGenericFour(
                generic
            )
        }

        return null
    }

    private fun parsePvMarker(
        code: Barcode
    ): Marker? {
        val text =
            code.rawValue
                ?: return null

        val p =
            text.split("|")

        if (
            p.size != 4 ||
            p[0] != "PV1"
        ) {
            return null
        }

        val role =
            p[1]

        if (
            role !in setOf(
                "BL",
                "BR",
                "TR",
                "TL"
            )
        ) {
            return null
        }

        val x =
            p[2].toFloatOrNull()
                ?: return null

        val y =
            p[3].toFloatOrNull()
                ?: return null

        val box =
            code.boundingBox
                ?: return null

        val center =
            PointF(
                box.exactCenterX(),
                box.exactCenterY()
            )

        return Marker(
            role = role,
            image = center,
            realCm =
                PointF(
                    x,
                    y
                )
        )
    }

    private fun similarSignature(
        a: String,
        b: String
    ): Boolean {
        if (
            a.isBlank() ||
            b.isBlank()
        ) {
            return false
        }

        val ap =
            a.split("|")

        val bp =
            b.split("|")

        if (
            ap.size != bp.size ||
            ap.size != 4
        ) {
            return false
        }

        fun parse(
            value: String
        ): Pair<Int, Int>? {
            val xy =
                value.split(",")

            if (
                xy.size != 2
            ) {
                return null
            }

            return (
                xy[0].toIntOrNull()
                    ?: return null
                ) to (
                xy[1].toIntOrNull()
                    ?: return null
                )
        }

        var totalMovement = 0.0

        for (i in 0 until 4) {
            val aa =
                parse(ap[i])
                    ?: return false

            val bb =
                parse(bp[i])
                    ?: return false

            val dx =
                aa.first -
                    bb.first

            val dy =
                aa.second -
                    bb.second

            totalMovement +=
                kotlin.math.hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
        }

        return totalMovement / 4.0 < 18.0
    }
}
