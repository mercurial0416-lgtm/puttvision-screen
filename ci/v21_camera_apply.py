from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        print(f"{label}: already current")
        return False
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 legacy match, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"{label}: applied")
    return True


main = Path("app/src/main/java/com/puttvision/screen/MainActivity.kt")
replace_once(
    main,
    '''        val preview =
            Preview.Builder()
                .build()
                .also {''',
    '''        val previewBuilder = Preview.Builder()
        V21CaptureConsistencyRuntime.attach(previewBuilder)
        val preview =
            previewBuilder
                .build()
                .also {''',
    "normal camera CaptureResult monitor",
)

hfr = Path("app/src/main/java/com/puttvision/screen/HighSpeedCaptureController.kt")
replace_once(
    hfr,
    '''        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }''',
    '''        val previewBuilder = Preview.Builder()
        V21CaptureConsistencyRuntime.attach(previewBuilder)
        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }''',
    "HFR CaptureResult monitor",
)

engine = Path("app/src/main/java/com/puttvision/screen/GameEngine.kt")
replace_once(
    engine,
    '''        val effectiveMetrics = V15CompanionRuntime.fusePrimary(deviceAdjusted)''',
    '''        val effectiveMetrics = V21CaptureConsistencyRuntime.adjust(
            V15CompanionRuntime.fusePrimary(deviceAdjusted)
        )''',
    "capture metadata confidence penalty",
)

systems = Path("app/src/main/java/com/puttvision/screen/ProductSystems.kt")
text = systems.read_text(encoding="utf-8")
changed = False
if "import android.hardware.camera2.CameraCharacteristics" not in text:
    text = text.replace(
        "import android.hardware.camera2.CaptureRequest\n",
        "import android.hardware.camera2.CameraCharacteristics\nimport android.hardware.camera2.CaptureRequest\n",
        1,
    )
    changed = True
if "import androidx.camera.camera2.interop.Camera2CameraInfo" not in text:
    text = text.replace(
        "import androidx.camera.camera2.interop.Camera2CameraControl\n",
        "import androidx.camera.camera2.interop.Camera2CameraControl\nimport androidx.camera.camera2.interop.Camera2CameraInfo\n",
        1,
    )
    changed = True

legacy = '''                    val opts = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                        .build()
                    Camera2CameraControl.from(camera.cameraControl)
                        .setCaptureRequestOptions(opts)'''
replacement = '''                    val options = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                    val availableBanding = runCatching {
                        Camera2CameraInfo.from(camera.cameraInfo)
                            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
                    }.getOrNull().orEmpty()
                    val bandingMode = when {
                        availableBanding.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ) ->
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ
                        availableBanding.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO) ->
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
                        else -> availableBanding.firstOrNull()
                    }
                    if (bandingMode != null) {
                        options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, bandingMode)
                    }
                    Camera2CameraControl.from(camera.cameraControl)
                        .setCaptureRequestOptions(options.build())'''
if replacement not in text:
    if text.count(legacy) != 1:
        raise SystemExit(f"60Hz antibanding: expected 1 legacy match, got {text.count(legacy)}")
    text = text.replace(legacy, replacement, 1)
    changed = True

release_old = '''        activeCamera = null
    }
}'''
release_new = '''        V21CaptureConsistencyRuntime.reset()
        activeCamera = null
    }
}'''
if release_new not in text:
    if text.count(release_old) < 1:
        raise SystemExit("camera consistency reset insertion point missing")
    text = text.replace(release_old, release_new, 1)
    changed = True

if changed:
    systems.write_text(text, encoding="utf-8")
    print("60Hz antibanding and capture reset: applied")
else:
    print("60Hz antibanding and capture reset: already current")
