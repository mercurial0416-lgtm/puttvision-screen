package com.puttvision.screen

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V128 replaces the toy-like flat V124 scene with one coherent screen-golf renderer.
 * It uses only original procedural geometry/shading and existing PuttVision physics.
 */
data class V128WorldPlan(
    val greenCols: Int,
    val greenRows: Int,
    val roughCols: Int,
    val roughRows: Int,
    val treeCount: Int,
    val movingFrameMs: Long,
    val idleFrameMs: Long,
    val fogNearM: Float,
    val fogFarM: Float
)

object V128WorldPlanner {
    fun plan(tier: V24RenderTier, distanceM: Double): V128WorldPlan {
        val d = distanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val longCourse = d >= 12.0
        return when (tier) {
            V24RenderTier.HIGH -> V128WorldPlan(
                greenCols = if (longCourse) 42 else 48,
                greenRows = if (longCourse) 116 else 104,
                roughCols = 24,
                roughRows = if (longCourse) 72 else 60,
                treeCount = 30,
                movingFrameMs = 16L,
                idleFrameMs = 70L,
                fogNearM = 18f,
                fogFarM = (d + 42.0).toFloat().coerceIn(34f, 68f)
            )
            V24RenderTier.BALANCED -> V128WorldPlan(
                greenCols = 34,
                greenRows = if (longCourse) 90 else 80,
                roughCols = 18,
                roughRows = if (longCourse) 56 else 48,
                treeCount = 22,
                movingFrameMs = 20L,
                idleFrameMs = 84L,
                fogNearM = 16f,
                fogFarM = (d + 38.0).toFloat().coerceIn(31f, 64f)
            )
            V24RenderTier.PERFORMANCE -> V128WorldPlan(
                greenCols = 24,
                greenRows = if (longCourse) 64 else 56,
                roughCols = 12,
                roughRows = if (longCourse) 40 else 34,
                treeCount = 12,
                movingFrameMs = 28L,
                idleFrameMs = 116L,
                fogNearM = 14f,
                fogFarM = (d + 32.0).toFloat().coerceIn(28f, 58f)
            )
        }
    }

    fun visualBallRadius(progressRaw: Double): Float {
        val p = progressRaw.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        return (0.026 + p * 0.012).toFloat().coerceIn(.026f, .038f)
    }
}

data class V128CameraFrame(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val lookX: Float,
    val lookY: Float,
    val lookZ: Float,
    val fovDeg: Float
)

object V128ScreenGolfCameraPlanner {
    fun plan(
        distanceM: Double,
        startX: Double,
        startY: Double,
        ballX: Double,
        ballY: Double,
        running: Boolean,
        result: SimResult?
    ): V128CameraFrame {
        val d = distanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val bx = ballX.takeIf { it.isFinite() } ?: startX
        val by = ballY.takeIf { it.isFinite() } ?: startY
        val progress = (by / d).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        return when {
            result != null && (result.holed || result.lipOut || result.distanceToCupM < .70) ->
                V128CameraFrame(
                    eyeX = .88f,
                    eyeY = (d - 1.18).toFloat(),
                    eyeZ = .58f,
                    lookX = 0f,
                    lookY = d.toFloat(),
                    lookZ = .035f,
                    fovDeg = 39f
                )
            running && progress > .76 ->
                V128CameraFrame(
                    eyeX = (bx + .58).toFloat(),
                    eyeY = (by - 1.08).toFloat(),
                    eyeZ = .64f,
                    lookX = (bx * .18).toFloat(),
                    lookY = min(d, by + 1.45).toFloat(),
                    lookZ = .045f,
                    fovDeg = 40.5f
                )
            running ->
                V128CameraFrame(
                    eyeX = (bx * .08).toFloat(),
                    eyeY = (by - 2.18).toFloat(),
                    eyeZ = .92f,
                    lookX = (bx * .24).toFloat(),
                    lookY = min(d, by + max(2.15, d * .20)).toFloat(),
                    lookZ = .055f,
                    fovDeg = (44.5 - progress * 2.5).toFloat()
                )
            else ->
                V128CameraFrame(
                    eyeX = startX.toFloat(),
                    eyeY = (startY - 2.42).toFloat(),
                    eyeZ = 1.14f,
                    lookX = (startX * .20).toFloat(),
                    lookY = min(d, startY + max(2.9, d * .58)).toFloat(),
                    lookZ = .05f,
                    fovDeg = 45.5f
                )
        }
    }
}

