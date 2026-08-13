package com.puttvision.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class HardwarelessScenario(val label: String) {
    CENTER("센터 정타"),
    PUSH("푸시"),
    PULL("풀"),
    SHORT("짧게"),
    LONG("길게"),
    BREAK_TEST("브레이크"),
    LOW_QUALITY("저품질 폐기")
}

object HardwarelessShotFactory {
    fun metrics(
        scenario: HardwarelessScenario,
        settings: GreenSettings,
        sequence: Int = 0
    ): ShotMetrics {
        val read = GreenReadRuntime.peek(settings)
        val d = settings.holeDistanceM.coerceIn(1.0, 15.0)
        val baseSpeed = read?.recommendedBallSpeedMps ?: (0.72 + d * .18).coerceIn(.72, 3.20)
        val baseAngle = read?.recommendedLaunchAngleDeg ?: 0.0
        val wobble = ((sequence % 5) - 2) * .025
        val (speed, angle, face, path, confidence) = when (scenario) {
            HardwarelessScenario.CENTER -> listOf(baseSpeed + wobble, baseAngle + wobble * .8, 0.05, 0.03, .96)
            HardwarelessScenario.PUSH -> listOf(baseSpeed + .03, baseAngle + 1.35, 1.45, 1.05, .92)
            HardwarelessScenario.PULL -> listOf(baseSpeed + .02, baseAngle - 1.30, -1.35, -1.00, .92)
            HardwarelessScenario.SHORT -> listOf(baseSpeed * .80, baseAngle + .12, .10, .08, .94)
            HardwarelessScenario.LONG -> listOf(baseSpeed * 1.20, baseAngle - .10, -.08, -.05, .94)
            HardwarelessScenario.BREAK_TEST -> listOf(baseSpeed, baseAngle, baseAngle * .18, baseAngle * .12, .95)
            HardwarelessScenario.LOW_QUALITY -> listOf(baseSpeed, baseAngle + 2.4, 2.1, 1.7, .42)
        }
        val head = (speed / 1.52).coerceAtLeast(.35)
        val faceToPath = face - path
        return ShotMetrics(
            ballSpeedMps = speed,
            launchAngleDeg = angle,
            headSpeedMps = head,
            faceAngleDeg = face,
            pathAngleDeg = path,
            faceToPathDeg = faceToPath,
            smash = speed / head,
            impactOffsetMm = when (scenario) {
                HardwarelessScenario.PUSH -> 4.2
                HardwarelessScenario.PULL -> -3.8
                else -> .4
            },
            measuredAtNs = System.nanoTime(),
            backswingMs = 610.0,
            downswingMs = 205.0,
            tempoRatio = 2.98,
            backswingLengthCm = 18.2,
            peakHeadAccelerationMps2 = 8.4,
            rawBallSpeedMps = speed,
            estimatedMatDecelMps2 = .52,
            estimatedMatStimpM = settings.stimpMeters,
            confidence = confidence,
            uncertainty = MeasurementUncertaintyEstimator.synthetic()
        )
    }

