from pathlib import Path

# Tighten the pure fitter and keep calibration point ordering 1:1 with metric destinations.
cal = Path("app/src/main/java/com/puttvision/screen/V23MarkerlessCalibrator.kt")
text = cal.read_text(encoding="utf-8")
changed = False
if "if (aspect !in .62..9.0) return null" not in text:
    old = "if (aspect !in .45..9.0) return null"
    if text.count(old) != 1:
        raise SystemExit(f"V23 aspect guard: expected 1 match, got {text.count(old)}")
    text = text.replace(old, "if (aspect !in .62..9.0) return null", 1)
    changed = True
if "imagePoints = detection.fitImagePoints()," not in text:
    old = "imagePoints = detection.cornersPx,"
    if text.count(old) != 1:
        raise SystemExit(f"V23 point ordering: expected 1 match, got {text.count(old)}")
    text = text.replace(old, "imagePoints = detection.fitImagePoints(),", 1)
    changed = True
if changed:
    cal.write_text(text, encoding="utf-8")
    print("V23 fitter guards applied")
else:
    print("V23 fitter guards already current")

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
