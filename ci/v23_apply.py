from pathlib import Path

# Tighten the pure fitter against full-width floor patches and perspective-edge outliers.
cal = Path("app/src/main/java/com/puttvision/screen/V23MarkerlessCalibrator.kt")
text = cal.read_text(encoding="utf-8")
changed = False
if "if (aspect !in .62..9.0) return null" not in text:
    old = "if (aspect !in .45..9.0) return null"
    if text.count(old) != 1:
        raise SystemExit(f"V23 aspect guard: expected 1 match, got {text.count(old)}")
    text = text.replace(old, "if (aspect !in .62..9.0) return null", 1)
    changed = True

old_robust = '''    private fun robustLine(points: List<V23Point>): V23Line? {
        var selected = points
        var fit = leastSquares(selected) ?: return null
        repeat(2) {
            val errors = selected.map { abs(it.y - (fit.a * it.x + fit.b)) }.sorted()
            val median = errors[errors.size / 2]
            val threshold = max(1.25, median * 2.8)
            val filtered = selected.filter { abs(it.y - (fit.a * it.x + fit.b)) <= threshold }
            if (filtered.size >= max(6, points.size / 3)) {
                selected = filtered
                fit = leastSquares(selected) ?: fit
            }
        }
        return fit
    }'''

new_robust = '''    private fun robustLine(points: List<V23Point>): V23Line? {
        if (points.size < 2) return null
        val span = (points.maxOf { it.x } - points.minOf { it.x }).coerceAtLeast(1.0)
        val step = max(1, points.size / 28)
        var bestInliers: List<V23Point>? = null
        var bestCount = 0
        var bestMse = Double.POSITIVE_INFINITY

        var i = 0
        while (i < points.size) {
            var j = i + step
            while (j < points.size) {
                val aPoint = points[i]
                val bPoint = points[j]
                val dx = bPoint.x - aPoint.x
                if (abs(dx) >= max(1.5, span * .08)) {
                    val slope = (bPoint.y - aPoint.y) / dx
                    val intercept = aPoint.y - slope * aPoint.x
                    val inliers = points.filter { abs(it.y - (slope * it.x + intercept)) <= 1.6 }
                    if (inliers.size >= max(6, points.size / 4)) {
                        val mse = inliers.sumOf {
                            val e = it.y - (slope * it.x + intercept)
                            e * e
                        } / inliers.size
                        if (inliers.size > bestCount || (inliers.size == bestCount && mse < bestMse)) {
                            bestInliers = inliers
                            bestCount = inliers.size
                            bestMse = mse
                        }
                    }
                }
                j += step
            }
            i += step
        }

        var selected = bestInliers ?: points
        var fit = leastSquares(selected) ?: return null
        val refined = points.filter { abs(it.y - (fit.a * it.x + fit.b)) <= 1.8 }
        if (refined.size >= max(6, points.size / 4)) {
            selected = refined
            fit = leastSquares(selected) ?: fit
        }
        return fit
    }'''

if new_robust not in text:
    if text.count(old_robust) != 1:
        raise SystemExit(f"V23 robust line: expected 1 legacy block, got {text.count(old_robust)}")
    text = text.replace(old_robust, new_robust, 1)
    changed = True

if changed:
    cal.write_text(text, encoding="utf-8")
    print("V23 fitter guards/RANSAC applied")
else:
    print("V23 fitter guards/RANSAC already current")

# AutoCalibrator: keep point ordering aligned and make each ImageProxy serial deliver at most once.
auto = Path("app/src/main/java/com/puttvision/screen/AutoCalibrator.kt")
text = auto.read_text(encoding="utf-8")
auto_changed = False
if "imagePoints = detection.fitImagePoints()," not in text:
    old = "imagePoints = detection.cornersPx,"
    if text.count(old) != 1:
        raise SystemExit(f"V23 point ordering: expected 1 match, got {text.count(old)}")
    text = text.replace(old, "imagePoints = detection.fitImagePoints(),", 1)
    auto_changed = True

if "private var frameSerial = 0L" not in text:
    marker = "    private var lastDeliveryNs = 0L\n"
    if text.count(marker) != 1:
        raise SystemExit("V23 frame serial field insertion point missing")
    text = text.replace(
        marker,
        marker + "    private var frameSerial = 0L\n    private var lastDeliveredFrameSerial = -1L\n",
        1,
    )
    auto_changed = True

if "val serial = ++frameSerial" not in text:
    marker = '''        if (media == null) {
            busy.set(false)
            image.close()
            return
        }

'''
    if text.count(marker) != 1:
        raise SystemExit("V23 frame serial analyze insertion point missing")
    text = text.replace(marker, marker + "        val serial = ++frameSerial\n\n", 1)
    auto_changed = True

replacements = [
    ("processMarkerless(image, markerless)", "processMarkerless(image, markerless, serial)"),
    (".addOnSuccessListener { codes -> processQr(image, codes) }", ".addOnSuccessListener { codes -> processQr(image, codes, serial) }"),
    ("private fun processMarkerless(image: ImageProxy, detection: V23MarkerlessDetection?)", "private fun processMarkerless(image: ImageProxy, detection: V23MarkerlessDetection?, serial: Long)"),
    ("private fun processQr(image: ImageProxy, codes: List<Barcode>)", "private fun processQr(image: ImageProxy, codes: List<Barcode>, serial: Long)"),
    ("if (deliver(result)) {", "if (deliver(result, serial)) {")
]
for old, new in replacements:
    if new in text:
        continue
    count = text.count(old)
    expected = 2 if old == "if (deliver(result)) {" else 1
    if count != expected:
        raise SystemExit(f"V23 serial patch {old!r}: expected {expected}, got {count}")
    text = text.replace(old, new)
    auto_changed = True

old_deliver = '''    @Synchronized
    private fun deliver(result: CalibrationResult): Boolean {
        val now = System.nanoTime()
        // Markerless and QR can finish on the same frame. Prevent duplicate callbacks while still
        // allowing another attempt when ProductCalibrationQuality rejects a weak result.
        if (lastDeliveryNs != 0L && now - lastDeliveryNs < 500_000_000L) return false
        lastDeliveryNs = now
        onCalibrated(result)
        return true
    }'''
new_deliver = '''    @Synchronized
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
    }'''
if new_deliver not in text:
    if text.count(old_deliver) != 1:
        raise SystemExit(f"V23 deliver block: expected 1 legacy block, got {text.count(old_deliver)}")
    text = text.replace(old_deliver, new_deliver, 1)
    auto_changed = True

if auto_changed:
    auto.write_text(text, encoding="utf-8")
    print("V23 markerless ordering/frame-idempotence applied")
else:
    print("V23 AutoCalibrator already current")

# The visible setup status should match the actual markerless-first policy.
main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
text = main.read_text(encoding="utf-8")
markerless_status = '''            overlay.status =
                if (V16MatGeometryRuntime.markerlessEnabled) {
                    "매트 자동 인식중 · QR은 선택"
                } else {
                    "QR 마커 4개 자동 인식중"
                }'''
legacy_status = '''            overlay.status =
                "QR 마커 4개 자동 인식중"'''
if markerless_status not in text:
    if text.count(legacy_status) != 1:
        raise SystemExit(f"MainActivity calibration status: expected 1 match, got {text.count(legacy_status)}")
    text = text.replace(legacy_status, markerless_status, 1)
    main.write_text(text, encoding="utf-8")
    print("V23 markerless-first UI status wired")
else:
    print("V23 MainActivity status already current")