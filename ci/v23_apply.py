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

# Keep image/world arrays in the exact same BL,BR,TR,TL order for downstream diagnostics/drift.
auto = Path("app/src/main/java/com/puttvision/screen/AutoCalibrator.kt")
text = auto.read_text(encoding="utf-8")
if "imagePoints = detection.fitImagePoints()," not in text:
    old = "imagePoints = detection.cornersPx,"
    if text.count(old) != 1:
        raise SystemExit(f"V23 point ordering: expected 1 match, got {text.count(old)}")
    auto.write_text(text.replace(old, "imagePoints = detection.fitImagePoints(),", 1), encoding="utf-8")
    print("V23 markerless point ordering applied")
else:
    print("V23 markerless point ordering already current")

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