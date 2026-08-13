from pathlib import Path

changed = []

# 1) HFR: after QR attempts, use the saved mat geometry with the conservative V15 mat detector.
hfr_path = Path("app/src/main/java/com/puttvision/screen/HfrVideoAnalyzer.kt")
hfr = hfr_path.read_text(encoding="utf-8")
marker = "// V16 MARKERLESS HFR FALLBACK"
if marker not in hfr:
    needle = '''    return null
}

private fun scanMarkerLayoutBlocking(
'''
    insert = '''    // V16 MARKERLESS HFR FALLBACK
    // QR remains the precision path. When QR is absent, use a high-confidence mat silhouette
    // together with the dimensions saved in Product Setup. Weak detections are rejected.
    if (V16MatGeometryRuntime.markerlessEnabled) {
        for (i in indices) {
            val bmp = safeFrame(mmr, i) ?: continue
            val detected = V15MatDetector.detect(bmp) ?: continue
            if (detected.confidence < .74) continue
            V15MatDetector.homography(
                detection = detected,
                frameInfo = FrameInfo(bmp.width, bmp.height, 0),
                matWidthCm = V16MatGeometryRuntime.widthCm,
                matLengthCm = V16MatGeometryRuntime.lengthCm
            )?.let { return it }
        }
    }

    return null
}

private fun scanMarkerLayoutBlocking(
'''
    if hfr.count(needle) != 1:
        raise SystemExit(f"HFR markerless insertion point expected 1, got {hfr.count(needle)}")
    hfr = hfr.replace(needle, insert, 1)
    hfr_path.write_text(hfr, encoding="utf-8")
    changed.append(str(hfr_path))

# 2) Product Setup: the existing PHYSICAL MAT entry now owns both geometry and stimp calibration.
setup_path = Path("app/src/main/java/com/puttvision/screen/ProductSetupDialog.kt")
setup = setup_path.read_text(encoding="utf-8")
old = "showMatCalibrationManager(context, matManager)"
new = "showV16MatSetupDialog(context, matManager)"
if new not in setup:
    if setup.count(old) != 1:
        raise SystemExit(f"ProductSetup mat action expected 1, got {setup.count(old)}")
    setup = setup.replace(old, new, 1)
    setup_path.write_text(setup, encoding="utf-8")
    changed.append(str(setup_path))

# 3) Calibration status tells the user markerless HFR is available instead of looking hard-blocked.
cal_path = Path("app/src/main/java/com/puttvision/screen/AutoCalibrator.kt")
cal = cal_path.read_text(encoding="utf-8")
old_status = '''append("자동 캘 · QR 마커 ${codes.count { it.boundingBox != null }}/4+")'''
new_status = '''append("자동 캘 · QR ${codes.count { it.boundingBox != null }}/4+ · V16 매트폴백 ON")'''
if new_status not in cal:
    if cal.count(old_status) != 1:
        raise SystemExit(f"AutoCalibrator status expected 1, got {cal.count(old_status)}")
    cal = cal.replace(old_status, new_status, 1)
    cal_path.write_text(cal, encoding="utf-8")
    changed.append(str(cal_path))

print("V16 integration patch:", ", ".join(changed) if changed else "already current")
