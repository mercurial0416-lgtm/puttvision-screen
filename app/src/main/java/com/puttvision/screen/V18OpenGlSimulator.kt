package com.puttvision.screen

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object V18SimulatorFactory {
    fun create(context: Context, engine: GameEngine): View {
        val supported = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
        }.getOrDefault(false)
        return if (supported) V18SimulatorStage(context, engine) else V17SimulatorTvView(context, engine)
    }
}

/** Real 3D world + lightweight 2D television HUD. */
class V18SimulatorStage(
    context: Context,
    private val engine: GameEngine
) : FrameLayout(context) {
    private val gl = V18PuttingGlView(context, engine)
    private val hud = V18SimulatorHudView(context, engine)

    init {
        setBackgroundColor(Color.rgb(55, 130, 190))
        addView(gl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V19StrokeStudioOverlay(context, engine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) gl.onResume() else gl.onPause()
    }
}

private class V18PuttingGlView(
    context: Context,
    private val engine: GameEngine
) : GLSurfaceView(context) {
    private val appContext = context.applicationContext
    private val renderHandler = Handler(Looper.getMainLooper())
    private var loopRunning = false
    private val renderTick = object : Runnable {
        override fun run() {
            if (!loopRunning || !isAttachedToWindow) return
            requestRender()
            val moving = engine.state?.running == true || TvInstantRollRuntime.isAnimating()
            val tier = V24TvQualityRuntime.snapshot(appContext).tier
            val delay = when {
                moving -> tier.movingFrameMs
                engine.lastResult == null -> tier.idleFrameMs
                else -> max(180L, tier.idleFrameMs * 2L)
            }
            renderHandler.postDelayed(this, delay)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(V18PuttingRenderer(appContext, engine))
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loopRunning = true
        renderHandler.removeCallbacks(renderTick)
        renderHandler.post(renderTick)
    }

    override fun onDetachedFromWindow() {
        loopRunning = false
        renderHandler.removeCallbacks(renderTick)
        super.onDetachedFromWindow()
    }
}

private data class V18TerrainKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val qualityTier: V24RenderTier
)

private class V18Mesh(data: FloatArray) {
    val count = data.size / 10
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(data); position(0) }
}