    fun replay(metrics: ShotMetrics, fps: Int = 240, frames: Int = 18): ImpactReplay {
        val out = ArrayList<Bitmap>(frames)
        val impact = frames / 2
        val width = 640
        val height = 360
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until frames) {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(Color.rgb(8, 12, 10))

            paint.color = Color.rgb(36, 80, 46)
            c.drawRect(0f, height * .58f, width.toFloat(), height.toFloat(), paint)
            paint.color = Color.argb(95, 220, 255, 228)
            paint.strokeWidth = 2f
            for (lane in -3..3) {
                val x = width * .5f + lane * width * .095f
                c.drawLine(x, height * .56f, x, height.toFloat(), paint)
            }

            val t = (i - impact) / fps.toFloat()
            val angle = Math.toRadians(metrics.launchAngleDeg)
            val after = max(0f, t)
            val ballForward = if (i < impact) 0f else (metrics.ballSpeedMps * after.toDouble() * 380.0).toFloat()
            val ballX = width * .5f + sin(angle).toFloat() * ballForward
            val ballY = height * .73f - cos(angle).toFloat() * ballForward

            val strokePhase = ((i - (impact - 6)).coerceIn(0, 7) / 7f)
            val headY = height * (.86f - .13f * strokePhase)
            val headX = width * .5f + (metrics.pathAngleDeg ?: 0.0).toFloat() * 1.8f * strokePhase
            paint.color = Color.rgb(235, 143, 64)
            c.drawCircle(headX - 30f, headY, 8f, paint)
            paint.color = Color.rgb(68, 146, 255)
            c.drawCircle(headX + 30f, headY, 8f, paint)
            paint.color = Color.rgb(175, 184, 189)
            c.drawRoundRect(RectF(headX - 42f, headY - 8f, headX + 42f, headY + 8f), 8f, 8f, paint)

            paint.color = Color.WHITE
            c.drawCircle(ballX, ballY, 11f, paint)
            if (i == impact) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = Color.rgb(246, 190, 74)
                c.drawCircle(ballX, ballY, 26f, paint)
                paint.style = Paint.Style.FILL
            }

            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 18f
            paint.color = Color.argb(215, 235, 243, 238)
            c.drawText("SIMULATED 240 FPS · FRAME ${i + 1}/$frames", 22f, 30f, paint)
            paint.textSize = 14f
            paint.color = Color.argb(170, 190, 205, 196)
            c.drawText("BALL ${"%.2f".format(metrics.ballSpeedMps)}m/s · START ${"%+.2f".format(metrics.launchAngleDeg)}°", 22f, 54f, paint)
            out += b
        }
        return ImpactReplay(out, fps, impact)
    }
}

data class TvImpactReplayPayload(
    val replay: ImpactReplay,
    val metrics: ShotMetrics,
    val synthetic: Boolean,
    val generation: Long
)

/** One shared replay feed so a physical TV and the on-phone TV preview behave identically. */
object TvImpactReplayBus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(TvImpactReplayPayload?) -> Unit>()
    @Volatile private var current: TvImpactReplayPayload? = null
    @Volatile private var generation = 0L

    fun publish(source: ImpactReplay, metrics: ShotMetrics, synthetic: Boolean) {
        val g = ++generation
        val frames = ArrayList<Bitmap>()
        val wanted = if (source.frames.size <= 18) source.frames.indices.toList() else source.frames.indices.filter { it % 2 == 0 }.take(18)
        var localImpact = 0
        wanted.forEachIndexed { outIndex, srcIndex ->
            val original = source.frames[srcIndex]
            if (original.isRecycled) return@forEachIndexed
            val maxWidth = 640
            val copy = if (original.width > maxWidth) {
                val scale = maxWidth.toFloat() / original.width
                Bitmap.createScaledBitmap(original, maxWidth, (original.height * scale).toInt().coerceAtLeast(1), false)
            } else original.copy(Bitmap.Config.ARGB_8888, false)
            if (srcIndex <= source.impactIndex) localImpact = outIndex
            frames += copy
        }
        if (frames.size < 3) return
        val payload = TvImpactReplayPayload(
            ImpactReplay(frames, source.fps, localImpact.coerceIn(0, frames.lastIndex)),
            metrics,
            synthetic,
            g
        )
        val old = current
        current = payload
        listeners.forEach { it(payload) }
        main.postDelayed({
            if (generation == g) clear()
        }, 4600L)
        if (old != null) recycleLater(old.replay.frames)
    }

    fun current(): TvImpactReplayPayload? = current

    fun subscribe(listener: (TvImpactReplayPayload?) -> Unit) {
        listeners += listener
        listener(current)
    }

    fun unsubscribe(listener: (TvImpactReplayPayload?) -> Unit) {
        listeners -= listener
    }

    fun clear() {
        val old = current
        current = null
        listeners.forEach { it(null) }
        old?.let { recycleLater(it.replay.frames) }
    }

    private fun recycleLater(frames: List<Bitmap>) {
        main.postDelayed({ frames.forEach { if (!it.isRecycled) it.recycle() } }, 320L)
    }
}

