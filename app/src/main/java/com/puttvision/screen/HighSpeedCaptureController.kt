package com.puttvision.screen

import android.content.Context
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

data class ActiveHfrSession(val fps: Int, val description: String)

class HighSpeedCaptureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val provider: ProcessCameraProvider,
    private val previewView: PreviewView,
    private val callbackExecutor: Executor,
    private val status: (String) -> Unit
) {
    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var activeFile: File? = null
    private var selectedFps: Int = 0

    fun isRecording(): Boolean = recording != null
    fun fps(): Int = selectedFps

    fun bindBest(): ActiveHfrSession? {
        provider.unbindAll()

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val info = provider.getCameraInfo(selector)
        val caps = Recorder.getHighSpeedVideoCapabilities(info)
        if (caps == null) {
            status("CameraX HFR 미지원")
            return null
        }

        val qualities = caps.getSupportedQualities(DynamicRange.SDR)
        if (qualities.isEmpty()) {
            status("HFR 해상도 없음")
            return null
        }

        val quality = when {
            qualities.contains(Quality.FHD) -> Quality.FHD
            qualities.contains(Quality.HD) -> Quality.HD
            else -> qualities.first()
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val rec = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()

        val capture = VideoCapture.withOutput(rec)

        val builder = HighSpeedVideoSessionConfig.Builder(capture)
            .setPreview(preview)
            .setSlowMotionEnabled(false)

        val ranges = info.getSupportedFrameRateRanges(builder.build())
            .filter { it.upper >= 120 }

        if (ranges.isEmpty()) {
            status("HFR FPS range 없음")
            return null
        }

        val chosen = ranges.sortedWith(
            compareByDescending<Range<Int>> {
                when {
                    it.lower == 240 && it.upper == 240 -> 10000
                    it.lower == 120 && it.upper == 120 -> 9000
                    it.lower == it.upper -> 1000 + it.upper
                    else -> it.upper
                }
            }
        ).first()

        builder.setFrameRateRange(chosen)

        return try {
            provider.bindToLifecycle(lifecycleOwner, selector, builder.build())
            recorder = rec
            videoCapture = capture
            selectedFps = chosen.upper
            val desc = "$quality @ ${chosen.upper}fps"
            status("PRECISION ${chosen.upper}fps 준비")
            ActiveHfrSession(chosen.upper, desc)
        } catch (t: Throwable) {
            recorder = null
            videoCapture = null
            selectedFps = 0
            status("HFR 바인딩 실패: ${t.message}")
            null
        }
    }

    fun start(
        onStart: (File, Int) -> Unit,
        onFinalize: (File?, Int, Throwable?) -> Unit
    ) {
        if (recording != null) return
        val rec = recorder ?: return

        val dir = File(context.cacheDir, "puttvision_hfr").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}_${selectedFps}fps.mp4")
        activeFile = file

        val output = FileOutputOptions.Builder(file).build()

        recording = rec.prepareRecording(context, output)
            .start(callbackExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        status("● ${selectedFps}fps RECORDING")
                        onStart(file, selectedFps)
                    }

                    is VideoRecordEvent.Finalize -> {
                        val error = if (event.hasError()) {
                            RuntimeException(
                                "record error=${event.error}; ${event.cause?.message ?: ""}"
                            )
                        } else null

                        val f = activeFile
                        activeFile = null
                        recording = null
                        onFinalize(f, selectedFps, error)
                    }
                }
            }
    }

    fun stop() {
        recording?.stop()
    }

    fun close() {
        try { recording?.stop() } catch (_: Throwable) {}
        recording = null
        activeFile = null
    }
}
