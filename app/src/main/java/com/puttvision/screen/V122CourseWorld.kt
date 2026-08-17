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

data class V122WorldDetailPlan(
    val greenCols: Int,
    val greenRows: Int,
    val treeCount: Int,
    val hillSegments: Int,
    val movingFrameMs: Long,
    val idleFrameMs: Long,
    val fogFarM: Float
)

/** Pure load policy for the replacement 3D world. */
object V122WorldDetailPlanner {
    fun plan(tier: V24RenderTier, distanceM: Double): V122WorldDetailPlan {
        val distance = distanceM.takeIf { it.isFinite() }?.coerceIn(1.0, 30.0) ?: 5.0
        val longCourse = distance >= 12.0
        return when (tier) {
            V24RenderTier.HIGH -> V122WorldDetailPlan(
                greenCols = if (longCourse) 34 else 38,
                greenRows = if (longCourse) 104 else 92,
                treeCount = 22,
                hillSegments = 34,
                movingFrameMs = tier.movingFrameMs,
                idleFrameMs = 72L,
                fogFarM = (distance + 24.0).toFloat().coerceIn(22f, 52f)
            )
            V24RenderTier.BALANCED -> V122WorldDetailPlan(
                greenCols = 28,
                greenRows = if (longCourse) 80 else 68,
                treeCount = 14,
                hillSegments = 24,
                movingFrameMs = tier.movingFrameMs,
                idleFrameMs = 92L,
                fogFarM = (distance + 22.0).toFloat().coerceIn(20f, 48f)
            )
            V24RenderTier.PERFORMANCE -> V122WorldDetailPlan(
                greenCols = 18,
                greenRows = if (longCourse) 52 else 44,
                treeCount = 8,
                hillSegments = 16,
                movingFrameMs = tier.movingFrameMs,
                idleFrameMs = 125L,
                fogFarM = (distance + 18.0).toFloat().coerceIn(18f, 42f)
            )
        }
    }
}

/**
 * New canonical world. This does not reuse V18's renderer, mesh or shader pipeline.
 * Simulation/measurement are read-only inputs; only presentation is replaced.
 */
object V122CourseWorldFactory {
    fun create(context: Context, engine: GameEngine): View {
        val supported = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
        }.getOrDefault(false)
        return if (supported) V122CourseWorldStage(context, engine) else V17SimulatorTvView(context, engine)
    }
}

class V122CourseWorldStage(
    context: Context,
    engine: GameEngine
) : FrameLayout(context) {
    private val gl = V122CourseGlView(context, engine)

    init {
        setBackgroundColor(Color.rgb(13, 31, 38))
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

private class V122CourseGlView(
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
            val plan = V122WorldDetailPlanner.plan(tier, engine.settings.holeDistanceM)
            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            handler.postDelayed(this, if (moving) plan.movingFrameMs else plan.idleFrameMs)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(V122CourseRenderer(context.applicationContext, engine))
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

private data class V122WorldKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val tier: V24RenderTier
)

private class V122Mesh(data: FloatArray) {
    val count = data.size / STRIDE_FLOATS
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(data); position(0) }

    companion object { const val STRIDE_FLOATS = 10 }
}

private class V122Builder {
    private val out = ArrayList<Float>(4096)

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, r: Float, g: Float, b: Float, a: Float = 1f) {
        out += x; out += y; out += z
        out += nx; out += ny; out += nz
        out += r; out += g; out += b; out += a
    }

    fun tri(
        a: FloatArray, b: FloatArray, c: FloatArray,
        color: FloatArray,
        normal: FloatArray = floatArrayOf(0f, 0f, 1f)
    ) {
        vertex(a[0], a[1], a[2], normal[0], normal[1], normal[2], color[0], color[1], color[2], color.getOrElse(3) { 1f })
        vertex(b[0], b[1], b[2], normal[0], normal[1], normal[2], color[0], color[1], color[2], color.getOrElse(3) { 1f })
        vertex(c[0], c[1], c[2], normal[0], normal[1], normal[2], color[0], color[1], color[2], color.getOrElse(3) { 1f })
    }

    fun build(): V122Mesh = V122Mesh(out.toFloatArray())
}

