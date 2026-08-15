package com.puttvision.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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
        // Hardwareless UI must consume the same solved GreenRead facts as production, never a
        // second hand-tuned approximation. The synthetic shot itself still remains synthetic.
        V56HardwarelessParityRuntime.publish(read)
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
        val parity = V56HardwarelessParityRuntime.snapshot()
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
            parity?.let { model ->
                paint.color = Color.argb(220, 4, 9, 8)
                c.drawRoundRect(RectF(20f, 72f, 340f, 124f), 12f, 12f, paint)
                paint.color = Color.rgb(255, 211, 64)
                paint.textSize = 15f
                c.drawText("AR READ · ${model.aimText}", 32f, 94f, paint)
                paint.color = Color.rgb(220, 232, 225)
                paint.textSize = 12f
                c.drawText(
                    "${"%.2f".format(model.recommendedBallSpeedMps)} → ${"%.2f".format(model.targetCupSpeedMps)} m/s · ${model.paceText}",
                    32f,
                    114f,
                    paint
                )
            }
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
        if (next != null) { visibility = VISIBLE; handler.post(tick) }
        else { visibility = GONE; invalidate() }
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
                if (loops >= 2) { handler.postDelayed({ listener(null) }, 420L); return }
            }
            handler.postDelayed(this, 52L)
        }
    }
    init { visibility = GONE; setBackgroundColor(Color.TRANSPARENT); isClickable = false }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); TvImpactReplayBus.subscribe(listener) }
    override fun onDetachedFromWindow() { TvImpactReplayBus.unsubscribe(listener); handler.removeCallbacksAndMessages(null); payload = null; super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = payload ?: return
        val bmp = data.replay.frames.getOrNull(frame) ?: return
        if (bmp.isRecycled || width <= 0 || height <= 0) return
        val w = width.toFloat(); val h = height.toFloat()
        if (data.synthetic) {
            V56HardwarelessParityRuntime.snapshot()?.let { drawSyntheticParityHud(canvas, it, w, h) }
        }
        // Screen-putting style PiP: green and rolling ball remain fully visible underneath.
        val cardW = w * .285f
        val cardH = h * .285f
        val right = w * .965f
        val top = h * .105f
        val card = RectF(right - cardW, top, right, top + cardH)
        p.color = Color.argb(225, 4, 9, 8)
        canvas.drawRoundRect(card, h * .018f, h * .018f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, w * .0012f); p.color = Color.argb(150, 78, 209, 121)
        canvas.drawRoundRect(card, h * .018f, h * .018f, p); p.style = Paint.Style.FILL
        val media = RectF(card.left + w*.009f, card.top + h*.052f, card.right - w*.009f, card.bottom - h*.067f)
        val srcAspect = bmp.width.toFloat()/bmp.height.coerceAtLeast(1)
        val dstAspect = media.width()/media.height().coerceAtLeast(1f)
        val target = if (srcAspect > dstAspect) {
            val fitH=media.width()/srcAspect; RectF(media.left,media.centerY()-fitH/2,media.right,media.centerY()+fitH/2)
        } else { val fitW=media.height()*srcAspect; RectF(media.centerX()-fitW/2,media.top,media.centerX()+fitW/2,media.bottom) }
        canvas.drawBitmap(bmp,null,target,p)
        p.typeface=Typeface.DEFAULT_BOLD; p.textSize=max(11f,w*.0074f); p.color=if(frame==data.replay.impactIndex) Color.rgb(246,190,74) else Color.WHITE
        canvas.drawText(if(frame==data.replay.impactIndex) "IMPACT" else "IMPACT REPLAY",card.left+w*.012f,card.top+h*.034f,p)
        p.textSize=max(9f,w*.0058f); p.color=Color.rgb(78,209,121)
        canvas.drawText("BALL ${"%.2f".format(data.metrics.ballSpeedMps)} · START ${"%+.2f".format(data.metrics.launchAngleDeg)}°",card.left+w*.012f,card.bottom-h*.025f,p)
    }

    private fun drawSyntheticParityHud(canvas: Canvas, model: V56GreenReadPresentation, w: Float, h: Float) {
        val panel = RectF(w * .025f, h * .105f, w * .345f, h * .345f)
        p.style = Paint.Style.FILL
        p.color = Color.argb(222, 4, 9, 8)
        canvas.drawRoundRect(panel, h * .020f, h * .020f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2f, w * .0011f)
        p.color = Color.argb(165, 255, 211, 64)
        canvas.drawRoundRect(panel, h * .020f, h * .020f, p)
        p.style = Paint.Style.FILL

        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f, w * .0062f)
        p.color = Color.rgb(255, 211, 64)
        canvas.drawText("NO HARDWARE · SAME GREEN READ", panel.left + w*.012f, panel.top + h*.035f, p)
        p.textSize = max(15f, w * .0102f)
        p.color = Color.WHITE
        canvas.drawText(model.aimText, panel.left + w*.012f, panel.top + h*.078f, p)
        p.textSize = max(9f, w * .0058f)
        p.color = Color.rgb(78, 209, 121)
        canvas.drawText(
            "BALL ${"%.2f".format(model.recommendedBallSpeedMps)} → CUP ${"%.2f".format(model.targetCupSpeedMps)} m/s",
            panel.left + w*.012f,
            panel.top + h*.112f,
            p
        )
        p.color = Color.argb(220, 220, 232, 225)
        canvas.drawText(model.paceText, panel.left + w*.012f, panel.top + h*.143f, p)

        val graph = RectF(panel.left + w*.018f, panel.top + h*.158f, panel.right - w*.018f, panel.bottom - h*.018f)
        drawReadTrail(canvas, graph, model)
    }

    private fun drawReadTrail(canvas: Canvas, box: RectF, model: V56GreenReadPresentation) {
        val trail = model.trail
        if (trail.size < 2 || box.width() <= 1f || box.height() <= 1f) return
        val xs = trail.map { it.first }.filter(Double::isFinite)
        val ys = trail.map { it.second }.filter(Double::isFinite)
        if (xs.isEmpty() || ys.isEmpty()) return
        val minX = xs.minOrNull() ?: return
        val maxX = xs.maxOrNull() ?: return
        val minY = ys.minOrNull() ?: return
        val maxY = ys.maxOrNull() ?: return
        val spanX = max(0.10, maxX - minX)
        val spanY = max(0.10, maxY - minY)
        fun map(point: Pair<Double, Double>): Pair<Float, Float> {
            val x = box.left + (((point.first - minX) / spanX).coerceIn(0.0, 1.0) * box.width()).toFloat()
            val y = box.bottom - (((point.second - minY) / spanY).coerceIn(0.0, 1.0) * box.height()).toFloat()
            return x to y
        }

        val path = Path()
        trail.forEachIndexed { index, point ->
            val mapped = map(point)
            if (index == 0) path.moveTo(mapped.first, mapped.second) else path.lineTo(mapped.first, mapped.second)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = max(2f, width * .0015f)
        p.color = Color.rgb(255, 211, 64)
        canvas.drawPath(path, p)
        p.style = Paint.Style.FILL

        model.apexIndex?.let { index ->
            trail.getOrNull(index)?.let { point ->
                val mapped = map(point)
                p.color = Color.WHITE
                canvas.drawCircle(mapped.first, mapped.second, max(4f, width*.0032f), p)
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = max(8f, width*.0051f)
                p.color = Color.rgb(255, 211, 64)
                canvas.drawText("APEX", mapped.first + width*.006f, mapped.second - width*.004f, p)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
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