object V128ScreenGolfWorldFactory {
    fun create(context: Context, engine: GameEngine): View {
        val supported = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
        }.getOrDefault(false)
        return if (supported) V128ScreenGolfWorldStage(context, engine)
        else V124ScreenGolfWorldFactory.create(context, engine)
    }
}

class V128ScreenGolfWorldStage(context: Context, engine: GameEngine) : FrameLayout(context) {
    private val gl = V128ScreenGolfGlView(context, engine)

    init {
        setBackgroundColor(Color.rgb(102, 178, 224))
        addView(gl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) runCatching { gl.onResume() } else runCatching { gl.onPause() }
    }

    override fun onDetachedFromWindow() {
        runCatching { gl.onPause() }
        super.onDetachedFromWindow()
    }
}

private class V128ScreenGolfGlView(
    context: Context,
    private val engine: GameEngine
) : GLSurfaceView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            if (!ticking || !isAttachedToWindow) return
            requestRender()
            val tier = V24TvQualityRuntime.snapshot(context.applicationContext).tier
            val plan = V128WorldPlanner.plan(tier, engine.settings.holeDistanceM)
            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            handler.postDelayed(this, if (moving) plan.movingFrameMs else plan.idleFrameMs)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(V128ScreenGolfRenderer(context.applicationContext, engine))
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticking = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        ticking = false
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}

private data class V128WorldKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val tier: V24RenderTier
)

private class V128Mesh(data: FloatArray) {
    val count: Int = data.size / 10
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(data); position(0) }
}

private class V128Builder {
    private val out = ArrayList<Float>(12288)

    fun vertex(p: FloatArray, n: FloatArray, color: FloatArray) {
        out += p[0]; out += p[1]; out += p[2]
        out += n[0]; out += n[1]; out += n[2]
        out += color[0]; out += color[1]; out += color[2]; out += color.getOrElse(3) { 1f }
    }

    fun tri(a: FloatArray, b: FloatArray, c: FloatArray, color: FloatArray, normal: FloatArray = floatArrayOf(0f, 0f, 1f)) {
        vertex(a, normal, color); vertex(b, normal, color); vertex(c, normal, color)
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, color: FloatArray, normal: FloatArray = floatArrayOf(0f, 0f, 1f)) {
        tri(a, b, c, color, normal)
        tri(a, c, d, color, normal)
    }

    fun build(): V128Mesh = V128Mesh(out.toFloatArray())
}

