package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V131 is the first real renderer-layer replacement rather than another Canvas/GLES skin.
 *
 * - GameEngine, GreenTerrain and all camera / HFR authority stay untouched.
 * - Filament owns only presentation: PBR lighting, tone mapping and the dynamic terrain mesh.
 * - V129 remains the hard fallback if Filament cannot initialize on a device.
 *
 * The first pass deliberately keeps the asset surface procedural. GLB vegetation / props can be
 * added later without coupling them to putting physics.
 */
object V131FilamentScreenGolfPresentationFactory {
    fun create(context: Context, engine: GameEngine): View =
        runCatching { V131FilamentStage(context, engine) }
            .getOrElse {
                V131FilamentRuntime.lastFailure = it.message ?: it.javaClass.simpleName
                V129ScreenGolfPresentationFactory.create(context, engine)
            }
}

object V131FilamentRuntime {
    @Volatile var lastFailure: String? = null
}

data class V131RenderPlan(
    val greenCols: Int,
    val greenRows: Int,
    val roughCols: Int,
    val roughRows: Int
)

object V131RenderPlanner {
    fun plan(tier: V24RenderTier, distanceMRaw: Double): V131RenderPlan {
        val long = (distanceMRaw.takeIf { it.isFinite() } ?: 5.0) >= 12.0
        return when (tier) {
            V24RenderTier.HIGH -> V131RenderPlan(48, if (long) 112 else 96, 26, if (long) 70 else 58)
            V24RenderTier.BALANCED -> V131RenderPlan(38, if (long) 88 else 76, 20, if (long) 54 else 46)
            V24RenderTier.PERFORMANCE -> V131RenderPlan(28, if (long) 64 else 56, 14, if (long) 40 else 34)
        }
    }
}

private data class V131SceneKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val tier: V24RenderTier
)

