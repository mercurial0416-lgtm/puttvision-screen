package com.puttvision.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Asset-free procedural feedback: impact, subtle roll texture, cup drop and lip-out rim. */
object V22AudioRuntime {
    private const val PREF = "puttvision_v22_audio"
    private const val RATE = 24000
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile var enabled: Boolean = true
        private set

    fun install(context: Context) {
        enabled = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("enabled", true)
    }

    fun toggle(context: Context): Boolean {
        enabled = !enabled
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (enabled) playImpact(.7)
        return enabled
    }

    fun launch(ballSpeedMps: Double) {
        if (!enabled) return
        playImpact((.55 + ballSpeedMps * .16).coerceIn(.45, .95))
        val duration = (.25 + ballSpeedMps * .20).coerceIn(.28, .75)
        playAsync(noiseRoll(duration, (.018 + ballSpeedMps * .006).coerceIn(.015, .038)))
    }

    fun result(result: SimResult) {
        if (!enabled) return
        when {
            result.holed -> playAsync(cupDrop())
            result.lipOut -> playAsync(lipOut())
            result.distanceToCupM <= .25 -> playAsync(nearStop())
        }
    }

    private fun playImpact(gain: Double) {
        val seconds = .075
        val n = (RATE * seconds).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val envelope = exp(-t * 48.0)
            val click = sin(2.0 * PI * 1450.0 * t) * .56 + sin(2.0 * PI * 2650.0 * t) * .22
            pcm[i] = (click * envelope * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playAsync(pcm)
    }

    private fun noiseRoll(seconds: Double, gain: Double): ShortArray {
        val n = (RATE * seconds).toInt()
        val pcm = ShortArray(n)
        var low = 0.0
        val random = Random(2206)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val raw = random.nextDouble(-1.0, 1.0)
            low = low * .82 + raw * .18
            val fade = (1.0 - t / seconds).coerceIn(0.0, 1.0)
            val texture = low * .65 + sin(2.0 * PI * 88.0 * t) * .12
            pcm[i] = (texture * gain * fade * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    private fun cupDrop(): ShortArray {
        val seconds = .30
        val n = (RATE * seconds).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val e1 = exp(-t * 17.0)
            val e2 = if (t > .07) exp(-(t - .07) * 22.0) else 0.0
            val s = sin(2 * PI * 710 * t) * .36 * e1 + sin(2 * PI * 1210 * t) * .22 * e1 + sin(2 * PI * 430 * (t - .07)) * .28 * e2
            pcm[i] = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    private fun lipOut(): ShortArray {
        val seconds = .22
        val n = (RATE * seconds).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val e = exp(-t * 20.0)
            val s = (sin(2 * PI * 980 * t) * .31 + sin(2 * PI * 1480 * t) * .17) * e
            pcm[i] = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    private fun nearStop(): ShortArray {
        val seconds = .11
        val n = (RATE * seconds).toInt()
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val s = sin(2 * PI * 390 * t) * exp(-t * 33.0) * .10
            pcm[i] = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    private fun playAsync(pcm: ShortArray) {
        if (!enabled || pcm.isEmpty()) return
        executor.execute {
            runCatching {
                val bytes = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bytes.coerceAtLeast(2048))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val waitMs = ((pcm.size * 1000L / RATE) + 80L).coerceAtMost(1200L)
                Thread.sleep(waitMs)
                track.stop()
                track.release()
            }
        }
    }
}
