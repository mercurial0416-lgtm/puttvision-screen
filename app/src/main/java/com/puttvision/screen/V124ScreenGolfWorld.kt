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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bright screen-golf world tuned around the visual grammar of mainstream Korean simulators:
 * vivid sky, saturated turf, clear fringe/rough separation, distant scenery and a low broadcast camera.
 * It does not contain third-party branding, characters or copied assets.
 */
data class V124WorldPlan(
    val cols: Int,
    val rows: Int,
    val treeCount: Int,
    val mountainSegments: Int,
    val movingFrameMs: Long,
    val idleFrameMs: Long,
    val fogNearM: Float,
    val fogFarM: Float
)

object V124WorldPlanner {
    fun plan(tier: V24RenderTier, distanceM: Double): V124WorldPlan {
        val d = distanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val longCourse = d >= 12.0
        return when (tier) {
            V24RenderTier.HIGH -> V124WorldPlan(
                cols = if (longCourse) 36 else 42,
                rows = if (longCourse) 108 else 96,
                treeCount = 26,
                mountainSegments = 40,
                movingFrameMs = 16L,
                idleFrameMs = 60L,
                fogNearM = 17f,
                fogFarM = (d + 34.0).toFloat().coerceIn(30f, 60f)
            )
            V24RenderTier.BALANCED -> V124WorldPlan(
                cols = 30,
                rows = if (longCourse) 84 else 72,
                treeCount = 18,
                mountainSegments = 28,
                movingFrameMs = 20L,
                idleFrameMs = 78L,
                fogNearM = 15f,
                fogFarM = (d + 30.0).toFloat().coerceIn(27f, 56f)
            )
            V24RenderTier.PERFORMANCE -> V124WorldPlan(
                cols = 20,
                rows = if (longCourse) 56 else 48,
                treeCount = 10,
                mountainSegments = 18,
                movingFrameMs = 28L,
                idleFrameMs = 110L,
                fogNearM = 13f,
                fogFarM = (d + 26.0).toFloat().coerceIn(24f, 50f)
            )
        }
    }
}

object V124ScreenGolfWorldFactory {
    fun create(context: Context, engine: GameEngine): View {
        val supported = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
        }.getOrDefault(false)
        return if (supported) V124ScreenGolfWorldStage(context, engine) else V17SimulatorTvView(context, engine)
    }
}

class V124ScreenGolfWorldStage(
    context: Context,
    engine: GameEngine
) : FrameLayout(context) {
    private val gl = V124ScreenGolfGlView(context, engine)

    init {
        setBackgroundColor(Color.rgb(104, 188, 235))
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

private class V124ScreenGolfGlView(
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
            val plan = V124WorldPlanner.plan(tier, engine.settings.holeDistanceM)
            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            handler.postDelayed(this, if (moving) plan.movingFrameMs else plan.idleFrameMs)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(V124ScreenGolfRenderer(context.applicationContext, engine))
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

private data class V124WorldKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val tier: V24RenderTier
)

private class V124Mesh(data: FloatArray) {
    val count = data.size / 10
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(data); position(0) }
}

private class V124Builder {
    private val out = ArrayList<Float>(8192)

    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, c: FloatArray) {
        out += x; out += y; out += z
        out += nx; out += ny; out += nz
        out += c[0]; out += c[1]; out += c[2]; out += c.getOrElse(3) { 1f }
    }

    fun tri(a: FloatArray, b: FloatArray, c0: FloatArray, color: FloatArray, n: FloatArray = floatArrayOf(0f, 0f, 1f)) {
        v(a[0], a[1], a[2], n[0], n[1], n[2], color)
        v(b[0], b[1], b[2], n[0], n[1], n[2], color)
        v(c0[0], c0[1], c0[2], n[0], n[1], n[2], color)
    }

    fun quad(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray) {
        tri(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z1), color)
        tri(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1), color)
    }

    fun build(): V124Mesh = V124Mesh(out.toFloatArray())
}