private class V131FilamentStage(
    context: Context,
    private val game: GameEngine
) : FrameLayout(context) {
    private val surface = SurfaceView(context)
    private val controller: V131FilamentController

    init {
        setBackgroundColor(Color.BLACK)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controller = V131FilamentController(context.applicationContext, surface, game)
        addView(V131GradeOverlay(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(V128CommercialScreenGolfHudView(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.start()
    }

    override fun onDetachedFromWindow() {
        controller.stop()
        controller.destroy()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) controller.start() else controller.stop()
    }
}

/** Minimal broadcast grade above the SurfaceView; all actual course lighting is in Filament. */
private class V131GradeOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(38, 22, 55, 82), Color.TRANSPARENT, Color.argb(26, 0, 18, 9)),
            floatArrayOf(0f, .48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(
            w * .78f, h * .13f, max(w, h) * .29f,
            intArrayOf(Color.argb(32, 255, 244, 202), Color.argb(7, 255, 244, 202), Color.TRANSPARENT),
            floatArrayOf(0f, .46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * .78f, h * .13f, max(w, h) * .29f, paint)
        paint.shader = null
    }
}

private class V131FilamentController(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val game: GameEngine
) : UiHelper.RendererCallback, Choreographer.FrameCallback {

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: com.google.android.filament.View
    private val camera: Camera
    private val uiHelper: UiHelper
    private val displayHelper = DisplayHelper(context)
    private val choreographer = Choreographer.getInstance()
    private var swapChain: SwapChain? = null
    private var running = false
    private var destroyed = false
    private var viewportWidth = 1
    private var viewportHeight = 1

    private lateinit var surfaceMaterial: Material
    private lateinit var ballMaterial: Material
    private val materialInstances = mutableListOf<MaterialInstance>()
    private val sceneMeshes = mutableListOf<V131FilamentMesh>()
    private var ballMesh: V131FilamentMesh? = null
    private var sceneKey: V131SceneKey? = null
    private var skybox: Skybox? = null
    private var sun = 0
    private var fill = 0

    init {
        Filament.init()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())
        view.camera = camera
        view.scene = scene
        view.isPostProcessingEnabled = true

        buildMaterials()
        setupLighting()
        setupBall()

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = this
        uiHelper.attachTo(surfaceView)
    }

    fun start() {
        if (destroyed || running) return
        running = true
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || destroyed) return
        try {
            syncScene()
            updateCameraAndBall()
            val sc = swapChain
            if (sc != null && uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        } catch (t: Throwable) {
            V131FilamentRuntime.lastFailure = t.message ?: t.javaClass.simpleName
        } finally {
            if (running && !destroyed) choreographer.postFrameCallback(this)
        }
    }

    private fun buildMaterials() {
        MaterialBuilder.init()
        try {
            surfaceMaterial = buildLitMaterial("PV131 Surface", .84f)
            ballMaterial = buildLitMaterial("PV131 Ball", .22f)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun buildLitMaterial(name: String, roughness: Float): Material {
        val source = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor.rgb = materialParams.baseColor;
                material.roughness = ${roughness.coerceIn(.08f, 1f)};
                material.metallic = 0.0;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name(name)
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .material(source)
            .optimization(MaterialBuilder.Optimization.NONE)
            .build(engine)
        check(pkg.isValid) { "Filament material compile failed: $name" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun materialInstance(material: Material, r: Float, g: Float, b: Float): MaterialInstance =
        material.createInstance().also {
            it.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
            materialInstances += it
        }

    private fun setupLighting() {
        skybox = Skybox.Builder().color(.34f, .62f, .82f, 1f).build(engine).also { scene.skybox = it }

        sun = EntityManager.get().create()
        val sunColor = Colors.cct(5_700f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(sunColor[0], sunColor[1], sunColor[2])
            .intensity(105_000f)
            .direction(-.42f, -.58f, -1f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        fill = EntityManager.get().create()
        val fillColor = Colors.cct(7_200f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(fillColor[0], fillColor[1], fillColor[2])
            .intensity(12_000f)
            .direction(.58f, .25f, -.72f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)

        camera.setExposure(16f, 1f / 125f, 100f)
    }

    private fun setupBall() {
        val geometry = V131GeometryFactory.sphere(.035f, 12, 20)
        ballMesh = createRenderable(
            geometry,
            materialInstance(ballMaterial, .97f, .98f, .99f),
            Box(0f, 0f, 0f, .05f, .05f, .05f)
        )
    }

    private fun syncScene() {
        val settings = game.settings.copy()
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val key = V131SceneKey(
            settings.terrainProfileId,
            (d * 100).toInt(),
            ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier
        )
        if (key == sceneKey) return
        sceneKey = key

        sceneMeshes.toList().forEach { destroyMesh(it) }
        sceneMeshes.clear()

        val plan = V131RenderPlanner.plan(tier, d)
        val rough = V131GeometryFactory.rough(settings, plan)
        sceneMeshes += createRenderable(
            rough,
            materialInstance(surfaceMaterial, .105f, .315f, .085f),
            rough.bounds
        )

        val fringe = V131GeometryFactory.green(settings, plan, fringe = true, stripeParity = -1)
        sceneMeshes += createRenderable(
            fringe,
            materialInstance(surfaceMaterial, .185f, .495f, .135f),
            fringe.bounds
        )

        val stripeA = V131GeometryFactory.green(settings, plan, fringe = false, stripeParity = 0)
        val stripeB = V131GeometryFactory.green(settings, plan, fringe = false, stripeParity = 1)
        sceneMeshes += createRenderable(
            stripeA,
            materialInstance(surfaceMaterial, .235f, .625f, .175f),
            stripeA.bounds
        )
        sceneMeshes += createRenderable(
            stripeB,
            materialInstance(surfaceMaterial, .275f, .675f, .195f),
            stripeB.bounds
        )

        val cupZ = GreenTerrain.effectiveHeightAt(settings, 0.0, d).toFloat() + .014f
        val cup = V131GeometryFactory.ring(0f, d.toFloat(), cupZ, .031f, .064f, 30)
        sceneMeshes += createRenderable(
            cup,
            materialInstance(surfaceMaterial, .93f, .94f, .90f),
            cup.bounds
        )
        val hole = V131GeometryFactory.disc(0f, d.toFloat(), cupZ - .004f, .034f, 28)
        sceneMeshes += createRenderable(
            hole,
            materialInstance(surfaceMaterial, .015f, .018f, .016f),
            hole.bounds
        )

        val pole = V131GeometryFactory.box(-.006f, d.toFloat() - .006f, cupZ, .006f, d.toFloat() + .006f, cupZ + .86f)
        sceneMeshes += createRenderable(
            pole,
            materialInstance(surfaceMaterial, .92f, .92f, .88f),
            pole.bounds
        )
        val flag = V131GeometryFactory.flag(0f, d.toFloat(), cupZ + .86f)
        sceneMeshes += createRenderable(
            flag,
            materialInstance(surfaceMaterial, .82f, .08f, .055f),
            flag.bounds
        )
    }

    private fun createRenderable(
        geometry: V131Geometry,
        material: MaterialInstance,
        bounds: Box
    ): V131FilamentMesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(geometry.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, V131Geometry.VERTEX_BYTES)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, V131Geometry.VERTEX_BYTES)
            .build(engine)
        vb.setBufferAt(engine, 0, geometry.vertices)

        val ib = IndexBuffer.Builder()
            .indexCount(geometry.indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, geometry.indices)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, geometry.indexCount)
            .material(0, material)
            .build(engine, entity)
        scene.addEntity(entity)
        return V131FilamentMesh(entity, vb, ib, material)
    }

    private fun updateCameraAndBall() {
        val settings = game.settings
        val state = game.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: start.first
        val by = display?.second ?: state?.y ?: start.second
        val frame = V128ScreenGolfCameraPlanner.plan(
            settings.holeDistanceM,
            start.first,
            start.second,
            bx,
            by,
            state?.running == true || TvInstantRollRuntime.isAnimating(),
            game.lastResult
        )
        val aspect = viewportWidth.toDouble() / viewportHeight.coerceAtLeast(1).toDouble()
        camera.setProjection(frame.fovDeg.toDouble(), aspect, .035, 90.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            frame.eyeX.toDouble(), frame.eyeY.toDouble(), frame.eyeZ.toDouble(),
            frame.lookX.toDouble(), frame.lookY.toDouble(), frame.lookZ.toDouble(),
            0.0, 0.0, 1.0
        )

        ballMesh?.let { mesh ->
            val z = GreenTerrain.effectiveHeightAt(settings, bx, by).toFloat() + .041f
            val transform = floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                bx.toFloat(), by.toFloat(), z, 1f
            )
            val tm = engine.transformManager
            tm.setTransform(tm.getInstance(mesh.entity), transform)
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        if (destroyed) return
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface)
        displayHelper.attach(renderer, surfaceView.display)
    }

    override fun onDetachedFromSurface() {
        if (destroyed) return
        displayHelper.detach()
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait()
            swapChain = null
        }
    }

    override fun onResized(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
        FilamentHelper.synchronizePendingFrames(engine)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        stop()
        runCatching { uiHelper.detach() }
        runCatching { displayHelper.detach() }
        sceneMeshes.toList().forEach { runCatching { destroyMesh(it) } }
        sceneMeshes.clear()
        ballMesh?.let { runCatching { destroyMesh(it) } }
        ballMesh = null
        swapChain?.let { runCatching { engine.destroySwapChain(it) } }
        swapChain = null
        materialInstances.clear()
        runCatching { engine.destroyMaterial(surfaceMaterial) }
        runCatching { engine.destroyMaterial(ballMaterial) }
        if (sun != 0) {
            runCatching { engine.destroyEntity(sun) }
            runCatching { EntityManager.get().destroy(sun) }
        }
        if (fill != 0) {
            runCatching { engine.destroyEntity(fill) }
            runCatching { EntityManager.get().destroy(fill) }
        }
        runCatching { engine.destroyRenderer(renderer) }
        runCatching { engine.destroyView(view) }
        runCatching { engine.destroyScene(scene) }
        runCatching { engine.destroyCameraComponent(camera.entity) }
        runCatching { EntityManager.get().destroy(camera.entity) }
        runCatching { engine.destroy() }
    }

    private fun destroyMesh(mesh: V131FilamentMesh) {
        runCatching { scene.removeEntity(mesh.entity) }
        runCatching { engine.destroyEntity(mesh.entity) }
        runCatching { EntityManager.get().destroy(mesh.entity) }
        runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
        runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        runCatching { engine.destroyMaterialInstance(mesh.material) }
        materialInstances.remove(mesh.material)
    }
}

private data class V131FilamentMesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance
)

private data class V131Geometry(
    val vertices: ByteBuffer,
    val indices: ByteBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val bounds: Box
) {
    companion object { const val VERTEX_BYTES = 28 }
}

private class V131GeometryBuilder {
    private data class Vertex(val p: FloatArray, val n: FloatArray)
    private val vertices = ArrayList<Vertex>()
    private val indices = ArrayList<Short>()
    private var minX = Float.POSITIVE_INFINITY
    private var minY = Float.POSITIVE_INFINITY
    private var minZ = Float.POSITIVE_INFINITY
    private var maxX = Float.NEGATIVE_INFINITY
    private var maxY = Float.NEGATIVE_INFINITY
    private var maxZ = Float.NEGATIVE_INFINITY

    fun tri(a: FloatArray, b: FloatArray, c: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 3 < 65535) { "V131 mesh exceeds ushort index range" }
        addVertex(a, normal); addVertex(b, normal); addVertex(c, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 4 < 65535) { "V131 mesh exceeds ushort index range" }
        addVertex(a, normal); addVertex(b, normal); addVertex(c, normal); addVertex(d, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
        indices += base.toShort(); indices += (base + 2).toShort(); indices += (base + 3).toShort()
    }

    private fun addVertex(p: FloatArray, nRaw: FloatArray) {
        val n = normalize(nRaw)
        vertices += Vertex(p, n)
        minX = minOf(minX, p[0]); minY = minOf(minY, p[1]); minZ = minOf(minZ, p[2])
        maxX = maxOf(maxX, p[0]); maxY = maxOf(maxY, p[1]); maxZ = maxOf(maxZ, p[2])
    }

    fun build(): V131Geometry {
        require(vertices.isNotEmpty() && indices.isNotEmpty()) { "empty V131 geometry" }
        val vb = ByteBuffer.allocateDirect(vertices.size * V131Geometry.VERTEX_BYTES).order(ByteOrder.nativeOrder())
        vertices.forEach { v ->
            vb.putFloat(v.p[0]); vb.putFloat(v.p[1]); vb.putFloat(v.p[2])
            val tangent = tangentFrame(v.n)
            tangent.forEach(vb::putFloat)
        }
        vb.flip()
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        indices.forEach(ib::putShort)
        ib.flip()
        val cx = (minX + maxX) * .5f
        val cy = (minY + maxY) * .5f
        val cz = (minZ + maxZ) * .5f
        val hx = max(.01f, (maxX - minX) * .5f)
        val hy = max(.01f, (maxY - minY) * .5f)
        val hz = max(.01f, (maxZ - minZ) * .5f)
        return V131Geometry(vb, ib, vertices.size, indices.size, Box(cx, cy, cz, hx, hy, hz))
    }

    private fun tangentFrame(n: FloatArray): FloatArray {
        var tx = 1f
        var ty = 0f
        var tz = if (kotlin.math.abs(n[2]) > .001f) -n[0] / n[2] else 0f
        val tmag = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(.001f)
        tx /= tmag; ty /= tmag; tz /= tmag
        var bx = n[1] * tz - n[2] * ty
        var by = n[2] * tx - n[0] * tz
        var bz = n[0] * ty - n[1] * tx
        val bmag = sqrt(bx * bx + by * by + bz * bz).coerceAtLeast(.001f)
        bx /= bmag; by /= bmag; bz /= bmag
        return FloatArray(4).also {
            MathUtils.packTangentFrame(tx, ty, tz, bx, by, bz, n[0], n[1], n[2], it)
        }
    }

    private fun normalize(v: FloatArray): FloatArray {
        val m = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(.001f)
        return floatArrayOf(v[0] / m, v[1] / m, v[2] / m)
    }
}

private object V131GeometryFactory {
    fun rough(settings: GreenSettings, plan: V131RenderPlan): V131Geometry {
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val length = max(15.0, d + 18.0)
        val cols = plan.roughCols
        val rows = plan.roughRows
        val half = 15.0
        val b = V131GeometryBuilder()
        for (r in 0 until rows) {
            val y0 = -4.0 + (length + 4.0) * r / rows
            val y1 = -4.0 + (length + 4.0) * (r + 1) / rows
            for (c in 0 until cols) {
                val x0 = -half + 2.0 * half * c / cols
                val x1 = -half + 2.0 * half * (c + 1) / cols
                fun z(x: Double, y: Double): Float {
                    val base = if (y in 0.0..(d * 1.45)) {
                        GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.5, 4.5), y).toFloat()
                    } else 0f
                    return base - .060f + (.018 * sin(x * .74 + y * .18) + .012 * sin(y * .43 - x * .31)).toFloat()
                }
                val p0 = floatArrayOf(x0.toFloat(), y0.toFloat(), z(x0, y0))
                val p1 = floatArrayOf(x1.toFloat(), y0.toFloat(), z(x1, y0))
                val p2 = floatArrayOf(x1.toFloat(), y1.toFloat(), z(x1, y1))
                val p3 = floatArrayOf(x0.toFloat(), y1.toFloat(), z(x0, y1))
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    fun green(settings: GreenSettings, plan: V131RenderPlan, fringe: Boolean, stripeParity: Int): V131Geometry {
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val length = max(4.4, d * 1.42)
        val cols = plan.greenCols
        val rows = plan.greenRows
        val b = V131GeometryBuilder()
        fun halfWidth(y: Double): Double {
            val t = (y / length).coerceIn(0.0, 1.0)
            val base = max(1.72, d * .235)
            val shoulder = .88 + .11 * sin(PI * t) + .055 * sin(t * 3.0 * PI + .55)
            val irregular = .045 * sin(y * .73) + .028 * sin(y * 1.57 + .8)
            return (base * shoulder + irregular + if (fringe) .34 else 0.0).coerceAtLeast(1.4)
        }
        fun point(x: Double, y: Double): FloatArray = floatArrayOf(
            x.toFloat(),
            y.toFloat(),
            GreenTerrain.effectiveHeightAt(settings, x, y).toFloat() + if (fringe) .002f else .010f
        )
        fun normalAt(x: Double, y: Double): FloatArray {
            val slope = GreenTerrain.effectiveSlopeAt(settings, x, y)
            return floatArrayOf(
                (-slope.sidePct / 100.0).toFloat(),
                (-slope.longPct / 100.0).toFloat(),
                1f
            )
        }
        for (r in 0 until rows) {
            val y0 = length * r / rows
            val y1 = length * (r + 1) / rows
            val stripe = ((y0 / .42).toInt() and 1)
            if (!fringe && stripeParity >= 0 && stripe != stripeParity) continue
            val hw0 = halfWidth(y0)
            val hw1 = halfWidth(y1)
            for (c in 0 until cols) {
                val x00 = -hw0 + 2.0 * hw0 * c / cols
                val x10 = -hw0 + 2.0 * hw0 * (c + 1) / cols
                val x01 = -hw1 + 2.0 * hw1 * c / cols
                val x11 = -hw1 + 2.0 * hw1 * (c + 1) / cols
                val n = normalAt((x00 + x11) * .5, (y0 + y1) * .5)
                b.quad(point(x00, y0), point(x10, y0), point(x11, y1), point(x01, y1), n)
            }
        }
        return b.build()
    }

    fun sphere(radius: Float, latN: Int, lonN: Int): V131Geometry {
        val b = V131GeometryBuilder()
        fun p(lat: Int, lon: Int): FloatArray {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            return floatArrayOf(
                (cos(phi) * cos(theta)).toFloat() * radius,
                (cos(phi) * sin(theta)).toFloat() * radius,
                sin(phi).toFloat() * radius
            )
        }
        for (lat in 0 until latN) for (lon in 0 until lonN) {
            val nl = (lon + 1) % lonN
            val a = p(lat, lon); val bb = p(lat + 1, lon); val c = p(lat + 1, nl); val d = p(lat, nl)
            b.tri(a, bb, c, normalFromPoint(a))
            b.tri(a, c, d, normalFromPoint(a))
        }
        return b.build()
    }

    fun ring(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, steps: Int): V131Geometry {
        val b = V131GeometryBuilder()
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = floatArrayOf(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, cz)
            val i1 = floatArrayOf(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, cz)
            val o0 = floatArrayOf(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, cz)
            val o1 = floatArrayOf(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, cz)
            b.quad(i0, o0, o1, i1, floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun disc(cx: Float, cy: Float, cz: Float, radius: Float, steps: Int): V131Geometry {
        val b = V131GeometryBuilder()
        val center = floatArrayOf(cx, cy, cz)
        val n = steps.coerceIn(12, 48)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(
                center,
                floatArrayOf(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, cz),
                floatArrayOf(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, cz),
                floatArrayOf(0f, 0f, 1f)
            )
        }
        return b.build()
    }

    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): V131Geometry {
        val b = V131GeometryBuilder()
        val p000 = floatArrayOf(x0, y0, z0); val p100 = floatArrayOf(x1, y0, z0)
        val p110 = floatArrayOf(x1, y1, z0); val p010 = floatArrayOf(x0, y1, z0)
        val p001 = floatArrayOf(x0, y0, z1); val p101 = floatArrayOf(x1, y0, z1)
        val p111 = floatArrayOf(x1, y1, z1); val p011 = floatArrayOf(x0, y1, z1)
        b.quad(p000, p100, p110, p010, floatArrayOf(0f, 0f, -1f))
        b.quad(p001, p011, p111, p101, floatArrayOf(0f, 0f, 1f))
        b.quad(p000, p001, p101, p100, floatArrayOf(0f, -1f, 0f))
        b.quad(p010, p110, p111, p011, floatArrayOf(0f, 1f, 0f))
        b.quad(p000, p010, p011, p001, floatArrayOf(-1f, 0f, 0f))
        b.quad(p100, p101, p111, p110, floatArrayOf(1f, 0f, 0f))
        return b.build()
    }

    fun flag(cx: Float, cy: Float, topZ: Float): V131Geometry {
        val b = V131GeometryBuilder()
        val z0 = topZ - .03f
        val z1 = topZ - .29f
        val a = floatArrayOf(cx, cy, z0)
        val bb = floatArrayOf(cx + .42f, cy, z0 - .08f)
        val c = floatArrayOf(cx, cy, z1)
        b.tri(a, bb, c, floatArrayOf(0f, -1f, 0f))
        b.tri(c, bb, a, floatArrayOf(0f, 1f, 0f))
        return b.build()
    }

    private fun normalFromPoint(p: FloatArray): FloatArray {
        val m = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]).coerceAtLeast(.001f)
        return floatArrayOf(p[0] / m, p[1] / m, p[2] / m)
    }

    private fun normalFromQuad(a: FloatArray, b: FloatArray, d: FloatArray): FloatArray {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = d[0] - a[0]; val vy = d[1] - a[1]; val vz = d[2] - a[2]
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val m = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
        return floatArrayOf(nx / m, ny / m, nz / m)
    }
}