private class V18PuttingRenderer(
    private val context: Context,
    private val engine: GameEngine
) : GLSurfaceView.Renderer {
    private var program = 0
    private var aPos = -1
    private var aNormal = -1
    private var aColor = -1
    private var uMvp = -1
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)
    private var aspect = 16f / 9f

    private var terrainKey: V18TerrainKey? = null
    private var terrainMesh: V18Mesh? = null
    private var roughMesh: V18Mesh? = null
    private var decorMesh: V18Mesh? = null

    private val eye = floatArrayOf(0f, -1.65f, .72f)
    private val target = floatArrayOf(0f, 2.0f, .03f)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.20f, .49f, .73f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projection, 0, 43f, aspect, .05f, 45f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val settings = engine.settings.copy()
        ensureTerrain(settings)
        updateCamera(settings)
        Matrix.setLookAtM(
            view, 0,
            eye[0], eye[1], eye[2],
            target[0], target[1], target[2],
            0f, 0f, 1f
        )
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)

        roughMesh?.let { draw(it, GLES20.GL_TRIANGLES) }
        decorMesh?.let { draw(it, GLES20.GL_TRIANGLES) }
        terrainMesh?.let { draw(it, GLES20.GL_TRIANGLES) }
        drawAimAndRead(settings)
        drawGhost(settings)
        drawTrail(settings)
        drawCupAndFlag(settings)
        drawBall(settings)
    }

    private fun ensureTerrain(settings: GreenSettings) {
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V18TerrainKey(
            settings.terrainProfileId,
            (settings.holeDistanceM * 100).toInt(),
            (settings.sideSlopePct * 100).toInt(),
            (settings.longSlopePct * 100).toInt(),
            tier
        )
        if (key == terrainKey) return
        terrainKey = key
        terrainMesh = buildTerrain(settings, tier)
        roughMesh = buildRough(settings)
        decorMesh = V18Mesh(V18ProceduralDecor.build(settings))
    }

    private fun buildRough(settings: GreenSettings): V18Mesh {
        val d = max(5.0, settings.holeDistanceM + 5.0)
        val z = -0.055f
        val c = floatArrayOf(.12f, .38f, .13f, 1f)
        return V18Mesh(quad(-9f, -3.5f, 9f, d.toFloat(), z, c))
    }

    private fun buildTerrain(settings: GreenSettings, tier: V24RenderTier): V18Mesh {
        val distance = max(3.5, settings.holeDistanceM * 1.32)
        val halfWidth = max(1.45, settings.holeDistanceM * .20)
        val cols = tier.terrainCols
        val rows = tier.terrainRows
        val out = ArrayList<Float>(cols * rows * 60)
        fun vertex(x: Double, y: Double, band: Int) {
            val z = GreenTerrain.effectiveHeightAt(settings, x, y).toFloat()
            val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
            var nx = (-slope.sidePct / 100.0).toFloat()
            var ny = (-slope.longPct / 100.0).toFloat()
            var nz = 1f
            val n = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
            nx /= n; ny /= n; nz /= n
            val stripe = if (band % 2 == 0) 1.0f else .94f
            out += x.toFloat(); out += y.toFloat(); out += z
            out += nx; out += ny; out += nz
            out += .23f * stripe; out += .62f * stripe; out += .18f * stripe; out += 1f
        }
        for (r in 0 until rows) {
            val y0 = distance * r / rows
            val y1 = distance * (r + 1) / rows
            val taper0 = (.74 + .26 * (1.0 - y0 / distance)).coerceIn(.72, 1.0)
            val taper1 = (.74 + .26 * (1.0 - y1 / distance)).coerceIn(.72, 1.0)
            for (c in 0 until cols) {
                val x0 = -halfWidth * taper0 + 2.0 * halfWidth * taper0 * c / cols
                val x1 = -halfWidth * taper0 + 2.0 * halfWidth * taper0 * (c + 1) / cols
                val xx0 = -halfWidth * taper1 + 2.0 * halfWidth * taper1 * c / cols
                val xx1 = -halfWidth * taper1 + 2.0 * halfWidth * taper1 * (c + 1) / cols
                vertex(x0, y0, r); vertex(x1, y0, r); vertex(xx1, y1, r)
                vertex(x0, y0, r); vertex(xx1, y1, r); vertex(xx0, y1, r)
            }
        }
        return V18Mesh(out.toFloatArray())
    }

    private fun updateCamera(settings: GreenSettings) {
        val state = engine.state
        val result = engine.lastResult
        val anim = TvInstantRollRuntime.displayPosition(state)
        val bx = anim?.first ?: state?.x ?: 0.0
        val by = anim?.second ?: state?.y ?: 0.0
        val running = state?.running == true || TvInstantRollRuntime.isAnimating()
        val progress = (by / settings.holeDistanceM.coerceAtLeast(.5)).coerceIn(0.0, 1.0)

        val desiredEye: FloatArray
        val desiredTarget: FloatArray
        when {
            result != null && (result.holed || result.lipOut || result.distanceToCupM < .7) -> {
                desiredEye = floatArrayOf(1.05f, (settings.holeDistanceM - 1.20).toFloat(), .48f)
                desiredTarget = floatArrayOf(0f, settings.holeDistanceM.toFloat(), .02f)
            }
            running && progress >= .68 -> {
                desiredEye = floatArrayOf(
                    (bx + .66).toFloat(),
                    (by - .88).toFloat(),
                    .48f
                )
                desiredTarget = floatArrayOf(0f, min(settings.holeDistanceM, by + 1.15).toFloat(), .03f)
            }
            running -> {
                desiredEye = floatArrayOf((bx * .18).toFloat(), (by - 1.50).toFloat(), .69f)
                desiredTarget = floatArrayOf((bx * .30).toFloat(), min(settings.holeDistanceM, by + 1.85).toFloat(), .04f)
            }
            else -> {
                desiredEye = floatArrayOf(0f, -1.58f, .70f)
                desiredTarget = floatArrayOf(0f, min(2.35, settings.holeDistanceM * .48).toFloat(), .035f)
            }
        }
        val smooth = if (running) .075f else .13f
        repeat(3) { i ->
            eye[i] += (desiredEye[i] - eye[i]) * smooth
            target[i] += (desiredTarget[i] - target[i]) * smooth
        }
    }

    private fun drawAimAndRead(settings: GreenSettings) {
        val mode = engine.gameModes.status.mode
        val feedback = V20GreenReadTrainingRuntime.feedback
        val hide = V20GreenReadTrainingRuntime.shouldHideSolution(mode, settings) && !feedback.revealed
        val read = GreenReadRuntime.peekOrSchedule(settings)
        if (!hide && read?.solverReliable == true && engine.state?.running != true && engine.lastResult == null) {
            val pts = if (read.predictedTrail.size >= 2) read.predictedTrail else listOf(0.0 to 0.0, read.aimOffsetCm / 100.0 to settings.holeDistanceM)
            ribbon(pts, settings, .010f, floatArrayOf(.91f, .08f, .06f, .90f))?.let { draw(it, GLES20.GL_TRIANGLES) }
        }
        if (feedback.active && feedback.revealed && read != null) {
            ribbon(read.predictedTrail, settings, .035f, floatArrayOf(.96f, .79f, .12f, .48f))?.let { draw(it, GLES20.GL_TRIANGLES) }
        }
    }

    private fun drawGhost(settings: GreenSettings) {
        if (engine.gameModes.status.mode != PracticeMode.GHOST) return
        val ghost = V15GhostRuntime.referenceForCurrent(settings) ?: return
        ribbon(ghost.trail, settings, .018f, floatArrayOf(.22f, .79f, 1f, .46f))?.let { draw(it, GLES20.GL_TRIANGLES) }
    }

    private fun drawTrail(settings: GreenSettings) {
        val points = engine.state?.trail.orEmpty()
        if (points.size >= 2) {
            ribbon(points, settings, .014f, floatArrayOf(1f, .88f, .18f, .82f))?.let { draw(it, GLES20.GL_TRIANGLES) }
        }
    }

    private fun drawCupAndFlag(settings: GreenSettings) {
        val y = settings.holeDistanceM
        val z = GreenTerrain.effectiveHeightAt(settings, 0.0, y).toFloat() + .004f
        draw(circle(0f, y.toFloat(), z, .055f, floatArrayOf(.025f, .025f, .025f, 1f), 28), GLES20.GL_TRIANGLES)
        draw(box(-.007f, y.toFloat(), z, .007f, y.toFloat() + .014f, z + .86f, floatArrayOf(.92f, .92f, .92f, 1f)), GLES20.GL_TRIANGLES)
        val flag = floatArrayOf(
            .008f, y.toFloat(), z + .85f, 0f, 0f, 1f, .90f, .08f, .06f, 1f,
            .48f, y.toFloat(), z + .72f, 0f, 0f, 1f, .90f, .08f, .06f, 1f,
            .008f, y.toFloat(), z + .62f, 0f, 0f, 1f, .90f, .08f, .06f, 1f
        )
        draw(V18Mesh(flag), GLES20.GL_TRIANGLES)
    }

    private fun drawBall(settings: GreenSettings) {
        val state = engine.state
        val display = TvInstantRollRuntime.displayPosition(state) ?: if (state != null) state.x to state.y else 0.0 to 0.0
        val ground = GreenTerrain.effectiveHeightAt(settings, display.first, display.second).toFloat()
        // Soft contact shadow anchors the ball to the 3D green instead of making it look pasted on.
        draw(
            circle(display.first.toFloat() + .012f, display.second.toFloat() + .010f, ground + .003f, .052f,
                floatArrayOf(.015f, .020f, .015f, .22f), 24),
            GLES20.GL_TRIANGLES
        )
        draw(sphere(display.first.toFloat(), display.second.toFloat(), ground + .043f, .043f), GLES20.GL_TRIANGLES)
    }

    private fun ribbon(
        points: List<Pair<Double, Double>>,
        settings: GreenSettings,
        width: Float,
        color: FloatArray
    ): V18Mesh? {
        if (points.size < 2) return null
        val out = ArrayList<Float>(points.size * 60)
        fun put(x: Double, y: Double, z: Float) {
            out += x.toFloat(); out += y.toFloat(); out += z
            out += 0f; out += 0f; out += 1f
            out += color[0]; out += color[1]; out += color[2]; out += color[3]
        }
        points.zipWithNext().forEach { (a, b) ->
            val dx = b.first - a.first
            val dy = b.second - a.second
            val len = hypot(dx, dy).coerceAtLeast(.0001)
            val ox = -dy / len * width
            val oy = dx / len * width
            val za = GreenTerrain.effectiveHeightAt(settings, a.first, a.second).toFloat() + .009f
            val zb = GreenTerrain.effectiveHeightAt(settings, b.first, b.second).toFloat() + .009f
            put(a.first + ox, a.second + oy, za); put(a.first - ox, a.second - oy, za); put(b.first - ox, b.second - oy, zb)
            put(a.first + ox, a.second + oy, za); put(b.first - ox, b.second - oy, zb); put(b.first + ox, b.second + oy, zb)
        }
        return V18Mesh(out.toFloatArray())
    }

    private fun sphere(cx: Float, cy: Float, cz: Float, r: Float): V18Mesh {
        val out = ArrayList<Float>(10 * 6 * 10 * 16)
        fun v(lat: Int, lon: Int) {
            val phi = Math.PI * (-.5 + lat / 10.0)
            val theta = Math.PI * 2.0 * lon / 16.0
            val nx = (cos(phi) * cos(theta)).toFloat()
            val ny = (cos(phi) * sin(theta)).toFloat()
            val nz = sin(phi).toFloat()
            out += cx + nx * r; out += cy + ny * r; out += cz + nz * r
            out += nx; out += ny; out += nz
            out += .96f; out += .97f; out += .94f; out += 1f
        }
        for (lat in 0 until 10) for (lon in 0 until 16) {
            val l2 = (lon + 1) % 16
            v(lat, lon); v(lat + 1, lon); v(lat + 1, l2)
            v(lat, lon); v(lat + 1, l2); v(lat, l2)
        }
        return V18Mesh(out.toFloatArray())
    }

    private fun circle(cx: Float, cy: Float, cz: Float, r: Float, color: FloatArray, steps: Int): V18Mesh {
        val out = ArrayList<Float>(steps * 30)
        fun add(x: Float, y: Float) {
            out += x; out += y; out += cz
            out += 0f; out += 0f; out += 1f
            out += color[0]; out += color[1]; out += color[2]; out += color[3]
        }
        repeat(steps) { i ->
            val a = Math.PI * 2.0 * i / steps
            val b = Math.PI * 2.0 * (i + 1) / steps
            add(cx, cy)
            add(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r)
            add(cx + cos(b).toFloat() * r, cy + sin(b).toFloat() * r)
        }
        return V18Mesh(out.toFloatArray())
    }

    private fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: FloatArray): V18Mesh {
        fun p(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): FloatArray =
            floatArrayOf(x,y,z,nx,ny,nz,color[0],color[1],color[2],color[3])
        val faces = arrayOf(
            arrayOf(p(x0,y0,z0,0f,-1f,0f),p(x1,y0,z0,0f,-1f,0f),p(x1,y0,z1,0f,-1f,0f),p(x0,y0,z1,0f,-1f,0f)),
            arrayOf(p(x0,y1,z0,0f,1f,0f),p(x1,y1,z0,0f,1f,0f),p(x1,y1,z1,0f,1f,0f),p(x0,y1,z1,0f,1f,0f)),
            arrayOf(p(x0,y0,z0,-1f,0f,0f),p(x0,y1,z0,-1f,0f,0f),p(x0,y1,z1,-1f,0f,0f),p(x0,y0,z1,-1f,0f,0f)),
            arrayOf(p(x1,y0,z0,1f,0f,0f),p(x1,y1,z0,1f,0f,0f),p(x1,y1,z1,1f,0f,0f),p(x1,y0,z1,1f,0f,0f))
        )
        val out = ArrayList<Float>(faces.size * 60)
        faces.forEach { f ->
            listOf(f[0],f[1],f[2],f[0],f[2],f[3]).forEach { out.addAll(it.toList()) }
        }
        return V18Mesh(out.toFloatArray())
    }

    private fun quad(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, color: FloatArray): FloatArray {
        fun v(x: Float, y: Float) = floatArrayOf(x,y,z,0f,0f,1f,color[0],color[1],color[2],color[3])
        return v(x0,y0) + v(x1,y0) + v(x1,y1) + v(x0,y0) + v(x1,y1) + v(x0,y1)
    }

    private fun draw(mesh: V18Mesh, mode: Int) {
        val b = mesh.buffer
        b.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 40, b)
        GLES20.glEnableVertexAttribArray(aPos)
        b.position(3)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 40, b)
        GLES20.glEnableVertexAttribArray(aNormal)
        b.position(6)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 40, b)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glDrawArrays(mode, 0, mesh.count)
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException(GLES20.glGetShaderInfoLog(id))
            return id
        }
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException(GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vec3 light = normalize(vec3(-0.35, -0.45, 0.82));
                float d = max(dot(normalize(aNormal), light), 0.0);
                float shade = 0.60 + d * 0.48;
                vColor = vec4(aColor.rgb * shade, aColor.a);
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;
            void main() { gl_FragColor = vColor; }
        """
    }
}

private class V18SimulatorHudView(
    context: Context,
    private val engine: GameEngine
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastResultStamp = 0L

    init { setWillNotDraw(false) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        drawTopPill(c)
        drawMode(c)
        drawTelemetry(c)
        drawReadChallenge(c)
        drawResult(c)
        postInvalidateDelayed(if (engine.state?.running == true) 16L else 55L)
    }

    private fun drawTopPill(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val ww = w * .182f; val hh = h * .058f
        val l = w * .5f - ww * .5f; val t = h * .027f
        p.color = Color.argb(178, 20, 24, 23)
        c.drawRoundRect(RectF(l,t,l+ww,t+hh), hh*.36f,hh*.36f,p)
        p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = max(10f,w*.0072f); p.color = Color.WHITE
        c.drawText("${"%.1f".format(engine.settings.holeDistanceM)} m", l+ww*.30f,t+hh*.42f,p)
        p.textSize = max(7f,w*.0052f); p.color = Color.argb(180,230,235,230)
        c.drawText("DISTANCE",l+ww*.30f,t+hh*.73f,p)
        p.color = Color.argb(60,255,255,255); c.drawRect(l+ww*.58f,t+hh*.18f,l+ww*.58f+1,t+hh*.82f,p)
        p.textSize = max(10f,w*.0072f); p.color = Color.rgb(128,235,145)
        c.drawText("${"%.1f".format(engine.settings.stimpMeters)}",l+ww*.78f,t+hh*.42f,p)
        p.textSize = max(7f,w*.0052f); p.color = Color.argb(180,230,235,230)
        c.drawText("GREEN",l+ww*.78f,t+hh*.73f,p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawMode(c: Canvas) {
        val game = engine.gameModes.status
        if (game.playerCount <= 1 && game.totalHoles <= 0 && game.mode == PracticeMode.PRACTICE) return
        val w=width.toFloat(); val h=height.toFloat()
        p.typeface=Typeface.DEFAULT_BOLD; p.textSize=max(8f,w*.0057f); p.color=Color.argb(210,255,255,255)
        val text = buildString {
            if (game.totalHoles > 0) append("HOLE ${game.hole}/${game.totalHoles}") else append(game.mode.label)
            if (game.playerCount > 1) append("  ·  P${game.activePlayer}/${game.playerCount}")
        }
        c.drawText(text,w*.025f,h*.055f,p)
    }

    private fun drawTelemetry(c: Canvas) {
        val shot=engine.currentShot ?: return
        if (engine.lastResult == null && engine.state?.running != true && !TvInstantRollRuntime.isAnimating()) return
        val w=width.toFloat(); val h=height.toFloat(); val q=V16MetricConfidenceEstimator.estimate(shot)
        val items=listOf(
            Triple("BALL","${"%.2f".format(shot.ballSpeedMps)}",q.ballSpeed),
            Triple("START","${"%+.2f".format(shot.launchAngleDeg)}°",q.launch),
            Triple("FACE",shot.faceAngleDeg?.let{"${"%+.2f".format(it)}°"}?:"--",q.face),
            Triple("PATH",shot.pathAngleDeg?.let{"${"%+.2f".format(it)}°"}?:"--",q.path)
        )
        val l=w*.022f; val b=h*.955f; val hh=h*.080f; val ww=w*.330f
        p.color=Color.argb(140,15,19,18); c.drawRoundRect(RectF(l,b-hh,l+ww,b),h*.012f,h*.012f,p)
        val cw=ww/items.size
        items.forEachIndexed{i,v->
            val x=l+cw*i+cw*.13f
            p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(6.5f,w*.0045f);p.color=Color.argb(160,225,230,225);c.drawText(v.first,x,b-hh*.62f,p)
            p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);p.textSize=max(13f,w*.0094f);p.color=if(v.third>=.72)Color.WHITE else Color.rgb(255,215,105);c.drawText(v.second,x,b-hh*.25f,p)
        }
    }

    private fun drawReadChallenge(c: Canvas) {
        val f=V20GreenReadTrainingRuntime.feedback
        if(!f.active) return
        val w=width.toFloat(); val h=height.toFloat()
        p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD
        if(!f.revealed){
            p.textSize=max(10f,w*.0072f);p.color=Color.argb(225,255,255,255)
            c.drawText(f.headline,w*.5f,h*.145f,p)
            p.textSize=max(7f,w*.005f);p.color=Color.argb(190,236,240,236);c.drawText("라인을 먼저 읽고 퍼팅 · 정답은 샷 후 공개",w*.5f,h*.172f,p)
        }else{
            p.textSize=max(12f,w*.0085f);p.color=Color.rgb(255,216,72);c.drawText(f.headline,w*.5f,h*.145f,p)
            p.textSize=max(7f,w*.005f);p.color=Color.WHITE;c.drawText(f.detail,w*.5f,h*.174f,p)
        }
        p.textAlign=Paint.Align.LEFT
    }

    private fun drawResult(c: Canvas) {
        val r=engine.lastResult?:return
        val w=width.toFloat();val h=height.toFloat();val ww=w*.215f;val hh=h*.112f;val l=w*.5f-ww*.5f;val t=h*.70f
        p.color=Color.argb(184,15,20,18);c.drawRoundRect(RectF(l,t,l+ww,t+hh),h*.018f,h*.018f,p)
        val title=when{r.holed->"NICE PUTT";r.lipOut->"LIP OUT";r.finishY<engine.settings.holeDistanceM->"SHORT";else->"RESULT"}
        p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.DEFAULT_BOLD;p.textSize=max(10f,w*.0072f);p.color=if(r.holed)Color.rgb(255,215,70)else Color.rgb(126,235,148);c.drawText(title,w*.5f,t+hh*.31f,p)
        p.textSize=max(25f,w*.018f);p.color=Color.WHITE
        c.drawText(if(r.holed)"IN" else "${"%.0f".format(r.distanceToCupM*100)} cm",w*.5f,t+hh*.68f,p)
        p.textSize=max(7f,w*.005f);p.color=Color.argb(185,230,235,230);c.drawText(engine.coachFeedback?.headline?:"분석 완료",w*.5f,t+hh*.89f,p)
        p.textAlign=Paint.Align.LEFT
    }
}
