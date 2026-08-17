package com.puttvision.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V133 deliberately removes the V132 camera-agnostic Canvas course art.
 *
 * Everything that looks like part of the golf course now lives in the same Filament 3D world as
 * the ball and flag. Measurement, HFR, calibration and GreenPhysics stay authoritative elsewhere.
 */
object V133FilamentCourseFactory {
    fun create(context: Context, game: GameEngine): View =
        runCatching { V133Stage(context, game) }
            .getOrElse {
                V133Runtime.lastFailure = it.message ?: it.javaClass.simpleName
                V131FilamentScreenGolfPresentationFactory.create(context, game)
            }
}

object V133Runtime {
    @Volatile var lastFailure: String? = null
}

object V133CourseSpec {
    const val BALL_RADIUS_M = 0.02135f
    const val HOLE_RADIUS_M = 0.054f
    const val FLAG_HEIGHT_M = 1.05f
}

data class V133CameraFrame(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val lookX: Float,
    val lookY: Float,
    val lookZ: Float,
    val fovDeg: Float
)

object V133CameraPlanner {
    fun plan(
        distanceMRaw: Double,
        startXRaw: Double,
        startYRaw: Double,
        ballXRaw: Double,
        ballYRaw: Double,
        running: Boolean,
        result: SimResult?
    ): V133CameraFrame {
        val d = distanceMRaw.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val sx = startXRaw.takeIf { it.isFinite() } ?: 0.0
        val sy = startYRaw.takeIf { it.isFinite() } ?: 0.0
        val bx = ballXRaw.takeIf { it.isFinite() } ?: sx
        val by = ballYRaw.takeIf { it.isFinite() } ?: sy
        val progress = (by / d).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0

        return when {
            result != null && (result.holed || result.lipOut || result.distanceToCupM < .55) ->
                V133CameraFrame(
                    eyeX = 1.35f,
                    eyeY = (d - 2.35).toFloat(),
                    eyeZ = 1.02f,
                    lookX = 0f,
                    lookY = d.toFloat(),
                    lookZ = .035f,
                    fovDeg = 34.5f
                )
            running && progress > .72 ->
                V133CameraFrame(
                    eyeX = (bx * .12 + .62).toFloat(),
                    eyeY = (by - 2.75).toFloat(),
                    eyeZ = 1.22f,
                    lookX = (bx * .16).toFloat(),
                    lookY = min(d, by + 2.2).toFloat(),
                    lookZ = .06f,
                    fovDeg = 36.5f
                )
            running ->
                V133CameraFrame(
                    eyeX = (sx * .32 + bx * .05).toFloat(),
                    eyeY = (by - max(3.35, d * .34)).toFloat(),
                    eyeZ = (1.48 + min(.24, d * .009)).toFloat(),
                    lookX = (bx * .18).toFloat(),
                    lookY = min(d, by + max(3.25, d * .30)).toFloat(),
                    lookZ = .075f,
                    fovDeg = 38.5f
                )
            else ->
                V133CameraFrame(
                    eyeX = (sx * .35).toFloat(),
                    eyeY = (sy - max(3.85, d * .52)).toFloat(),
                    eyeZ = (1.62 + min(.30, d * .012)).toFloat(),
                    lookX = (sx * .12).toFloat(),
                    lookY = min(d, sy + max(3.7, d * .70)).toFloat(),
                    lookZ = .075f,
                    fovDeg = 38.0f
                )
        }
    }
}

private class V133Stage(context: Context, private val game: GameEngine) : FrameLayout(context) {
    private val surface = SurfaceView(context)
    private val controller: V133Controller

    init {
        setBackgroundColor(Color.rgb(99, 132, 143))
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controller = V133Controller(context.applicationContext, surface, game)
        addView(V133Hud(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.start()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) controller.start() else controller.stop()
    }

    override fun onDetachedFromWindow() {
        controller.stop()
        controller.destroy()
        super.onDetachedFromWindow()
    }
}

private data class V133SceneKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val tier: V24RenderTier
)

