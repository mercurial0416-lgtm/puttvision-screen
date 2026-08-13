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

# 4) TV framing: ball/address point sits around the lower-middle of the screen like a simulator,
# leaving turf below it for the READY pill instead of putting the ball on the bezel edge.
tv_path = Path("app/src/main/java/com/puttvision/screen/V16SimulatorTvView.kt")
tv = tv_path.read_text(encoding="utf-8")
if "val projectionBottomY = h * .74f" not in tv:
    old_block = '''        val horizonY = h * .405f
        val bottomY = h * 1.02f
        val centerX = w * .50f
'''
    new_block = '''        val horizonY = h * .405f
        val projectionBottomY = h * .74f
        val greenBottomY = h * 1.02f
        val centerX = w * .50f
'''
    if tv.count(old_block) != 1:
        raise SystemExit(f"TV framing header expected 1, got {tv.count(old_block)}")
    tv = tv.replace(old_block, new_block, 1)
    tv = tv.replace(
        '''            return bottomY - (bottomY - horizonY) * t''',
        '''            return projectionBottomY - (projectionBottomY - horizonY) * t''',
        1
    )
    tv = tv.replace(
        '''            val t = ((yPix - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)''',
        '''            val t = ((yPix - horizonY) / (projectionBottomY - horizonY)).coerceIn(0f, 1f)''',
        1
    )
    tv = tv.replace('''            cubicTo(w * .16f, h * .60f, w * .04f, h * .87f, -w * .03f, bottomY)
            lineTo(w * 1.03f, bottomY)''', '''            cubicTo(w * .16f, h * .60f, w * .04f, h * .87f, -w * .03f, greenBottomY)
            lineTo(w * 1.03f, greenBottomY)''', 1)
    tv = tv.replace('''            0f, horizonY, 0f, bottomY,''', '''            0f, horizonY, 0f, greenBottomY,''', 1)
    tv_path.write_text(tv, encoding="utf-8")
    changed.append(str(tv_path))

# 5) Load persisted V16 mat geometry and the per-device auto-calibration profile at startup.
main_path = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
main = main_path.read_text(encoding="utf-8")
if "V16DeviceAutoCalibrationRuntime.install(this)" not in main:
    old_runtime = '''        putterProfileStore = PutterProfileStore(this)
        matCalibrationManager = MatCalibrationManager(this)
        voiceCoach = HandsFreeVoiceCoach(this)
'''
    new_runtime = '''        putterProfileStore = PutterProfileStore(this)
        matCalibrationManager = MatCalibrationManager(this)
        V16MatGeometryStore(this)
        V16DeviceAutoCalibrationRuntime.install(this)
        voiceCoach = HandsFreeVoiceCoach(this)
'''
    if main.count(old_runtime) != 1:
        raise SystemExit(f"MainActivity V16 runtime insertion expected 1, got {main.count(old_runtime)}")
    main = main.replace(old_runtime, new_runtime, 1)
    main_path.write_text(main, encoding="utf-8")
    changed.append(str(main_path))

print("V16 integration patch:", ", ".join(changed) if changed else "already current")
