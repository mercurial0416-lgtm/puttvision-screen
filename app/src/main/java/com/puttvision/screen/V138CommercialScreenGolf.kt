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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V138 is an independent commercial-style TV renderer.
 *
 * It intentionally does not layer Canvas course art on top of another world. Every visible course
 * feature belongs to one Filament scene, and V135/V136/V137 remain the authoritative ball physics.
 * If this renderer cannot start on a device, V133 is the fail-safe fallback.
 */
object V138CommercialScreenGolfFactory {
    fun create(context: Context, game: GameEngine): View =
        runCatching { V138Stage(context, game) }
            .getOrElse {
                V138CommercialRuntime.lastFailure = it.message ?: it.javaClass.simpleName
                V133FilamentCourseFactory.create(context, game)
            }
}

object V138CommercialRuntime {
    @Volatile var lastFailure: String? = null
}

private data class V138SceneKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val flagstickIn: Boolean,
    val tier: V24RenderTier
)

private data class V138Mesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance
)

private class V138Stage(context: Context, game: GameEngine) : FrameLayout(context) {
    private val surface = SurfaceView(context)
    private val controller: V138Controller

    init {
        setBackgroundColor(Color.rgb(107, 158, 187))
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controller = V138Controller(context.applicationContext, surface, game)
        addView(V139FriendsHud(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

private class V138Controller(
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
    private val cameraSmoother = V139FriendsCameraSmoother()

    private var swapChain: SwapChain? = null
    private var running = false
    private var destroyed = false
    private var viewportWidth = 1
    private var viewportHeight = 1

    private lateinit var turfMaterial: Material
    private lateinit var litMaterial: Material
    private lateinit var sandMaterial: Material
    private lateinit var ballMaterial: Material
    private val materialInstances = mutableListOf<MaterialInstance>()
    private val sceneMeshes = mutableListOf<V138Mesh>()
    private var ballMesh: V138Mesh? = null
    private var markerMesh: V138Mesh? = null
    private var sceneKey: V138SceneKey? = null
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
            V138CommercialRuntime.lastFailure = t.message ?: t.javaClass.simpleName
        } finally {
            if (running && !destroyed) choreographer.postFrameCallback(this)
        }
    }

    private fun buildMaterials() {
        MaterialBuilder.init()
        try {
            turfMaterial = buildTurfMaterial()
            litMaterial = buildLitMaterial("PV138 Props", .72f, .28f)
            sandMaterial = buildLitMaterial("PV138 Sand", .96f, .16f)
            ballMaterial = buildLitMaterial("PV138 Ball", .17f, .52f)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun buildTurfMaterial(): Material {
        val source = """
            void material(inout MaterialInputs material) {
                vec3 p = getUserWorldPosition();
                float f1 = sin(p.x * 91.0 + p.y * 17.0);
                float f2 = cos(p.y * 123.0 - p.x * 13.0);
                float f3 = sin((p.x + p.y) * 169.0);
                material.normal = normalize(vec3(
                    (f1 + 0.42 * f3) * 0.024,
                    (f2 - 0.36 * f3) * 0.024,
                    1.0
                ));
                prepareMaterial(material);
                float macroA = sin(p.x * 0.73 + p.y * 0.27);
                float macroB = cos(p.y * 1.21 - p.x * 0.41);
                float mowing = sin(p.y * 3.65 + 0.16 * sin(p.x * 0.82));
                float fleck = f1 * f2 * 0.006 + f3 * 0.004;
                float shade = 0.966 + 0.018 * macroA + 0.014 * macroB + 0.006 * mowing + fleck;
                material.baseColor.rgb = materialParams.baseColor * shade;
                material.roughness = 0.88 + 0.035 * (0.5 + 0.5 * f2);
                material.reflectance = 0.20;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV138 Layered Turf")
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V138 turf material compile failed" }
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
        check(pkg.isValid) { "V138 material compile failed: $name" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun instance(material: Material, r: Float, g: Float, b: Float): MaterialInstance =
        material.createInstance().also {
            it.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
            materialInstances += it
        }

    private fun setupLighting() {
        skybox = Skybox.Builder().color(.47f, .67f, .82f, 1f).build(engine).also { scene.skybox = it }

        sun = EntityManager.get().create()
        val warm = Colors.cct(5_350f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(warm[0], warm[1], warm[2])
            .intensity(112_000f)
            .direction(-.34f, -.58f, -.93f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        fill = EntityManager.get().create()
        val cool = Colors.cct(7_100f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(cool[0], cool[1], cool[2])
            .intensity(18_000f)
            .direction(.54f, .26f, -.70f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)
        camera.setExposure(10.15f, 1f / 125f, 100f)
    }

    private fun setupBall() {
        ballMesh = createRenderable(
            V138Geometry.ellipsoid(0f, 0f, 0f, .021335f, .021335f, .021335f, 18, 28),
            instance(ballMaterial, .955f, .960f, .945f),
            castShadow = true,
            contactShadow = true
        )
        markerMesh = createRenderable(
            V138Geometry.ellipsoid(0f, 0f, .0200f, .0044f, .0044f, .0014f, 7, 12),
            instance(litMaterial, .035f, .038f, .036f),
            castShadow = false,
            contactShadow = false
        )
    }

    private fun syncScene() {
        val settings = game.settings.copy()
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V138SceneKey(
            settings.terrainProfileId,
            (d * 100).toInt(),
            ((settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            ((settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0) * 100).toInt(),
            V28CustomGreenCodec.signature(V22CustomGreenRuntime.profile),
            settings.flagstickIn,
            tier
        )
        if (key == sceneKey) return
        sceneKey = key
        cameraSmoother.reset()
        sceneMeshes.toList().forEach(::destroyMesh)
        sceneMeshes.clear()

        val density = when (tier) {
            V24RenderTier.HIGH -> 1.0
            V24RenderTier.BALANCED -> .78
            V24RenderTier.PERFORMANCE -> .60
        }

        sceneMeshes += createRenderable(
            V138Geometry.courseGround(settings, d, density),
            instance(turfMaterial, .108f, .258f, .067f),
            castShadow = false,
            contactShadow = false
        )
        sceneMeshes += createRenderable(
            V138Geometry.green(settings, d, density, fringe = true),
            instance(turfMaterial, .155f, .355f, .092f),
            castShadow = false,
            contactShadow = false
        )
        sceneMeshes += createRenderable(
            V138Geometry.green(settings, d, density, fringe = false),
            instance(turfMaterial, .205f, .468f, .118f),
            castShadow = false,
            contactShadow = false
        )

        addBunkers(settings, d)
        addVegetation(settings, d, tier)
        addCup(settings, d)
    }

    private fun addBunkers(settings: GreenSettings, d: Double) {
        V138Geometry.bunkerPlacements(d).forEach { p ->
            val z = V138Geometry.groundHeight(settings, d, p[0].toDouble(), p[1].toDouble()) + .008f
            sceneMeshes += createRenderable(
                V138Geometry.bunkerBowl(p[0], p[1], z, p[2], p[3], 48),
                instance(sandMaterial, .73f, .66f, .51f),
                castShadow = false,
                contactShadow = true
            )
        }
    }

    private fun addVegetation(settings: GreenSettings, d: Double, tier: V24RenderTier) {
        val count = when (tier) {
            V24RenderTier.HIGH -> 22
            V24RenderTier.BALANCED -> 15
            V24RenderTier.PERFORMANCE -> 9
        }
        for (i in 0 until count) {
            val side = if (i and 1 == 0) -1f else 1f
            val lane = i / 2
            val x = side * (5.3f + (lane % 4) * 1.30f + ((i * 37) % 9) * .08f)
            val y = (-1.0 + lane * 2.05 + (i % 3) * .43).toFloat().coerceAtMost((d + 15.0).toFloat())
            val scale = .84f + ((i * 17) % 7) * .064f
            val baseZ = V138Geometry.groundHeight(settings, d, x.toDouble(), y.toDouble())

            sceneMeshes += createRenderable(
                V138Geometry.cylinder(x, y, baseZ, .052f * scale, .88f * scale, 12),
                instance(litMaterial, .205f, .120f, .060f),
                castShadow = true,
                contactShadow = true
            )

            if (i % 4 == 0) {
                for (layer in 0..2) {
                    val z = baseZ + (.48f + layer * .27f) * scale
                    val r = (.55f - layer * .10f) * scale
                    val h = (.83f - layer * .08f) * scale
                    sceneMeshes += createRenderable(
                        V138Geometry.cone(x, y, z, r, h, 16),
                        instance(litMaterial, .064f + layer * .007f, .178f + layer * .011f, .058f),
                        castShadow = true,
                        contactShadow = false
                    )
                }
            } else {
                val z = baseZ + .98f * scale
                val baseTone = when (i % 3) {
                    0 -> floatArrayOf(.073f, .202f, .068f)
                    1 -> floatArrayOf(.086f, .228f, .075f)
                    else -> floatArrayOf(.063f, .181f, .058f)
                }
                val lobes = arrayOf(
                    floatArrayOf(-.23f, -.04f, .02f, .52f, .46f, .57f),
                    floatArrayOf(.23f, .02f, .08f, .49f, .44f, .61f),
                    floatArrayOf(0f, .12f, .34f, .43f, .40f, .54f)
                )
                lobes.forEachIndexed { index, l ->
                    sceneMeshes += createRenderable(
                        V138Geometry.ellipsoid(
                            x + l[0] * scale,
                            y + l[1] * scale,
                            z + l[2] * scale,
                            l[3] * scale,
                            l[4] * scale,
                            l[5] * scale,
                            8,
                            14
                        ),
                        instance(
                            litMaterial,
                            baseTone[0] + index * .006f,
                            baseTone[1] + index * .007f,
                            baseTone[2]
                        ),
                        castShadow = true,
                        contactShadow = false
                    )
                }
            }
        }
    }

    private fun addCup(settings: GreenSettings, d: Double) {
        val surfaceZ = GreenTerrain.effectiveHeightAt(settings, 0.0, d).toFloat() + .020f
        val depth = V135RigidBallPhysics.CUP_DEPTH_M.toFloat()
        sceneMeshes += createRenderable(
            V138Geometry.cupWall(0f, d.toFloat(), surfaceZ - .001f, .054f, depth, 48),
            instance(litMaterial, .86f, .87f, .82f),
            castShadow = false,
            contactShadow = true
        )
        sceneMeshes += createRenderable(
            V138Geometry.disc(0f, d.toFloat(), surfaceZ - depth + .002f, .052f, 48),
            instance(litMaterial, .010f, .012f, .010f),
            castShadow = false,
            contactShadow = false
        )
        sceneMeshes += createRenderable(
            V138Geometry.ring(0f, d.toFloat(), surfaceZ - .003f, .047f, .054f, 48),
            instance(litMaterial, .79f, .81f, .76f),
            castShadow = false,
            contactShadow = false
        )

        if (settings.flagstickIn) {
            sceneMeshes += createRenderable(
                V138Geometry.cylinder(0f, d.toFloat(), surfaceZ - depth, .0065f, 1.95f, 14),
                instance(litMaterial, .84f, .85f, .81f),
                castShadow = true,
                contactShadow = true
            )
            sceneMeshes += createRenderable(
                V138Geometry.flag(0f, d.toFloat(), surfaceZ + 1.80f),
                instance(litMaterial, .62f, .058f, .043f),
                castShadow = true,
                contactShadow = false
            )
        }
    }

    private fun updateCameraAndBall() {
        val settings = game.settings
        val state = game.state
        val start = V26BallStartRuntime.current(settings)
        val display = TvInstantRollRuntime.displayPosition(state)
        val bx = display?.first ?: state?.x ?: start.first
        val by = display?.second ?: state?.y ?: start.second
        val target = V139FriendsCameraPlanner.target(
            settings.holeDistanceM,
            start.first,
            start.second,
            bx,
            by,
            state,
            game.lastResult
        )
        val hero = state?.cupPhase == V134CupPhase.RIM ||
            state?.cupPhase == V134CupPhase.DROP ||
            state?.cupPhase == V134CupPhase.SETTLED
        val frame = cameraSmoother.step(target, hero)
        val aspect = viewportWidth.toDouble() / viewportHeight.coerceAtLeast(1).toDouble()
        camera.setProjection(frame.fovDeg.toDouble(), aspect, .025, 120.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            frame.eyeX.toDouble(), frame.eyeY.toDouble(), frame.eyeZ.toDouble(),
            frame.lookX.toDouble(), frame.lookY.toDouble(), frame.lookZ.toDouble(),
            0.0, 0.0, 1.0
        )

        val fallbackZ = GreenTerrain.effectiveHeightAt(settings, bx, by) + .021335
        val transform = V136BallPose.matrix(state, bx, by, fallbackZ)
        val tm = engine.transformManager
        ballMesh?.let { tm.setTransform(tm.getInstance(it.entity), transform) }
        markerMesh?.let { tm.setTransform(tm.getInstance(it.entity), transform) }
    }

    private fun createRenderable(
        geometry: V138GeometryData,
        material: MaterialInstance,
        castShadow: Boolean,
        contactShadow: Boolean
    ): V138Mesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(geometry.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, V138GeometryData.VERTEX_BYTES)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, V138GeometryData.VERTEX_BYTES)
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
        return V138Mesh(entity, vb, ib, material)
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
        markerMesh?.let { runCatching { destroyMesh(it) } }
        ballMesh = null
        markerMesh = null
        swapChain?.let { runCatching { engine.destroySwapChain(it) } }
        swapChain = null
        runCatching { engine.destroyMaterial(turfMaterial) }
        runCatching { engine.destroyMaterial(litMaterial) }
        runCatching { engine.destroyMaterial(sandMaterial) }
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

    private fun destroyMesh(mesh: V138Mesh) {
        runCatching { scene.removeEntity(mesh.entity) }
        runCatching { engine.destroyEntity(mesh.entity) }
        runCatching { EntityManager.get().destroy(mesh.entity) }
        runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
        runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        runCatching { engine.destroyMaterialInstance(mesh.material) }
        materialInstances.remove(mesh.material)
    }
}

private data class V138GeometryData(
    val vertices: ByteBuffer,
    val indices: ByteBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val bounds: Box
) {
    companion object { const val VERTEX_BYTES = 28 }
}

private class V138Builder {
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
        require(base + 3 < 65535) { "V138 mesh exceeds ushort index range" }
        add(a, normal); add(b, normal); add(c, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 4 < 65535) { "V138 mesh exceeds ushort index range" }
        add(a, normal); add(b, normal); add(c, normal); add(d, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
        indices += base.toShort(); indices += (base + 2).toShort(); indices += (base + 3).toShort()
    }

    private fun add(p: FloatArray, nRaw: FloatArray) {
        val n = normalize(nRaw)
        vertices += Vertex(p, n)
        minX = min(minX, p[0]); minY = min(minY, p[1]); minZ = min(minZ, p[2])
        maxX = max(maxX, p[0]); maxY = max(maxY, p[1]); maxZ = max(maxZ, p[2])
    }

    fun build(): V138GeometryData {
        require(vertices.isNotEmpty() && indices.isNotEmpty()) { "empty V138 geometry" }
        val vb = ByteBuffer.allocateDirect(vertices.size * V138GeometryData.VERTEX_BYTES).order(ByteOrder.nativeOrder())
        vertices.forEach { v ->
            vb.putFloat(v.p[0]); vb.putFloat(v.p[1]); vb.putFloat(v.p[2])
            tangentFrame(v.n).forEach(vb::putFloat)
        }
        vb.flip()
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        indices.forEach(ib::putShort)
        ib.flip()
        val cx = (minX + maxX) * .5f
        val cy = (minY + maxY) * .5f
        val cz = (minZ + maxZ) * .5f
        return V138GeometryData(
            vb, ib, vertices.size, indices.size,
            Box(
                cx, cy, cz,
                max(.01f, (maxX - minX) * .5f),
                max(.01f, (maxY - minY) * .5f),
                max(.01f, (maxZ - minZ) * .5f)
            )
        )
    }

    private fun normalize(v: FloatArray): FloatArray {
        val m = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(.001f)
        return floatArrayOf(v[0] / m, v[1] / m, v[2] / m)
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
}

private object V138Geometry {
    fun bunkerPlacements(d: Double): List<FloatArray> = listOf(
        floatArrayOf(-3.05f, (d * .72).toFloat(), .95f, .46f),
        floatArrayOf(3.20f, (d * .90 + .45).toFloat(), 1.08f, .52f)
    )

    fun groundHeight(settings: GreenSettings, d: Double, x: Double, y: Double): Float {
        val greenInfluence = y in -1.0..(d * 1.60)
        val base = if (greenInfluence) {
            GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.8, 4.8), y.coerceAtLeast(0.0)).toFloat()
        } else 0f
        val noise = (.016 * sin(x * .57 + y * .17) + .011 * sin(y * .39 - x * .29)).toFloat()
        val sideT = ((abs(x) - 4.9) / 9.0).coerceIn(0.0, 1.0)
        val sideRise = (.58 * sideT.pow(1.50)).toFloat()
        val farT = ((y - (d + 4.0)) / 17.5).coerceIn(0.0, 1.0)
        val farRise = (1.35 * farT.pow(1.42) + .13 * farT * sin(x * .31 + y * .14)).toFloat()
        return base - .050f + noise + sideRise + farRise
    }

    private fun insideBunker(d: Double, x: Double, y: Double): Boolean {
        return bunkerPlacements(d).any { p ->
            val dx = (x - p[0]) / (p[2] * 1.09)
            val dy = (y - p[1]) / (p[3] * 1.18)
            dx * dx + dy * dy < 1.0
        }
    }

    fun courseGround(settings: GreenSettings, d: Double, density: Double): V138GeometryData {
        val cols = (38 * density).toInt().coerceIn(22, 42)
        val rows = (88 * density).toInt().coerceIn(48, 94)
        val xHalf = 15.5
        val yMin = -6.5
        val yMax = d + 26.0
        val b = V138Builder()
        for (r in 0 until rows) {
            val y0 = yMin + (yMax - yMin) * r / rows
            val y1 = yMin + (yMax - yMin) * (r + 1) / rows
            for (c in 0 until cols) {
                val x0 = -xHalf + 2.0 * xHalf * c / cols
                val x1 = -xHalf + 2.0 * xHalf * (c + 1) / cols
                if (insideBunker(d, (x0 + x1) * .5, (y0 + y1) * .5)) continue
                val p0 = p(x0, y0, groundHeight(settings, d, x0, y0))
                val p1 = p(x1, y0, groundHeight(settings, d, x1, y0))
                val p2 = p(x1, y1, groundHeight(settings, d, x1, y1))
                val p3 = p(x0, y1, groundHeight(settings, d, x0, y1))
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    fun green(settings: GreenSettings, d: Double, density: Double, fringe: Boolean): V138GeometryData {
        val cols = (58 * density).toInt().coerceIn(34, 62)
        val rows = (132 * density).toInt().coerceIn(72, 142)
        val length = max(4.8, d * 1.44)
        val b = V138Builder()

        fun halfWidth(y: Double): Double {
            val t = (y / length).coerceIn(0.0, 1.0)
            val base = max(1.74, d * .235)
            val organic = .91 + .09 * sin(PI * t) + .035 * sin(t * 4.0 * PI + .62)
            val edge = .032 * sin(y * .91) + .020 * sin(y * 1.73 + .44)
            return (base * organic + edge + if (fringe) .30 else 0.0).coerceAtLeast(1.45)
        }

        fun z(x: Double, y: Double): Float =
            GreenTerrain.effectiveHeightAt(settings, x, y).toFloat() + if (fringe) .010f else .020f

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
                val centerX = (x00 + x10 + x01 + x11) * .25
                val centerY = (y0 + y1) * .5
                val cut = if (fringe) .074 else .064
                if (hypot(centerX, centerY - d) <= cut) continue
                val p0 = p(x00, y0, z(x00, y0))
                val p1 = p(x10, y0, z(x10, y0))
                val p2 = p(x11, y1, z(x11, y1))
                val p3 = p(x01, y1, z(x01, y1))
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

    fun bunkerBowl(cx: Float, cy: Float, surfaceZ: Float, rx: Float, ry: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val n = steps.coerceIn(24, 64)
        val center = p(cx, cy, surfaceZ - .125f)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            fun q(a: Double, s: Float, z: Float) = p(
                cx + cos(a).toFloat() * rx * s,
                cy + sin(a).toFloat() * ry * s,
                z
            )
            val o0 = q(a0, 1f, surfaceZ)
            val o1 = q(a1, 1f, surfaceZ)
            val m0 = q(a0, .76f, surfaceZ - .070f)
            val m1 = q(a1, .76f, surfaceZ - .070f)
            val i0 = q(a0, .48f, surfaceZ - .118f)
            val i1 = q(a1, .48f, surfaceZ - .118f)
            b.quad(o0, o1, m1, m0, normalFromQuad(o0, o1, m0))
            b.quad(m0, m1, i1, i0, normalFromQuad(m0, m1, i0))
            b.tri(center, i1, i0, floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun cupWall(cx: Float, cy: Float, topZ: Float, radius: Float, depth: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val n = steps.coerceIn(24, 64)
        val bottomZ = topZ - depth
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val t0 = p(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, topZ)
            val t1 = p(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, topZ)
            val b0 = p(t0[0], t0[1], bottomZ)
            val b1 = p(t1[0], t1[1], bottomZ)
            val inward = floatArrayOf(-cos((a0 + a1) * .5).toFloat(), -sin((a0 + a1) * .5).toFloat(), 0f)
            b.quad(t1, t0, b0, b1, inward)
            b.quad(t0, t1, b1, b0, floatArrayOf(-inward[0], -inward[1], 0f))
        }
        return b.build()
    }

    fun disc(cx: Float, cy: Float, cz: Float, radius: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val center = p(cx, cy, cz)
        val n = steps.coerceIn(18, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(
                center,
                p(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, cz),
                p(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, cz),
                floatArrayOf(0f, 0f, 1f)
            )
        }
        return b.build()
    }

    fun ring(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val n = steps.coerceIn(18, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = p(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, cz)
            val i1 = p(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, cz)
            val o0 = p(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, cz)
            val o1 = p(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, cz)
            b.quad(i0, o0, o1, i1, floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun cylinder(cx: Float, cy: Float, baseZ: Float, radius: Float, height: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val n = steps.coerceIn(8, 32)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val x0 = cx + cos(a0).toFloat() * radius
            val y0 = cy + sin(a0).toFloat() * radius
            val x1 = cx + cos(a1).toFloat() * radius
            val y1 = cy + sin(a1).toFloat() * radius
            val normal = floatArrayOf(cos((a0 + a1) * .5).toFloat(), sin((a0 + a1) * .5).toFloat(), 0f)
            b.quad(p(x0, y0, baseZ), p(x1, y1, baseZ), p(x1, y1, baseZ + height), p(x0, y0, baseZ + height), normal)
        }
        return b.build()
    }

    fun cone(cx: Float, cy: Float, baseZ: Float, radius: Float, height: Float, steps: Int): V138GeometryData {
        val b = V138Builder()
        val n = steps.coerceIn(10, 32)
        val apex = p(cx, cy, baseZ + height)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val p0 = p(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, baseZ)
            val p1 = p(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, baseZ)
            val mid = (a0 + a1) * .5
            b.tri(p0, p1, apex, floatArrayOf(cos(mid).toFloat(), sin(mid).toFloat(), (radius / height).coerceAtLeast(.05f)))
        }
        return b.build()
    }

    fun ellipsoid(
        cx: Float,
        cy: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        latRaw: Int,
        lonRaw: Int
    ): V138GeometryData {
        val latN = latRaw.coerceIn(5, 20)
        val lonN = lonRaw.coerceIn(8, 32)
        val b = V138Builder()
        fun point(lat: Int, lon: Int): FloatArray {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            return p(
                cx + (cos(phi) * cos(theta)).toFloat() * rx,
                cy + (cos(phi) * sin(theta)).toFloat() * ry,
                cz + sin(phi).toFloat() * rz
            )
        }
        fun normal(v: FloatArray): FloatArray {
            val nx = (v[0] - cx) / max(.001f, rx)
            val ny = (v[1] - cy) / max(.001f, ry)
            val nz = (v[2] - cz) / max(.001f, rz)
            val m = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(.001f)
            return floatArrayOf(nx / m, ny / m, nz / m)
        }
        for (lat in 0 until latN) for (lon in 0 until lonN) {
            val nl = (lon + 1) % lonN
            val a = point(lat, lon)
            val bb = point(lat + 1, lon)
            val c = point(lat + 1, nl)
            val d = point(lat, nl)
            b.tri(a, bb, c, normal(a))
            b.tri(a, c, d, normal(a))
        }
        return b.build()
    }

    fun flag(cx: Float, cy: Float, topZ: Float): V138GeometryData {
        val b = V138Builder()
        val a = p(cx, cy, topZ)
        val bb = p(cx + .38f, cy, topZ - .10f)
        val c = p(cx, cy, topZ - .30f)
        b.tri(a, bb, c, floatArrayOf(0f, -1f, 0f))
        b.tri(c, bb, a, floatArrayOf(0f, 1f, 0f))
        return b.build()
    }

    private fun p(x: Double, y: Double, z: Float) = floatArrayOf(x.toFloat(), y.toFloat(), z)
    private fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

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

private class V138CommercialHud(context: Context, private val game: GameEngine) : View(context) {
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
        val state = game.state
        val d = settings.holeDistanceM.takeIf { it.isFinite() } ?: 5.0
        val side = settings.sideSlopePct.takeIf { it.isFinite() } ?: 0.0
        val long = settings.longSlopePct.takeIf { it.isFinite() } ?: 0.0

        val panel = RectF(22f * s, 20f * s, 374f * s, 94f * s)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(184, 6, 10, 9)
        canvas.drawRoundRect(panel, 10f * s, 10f * s, paint)
        paint.color = Color.rgb(105, 213, 145)
        canvas.drawRoundRect(RectF(panel.left, panel.top, panel.left + 4f * s, panel.bottom), 2f * s, 2f * s, paint)

        paint.typeface = medium
        paint.textSize = 13f * s
        paint.color = Color.rgb(239, 242, 237)
        canvas.drawText("PUTTVISION  ·  SIMULATION", panel.left + 17f * s, panel.top + 24f * s, paint)

        val breakText = when {
            abs(side) < .15 && abs(long) < .15 -> "FLAT"
            abs(side) >= abs(long) -> if (side > 0) "R → L" else "L → R"
            long > 0 -> "UPHILL"
            else -> "DOWNHILL"
        }
        paint.typeface = regular
        paint.textSize = 10.5f * s
        paint.color = Color.rgb(163, 174, 166)
        canvas.drawText(
            String.format(Locale.US, "%s   ·   STIMP %.1f   ·   %.1f m", breakText, settings.stimpMeters, d),
            panel.left + 17f * s,
            panel.top + 49f * s,
            paint
        )

        val cupAction = state?.cupPhase == V134CupPhase.RIM || state?.cupPhase == V134CupPhase.DROP
        if (cupAction) {
            paint.typeface = medium
            paint.textSize = 11f * s
            paint.color = Color.rgb(235, 215, 137)
            canvas.drawText("CUP CAM", panel.left + 17f * s, panel.top + 67f * s, paint)
        }

        paint.typeface = medium
        paint.textSize = 9f * s
        paint.color = Color.argb(145, 235, 239, 234)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("V138 PHYSICS · FILAMENT", w - 20f * s, h - 17f * s, paint)
        paint.textAlign = Paint.Align.LEFT
        postInvalidateOnAnimation()
    }
}