private data class V133Mesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance
)

private class V133Controller(
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

    private lateinit var turfMaterial: Material
    private lateinit var sandMaterial: Material
    private lateinit var propMaterial: Material
    private lateinit var ballMaterial: Material
    private val materialInstances = mutableListOf<MaterialInstance>()
    private val sceneMeshes = mutableListOf<V133Mesh>()
    private var ballMesh: V133Mesh? = null
    private var sceneKey: V133SceneKey? = null
    private var skybox: Skybox? = null
    private var sun = 0
    private var fill = 0

    init {
        com.google.android.filament.Filament.init()
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
            V133Runtime.lastFailure = t.message ?: t.javaClass.simpleName
        } finally {
            if (running && !destroyed) choreographer.postFrameCallback(this)
        }
    }

    private fun buildMaterials() {
        MaterialBuilder.init()
        try {
            turfMaterial = buildTurfMaterial()
            sandMaterial = buildLitMaterial("PV133 Sand", .96f, .34f)
            propMaterial = buildLitMaterial("PV133 Prop", .72f, .42f)
            ballMaterial = buildLitMaterial("PV133 Ball", .18f, .52f)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun buildTurfMaterial(): Material {
        val source = """
            void material(inout MaterialInputs material) {
                vec3 p = getUserWorldPosition();
                float bladeX = sin(p.x * 118.0 + p.y * 11.0);
                float bladeY = cos(p.y * 127.0 - p.x * 9.0);
                material.normal = normalize(vec3(bladeX * 0.035, bladeY * 0.035, 1.0));
                prepareMaterial(material);
                float broad = 0.972 + 0.018 * sin(p.x * 1.35 + p.y * 0.71)
                                      + 0.014 * sin(p.y * 2.41 - p.x * 0.37);
                float fine = 0.012 * bladeX * bladeY;
                material.baseColor.rgb = materialParams.baseColor * (broad + fine);
                material.roughness = 0.92;
                material.reflectance = 0.26;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV133 Procedural Turf")
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V133 turf material compile failed" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun buildLitMaterial(name: String, roughness: Float, reflectance: Float): Material {
        val source = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor.rgb = materialParams.baseColor;
                material.roughness = ${roughness.coerceIn(.05f, 1f)};
                material.reflectance = ${reflectance.coerceIn(.05f, .9f)};
                material.metallic = 0.0;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name(name)
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V133 material compile failed: $name" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun materialInstance(material: Material, r: Float, g: Float, b: Float): MaterialInstance =
        material.createInstance().also {
            it.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
            materialInstances += it
        }

    private fun setupLighting() {
        skybox = Skybox.Builder().color(.43f, .58f, .64f, 1f).build(engine).also { scene.skybox = it }

        sun = EntityManager.get().create()
        val sunColor = Colors.cct(5_450f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(sunColor[0], sunColor[1], sunColor[2])
            .intensity(82_000f)
            .direction(-.38f, -.62f, -.92f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        fill = EntityManager.get().create()
        val fillColor = Colors.cct(7_000f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(fillColor[0], fillColor[1], fillColor[2])
            .intensity(7_500f)
            .direction(.48f, .20f, -.72f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)

        camera.setExposure(11f, 1f / 125f, 100f)
    }

    private fun setupBall() {
        val geometry = V133Geometry.sphere(0f, 0f, 0f, V133CourseSpec.BALL_RADIUS_M, 12, 18)
        ballMesh = createRenderable(
            geometry,
            materialInstance(ballMaterial, .94f, .95f, .93f),
            castShadow = true,
            contactShadow = true
        )
    }

    private fun syncScene() {
        val settings = game.settings.copy()
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val key = V133SceneKey(
            settings.terrainProfileId,
            (d * 100).toInt(),
            ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            tier
        )
        if (key == sceneKey) return
        sceneKey = key

        sceneMeshes.toList().forEach(::destroyMesh)
        sceneMeshes.clear()

        val density = when (tier) {
            V24RenderTier.HIGH -> 1.0
            V24RenderTier.BALANCED -> .78
            V24RenderTier.PERFORMANCE -> .58
        }

        val ground = V133Geometry.courseGround(settings, d, density)
        sceneMeshes += createRenderable(
            ground,
            materialInstance(turfMaterial, .115f, .245f, .085f),
            castShadow = false,
            contactShadow = false
        )

        val fringe = V133Geometry.green(settings, d, density, fringe = true)
        sceneMeshes += createRenderable(
            fringe,
            materialInstance(turfMaterial, .155f, .335f, .105f),
            castShadow = false,
            contactShadow = false
        )

        val green = V133Geometry.green(settings, d, density, fringe = false)
        sceneMeshes += createRenderable(
            green,
            materialInstance(turfMaterial, .205f, .455f, .135f),
            castShadow = false,
            contactShadow = false
        )

        addBunkers(settings, d)
        addTrees(settings, d, tier)
        addCupAndFlag(settings, d)
    }

    private fun addBunkers(settings: GreenSettings, d: Double) {
        val placements = listOf(
            floatArrayOf(-3.05f, (d * .72).toFloat(), .95f, .46f),
            floatArrayOf(3.20f, (d * .90 + .45).toFloat(), 1.08f, .52f)
        )
        placements.forEach { p ->
            val z = V133Geometry.groundHeight(settings, d, p[0].toDouble(), p[1].toDouble()) + .012f
            val bunker = V133Geometry.ellipseDisc(p[0], p[1], z, p[2], p[3], 38)
            sceneMeshes += createRenderable(
                bunker,
                materialInstance(sandMaterial, .69f, .65f, .51f),
                castShadow = false,
                contactShadow = false
            )
        }
    }

    private fun addTrees(settings: GreenSettings, d: Double, tier: V24RenderTier) {
        val count = when (tier) {
            V24RenderTier.HIGH -> 12
            V24RenderTier.BALANCED -> 9
            V24RenderTier.PERFORMANCE -> 6
        }
        for (i in 0 until count) {
            val side = if (i and 1 == 0) -1f else 1f
            val lane = i / 2
            val x = side * (5.7f + (lane % 3) * 1.55f + ((i * 37) % 10) * .07f)
            val y = (d + 4.8 + lane * 2.15 + (i % 3) * .43).toFloat()
            val scale = .78f + ((i * 17) % 7) * .055f
            val baseZ = V133Geometry.groundHeight(settings, d, x.toDouble(), y.toDouble())

            val trunk = V133Geometry.cylinder(x, y, baseZ, .065f * scale, .72f * scale, 10)
            sceneMeshes += createRenderable(
                trunk,
                materialInstance(propMaterial, .26f, .17f, .095f),
                castShadow = true,
                contactShadow = false
            )

            val crownZ = baseZ + .88f * scale
            val crown = V133Geometry.ellipsoid(
                x,
                y,
                crownZ,
                .54f * scale,
                .48f * scale,
                .72f * scale,
                7,
                11
            )
            val tone = if (i % 3 == 0) floatArrayOf(.12f, .25f, .10f) else floatArrayOf(.10f, .22f, .085f)
            sceneMeshes += createRenderable(
                crown,
                materialInstance(propMaterial, tone[0], tone[1], tone[2]),
                castShadow = true,
                contactShadow = false
            )
        }
    }

    private fun addCupAndFlag(settings: GreenSettings, d: Double) {
        val base = GreenTerrain.effectiveHeightAt(settings, 0.0, d).toFloat() + .020f
        val hole = V133Geometry.disc(0f, d.toFloat(), base + .001f, V133CourseSpec.HOLE_RADIUS_M, 32)
        sceneMeshes += createRenderable(
            hole,
            materialInstance(propMaterial, .018f, .021f, .018f),
            castShadow = false,
            contactShadow = false
        )

        val lip = V133Geometry.ring(0f, d.toFloat(), base + .002f, .052f, .057f, 32)
        sceneMeshes += createRenderable(
            lip,
            materialInstance(propMaterial, .72f, .73f, .68f),
            castShadow = false,
            contactShadow = false
        )

        val pole = V133Geometry.cylinder(
            0f,
            d.toFloat(),
            base + .004f,
            .0065f,
            V133CourseSpec.FLAG_HEIGHT_M,
            12
        )
        sceneMeshes += createRenderable(
            pole,
            materialInstance(propMaterial, .83f, .84f, .80f),
            castShadow = true,
            contactShadow = true
        )

        val flag = V133Geometry.flag(0f, d.toFloat(), base + V133CourseSpec.FLAG_HEIGHT_M)
        sceneMeshes += createRenderable(
            flag,
            materialInstance(propMaterial, .64f, .075f, .055f),
            castShadow = true,
            contactShadow = false
        )
    }

    private fun createRenderable(
        geometry: V133GeometryData,
        material: MaterialInstance,
        castShadow: Boolean,
        contactShadow: Boolean
    ): V133Mesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(geometry.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, V133GeometryData.VERTEX_BYTES)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, V133GeometryData.VERTEX_BYTES)
            .build(engine)
        vb.setBufferAt(engine, 0, geometry.vertices)

        val ib = IndexBuffer.Builder()
            .indexCount(geometry.indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, geometry.indices)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(geometry.bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, geometry.indexCount)
            .material(0, material)
            .castShadows(castShadow)
            .receiveShadows(true)
            .screenSpaceContactShadows(contactShadow)
            .build(engine, entity)
        scene.addEntity(entity)
        return V133Mesh(entity, vb, ib, material)
    }

    private fun updateCameraAndBall() {
        val settings = game.settings
        val state = game.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: start.first
        val by = display?.second ?: state?.y ?: start.second
        val frame = V133CameraPlanner.plan(
            settings.holeDistanceM,
            start.first,
            start.second,
            bx,
            by,
            state?.running == true || TvInstantRollRuntime.isAnimating(),
            game.lastResult
        )
        val aspect = viewportWidth.toDouble() / viewportHeight.coerceAtLeast(1).toDouble()
        camera.setProjection(frame.fovDeg.toDouble(), aspect, .04, 100.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            frame.eyeX.toDouble(), frame.eyeY.toDouble(), frame.eyeZ.toDouble(),
            frame.lookX.toDouble(), frame.lookY.toDouble(), frame.lookZ.toDouble(),
            0.0, 0.0, 1.0
        )

        ballMesh?.let { mesh ->
            val cupVerticalOffset = state
                ?.takeIf { it.cupPhase != V134CupPhase.NONE }
                ?.cupVerticalOffsetM
                ?.toFloat()
                ?: 0f
            val z = GreenTerrain.effectiveHeightAt(settings, bx, by).toFloat() +
                V133CourseSpec.BALL_RADIUS_M + .020f + cupVerticalOffset
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
        runCatching { engine.destroyMaterial(turfMaterial) }
        runCatching { engine.destroyMaterial(sandMaterial) }
        runCatching { engine.destroyMaterial(propMaterial) }
        runCatching { engine.destroyMaterial(ballMaterial) }
        skybox?.let { runCatching { engine.destroySkybox(it) } }
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

    private fun destroyMesh(mesh: V133Mesh) {
        runCatching { scene.removeEntity(mesh.entity) }
        runCatching { engine.destroyEntity(mesh.entity) }
        runCatching { EntityManager.get().destroy(mesh.entity) }
        runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
        runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        runCatching { engine.destroyMaterialInstance(mesh.material) }
        materialInstances.remove(mesh.material)
    }
}

private data class V133GeometryData(
    val vertices: ByteBuffer,
    val indices: ByteBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val bounds: Box
) {
    companion object { const val VERTEX_BYTES = 28 }
}

private class V133Builder {
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
        require(base + 3 < 65535) { "V133 mesh exceeds ushort index range" }
        add(a, normal); add(b, normal); add(c, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 4 < 65535) { "V133 mesh exceeds ushort index range" }
        add(a, normal); add(b, normal); add(c, normal); add(d, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
        indices += base.toShort(); indices += (base + 2).toShort(); indices += (base + 3).toShort()
    }

    private fun add(p: FloatArray, normalRaw: FloatArray) {
        val n = normalize(normalRaw)
        vertices += Vertex(p, n)
        minX = minOf(minX, p[0]); minY = minOf(minY, p[1]); minZ = minOf(minZ, p[2])
        maxX = maxOf(maxX, p[0]); maxY = maxOf(maxY, p[1]); maxZ = maxOf(maxZ, p[2])
    }

    fun build(): V133GeometryData {
        require(vertices.isNotEmpty() && indices.isNotEmpty()) { "empty V133 geometry" }
        val vb = ByteBuffer.allocateDirect(vertices.size * V133GeometryData.VERTEX_BYTES).order(ByteOrder.nativeOrder())
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
        return V133GeometryData(
            vb,
            ib,
            vertices.size,
            indices.size,
            Box(
                cx, cy, cz,
                max(.01f, (maxX - minX) * .5f),
                max(.01f, (maxY - minY) * .5f),
                max(.01f, (maxZ - minZ) * .5f)
            )
        )
    }

    private fun tangentFrame(n: FloatArray): FloatArray {
        var tx = 1f
        var ty = 0f
        var tz = if (abs(n[2]) > .001f) -n[0] / n[2] else 0f
        val tm = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(.001f)
        tx /= tm; ty /= tm; tz /= tm
        var bx = n[1] * tz - n[2] * ty
        var by = n[2] * tx - n[0] * tz
        var bz = n[0] * ty - n[1] * tx
        val bm = sqrt(bx * bx + by * by + bz * bz).coerceAtLeast(.001f)
        bx /= bm; by /= bm; bz /= bm
        return FloatArray(4).also { MathUtils.packTangentFrame(tx, ty, tz, bx, by, bz, n[0], n[1], n[2], it) }
    }

    private fun normalize(v: FloatArray): FloatArray {
        val m = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(.001f)
        return floatArrayOf(v[0] / m, v[1] / m, v[2] / m)
    }
}

private object V133Geometry {
    fun groundHeight(settings: GreenSettings, d: Double, x: Double, y: Double): Float {
        val greenInfluence = y in -1.0..(d * 1.55)
        val base = if (greenInfluence) {
            GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.8, 4.8), y.coerceAtLeast(0.0)).toFloat()
        } else 0f
        val noise = (.020 * sin(x * .58 + y * .17) + .014 * sin(y * .39 - x * .29)).toFloat()
        val sideT = ((abs(x) - 5.4) / 8.5).coerceIn(0.0, 1.0)
        val sideRise = (.50 * sideT.pow(1.55)).toFloat()
        val farT = ((y - (d + 4.5)) / 17.0).coerceIn(0.0, 1.0)
        val farRise = (1.20 * farT.pow(1.45) + .16 * farT * sin(x * .31 + y * .14)).toFloat()
        return base - .045f + noise + sideRise + farRise
    }

    fun courseGround(settings: GreenSettings, d: Double, density: Double): V133GeometryData {
        val cols = (34 * density).toInt().coerceIn(18, 38)
        val rows = (78 * density).toInt().coerceIn(40, 84)
        val xHalf = 15.5
        val yMin = -6.0
        val yMax = d + 25.0
        val b = V133Builder()
        for (r in 0 until rows) {
            val y0 = yMin + (yMax - yMin) * r / rows
            val y1 = yMin + (yMax - yMin) * (r + 1) / rows
            for (c in 0 until cols) {
                val x0 = -xHalf + 2.0 * xHalf * c / cols
                val x1 = -xHalf + 2.0 * xHalf * (c + 1) / cols
                val p0 = point(x0, y0, groundHeight(settings, d, x0, y0))
                val p1 = point(x1, y0, groundHeight(settings, d, x1, y0))
                val p2 = point(x1, y1, groundHeight(settings, d, x1, y1))
                val p3 = point(x0, y1, groundHeight(settings, d, x0, y1))
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    fun green(settings: GreenSettings, d: Double, density: Double, fringe: Boolean): V133GeometryData {
        val cols = (44 * density).toInt().coerceIn(26, 48)
        val rows = (92 * density).toInt().coerceIn(52, 100)
        val length = max(4.8, d * 1.44)
        val b = V133Builder()

        fun halfWidth(y: Double): Double {
            val t = (y / length).coerceIn(0.0, 1.0)
            val base = max(1.74, d * .235)
            val organic = .91 + .09 * sin(PI * t) + .035 * sin(t * 4.0 * PI + .62)
            val edge = .032 * sin(y * .91) + .020 * sin(y * 1.73 + .44)
            return (base * organic + edge + if (fringe) .30 else 0.0).coerceAtLeast(1.45)
        }

        fun z(x: Double, y: Double): Float =
            GreenTerrain.effectiveHeightAt(settings, x, y).toFloat() + if (fringe) .006f else .018f

        for (r in 0 until rows) {
            val y0 = length * r / rows
            val y1 = length * (r + 1) / rows
            val hw0 = halfWidth(y0)
            val hw1 = halfWidth(y1)
            for (c in 0 until cols) {
                val x00 = -hw0 + 2.0 * hw0 * c / cols
                val x10 = -hw0 + 2.0 * hw0 * (c + 1) / cols
                val x01 = -hw1 + 2.0 * hw1 * c / cols
                val x11 = -hw1 + 2.0 * hw1 * (c + 1) / cols
                val p0 = point(x00, y0, z(x00, y0))
                val p1 = point(x10, y0, z(x10, y0))
                val p2 = point(x11, y1, z(x11, y1))
                val p3 = point(x01, y1, z(x01, y1))
                val slope = GreenTerrain.effectiveSlopeAt(settings, (x00 + x11) * .5, (y0 + y1) * .5)
                val n = floatArrayOf(
                    (-slope.sidePct / 100.0).toFloat(),
                    (-slope.longPct / 100.0).toFloat(),
                    1f
                )
                b.quad(p0, p1, p2, p3, n)
            }
        }
        return b.build()
    }

    fun ellipseDisc(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, steps: Int): V133GeometryData {
        val b = V133Builder()
        val center = point(cx, cy, cz)
        val n = steps.coerceIn(18, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(
                center,
                point(cx + cos(a0).toFloat() * rx, cy + sin(a0).toFloat() * ry, cz),
                point(cx + cos(a1).toFloat() * rx, cy + sin(a1).toFloat() * ry, cz),
                floatArrayOf(0f, 0f, 1f)
            )
        }
        return b.build()
    }

    fun disc(cx: Float, cy: Float, cz: Float, radius: Float, steps: Int): V133GeometryData =
        ellipseDisc(cx, cy, cz, radius, radius, steps)

    fun ring(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, steps: Int): V133GeometryData {
        val b = V133Builder()
        val n = steps.coerceIn(16, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = point(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, cz)
            val i1 = point(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, cz)
            val o0 = point(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, cz)
            val o1 = point(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, cz)
            b.quad(i0, o0, o1, i1, floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun cylinder(cx: Float, cy: Float, baseZ: Float, radius: Float, height: Float, steps: Int): V133GeometryData {
        val b = V133Builder()
        val n = steps.coerceIn(8, 32)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val x0 = cx + cos(a0).toFloat() * radius
            val y0 = cy + sin(a0).toFloat() * radius
            val x1 = cx + cos(a1).toFloat() * radius
            val y1 = cy + sin(a1).toFloat() * radius
            val normal = floatArrayOf(cos((a0 + a1) * .5).toFloat(), sin((a0 + a1) * .5).toFloat(), 0f)
            b.quad(
                point(x0, y0, baseZ),
                point(x1, y1, baseZ),
                point(x1, y1, baseZ + height),
                point(x0, y0, baseZ + height),
                normal
            )
        }
        return b.build()
    }

    fun sphere(cx: Float, cy: Float, cz: Float, radius: Float, latN: Int, lonN: Int): V133GeometryData =
        ellipsoid(cx, cy, cz, radius, radius, radius, latN, lonN)

    fun ellipsoid(
        cx: Float,
        cy: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        latNRaw: Int,
        lonNRaw: Int
    ): V133GeometryData {
        val latN = latNRaw.coerceIn(5, 18)
        val lonN = lonNRaw.coerceIn(8, 28)
        val b = V133Builder()
        fun p(lat: Int, lon: Int): FloatArray {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            return point(
                cx + (cos(phi) * cos(theta)).toFloat() * rx,
                cy + (cos(phi) * sin(theta)).toFloat() * ry,
                cz + sin(phi).toFloat() * rz
            )
        }
        fun normal(p: FloatArray): FloatArray {
            val nx = (p[0] - cx) / max(.001f, rx)
            val ny = (p[1] - cy) / max(.001f, ry)
            val nz = (p[2] - cz) / max(.001f, rz)
            val m = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
            return floatArrayOf(nx / m, ny / m, nz / m)
        }
        for (lat in 0 until latN) for (lon in 0 until lonN) {
            val nl = (lon + 1) % lonN
            val a = p(lat, lon)
            val bb = p(lat + 1, lon)
            val c = p(lat + 1, nl)
            val d = p(lat, nl)
            b.tri(a, bb, c, normal(a))
            b.tri(a, c, d, normal(a))
        }
        return b.build()
    }

    fun flag(cx: Float, cy: Float, topZ: Float): V133GeometryData {
        val b = V133Builder()
        val a = point(cx, cy, topZ - .015f)
        val bb = point(cx + .34f, cy, topZ - .10f)
        val c = point(cx, cy, topZ - .255f)
        b.tri(a, bb, c, floatArrayOf(0f, -1f, 0f))
        b.tri(c, bb, a, floatArrayOf(0f, 1f, 0f))
        return b.build()
    }

    private fun point(x: Double, y: Double, z: Float): FloatArray = floatArrayOf(x.toFloat(), y.toFloat(), z)
    private fun point(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(x, y, z)

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

private class V133Hud(context: Context, private val game: GameEngine) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

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
        val s = min(w / 1536f, h / 864f).coerceAtLeast(.58f)
        val settings = game.settings
        val d = settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0
        val side = settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0
        val long = settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0

        val x = 24f * s
        val y = 22f * s
        val panelW = 292f * s
        val panelH = 72f * s
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(194, 8, 13, 12)
        canvas.drawRoundRect(RectF(x, y, x + panelW, y + panelH), 11f * s, 11f * s, paint)
        paint.color = Color.rgb(93, 202, 143)
        canvas.drawRoundRect(RectF(x, y, x + 3.5f * s, y + panelH), 2f * s, 2f * s, paint)

        paint.typeface = medium
        paint.textSize = 14f * s
        paint.color = Color.rgb(238, 241, 236)
        canvas.drawText("PUTTVISION  /  PRACTICE", x + 16f * s, y + 24f * s, paint)

        paint.typeface = regular
        paint.textSize = 10.5f * s
        paint.color = Color.rgb(156, 166, 159)
        val breakText = when {
            abs(side) < .15 && abs(long) < .15 -> "FLAT"
            abs(side) >= abs(long) -> if (side > 0) "R → L" else "L → R"
            long > 0 -> "UPHILL"
            else -> "DOWNHILL"
        }
        canvas.drawText("$breakText  ·  GREEN READ", x + 16f * s, y + 49f * s, paint)

        paint.typeface = medium
        paint.textSize = 22f * s
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(String.format(Locale.US, "%.1f m", d), x + panelW - 16f * s, y + 44f * s, paint)
        paint.textAlign = Paint.Align.LEFT

        paint.typeface = medium
        paint.textSize = 9.5f * s
        paint.color = Color.argb(150, 230, 235, 230)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("PUTTVISION", w - 22f * s, h - 18f * s, paint)
        paint.textAlign = Paint.Align.LEFT
        postInvalidateOnAnimation()
    }
}