private class V128ScreenGolfRenderer(
    private val context: Context,
    private val engine: GameEngine
) : GLSurfaceView.Renderer {
    private var program = 0
    private var aPos = -1
    private var aNormal = -1
    private var aColor = -1
    private var uMvp = -1
    private var uLight = -1
    private var uCamera = -1
    private var uFogColor = -1
    private var uFogNear = -1
    private var uFogFar = -1
    private var uMaterial = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private var widthPx = 1
    private var heightPx = 1

    private var key: V128WorldKey? = null
    private var sky: V128Mesh? = null
    private var mountainsBack: V128Mesh? = null
    private var mountainsFront: V128Mesh? = null
    private var rough: V128Mesh? = null
    private var fringe: V128Mesh? = null
    private var green: V128Mesh? = null
    private var trees: V128Mesh? = null

    private val eye = floatArrayOf(0f, -2.42f, 1.14f)
    private val look = floatArrayOf(0f, 2.9f, .05f)
    private var currentFov = 45.5f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.42f, .71f, .88f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = link(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
        uCamera = GLES20.glGetUniformLocation(program, "uCamera")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogNear = GLES20.glGetUniformLocation(program, "uFogNear")
        uFogFar = GLES20.glGetUniformLocation(program, "uFogFar")
        uMaterial = GLES20.glGetUniformLocation(program, "uMaterial")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, widthPx, heightPx)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val settings = engine.settings.copy()
        ensureScene(settings)
        updateCamera(settings)

        val aspect = widthPx.toFloat() / heightPx.toFloat()
        Matrix.perspectiveM(projection, 0, currentFov, aspect, .035f, 90f)
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], look[0], look[1], look[2], 0f, 0f, 1f)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        V25FlagProjectionRuntime.publish(mvp, widthPx, heightPx)

        val tier = V24TvQualityRuntime.snapshot(context).tier
        val plan = V128WorldPlanner.plan(tier, settings.holeDistanceM)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform3f(uLight, -.34f, -.42f, .84f)
        GLES20.glUniform3f(uCamera, eye[0], eye[1], eye[2])
        GLES20.glUniform3f(uFogColor, .54f, .73f, .82f)
        GLES20.glUniform1f(uFogNear, plan.fogNearM)
        GLES20.glUniform1f(uFogFar, plan.fogFarM)

        sky?.let { draw(it, MAT_SKY) }
        mountainsBack?.let { draw(it, MAT_MOUNTAIN) }
        mountainsFront?.let { draw(it, MAT_MOUNTAIN) }
        rough?.let { draw(it, MAT_ROUGH) }
        fringe?.let { draw(it, MAT_FRINGE) }
        green?.let { draw(it, MAT_GREEN) }
        trees?.let { draw(it, MAT_TREE) }
        drawCupFlag(settings)
        drawBall(settings)
    }

    private fun ensureScene(settings: GreenSettings) {
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val d = safeDistance(settings)
        val next = V128WorldKey(
            profile = settings.terrainProfileId,
            distance100 = (d * 100).toInt(),
            side100 = ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            long100 = ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            customHash = V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier = tier
        )
        if (next == key) return
        key = next
        val plan = V128WorldPlanner.plan(tier, d)
        sky = buildSky(d)
        mountainsBack = buildMountains(d, 34f, 1.8f, .11f, .28f, .20f, 28)
        mountainsFront = buildMountains(d, 25f, 1.25f, .10f, .34f, .19f, 34)
        rough = buildRough(settings, plan)
        fringe = buildGreenSurface(settings, plan, true)
        green = buildGreenSurface(settings, plan, false)
        trees = buildTrees(settings, plan.treeCount)
    }

    private fun safeDistance(settings: GreenSettings): Double =
        settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0

    private fun buildSky(distanceM: Double): V128Mesh {
        val y = (distanceM + 48.0).toFloat()
        val b = V128Builder()
        b.quad(
            floatArrayOf(-55f, y, -1.5f),
            floatArrayOf(55f, y, -1.5f),
            floatArrayOf(55f, y, 18f),
            floatArrayOf(-55f, y, 18f),
            floatArrayOf(.46f, .73f, .90f, 1f),
            floatArrayOf(0f, -1f, 0f)
        )
        return b.build()
    }

    private fun buildMountains(
        distanceM: Double,
        offsetM: Float,
        baseHeight: Float,
        r: Float,
        g: Float,
        bl: Float,
        segments: Int
    ): V128Mesh {
        val y = (distanceM + offsetM).toFloat()
        val bld = V128Builder()
        val n = segments.coerceIn(12, 48)
        val span = 48.0
        for (i in 0 until n) {
            val x0 = (-span / 2.0 + span * i / n).toFloat()
            val x1 = (-span / 2.0 + span * (i + 1) / n).toFloat()
            val z0 = baseHeight + (.64 * sin(i * .59) + .26 * sin(i * 1.47)).toFloat()
            val z1 = baseHeight + (.64 * sin((i + 1) * .59) + .26 * sin((i + 1) * 1.47)).toFloat()
            val color = floatArrayOf(r * (if (i % 2 == 0) 1f else .91f), g, bl, 1f)
            bld.quad(
                floatArrayOf(x0, y, -.15f),
                floatArrayOf(x1, y, -.15f),
                floatArrayOf(x1, y, z1.coerceAtLeast(.45f)),
                floatArrayOf(x0, y, z0.coerceAtLeast(.45f)),
                color,
                floatArrayOf(0f, -1f, .16f)
            )
        }
        return bld.build()
    }

    private fun buildRough(settings: GreenSettings, plan: V128WorldPlan): V128Mesh {
        val d = safeDistance(settings)
        val length = max(15.0, d + 18.0)
        val cols = plan.roughCols.coerceAtLeast(8)
        val rows = plan.roughRows.coerceAtLeast(18)
        val bld = V128Builder()
        val half = 15.0
        for (row in 0 until rows) {
            val y0 = -4.0 + (length + 4.0) * row / rows
            val y1 = -4.0 + (length + 4.0) * (row + 1) / rows
            for (col in 0 until cols) {
                val x0 = -half + 2.0 * half * col / cols
                val x1 = -half + 2.0 * half * (col + 1) / cols
                fun z(x: Double, y: Double): Float {
                    val terrain = if (y >= 0.0 && y <= d * 1.45) GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.5, 4.5), y).toFloat() else 0f
                    val mound = (.018 * sin(x * .74 + y * .18) + .012 * sin(y * .43 - x * .31)).toFloat()
                    return terrain - .052f + mound
                }
                val a = floatArrayOf(x0.toFloat(), y0.toFloat(), z(x0, y0))
                val bb = floatArrayOf(x1.toFloat(), y0.toFloat(), z(x1, y0))
                val c = floatArrayOf(x1.toFloat(), y1.toFloat(), z(x1, y1))
                val dd = floatArrayOf(x0.toFloat(), y1.toFloat(), z(x0, y1))
                val shade = (0.88 + 0.10 * sin(row * .52 + col * .31)).toFloat()
                bld.quad(a, bb, c, dd, floatArrayOf(.16f * shade, .43f * shade, .13f * shade, 1f))
            }
        }
        return bld.build()
    }

    private fun buildGreenSurface(settings: GreenSettings, plan: V128WorldPlan, fringeMode: Boolean): V128Mesh {
        val d = safeDistance(settings)
        val length = max(4.4, d * 1.42)
        val cols = plan.greenCols.coerceAtLeast(12)
        val rows = plan.greenRows.coerceAtLeast(24)
        val bld = V128Builder()

        fun halfWidth(y: Double): Double {
            val t = (y / length).coerceIn(0.0, 1.0)
            val base = max(1.72, d * .235)
            val shoulder = .88 + .11 * sin(PI * t) + .055 * sin(t * 3.0 * PI + .55)
            val irregular = .045 * sin(y * .73) + .028 * sin(y * 1.57 + .8)
            return (base * shoulder + irregular + if (fringeMode) .34 else 0.0).coerceAtLeast(1.4)
        }

        fun point(x: Double, y: Double): FloatArray {
            val z = GreenTerrain.effectiveHeightAt(settings, x, y).toFloat() + if (fringeMode) .001f else .007f
            return floatArrayOf(x.toFloat(), y.toFloat(), z)
        }

        fun normalAt(x: Double, y: Double): FloatArray {
            val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
            var nx = (-slope.sidePct / 100.0).toFloat()
            var ny = (-slope.longPct / 100.0).toFloat()
            var nz = 1f
            val mag = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
            nx /= mag; ny /= mag; nz /= mag
            return floatArrayOf(nx, ny, nz)
        }

        for (row in 0 until rows) {
            val y0 = length * row / rows
            val y1 = length * (row + 1) / rows
            val hw0 = halfWidth(y0)
            val hw1 = halfWidth(y1)
            for (col in 0 until cols) {
                val x00 = -hw0 + 2.0 * hw0 * col / cols
                val x10 = -hw0 + 2.0 * hw0 * (col + 1) / cols
                val x01 = -hw1 + 2.0 * hw1 * col / cols
                val x11 = -hw1 + 2.0 * hw1 * (col + 1) / cols
                val cx = (x00 + x11) * .5
                val cy = (y0 + y1) * .5
                val n = normalAt(cx, cy)
                val color = if (fringeMode) floatArrayOf(.205f, .555f, .165f, 1f) else floatArrayOf(.285f, .665f, .205f, 1f)
                bld.quad(point(x00, y0), point(x10, y0), point(x11, y1), point(x01, y1), color, n)
            }
        }
        return bld.build()
    }

    private fun buildTrees(settings: GreenSettings, count: Int): V128Mesh {
        val d = safeDistance(settings)
        val bld = V128Builder()
        val n = count.coerceIn(6, 36)
        for (i in 0 until n) {
            val side = if (i % 2 == 0) -1f else 1f
            val y = (-.5 + (i + 1) * (d + 14.0) / (n + 1)).toFloat()
            val x = side * (4.25f + (i % 5) * .74f + sin(i * 1.31).toFloat() * .34f)
            val trunkTop = .36f + (i % 3) * .055f
            val crownTop = 1.28f + (i % 6) * .09f
            val crownHalf = .38f + (i % 4) * .045f
            val trunk = floatArrayOf(.31f, .205f, .095f, 1f)
            val leafA = floatArrayOf(.085f, .30f + (i % 3) * .025f, .105f, 1f)
            val leafB = floatArrayOf(.105f, .37f + (i % 2) * .025f, .12f, 1f)
            bld.quad(
                floatArrayOf(x - .035f, y, -.045f), floatArrayOf(x + .035f, y, -.045f),
                floatArrayOf(x + .035f, y, trunkTop), floatArrayOf(x - .035f, y, trunkTop), trunk, floatArrayOf(0f, -1f, .1f)
            )
            bld.tri(floatArrayOf(x - crownHalf, y, trunkTop * .70f), floatArrayOf(x + crownHalf, y, trunkTop * .70f), floatArrayOf(x, y, crownTop), leafA, floatArrayOf(0f, -1f, .25f))
            bld.tri(floatArrayOf(x, y - crownHalf * .62f, trunkTop * .72f), floatArrayOf(x, y + crownHalf * .62f, trunkTop * .72f), floatArrayOf(x, y, crownTop * .97f), leafB, floatArrayOf(side, 0f, .25f))
            bld.tri(floatArrayOf(x - crownHalf * .78f, y + .04f, trunkTop * .84f), floatArrayOf(x + crownHalf * .78f, y + .04f, trunkTop * .84f), floatArrayOf(x, y + .04f, crownTop * .84f), leafB, floatArrayOf(0f, -1f, .28f))
        }
        return bld.build()
    }

    private fun updateCamera(settings: GreenSettings) {
        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val animated = TvInstantRollRuntime.displayPosition(state)
        val bx = animated?.first ?: state?.x ?: start.first
        val by = animated?.second ?: state?.y ?: start.second
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val frame = V128ScreenGolfCameraPlanner.plan(
            distanceM = safeDistance(settings),
            startX = start.first,
            startY = start.second,
            ballX = bx,
            ballY = by,
            running = running,
            result = engine.lastResult
        )
        val k = if (running) .095f else .16f
        eye[0] += (frame.eyeX - eye[0]) * k
        eye[1] += (frame.eyeY - eye[1]) * k
        eye[2] += (frame.eyeZ - eye[2]) * k
        look[0] += (frame.lookX - look[0]) * k
        look[1] += (frame.lookY - look[1]) * k
        look[2] += (frame.lookZ - look[2]) * k
        currentFov += (frame.fovDeg - currentFov) * k
    }

    private fun drawCupFlag(settings: GreenSettings) {
        val d = safeDistance(settings).toFloat()
        val ground = GreenTerrain.effectiveHeightAt(settings, 0.0, d.toDouble()).toFloat()
        draw(circleMesh(0f, d, ground + .008f, .056f, floatArrayOf(.018f, .021f, .017f, 1f), 38), MAT_CUP)
        draw(ringMesh(0f, d, ground + .010f, .056f, .066f, floatArrayOf(.90f, .91f, .87f, 1f), 38), MAT_CUP)
        draw(boxMesh(-.006f, d - .006f, ground + .012f, .006f, d + .006f, ground + 1.72f, floatArrayOf(.94f, .95f, .92f, 1f)), MAT_PIN)
        val flag = V128Builder().apply {
            tri(
                floatArrayOf(.008f, d, ground + 1.70f),
                floatArrayOf(.46f, d, ground + 1.56f),
                floatArrayOf(.008f, d, ground + 1.42f),
                floatArrayOf(.91f, .105f, .09f, 1f),
                floatArrayOf(0f, -1f, .06f)
            )
        }.build()
        draw(flag, MAT_PIN)
    }

    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val p = TvInstantRollRuntime.displayPosition(state) ?: state?.let { it.x to it.y } ?: start
        if (!p.first.isFinite() || !p.second.isFinite()) return
        val d = safeDistance(settings)
        val progress = (p.second / d).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        val radius = V128WorldPlanner.visualBallRadius(progress)
        val z = GreenTerrain.effectiveHeightAt(settings, p.first, p.second).toFloat()
        val shadowR = radius * 1.42f
        draw(ellipseMesh((p.first + .018).toFloat(), (p.second + .012).toFloat(), z + .009f, shadowR, shadowR * .58f, floatArrayOf(.025f, .035f, .022f, .34f), 28), MAT_SHADOW)
        draw(sphereMesh(p.first.toFloat(), p.second.toFloat(), z + radius, radius, floatArrayOf(.985f, .988f, .972f, 1f)), MAT_BALL)
    }

    private fun draw(mesh: V128Mesh, material: Float) {
        GLES20.glUniform1f(uMaterial, material)
        val stride = 10 * 4
        mesh.buffer.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, mesh.buffer)
        mesh.buffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, mesh.buffer)
        mesh.buffer.position(6)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, mesh.buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count)
    }

    private fun circleMesh(cx: Float, cy: Float, cz: Float, r: Float, color: FloatArray, steps: Int): V128Mesh =
        ellipseMesh(cx, cy, cz, r, r, color, steps)

    private fun ellipseMesh(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, color: FloatArray, steps: Int): V128Mesh {
        val bld = V128Builder()
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            bld.tri(
                floatArrayOf(cx, cy, cz),
                floatArrayOf(cx + cos(a0).toFloat() * rx, cy + sin(a0).toFloat() * ry, cz),
                floatArrayOf(cx + cos(a1).toFloat() * rx, cy + sin(a1).toFloat() * ry, cz),
                color
            )
        }
        return bld.build()
    }

    private fun ringMesh(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, color: FloatArray, steps: Int): V128Mesh {
        val bld = V128Builder()
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = floatArrayOf(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, cz)
            val i1 = floatArrayOf(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, cz)
            val o0 = floatArrayOf(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, cz)
            val o1 = floatArrayOf(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, cz)
            bld.quad(i0, o0, o1, i1, color)
        }
        return bld.build()
    }

    private fun sphereMesh(cx: Float, cy: Float, cz: Float, r: Float, color: FloatArray): V128Mesh {
        val bld = V128Builder()
        val latN = 11
        val lonN = 18
        fun point(lat: Int, lon: Int): Pair<FloatArray, FloatArray> {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            val nx = (cos(phi) * cos(theta)).toFloat()
            val ny = (cos(phi) * sin(theta)).toFloat()
            val nz = sin(phi).toFloat()
            return floatArrayOf(cx + nx * r, cy + ny * r, cz + nz * r) to floatArrayOf(nx, ny, nz)
        }
        for (lat in 0 until latN) for (lon in 0 until lonN) {
            val nextLon = (lon + 1) % lonN
            val a = point(lat, lon)
            val bb = point(lat + 1, lon)
            val c = point(lat + 1, nextLon)
            val d = point(lat, nextLon)
            bld.tri(a.first, bb.first, c.first, color, normalizedAverage(a.second, bb.second, c.second))
            bld.tri(a.first, c.first, d.first, color, normalizedAverage(a.second, c.second, d.second))
        }
        return bld.build()
    }

    private fun normalizedAverage(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
        var x = (a[0] + b[0] + c[0]) / 3f
        var y = (a[1] + b[1] + c[1]) / 3f
        var z = (a[2] + b[2] + c[2]) / 3f
        val mag = sqrt(x * x + y * y + z * z).coerceAtLeast(.001f)
        x /= mag; y /= mag; z /= mag
        return floatArrayOf(x, y, z)
    }

    private fun boxMesh(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray): V128Mesh {
        val bld = V128Builder()
        val p000 = floatArrayOf(x0, y0, z0); val p100 = floatArrayOf(x1, y0, z0)
        val p110 = floatArrayOf(x1, y1, z0); val p010 = floatArrayOf(x0, y1, z0)
        val p001 = floatArrayOf(x0, y0, z1); val p101 = floatArrayOf(x1, y0, z1)
        val p111 = floatArrayOf(x1, y1, z1); val p011 = floatArrayOf(x0, y1, z1)
        bld.quad(p000, p100, p110, p010, color, floatArrayOf(0f, 0f, -1f))
        bld.quad(p001, p011, p111, p101, color, floatArrayOf(0f, 0f, 1f))
        bld.quad(p000, p001, p101, p100, color, floatArrayOf(0f, -1f, 0f))
        bld.quad(p010, p110, p111, p011, color, floatArrayOf(0f, 1f, 0f))
        bld.quad(p000, p010, p011, p001, color, floatArrayOf(-1f, 0f, 0f))
        bld.quad(p100, p101, p111, p110, color, floatArrayOf(1f, 0f, 0f))
        return bld.build()
    }

    private fun link(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val msg = GLES20.glGetShaderInfoLog(id)
                GLES20.glDeleteShader(id)
                throw IllegalStateException("shader compile failed: $msg")
            }
            return id
        }
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vs)
        GLES20.glAttachShader(id, fs)
        GLES20.glLinkProgram(id)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (ok[0] == 0) {
            val msg = GLES20.glGetProgramInfoLog(id)
            GLES20.glDeleteProgram(id)
            throw IllegalStateException("program link failed: $msg")
        }
        return id
    }

    companion object {
        private const val MAT_GREEN = 0f
        private const val MAT_FRINGE = 1f
        private const val MAT_ROUGH = 2f
        private const val MAT_TREE = 3f
        private const val MAT_MOUNTAIN = 4f
        private const val MAT_CUP = 5f
        private const val MAT_BALL = 6f
        private const val MAT_SHADOW = 7f
        private const val MAT_SKY = 8f
        private const val MAT_PIN = 9f

        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec4 vBaseColor;
            varying float vDepth;
            void main() {
                vWorld = aPosition;
                vNormal = aNormal;
                vBaseColor = aColor;
                vec4 p = uMvp * vec4(aPosition, 1.0);
                gl_Position = p;
                vDepth = abs(p.w);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uLight;
            uniform vec3 uCamera;
            uniform vec3 uFogColor;
            uniform float uFogNear;
            uniform float uFogFar;
            uniform float uMaterial;
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec4 vBaseColor;
            varying float vDepth;

            float hash21(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            void main() {
                vec3 base = vBaseColor.rgb;

                if (uMaterial < 2.5) {
                    float fine = hash21(floor(vWorld.xy * 54.0));
                    float coarse = hash21(floor(vWorld.xy * 7.0));
                    float mow = 0.5 + 0.5 * sin(vWorld.y * 3.05 + sin(vWorld.x * 1.35) * 0.30);
                    base *= 0.875 + fine * 0.095 + coarse * 0.045;
                    base *= 0.975 + mow * 0.035;
                }

                if (uMaterial > 7.5 && uMaterial < 8.5) {
                    float t = clamp((vWorld.z + 1.0) / 11.0, 0.0, 1.0);
                    vec3 lowSky = vec3(0.64, 0.80, 0.91);
                    vec3 highSky = vec3(0.25, 0.58, 0.84);
                    gl_FragColor = vec4(mix(lowSky, highSky, t), vBaseColor.a);
                    return;
                }

                vec3 n = normalize(vNormal);
                vec3 l = normalize(uLight);
                float diffuse = max(dot(n, l), 0.0);
                float hemi = 0.65 + 0.35 * max(n.z, 0.0);
                float light = 0.56 + diffuse * 0.34 + hemi * 0.10;
                vec3 rgb = base * light;

                if (uMaterial > 5.5 && uMaterial < 6.5) {
                    vec3 v = normalize(uCamera - vWorld);
                    vec3 h = normalize(l + v);
                    float spec = pow(max(dot(n, h), 0.0), 28.0);
                    rgb += vec3(0.42) * spec;
                    float dimple = hash21(floor(vWorld.xy * 170.0));
                    rgb *= 0.965 + dimple * 0.035;
                }

                if (uMaterial > 6.5 && uMaterial < 7.5) {
                    gl_FragColor = vec4(rgb, vBaseColor.a);
                    return;
                }

                float fog = clamp((vDepth - uFogNear) / max(0.1, uFogFar - uFogNear), 0.0, 1.0);
                float fogStrength = (uMaterial > 3.5 && uMaterial < 5.5) ? 0.82 : 0.60;
                rgb = mix(rgb, uFogColor, fog * fogStrength);
                gl_FragColor = vec4(rgb, vBaseColor.a);
            }
        """
    }
}