class TvImpactReplayView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var payload: TvImpactReplayPayload? = null
    private var frame = 0
    private var loops = 0

    private val listener: (TvImpactReplayPayload?) -> Unit = { next ->
        payload = next
        frame = 0
        loops = 0
        handler.removeCallbacks(tick)
        if (next != null) {
            visibility = VISIBLE
            handler.post(tick)
        } else {
            visibility = GONE
            invalidate()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val data = payload ?: return
            if (data.replay.frames.isEmpty()) return
            invalidate()
            frame++
            if (frame >= data.replay.frames.size) {
                frame = 0
                loops++
                if (loops >= 2) {
                    handler.postDelayed({ listener(null) }, 650L)
                    return
                }
            }
            handler.postDelayed(this, 58L)
        }
    }

    init {
        visibility = GONE
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TvImpactReplayBus.subscribe(listener)
    }

    override fun onDetachedFromWindow() {
        TvImpactReplayBus.unsubscribe(listener)
        handler.removeCallbacksAndMessages(null)
        payload = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = payload ?: return
        val bmp = data.replay.frames.getOrNull(frame) ?: return
        if (bmp.isRecycled || width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Color.argb(185, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, p)

        val card = RectF(w * .11f, h * .09f, w * .89f, h * .88f)
        p.color = Color.rgb(5, 9, 11)
        canvas.drawRoundRect(card, h * .026f, h * .026f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, w * .0014f)
        p.color = Color.argb(145, 78, 209, 121)
        canvas.drawRoundRect(card, h * .026f, h * .026f, p)
        p.style = Paint.Style.FILL

        val media = RectF(card.left + w * .02f, card.top + h * .095f, card.right - w * .02f, card.bottom - h * .16f)
        p.color = Color.BLACK
        canvas.drawRoundRect(media, h * .014f, h * .014f, p)
        val srcAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        val dstAspect = media.width() / media.height().coerceAtLeast(1f)
        val target = if (srcAspect > dstAspect) {
            val fitH = media.width() / srcAspect
            RectF(media.left, media.centerY() - fitH / 2f, media.right, media.centerY() + fitH / 2f)
        } else {
            val fitW = media.height() * srcAspect
            RectF(media.centerX() - fitW / 2f, media.top, media.centerX() + fitW / 2f, media.bottom)
        }
        canvas.drawBitmap(bmp, null, target, p)

        val impact = frame == data.replay.impactIndex
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(18f, w * .015f)
        p.color = if (impact) Color.rgb(246, 190, 74) else Color.WHITE
        canvas.drawText(if (impact) "IMPACT" else if (data.synthetic) "SIM CAMERA REPLAY" else "PRECISION REPLAY", card.left + w * .022f, card.top + h * .057f, p)
        p.textSize = max(10f, w * .0075f)
        p.color = Color.argb(180, 190, 205, 196)
        canvas.drawText("${data.replay.fps} FPS · ${frame + 1}/${data.replay.frames.size}", card.left + w * .022f, card.top + h * .081f, p)

        val m = data.metrics
        val railY = card.bottom - h * .09f
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = max(20f, w * .016f)
        p.color = Color.rgb(78, 209, 121)
        canvas.drawText("BALL ${"%.2f".format(m.ballSpeedMps)}", card.left + w * .025f, railY, p)
        p.color = Color.WHITE
        canvas.drawText("START ${"%+.2f".format(m.launchAngleDeg)}°", card.left + w * .25f, railY, p)
        canvas.drawText("HEAD ${m.headSpeedMps?.let { "%.2f".format(it) } ?: "--"}", card.left + w * .49f, railY, p)
        m.uncertainty?.let {
            p.textSize = max(9f, w * .0072f)
            p.color = Color.argb(175, 190, 205, 196)
            canvas.drawText(it.compact(), card.left + w * .025f, card.bottom - h * .035f, p)
        }
    }
}

/** Deterministic non-Android-hardware smoke runner used by UI lab and unit tests. */
object HardwarelessEngineSmoke {
    data class Result(val completed: Boolean, val holed: Boolean, val distanceCm: Double, val steps: Int)

    fun run(settings: GreenSettings, metrics: ShotMetrics): Result {
        val engine = GameEngine()
        engine.settings.holeDistanceM = settings.holeDistanceM
        engine.settings.stimpMeters = settings.stimpMeters
        engine.settings.sideSlopePct = settings.sideSlopePct
        engine.settings.longSlopePct = settings.longSlopePct
        engine.settings.terrainProfileId = settings.terrainProfileId
        engine.launch(metrics)
        var result: SimResult? = null
        var steps = 0
        while (steps < 2000 && result == null) {
            result = engine.step(.016)
            steps++
        }
        return Result(
            completed = result != null,
            holed = result?.holed == true,
            distanceCm = (result?.distanceToCupM ?: Double.POSITIVE_INFINITY) * 100.0,
            steps = steps
        )
    }
}