private class V124ScreenGolfRenderer(
    private val context: Context,
    private val engine: GameEngine
) : GLSurfaceView.Renderer {
    private var program = 0
    private var aPos = -1
    private var aNormal = -1
    private var aColor = -1
    private var uMvp = -1
    private var uLight = -1
    private var uFogColor = -1
    private var uFogNear = -1
    private var uFogFar = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private var widthPx = 1
    private var heightPx = 1

    private var key: V124WorldKey? = null
    private var sky: V124Mesh? = null
    private var clouds: V124Mesh? = null
    private var mountains: V124Mesh? = null
    private var rough: V124Mesh? = null
    private var fringe: V124Mesh? = null
    private var green: V124Mesh? = null
    private var trees: V124Mesh? = null

    private val eye = floatArrayOf(0f, -2.7f, 1.05f)
    private val look = floatArrayOf(0f, 2.8f, .04f)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.35f, .70f, .92f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = link(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogNear = GLES20.glGetUniformLocation(program, "uFogNear")
        uFogFar = GLES20.glGetUniformLocation(program, "uFogFar")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, widthPx, heightPx)
        val aspect = widthPx.toFloat() / heightPx.toFloat()
        Matrix.perspectiveM(projection, 0, 51f, aspect, .04f, 70f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val settings = engine.settings.copy()
        ensureScene(settings)
        updateCamera(settings)
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], look[0], look[1], look[2], 0f, 0f, 1f)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        V25FlagProjectionRuntime.publish(mvp, widthPx, heightPx)

        val tier = V24TvQualityRuntime.snapshot(context).tier
        val plan = V124WorldPlanner.plan(tier, settings.holeDistanceM)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform3f(uLight, -.28f, -.34f, .90f)
        GLES20.glUniform3f(uFogColor, .55f, .78f, .92f)
        GLES20.glUniform1f(uFogNear, plan.fogNearM)
        GLES20.glUniform1f(uFogFar, plan.fogFarM)

        sky?.let(::draw)
        clouds?.let(::draw)
        mountains?.let(::draw)
        rough?.let(::draw)
        trees?.let(::draw)
        fringe?.let(::draw)
        green?.let(::draw)
        drawCupFlag(settings)
        drawBall(settings)
    }

    private fun ensureScene(settings: GreenSettings) {
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val safeDistance = settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0
        val next = V124WorldKey(
            settings.terrainProfileId,
            (safeDistance * 100).toInt(),
            ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier
        )
        if (next == key) return
        key = next
        val plan = V124WorldPlanner.plan(tier, safeDistance)
        sky = buildSky(settings)
        clouds = buildClouds(settings)
        mountains = buildMountains(settings, plan.mountainSegments)
        rough = buildRough(settings)
        fringe = buildGreenSurface(settings, plan, fringeMode = true)
        green = buildGreenSurface(settings, plan, fringeMode = false)
        trees = buildTrees(settings, plan.treeCount)
    }

    private fun safeDistance(settings: GreenSettings): Double =
        settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0

    private fun buildSky(settings: GreenSettings): V124Mesh {
        val y = (safeDistance(settings) + 22.0).toFloat()
        val b = V124Builder()
        val w = 42f
        b.quad(-w, y, -.5f, w, y, 3.8f, floatArrayOf(.47f, .77f, .96f, 1f))
        b.quad(-w, y + .02f, 3.8f, w, y + .02f, 10.5f, floatArrayOf(.23f, .62f, .92f, 1f))
        return b.build()
    }

    private fun buildClouds(settings: GreenSettings): V124Mesh {
        val y = (safeDistance(settings) + 21.5).toFloat()
        val b = V124Builder()
        val white = floatArrayOf(.98f, .99f, 1f, .86f)
        fun cloud(cx: Float, z: Float, sx: Float) {
            b.quad(cx - sx, y, z - .18f, cx + sx, y, z + .35f, white)
            b.quad(cx - sx * .62f, y - .01f, z + .18f, cx + sx * .48f, y - .01f, z + .66f, white)
        }
        cloud(-7.8f, 5.6f, 2.6f)
        cloud(1.5f, 7.0f, 2.1f)
        cloud(9.0f, 5.0f, 2.9f)
        return b.build()
    }

    private fun buildMountains(settings: GreenSettings, segments: Int): V124Mesh {
        val d = safeDistance(settings)
        val y = (d + 15.0).toFloat()
        val b = V124Builder()
        val n = segments.coerceIn(10, 48)
        val span = 34.0
        val step = span / n
        for (i in 0 until n) {
            val x0 = (-span / 2 + i * step).toFloat()
            val x1 = (-span / 2 + (i + 1) * step).toFloat()
            val p0 = (1.0 + .55 * sin(i * .61) + .22 * sin(i * 1.77)).toFloat()
            val p1 = (1.0 + .55 * sin((i + 1) * .61) + .22 * sin((i + 1) * 1.77)).toFloat()
            val c = if (i % 2 == 0) floatArrayOf(.18f, .43f, .26f, 1f) else floatArrayOf(.15f, .38f, .23f, 1f)
            b.tri(floatArrayOf(x0, y, -.04f), floatArrayOf(x1, y, -.04f), floatArrayOf(x1, y, p1), c)
            b.tri(floatArrayOf(x0, y, -.04f), floatArrayOf(x1, y, p1), floatArrayOf(x0, y, p0), c)
        }
        return b.build()
    }

    private fun buildRough(settings: GreenSettings): V124Mesh {
        val d = (safeDistance(settings) + 14.0).toFloat()
        val b = V124Builder()
        b.quad(-13f, -5f, -.07f, 13f, d, -.07f, floatArrayOf(.13f, .47f, .12f, 1f))
        b.quad(-13f, -5f, -.065f, -5.0f, d, -.065f, floatArrayOf(.10f, .40f, .10f, 1f))
        b.quad(5.0f, -5f, -.064f, 13f, d, -.064f, floatArrayOf(.11f, .43f, .10f, 1f))
        return b.build()
    }

    private fun buildGreenSurface(settings: GreenSettings, plan: V124WorldPlan, fringeMode: Boolean): V124Mesh {
        val d = safeDistance(settings)
        val length = max(4.2, d * 1.34)
        val half = max(1.65, d * .22) + if (fringeMode) .42 else 0.0
        val cols = plan.cols.coerceAtLeast(8)
        val rows = plan.rows.coerceAtLeast(20)
        val b = V124Builder()

        fun point(x: Double, y: Double): FloatArray {
            val z = GreenTerrain.effectiveHeightAt(settings, x, y).toFloat() + if (fringeMode) -.006f else .006f
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

        for (r in 0 until rows) {
            val y0 = length * r / rows
            val y1 = length * (r + 1) / rows
            val t0 = (.78 + .22 * (1.0 - y0 / length)).coerceIn(.72, 1.0)
            val t1 = (.78 + .22 * (1.0 - y1 / length)).coerceIn(.72, 1.0)
            for (c in 0 until cols) {
                val x00 = -half * t0 + 2.0 * half * t0 * c / cols
                val x10 = -half * t0 + 2.0 * half * t0 * (c + 1) / cols
                val x01 = -half * t1 + 2.0 * half * t1 * c / cols
                val x11 = -half * t1 + 2.0 * half * t1 * (c + 1) / cols
                val centerX = (x00 + x11) * .5
                val centerY = (y0 + y1) * .5
                val n = normalAt(centerX, centerY)
                val stripe = if ((r / 4) % 2 == 0) 1f else .94f
                val color = if (fringeMode) {
                    floatArrayOf(.20f * stripe, .58f * stripe, .17f * stripe, 1f)
                } else {
                    floatArrayOf(.27f * stripe, .72f * stripe, .20f * stripe, 1f)
                }
                val a = point(x00, y0)
                val b0 = point(x10, y0)
                val c0 = point(x11, y1)
                val d0 = point(x01, y1)
                b.tri(a, b0, c0, color, n)
                b.tri(a, c0, d0, color, n)
            }
        }
        return b.build()
    }

    private fun buildTrees(settings: GreenSettings, count: Int): V124Mesh {
        val d = safeDistance(settings)
        val b = V124Builder()
        val n = count.coerceIn(4, 32)
        for (i in 0 until n) {
            val side = if (i % 2 == 0) -1f else 1f
            val y = (-.5 + (i + 1) * (d + 13.0) / (n + 1)).toFloat()
            val wobble = sin(i * 1.73).toFloat()
            val x = side * (4.0f + (i % 4) * .82f + wobble * .30f)
            val trunkH = .36f + (i % 3) * .05f
            val topH = 1.35f + (i % 5) * .08f
            val trunk = floatArrayOf(.32f, .20f, .09f, 1f)
            val leaf = if (i % 3 == 0) floatArrayOf(.08f, .34f, .12f, 1f) else floatArrayOf(.10f, .40f, .13f, 1f)
            b.quad(x - .035f, y, -.05f, x + .035f, y + .03f, trunkH, trunk)
            b.tri(floatArrayOf(x - .52f, y, trunkH * .72f), floatArrayOf(x + .52f, y, trunkH * .72f), floatArrayOf(x, y, topH), leaf, floatArrayOf(0f, -1f, .25f))
            b.tri(floatArrayOf(x, y - .30f, trunkH * .75f), floatArrayOf(x, y + .30f, trunkH * .75f), floatArrayOf(x, y, topH * .96f), leaf, floatArrayOf(side, 0f, .25f))
        }
        return b.build()
    }

    private fun updateCamera(settings: GreenSettings) {
        val state = engine.state
        val result = engine.lastResult
        val animated = TvInstantRollRuntime.displayPosition(state)
        val start = V26BallStartRuntime.current(settings)
        val bx = animated?.first ?: state?.x ?: start.first
        val by = animated?.second ?: state?.y ?: start.second
        val d = safeDistance(settings)
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val progress = (by / d).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0

        val desiredEye: FloatArray
        val desiredLook: FloatArray
        when {
            result != null && (result.holed || result.lipOut || result.distanceToCupM < .65) -> {
                desiredEye = floatArrayOf(1.30f, (d - 1.48).toFloat(), .67f)
                desiredLook = floatArrayOf(0f, d.toFloat(), .02f)
            }
            running && progress > .72 -> {
                desiredEye = floatArrayOf((bx + .72).toFloat(), (by - 1.20).toFloat(), .72f)
                desiredLook = floatArrayOf(0f, min(d, by + 1.6).toFloat(), .035f)
            }
            running -> {
                desiredEye = floatArrayOf((bx * .12).toFloat(), (by - 2.35).toFloat(), .95f)
                desiredLook = floatArrayOf((bx * .24).toFloat(), min(d, by + 2.65).toFloat(), .055f)
            }
            else -> {
                desiredEye = floatArrayOf(start.first.toFloat(), (start.second - 2.72).toFloat(), 1.08f)
                desiredLook = floatArrayOf((start.first * .25).toFloat(), min(d, start.second + max(2.8, d * .52)).toFloat(), .045f)
            }
        }
        val k = if (running) .085f else .14f
        repeat(3) { i ->
            eye[i] += (desiredEye[i] - eye[i]) * k
            look[i] += (desiredLook[i] - look[i]) * k
        }
    }

    private fun drawCupFlag(settings: GreenSettings) {
        val d = safeDistance(settings).toFloat()
        val ground = GreenTerrain.effectiveHeightAt(settings, 0.0, d.toDouble()).toFloat()
        draw(circleMesh(0f, d, ground + .007f, .059f, floatArrayOf(.035f, .035f, .03f, 1f), 34))
        draw(ringMesh(0f, d, ground + .011f, .060f, .073f, floatArrayOf(.94f, .94f, .92f, 1f), 34))
        draw(boxMesh(-.008f, d - .007f, ground + .012f, .008f, d + .007f, ground + .96f, floatArrayOf(.97f, .97f, .94f, 1f)))
        val flag = V124Builder().apply {
            tri(
                floatArrayOf(.010f, d, ground + .94f),
                floatArrayOf(.52f, d, ground + .80f),
                floatArrayOf(.010f, d, ground + .67f),
                floatArrayOf(.94f, .12f, .11f, 1f),
                floatArrayOf(0f, -1f, 0f)
            )
        }.build()
        draw(flag)
    }

    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val p = TvInstantRollRuntime.displayPosition(state) ?: state?.let { it.x to it.y } ?: start
        if (!p.first.isFinite() || !p.second.isFinite()) return
        val z = GreenTerrain.effectiveHeightAt(settings, p.first, p.second).toFloat()
        draw(circleMesh((p.first + .015).toFloat(), (p.second + .012).toFloat(), z + .004f, .055f, floatArrayOf(.08f, .11f, .07f, .28f), 26))
        draw(sphereMesh(p.first.toFloat(), p.second.toFloat(), z + .044f, .043f, floatArrayOf(.98f, .985f, .97f, 1f)))
    }

    private fun draw(mesh: V124Mesh) {
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

    private fun circleMesh(cx: Float, cy: Float, cz: Float, r: Float, color: FloatArray, steps: Int): V124Mesh {
        val b = V124Builder()
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(
                floatArrayOf(cx, cy, cz),
                floatArrayOf(cx + cos(a0).toFloat() * r, cy + sin(a0).toFloat() * r, cz),
                floatArrayOf(cx + cos(a1).toFloat() * r, cy + sin(a1).toFloat() * r, cz),
                color
            )
        }
        return b.build()
    }

    private fun ringMesh(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, color: FloatArray, steps: Int): V124Mesh {
        val b = V124Builder()
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = floatArrayOf(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, cz)
            val i1 = floatArrayOf(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, cz)
            val o0 = floatArrayOf(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, cz)
            val o1 = floatArrayOf(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, cz)
            b.tri(i0, o0, o1, color)
            b.tri(i0, o1, i1, color)
        }
        return b.build()
    }

    private fun sphereMesh(cx: Float, cy: Float, cz: Float, r: Float, color: FloatArray): V124Mesh {
        val b = V124Builder()
        val latN = 9
        val lonN = 14
        fun point(lat: Int, lon: Int): Pair<FloatArray, FloatArray> {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            val nx = (cos(phi) * cos(theta)).toFloat()
            val ny = (cos(phi) * sin(theta)).toFloat()
            val nz = sin(phi).toFloat()
            return floatArrayOf(cx + nx * r, cy + ny * r, cz + nz * r) to floatArrayOf(nx, ny, nz)
        }
        for (lat in 0 until latN) for (lon in 0 until lonN) {
            val l2 = (lon + 1) % lonN
            val a = point(lat, lon)
            val b0 = point(lat + 1, lon)
            val c = point(lat + 1, l2)
            val d = point(lat, l2)
            b.tri(a.first, b0.first, c.first, color, a.second)
            b.tri(a.first, c.first, d.first, color, a.second)
        }
        return b.build()
    }

    private fun boxMesh(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray): V124Mesh {
        val b = V124Builder()
        b.quad(x0, y0, z0, x1, y1, z0, color)
        b.quad(x0, y0, z1, x1, y1, z1, color)
        b.tri(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), color, floatArrayOf(0f, -1f, 0f))
        b.tri(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1), color, floatArrayOf(0f, -1f, 0f))
        b.tri(floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0), color, floatArrayOf(0f, 1f, 0f))
        b.tri(floatArrayOf(x0, y1, z0), floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1), color, floatArrayOf(0f, 1f, 0f))
        return b.build()
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
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform vec3 uLight;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vColor;
            varying float vDepth;
            void main() {
                vec3 n = normalize(aNormal);
                float diffuse = max(dot(n, normalize(uLight)), 0.0);
                float light = 0.72 + diffuse * 0.38;
                vColor = vec4(aColor.rgb * light, aColor.a);
                vec4 p = uMvp * vec4(aPosition, 1.0);
                gl_Position = p;
                vDepth = abs(p.w);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uFogColor;
            uniform float uFogNear;
            uniform float uFogFar;
            varying vec4 vColor;
            varying float vDepth;
            void main() {
                float fog = clamp((vDepth - uFogNear) / max(0.1, uFogFar - uFogNear), 0.0, 1.0);
                vec3 rgb = mix(vColor.rgb, uFogColor, fog * 0.72);
                gl_FragColor = vec4(rgb, vColor.a);
            }
        """
    }
}