private class V122CourseRenderer(
    private val context: Context,
    private val engine: GameEngine
) : GLSurfaceView.Renderer {
    private var program = 0
    private var aPos = -1
    private var aNormal = -1
    private var aColor = -1
    private var uMvp = -1
    private var uLightDir = -1
    private var uFogColor = -1
    private var uFogNear = -1
    private var uFogFar = -1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 16f / 9f
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var worldKey: V122WorldKey? = null
    private var sky: V122Mesh? = null
    private var hills: V122Mesh? = null
    private var rough: V122Mesh? = null
    private var fringe: V122Mesh? = null
    private var green: V122Mesh? = null
    private var trees: V122Mesh? = null

    private val eye = floatArrayOf(0f, -2.25f, 1.18f)
    private val target = floatArrayOf(0f, 2.2f, .04f)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.055f, .135f, .17f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogNear = GLES20.glGetUniformLocation(program, "uFogNear")
        uFogFar = GLES20.glGetUniformLocation(program, "uFogFar")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        Matrix.perspectiveM(projection, 0, 47f, aspect, .05f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val settings = engine.settings.copy()
        ensureWorld(settings)
        updateCamera(settings)
        Matrix.setLookAtM(
            view, 0,
            eye[0], eye[1], eye[2],
            target[0], target[1], target[2],
            0f, 0f, 1f
        )
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        V25FlagProjectionRuntime.publish(mvp, viewportWidth, viewportHeight)

        val tier = V24TvQualityRuntime.snapshot(context).tier
        val detail = V122WorldDetailPlanner.plan(tier, settings.holeDistanceM)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniform3f(uLightDir, -.34f, -.22f, .91f)
        GLES20.glUniform3f(uFogColor, .20f, .34f, .36f)
        GLES20.glUniform1f(uFogNear, 11f)
        GLES20.glUniform1f(uFogFar, detail.fogFarM)

        sky?.let(::draw)
        hills?.let(::draw)
        rough?.let(::draw)
        trees?.let(::draw)
        fringe?.let(::draw)
        green?.let(::draw)
        drawCupAndFlag(settings)
        drawBall(settings)
    }

    private fun ensureWorld(settings: GreenSettings) {
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V122WorldKey(
            settings.terrainProfileId,
            ((settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0) * 100).toInt(),
            ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier
        )
        if (key == worldKey) return
        worldKey = key
        val detail = V122WorldDetailPlanner.plan(tier, settings.holeDistanceM)
        sky = buildSky(settings)
        hills = buildHills(settings, detail.hillSegments)
        rough = buildRough(settings)
        fringe = buildSurface(settings, detail, fringeMode = true)
        green = buildSurface(settings, detail, fringeMode = false)
        trees = buildTrees(settings, detail.treeCount)
    }

    private fun buildSky(settings: GreenSettings): V122Mesh {
        val distance = safeDistance(settings)
        val y = (distance + 16.0).toFloat()
        val width = 34f
        val b = V122Builder()
        val bottom = floatArrayOf(.27f, .48f, .52f, 1f)
        val mid = floatArrayOf(.16f, .34f, .42f, 1f)
        val top = floatArrayOf(.07f, .18f, .27f, 1f)
        val z0 = -.2f; val z1 = 3.2f; val z2 = 8.2f
        b.tri(floatArrayOf(-width, y, z0), floatArrayOf(width, y, z0), floatArrayOf(width, y, z1), bottom)
        b.tri(floatArrayOf(-width, y, z0), floatArrayOf(width, y, z1), floatArrayOf(-width, y, z1), bottom)
        b.tri(floatArrayOf(-width, y, z1), floatArrayOf(width, y, z1), floatArrayOf(width, y, z2), mid)
        b.tri(floatArrayOf(-width, y, z1), floatArrayOf(width, y, z2), floatArrayOf(-width, y, z2), top)
        return b.build()
    }

    private fun buildHills(settings: GreenSettings, segments: Int): V122Mesh {
        val distance = safeDistance(settings)
        val y = distance + 10.0
        val b = V122Builder()
        val step = 28.0 / segments.coerceAtLeast(8)
        for (i in 0 until segments) {
            val x0 = -14.0 + step * i
            val x1 = x0 + step
            val z0 = .40 + .38 * sin(i * .73) + .16 * sin(i * 1.91)
            val z1 = .40 + .38 * sin((i + 1) * .73) + .16 * sin((i + 1) * 1.91)
            val color = if (i % 2 == 0) floatArrayOf(.075f, .22f, .15f, 1f) else floatArrayOf(.065f, .19f, .14f, 1f)
            b.tri(
                floatArrayOf(x0.toFloat(), y.toFloat(), -.08f),
                floatArrayOf(x1.toFloat(), y.toFloat(), -.08f),
                floatArrayOf(x1.toFloat(), y.toFloat(), z1.toFloat()),
                color
            )
            b.tri(
                floatArrayOf(x0.toFloat(), y.toFloat(), -.08f),
                floatArrayOf(x1.toFloat(), y.toFloat(), z1.toFloat()),
                floatArrayOf(x0.toFloat(), y.toFloat(), z0.toFloat()),
                color
            )
        }
        return b.build()
    }

    private fun buildRough(settings: GreenSettings): V122Mesh {
        val distance = safeDistance(settings)
        val d = (distance + 13.0).toFloat()
        val b = V122Builder()
        val c1 = floatArrayOf(.075f, .255f, .105f, 1f)
        val c2 = floatArrayOf(.065f, .225f, .095f, 1f)
        quad(b, -12f, -4.5f, 0f, 12f, d, 0f, c1)
        // Slight side panels create richer rough variation without texture assets.
        quad(b, -12f, -4.5f, .002f, -4.5f, d, .002f, c2)
        quad(b, 4.5f, -4.5f, .002f, 12f, d, .002f, c2)
        return b.build()
    }

    private fun buildSurface(settings: GreenSettings, detail: V122WorldDetailPlan, fringeMode: Boolean): V122Mesh {
        val distance = safeDistance(settings)
        val length = max(4.2, distance * 1.34)
        val coreHalf = max(1.58, distance * .19).coerceAtMost(3.25)
        val halfWidth = coreHalf + if (fringeMode) .22 else 0.0
        val cols = detail.greenCols.coerceAtLeast(10)
        val rows = detail.greenRows.coerceAtLeast(20)
        val b = V122Builder()

        fun point(x: Double, y: Double, zOffset: Double): FloatArray {
            val z = GreenTerrain.effectiveHeightAt(settings, x, y)
                .takeIf { it.isFinite() } ?: 0.0
            return floatArrayOf(x.toFloat(), y.toFloat(), (z + zOffset).toFloat())
        }

        fun normal(x: Double, y: Double): FloatArray {
            val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
            var nx = (-(slope.sidePct.takeIf { it.isFinite() } ?: 0.0) / 100.0).toFloat()
            var ny = (-(slope.longPct.takeIf { it.isFinite() } ?: 0.0) / 100.0).toFloat()
            var nz = 1f
            val n = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
            nx /= n; ny /= n; nz /= n
            return floatArrayOf(nx, ny, nz)
        }

        for (r in 0 until rows) {
            val y0 = length * r / rows
            val y1 = length * (r + 1) / rows
            val taper0 = (.78 + .22 * (1.0 - y0 / length)).coerceIn(.76, 1.0)
            val taper1 = (.78 + .22 * (1.0 - y1 / length)).coerceIn(.76, 1.0)
            for (c in 0 until cols) {
                val x0 = -halfWidth * taper0 + 2.0 * halfWidth * taper0 * c / cols
                val x1 = -halfWidth * taper0 + 2.0 * halfWidth * taper0 * (c + 1) / cols
                val xx0 = -halfWidth * taper1 + 2.0 * halfWidth * taper1 * c / cols
                val xx1 = -halfWidth * taper1 + 2.0 * halfWidth * taper1 * (c + 1) / cols
                val offset = if (fringeMode) -.012 else .006
                val stripe = ((r / 5) + (c / 6)) % 2
                val color = if (fringeMode) {
                    if (stripe == 0) floatArrayOf(.18f, .47f, .17f, 1f) else floatArrayOf(.16f, .43f, .155f, 1f)
                } else {
                    if (stripe == 0) floatArrayOf(.25f, .65f, .205f, 1f) else floatArrayOf(.215f, .585f, .185f, 1f)
                }
                val p00 = point(x0, y0, offset)
                val p10 = point(x1, y0, offset)
                val p01 = point(xx0, y1, offset)
                val p11 = point(xx1, y1, offset)
                b.tri(p00, p10, p11, color, normal((x0 + x1 + xx1) / 3.0, (y0 + y0 + y1) / 3.0))
                b.tri(p00, p11, p01, color, normal((x0 + xx1 + xx0) / 3.0, (y0 + y1 + y1) / 3.0))
            }
        }
        return b.build()
    }

    private fun buildTrees(settings: GreenSettings, count: Int): V122Mesh {
        val distance = safeDistance(settings)
        val b = V122Builder()
        val safeCount = count.coerceIn(0, 26)
        for (i in 0 until safeCount) {
            val side = if (i % 2 == 0) -1.0 else 1.0
            val lane = i / 2
            val y = 1.4 + (lane * 1.37) % (distance + 7.0)
            val x = side * (3.4 + .48 * (lane % 4))
            val ground = GreenTerrain.effectiveHeightAt(settings, x, y).takeIf { it.isFinite() } ?: 0.0
            val scale = .72 + .12 * (i % 5)
            appendTree(b, x.toFloat(), y.toFloat(), ground.toFloat(), scale.toFloat(), i)
        }
        return b.build()
    }

    private fun appendTree(b: V122Builder, x: Float, y: Float, z: Float, scale: Float, seed: Int) {
        val trunk = floatArrayOf(.24f, .16f, .085f, 1f)
        val leaf = when (seed % 3) {
            0 -> floatArrayOf(.08f, .30f, .115f, 1f)
            1 -> floatArrayOf(.065f, .265f, .105f, 1f)
            else -> floatArrayOf(.095f, .335f, .125f, 1f)
        }
        val trunkH = .72f * scale
        val trunkR = .065f * scale
        appendBox(b, x - trunkR, y - trunkR, z, x + trunkR, y + trunkR, z + trunkH, trunk)
        appendCone(b, x, y, z + trunkH * .65f, .52f * scale, 1.42f * scale, leaf, 9)
        appendCone(b, x + .06f * scale, y, z + trunkH + .40f * scale, .40f * scale, 1.12f * scale, leaf, 9)
    }

    private fun appendCone(b: V122Builder, cx: Float, cy: Float, cz: Float, radius: Float, height: Float, color: FloatArray, steps: Int) {
        val top = floatArrayOf(cx, cy, cz + height)
        for (i in 0 until steps) {
            val a0 = 2.0 * PI * i / steps
            val a1 = 2.0 * PI * (i + 1) / steps
            val p0 = floatArrayOf(cx + (cos(a0) * radius).toFloat(), cy + (sin(a0) * radius).toFloat(), cz)
            val p1 = floatArrayOf(cx + (cos(a1) * radius).toFloat(), cy + (sin(a1) * radius).toFloat(), cz)
            val mx = ((p0[0] + p1[0]) * .5f - cx)
            val my = ((p0[1] + p1[1]) * .5f - cy)
            val nLen = sqrt(mx * mx + my * my + radius * radius).coerceAtLeast(.001f)
            val normal = floatArrayOf(mx / nLen, my / nLen, radius / nLen)
            b.tri(p0, p1, top, color, normal)
        }
    }

    private fun drawCupAndFlag(settings: GreenSettings) {
        val y = safeDistance(settings).toFloat()
        val z = (GreenTerrain.effectiveHeightAt(settings, 0.0, y.toDouble()).takeIf { it.isFinite() } ?: 0.0).toFloat() + .010f
        draw(buildRing(0f, y, z, .052f, .073f, floatArrayOf(.78f, .79f, .72f, 1f), 32))
        draw(buildDisc(0f, y, z - .006f, .052f, floatArrayOf(.025f, .030f, .026f, 1f), 32))
        draw(buildBox(-.009f, y - .009f, z, .009f, y + .009f, z + .92f, floatArrayOf(.92f, .93f, .90f, 1f)))
        val flag = V122Builder().apply {
            tri(
                floatArrayOf(.012f, y, z + .90f),
                floatArrayOf(.51f, y, z + .76f),
                floatArrayOf(.012f, y, z + .61f),
                floatArrayOf(.88f, .08f, .055f, 1f),
                floatArrayOf(0f, -1f, 0f)
            )
        }.build()
        draw(flag)
    }

    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state) ?: state?.let { it.x to it.y } ?: start
        val bx = display.first.takeIf { it.isFinite() } ?: start.first
        val by = display.second.takeIf { it.isFinite() } ?: start.second
        val ground = (GreenTerrain.effectiveHeightAt(settings, bx, by).takeIf { it.isFinite() } ?: 0.0).toFloat()
        draw(buildDisc((bx + .018).toFloat(), (by + .025).toFloat(), ground + .009f, .055f, floatArrayOf(.015f, .022f, .018f, .28f), 24))
        draw(buildSphere(bx.toFloat(), by.toFloat(), ground + .043f, .043f, 10, 16))
    }

    private fun updateCamera(settings: GreenSettings) {
        val state = engine.state
        val result = engine.lastResult
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: start.first
        val by = display?.second ?: state?.y ?: start.second
        val distance = safeDistance(settings)
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val progress = (by / distance.coerceAtLeast(.5)).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0

        val desiredEye: FloatArray
        val desiredTarget: FloatArray
        when {
            result != null && (result.holed || result.lipOut || result.distanceToCupM < .75) -> {
                desiredEye = floatArrayOf(1.22f, (distance - 1.48).toFloat(), .66f)
                desiredTarget = floatArrayOf(0f, distance.toFloat(), .035f)
            }
            running && progress >= .70 -> {
                desiredEye = floatArrayOf((bx + .82).toFloat(), (by - 1.12).toFloat(), .72f)
                desiredTarget = floatArrayOf(0f, min(distance, by + 1.30).toFloat(), .045f)
            }
            running -> {
                desiredEye = floatArrayOf((bx * .16).toFloat(), (by - 2.05).toFloat(), .98f)
                desiredTarget = floatArrayOf((bx * .25).toFloat(), min(distance, by + 2.35).toFloat(), .05f)
            }
            else -> {
                val remaining = (distance - start.second).coerceAtLeast(.5)
                desiredEye = floatArrayOf(start.first.toFloat(), (start.second - 2.35).toFloat(), 1.22f)
                desiredTarget = floatArrayOf((start.first * .35).toFloat(), (start.second + min(3.3, remaining * .54)).toFloat(), .045f)
            }
        }
        val smooth = if (running) .065f else .11f
        repeat(3) { i ->
            eye[i] += (desiredEye[i] - eye[i]) * smooth
            target[i] += (desiredTarget[i] - target[i]) * smooth
        }
    }

    private fun draw(mesh: V122Mesh) {
        val stride = V122Mesh.STRIDE_FLOATS * 4
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

    private fun buildSphere(cx: Float, cy: Float, cz: Float, radius: Float, latSteps: Int, lonSteps: Int): V122Mesh {
        val b = V122Builder()
        fun point(lat: Int, lon: Int): Pair<FloatArray, FloatArray> {
            val phi = PI * (-.5 + lat / latSteps.toDouble())
            val theta = PI * 2.0 * lon / lonSteps.toDouble()
            val nx = (cos(phi) * cos(theta)).toFloat()
            val ny = (cos(phi) * sin(theta)).toFloat()
            val nz = sin(phi).toFloat()
            return floatArrayOf(cx + nx * radius, cy + ny * radius, cz + nz * radius) to floatArrayOf(nx, ny, nz)
        }
        val white = floatArrayOf(.97f, .98f, .955f, 1f)
        for (lat in 0 until latSteps) for (lon in 0 until lonSteps) {
            val nextLon = (lon + 1) % lonSteps
            val a = point(lat, lon)
            val bb = point(lat + 1, lon)
            val c = point(lat + 1, nextLon)
            val d = point(lat, nextLon)
            b.tri(a.first, bb.first, c.first, white, a.second)
            b.tri(a.first, c.first, d.first, white, a.second)
        }
        return b.build()
    }

    private fun buildDisc(cx: Float, cy: Float, cz: Float, radius: Float, color: FloatArray, steps: Int): V122Mesh {
        val b = V122Builder()
        val center = floatArrayOf(cx, cy, cz)
        for (i in 0 until steps) {
            val a0 = 2.0 * PI * i / steps
            val a1 = 2.0 * PI * (i + 1) / steps
            b.tri(
                center,
                floatArrayOf(cx + (cos(a0) * radius).toFloat(), cy + (sin(a0) * radius).toFloat(), cz),
                floatArrayOf(cx + (cos(a1) * radius).toFloat(), cy + (sin(a1) * radius).toFloat(), cz),
                color
            )
        }
        return b.build()
    }

    private fun buildRing(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, color: FloatArray, steps: Int): V122Mesh {
        val b = V122Builder()
        for (i in 0 until steps) {
            val a0 = 2.0 * PI * i / steps
            val a1 = 2.0 * PI * (i + 1) / steps
            val i0 = floatArrayOf(cx + (cos(a0) * inner).toFloat(), cy + (sin(a0) * inner).toFloat(), cz)
            val o0 = floatArrayOf(cx + (cos(a0) * outer).toFloat(), cy + (sin(a0) * outer).toFloat(), cz)
            val i1 = floatArrayOf(cx + (cos(a1) * inner).toFloat(), cy + (sin(a1) * inner).toFloat(), cz)
            val o1 = floatArrayOf(cx + (cos(a1) * outer).toFloat(), cy + (sin(a1) * outer).toFloat(), cz)
            b.tri(i0, o0, o1, color)
            b.tri(i0, o1, i1, color)
        }
        return b.build()
    }

    private fun buildBox(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray): V122Mesh =
        V122Builder().also { appendBox(it, x0, y0, z0, x1, y1, z1, color) }.build()

    private fun appendBox(b: V122Builder, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray) {
        val p000 = floatArrayOf(x0, y0, z0); val p100 = floatArrayOf(x1, y0, z0)
        val p010 = floatArrayOf(x0, y1, z0); val p110 = floatArrayOf(x1, y1, z0)
        val p001 = floatArrayOf(x0, y0, z1); val p101 = floatArrayOf(x1, y0, z1)
        val p011 = floatArrayOf(x0, y1, z1); val p111 = floatArrayOf(x1, y1, z1)
        fun face(a: FloatArray, bb: FloatArray, c: FloatArray, d: FloatArray, n: FloatArray) {
            b.tri(a, bb, c, color, n); b.tri(a, c, d, color, n)
        }
        face(p000, p100, p101, p001, floatArrayOf(0f, -1f, 0f))
        face(p010, p011, p111, p110, floatArrayOf(0f, 1f, 0f))
        face(p000, p001, p011, p010, floatArrayOf(-1f, 0f, 0f))
        face(p100, p110, p111, p101, floatArrayOf(1f, 0f, 0f))
        face(p001, p101, p111, p011, floatArrayOf(0f, 0f, 1f))
    }

    private fun quad(b: V122Builder, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray) {
        val a = floatArrayOf(x0, y0, z0)
        val bb = floatArrayOf(x1, y0, z0)
        val c = floatArrayOf(x1, y1, z1)
        val d = floatArrayOf(x0, y1, z1)
        b.tri(a, bb, c, color)
        b.tri(a, c, d, color)
    }

    private fun safeDistance(settings: GreenSettings): Double =
        settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0

    private fun createProgram(vertex: String, fragment: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw IllegalStateException("V122 program link failed: $log")
        }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("V122 shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform vec3 uLightDir;
            uniform float uFogNear;
            uniform float uFogFar;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vColor;
            varying float vLight;
            varying float vFog;
            void main() {
                vec3 n = normalize(aNormal);
                float diffuse = max(dot(n, normalize(uLightDir)), 0.0);
                vLight = 0.48 + diffuse * 0.62;
                vColor = aColor;
                vFog = clamp((aPosition.y - uFogNear) / max(0.1, uFogFar - uFogNear), 0.0, 1.0);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uFogColor;
            varying vec4 vColor;
            varying float vLight;
            varying float vFog;
            void main() {
                vec3 lit = vColor.rgb * vLight;
                vec3 finalColor = mix(lit, uFogColor, vFog * 0.68);
                gl_FragColor = vec4(finalColor, vColor.a);
            }
        """
    }
}
