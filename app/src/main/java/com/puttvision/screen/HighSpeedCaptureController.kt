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

data class ActiveHfrSession(val fps: Int, val description: String, val resolution: String)

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
    private val stability = CameraStabilityController()
    private val failureCircuit = V43HfrFailureCircuit()

    fun isRecording(): Boolean = recording != null
    fun fps(): Int = selectedFps

    fun bindBest(maxFps: Int = 240): ActiveHfrSession? {
        if (!failureCircuit.allow()) {
            val seconds = ((failureCircuit.remainingMs() + 999L) / 1000L).coerceAtLeast(1L)
            status("HFR 자동복구 대기 ${seconds}s · NORMAL 사용")
            return null
        }

        stability.release()
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

        val resolution = when (quality) {
            Quality.UHD -> "3840x2160"
            Quality.FHD -> "1920x1080"
            Quality.HD -> "1280x720"
            Quality.SD -> "720x480"
            else -> quality.toString()
        }

        val previewBuilder = Preview.Builder()
        V21CaptureConsistencyRuntime.attach(previewBuilder)
        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val rec = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()

        val capture = VideoCapture.withOutput(rec)

        val builder = HighSpeedVideoSessionConfig.Builder(capture)
            .setPreview(preview)
            .setSlowMotionEnabled(false)

        val cap = maxFps.coerceIn(0, 240)
        if (cap < 120) {
            status("THERMAL SAFE · NORMAL")
            return null
        }
        val ranges = info.getSupportedFrameRateRanges(builder.build())
            .filter { it.upper >= 120 && it.upper <= cap }

        if (ranges.isEmpty()) {
            status("${cap}fps 이하 HFR range 없음")
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
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, builder.build())
            stability.stabilize(camera, previewView)
            recorder = rec
            videoCapture = capture
            selectedFps = chosen.upper
            val desc = "$quality @ ${chosen.upper}fps"
            status("PRECISION ${chosen.upper}fps 준비 · AF/AE LOCK")
            ActiveHfrSession(chosen.upper, desc, resolution)
        } catch (t: Throwable) {
            failureCircuit.recordFailure()
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

        if (!failureCircuit.allow()) {
            val error = IllegalStateException("HFR failure cooldown")
            status("HFR 자동복구 대기 · NORMAL 사용")
            callbackExecutor.execute { onFinalize(null, selectedFps, error) }
            return
        }

        val fpsAtStart = selectedFps
        val dir = File(context.cacheDir, "puttvision_hfr").apply { mkdirs() }
        val storage = V43HfrStorageGuard.prepare(dir)
        if (!storage.ok) {
            failureCircuit.recordFailure()
            status(storage.label)
            callbackExecutor.execute {
                onFinalize(null, fpsAtStart, IllegalStateException(storage.label))
            }
            return
        }

        val file = V43CaptureFileNamer.create(dir, fpsAtStart)
        activeFile = file
        val output = FileOutputOptions.Builder(file).build()

        recording = rec.prepareRecording(context, output)
            .start(callbackExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        V50HfrCaptureClockRuntime.onRecordingStarted(file, fpsAtStart)
                        status("● ${fpsAtStart}fps RECORDING")
                        onStart(file, fpsAtStart)
                    }

                    is VideoRecordEvent.Finalize -> {
                        val recorderError = if (event.hasError()) {
                            RuntimeException(
                                "record error=${event.error}; ${event.cause?.message ?: ""}"
                            )
                        } else null
                        val validFile = file.takeIf { it.exists() && it.length() > 0L }
                        val finalError = recorderError ?: if (validFile == null) {
                            RuntimeException("HFR recording finalized without a valid video")
                        } else null

                        if (activeFile == file) activeFile = null
                        recording = null

                        if (finalError != null) {
                            failureCircuit.recordFailure()
                            runCatching { file.delete() }
                        } else {
                            failureCircuit.recordSuccess()
                        }
                        onFinalize(validFile.takeIf { finalError == null }, fpsAtStart, finalError)
                    }
                }
            }
    }

    fun stop() {
        recording?.stop()
    }

    fun close() {
        val current = recording
        if (current != null) {
            // Finalize is asynchronous. Keep activeFile/recording state intact until the callback
            // so the owner can delete the real temp file instead of receiving a false null file.
            try {
                current.stop()
            } catch (_: Throwable) {
                failureCircuit.recordFailure()
                recording = null
                activeFile?.let { runCatching { it.delete() } }
                activeFile = null
            }
        } else {
            activeFile?.let { runCatching { it.delete() } }
            activeFile = null
        }
        stability.release()
        recorder = null
        videoCapture = null
    }
}
