package com.puttvision.screen

import android.content.Context
import android.graphics.Color
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
 * V141 is the first PuttVision TV scene that uses sampled GPU surface assets instead of relying on
 * procedural color alone. V135-V137 remain the authority for all ball / cup motion.
 */
object V141FriendsPbrScreenGolfFactory {
    fun create(context: Context, game: GameEngine): View =
        runCatching { V141Stage(context, game) }
            .getOrElse {
                V141FriendsPbrRuntime.lastFailure = it.message ?: it.javaClass.simpleName
                V140FriendsScreenGolfFactory.create(context, game)
            }
}

object V141FriendsPbrRuntime {
    @Volatile var lastFailure: String? = null
}

private data class V141SceneKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val flagstickIn: Boolean,
    val tier: V24RenderTier
)

private data class V141Mesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance
)

private class V141Stage(context: Context, game: GameEngine) : FrameLayout(context) {
    private val surface = SurfaceView(context)
    private val controller: V141Controller

    init {
        setBackgroundColor(Color.rgb(47, 103, 183))
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controller = V141Controller(context.applicationContext, surface, game)
        addView(V140FriendsHud(context, game), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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

private class V141Controller(
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
    private val cameraSmoother = V140FriendsCameraSmoother()

    private var swapChain: SwapChain? = null
    private var running = false
    private var destroyed = false
    private var viewportWidth = 1
    private var viewportHeight = 1

    private lateinit var turfMaterial: Material
    private lateinit var litMaterial: Material
    private lateinit var ballMaterial: Material
    private lateinit var skyMaterial: Material
    private lateinit var treeMaterial: Material
    private var turfMaps: V141PbrAssets.TurfMaps? = null
    private var sceneryMaps: V141SceneryAssets.Maps? = null

    private val materialInstances = mutableListOf<MaterialInstance>()
    private val sceneMeshes = mutableListOf<V141Mesh>()
    private var ballMesh: V141Mesh? = null
    private var markerMesh: V141Mesh? = null
    private var sceneKey: V141SceneKey? = null
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
        V141PbrAssets.configureView(view)

        buildMaterialsAndAssets()
        setupLighting()
        setupBall()

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = this
        uiHelper.attachTo(surfaceView)
    }

    private fun buildMaterialsAndAssets() {
        MaterialBuilder.init()
        try {
            turfMaterial = V141PbrAssets.buildTurfMaterial(engine)
            litMaterial = buildLitMaterial("PV141 Props", .72f, .22f)
            ballMaterial = buildLitMaterial("PV141 Ball", .14f, .56f)
            skyMaterial = V141SceneryAssets.buildSkyMaterial(engine)
            treeMaterial = V141SceneryAssets.buildTreeMaterial(engine)
        } finally {
            MaterialBuilder.shutdown()
        }
        turfMaps = V141PbrAssets.createTurfMaps(engine, 384)
        sceneryMaps = V141SceneryAssets.create(engine)
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
            V141FriendsPbrRuntime.lastFailure = t.message ?: t.javaClass.simpleName
        } finally {
            if (running && !destroyed) choreographer.postFrameCallback(this)
        }
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
        check(pkg.isValid) { "V141 material compile failed: $name" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun litInstance(material: Material, r: Float, g: Float, b: Float): MaterialInstance =
        material.createInstance().also {
            it.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
            materialInstances += it
        }

    private fun turfInstance(
        r: Float,
        g: Float,
        b: Float,
        tile: Float,
        normal: Float
    ): MaterialInstance {
        val maps = requireNotNull(turfMaps)
        return V141PbrAssets.createTurfInstance(turfMaterial, maps, r, g, b, tile, normal).also {
            materialInstances += it
        }
    }

    private fun setupLighting() {
        // Textured horizon is the visible sky; the skybox only fills extreme camera edges.
        skybox = Skybox.Builder().color(.18f, .40f, .72f, 1f).build(engine).also { scene.skybox = it }

        sun = EntityManager.get().create()
        val daylight = Colors.cct(5_750f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(daylight[0], daylight[1], daylight[2])
            .intensity(108_000f)
            .direction(-.31f, -.53f, -.94f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        fill = EntityManager.get().create()
        val skylight = Colors.cct(7_300f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(skylight[0], skylight[1], skylight[2])
            .intensity(14_000f)
            .direction(.52f, .15f, -.76f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)
        camera.setExposure(10.0f, 1f / 125f, 100f)
    }

    private fun setupBall() {
        ballMesh = createRenderable(
            V141Geometry.ellipsoid(0f, 0f, 0f, .021335f, .021335f, .021335f, 22, 34),
            litInstance(ballMaterial, .975f, .978f, .965f),
            castShadow = true,
            contactShadow = true
        )
        markerMesh = createRenderable(
            V141Geometry.ellipsoid(0f, 0f, .0202f, .0037f, .0037f, .0010f, 7, 12),
            litInstance(litMaterial, .025f, .027f, .026f),
            castShadow = false,
            contactShadow = false
        )
    }

    private fun syncScene() {
        val settings = game.settings.copy()
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V141SceneKey(
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
            V24RenderTier.BALANCED -> .82
            V24RenderTier.PERFORMANCE -> .66
        }

        sceneMeshes += createRenderable(
            V141Geometry.courseGround(settings, d, density),
            turfInstance(.76f, .96f, .72f, 1.25f, .72f),
            false,
            false
        )
        sceneMeshes += createRenderable(
            V141Geometry.green(settings, d, density, fringe = true),
            turfInstance(.92f, 1.02f, .83f, 2.20f, .88f),
            false,
            true
        )
        sceneMeshes += createRenderable(
            V141Geometry.green(settings, d, density, fringe = false),
            turfInstance(1.04f, 1.08f, .91f, 3.05f, 1.04f),
            false,
            true
        )

        addTexturedSky(d)
        addHorizonTrees(settings, d, tier)
        addClubhouseAndFence(settings, d)
        addCup(settings, d)
    }

    private fun addTexturedSky(d: Double) {
        val maps = requireNotNull(sceneryMaps)
        val y = (d + 23.0).toFloat()
        val baseZ = -1.3f
        val width = 42f
        val height = 14f
        val mi = V141SceneryAssets.skyInstance(skyMaterial, maps, 0f, baseZ, width, height).also {
            materialInstances += it
        }
        sceneMeshes += createRenderable(
            V141Geometry.verticalQuad(0f, y, baseZ, width, height),
            mi,
            false,
            false
        )
    }

    private fun addHorizonTrees(settings: GreenSettings, d: Double, tier: V24RenderTier) {
        val maps = requireNotNull(sceneryMaps)
        val count = when (tier) {
            V24RenderTier.HIGH -> 13
            V24RenderTier.BALANCED -> 11
            V24RenderTier.PERFORMANCE -> 9
        }
        for (i in 0 until count) {
            val t = if (count <= 1) .5f else i.toFloat() / (count - 1)
            val x = -10.0f + 20.0f * t
            val y = (d + 8.6 + (i % 3) * .82).toFloat()
            val ground = V141Geometry.groundHeight(settings, d, x.toDouble(), y.toDouble())
            val width = 3.5f + (i % 4) * .48f
            val height = 4.8f + (i % 5) * .38f
            val autumn = i == count - 4
            val mi = V141SceneryAssets.treeInstance(
                treeMaterial,
                maps,
                x,
                ground,
                width,
                height,
                if (autumn) 2.15f else .90f,
                if (autumn) .55f else 1.00f,
                if (autumn) .50f else .88f
            ).also { materialInstances += it }
            sceneMeshes += createRenderable(
                V141Geometry.verticalQuad(x, y, ground, width, height),
                mi,
                castShadow = true,
                contactShadow = false
            )
        }
    }

    private fun addClubhouseAndFence(settings: GreenSettings, d: Double) {
        val houseY = (d + 6.9).toFloat()
        val houseX = -3.45f
        val ground = V141Geometry.groundHeight(settings, d, houseX.toDouble(), houseY.toDouble())
        sceneMeshes += createRenderable(
            V141Geometry.box(houseX, houseY, ground + .58f, 1.75f, .72f, .58f),
            litInstance(litMaterial, .74f, .72f, .64f), true, true
        )
        sceneMeshes += createRenderable(
            V141Geometry.gableRoof(houseX, houseY, ground + 1.16f, 1.96f, .88f, .44f),
            litInstance(litMaterial, .23f, .23f, .25f), true, true
        )
        for (i in 0..3) {
            val wx = houseX - 1.08f + i * .72f
            sceneMeshes += createRenderable(
                V141Geometry.box(wx, houseY - .735f, ground + .68f, .22f, .024f, .24f),
                litInstance(litMaterial, .08f, .13f, .15f), false, false
            )
        }

        val fenceY = (d + 3.55).toFloat()
        val fenceGround = V141Geometry.groundHeight(settings, d, 0.0, fenceY.toDouble())
        for (x in -7..7) {
            sceneMeshes += createRenderable(
                V141Geometry.box(x.toFloat(), fenceY, fenceGround + .32f, .027f, .027f, .32f),
                litInstance(litMaterial, .90f, .90f, .86f), true, false
            )
        }
        for (z in listOf(.22f, .45f)) {
            sceneMeshes += createRenderable(
                V141Geometry.box(0f, fenceY, fenceGround + z, 7.0f, .024f, .024f),
                litInstance(litMaterial, .90f, .90f, .86f), true, false
            )
        }
    }

    private fun addCup(settings: GreenSettings, d: Double) {
        val surfaceZ = GreenTerrain.effectiveHeightAt(settings, 0.0, d).toFloat() + .020f
        val depth = V135RigidBallPhysics.CUP_DEPTH_M.toFloat()
        sceneMeshes += createRenderable(
            V141Geometry.cupWall(0f, d.toFloat(), surfaceZ - .001f, .054f, depth, 56),
            litInstance(litMaterial, .88f, .89f, .85f), false, true
        )
        sceneMeshes += createRenderable(
            V141Geometry.disc(0f, d.toFloat(), surfaceZ - depth + .002f, .052f, 56),
            litInstance(litMaterial, .008f, .010f, .008f), false, false
        )
        sceneMeshes += createRenderable(
            V141Geometry.ring(0f, d.toFloat(), surfaceZ - .003f, .047f, .054f, 56),
            litInstance(litMaterial, .83f, .84f, .80f), false, false
        )
        if (settings.flagstickIn) {
            sceneMeshes += createRenderable(
                V141Geometry.cylinder(0f, d.toFloat(), surfaceZ - depth, .0065f, 1.95f, 16),
                litInstance(litMaterial, .90f, .91f, .88f), true, true
            )
            sceneMeshes += createRenderable(
                V141Geometry.flag(0f, d.toFloat(), surfaceZ + 1.80f),
                litInstance(litMaterial, .79f, .030f, .025f), true, false
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
        val target = V140FriendsCameraPlanner.target(
            settings.holeDistanceM,
            start.first,
            start.second,
            bx,
            by,
            state,
            game.lastResult
        )
        val cupAction = state?.cupPhase == V134CupPhase.RIM ||
            state?.cupPhase == V134CupPhase.DROP ||
            state?.cupPhase == V134CupPhase.SETTLED
        val frame = cameraSmoother.step(target, cupAction)
        val aspect = viewportWidth.toDouble() / viewportHeight.coerceAtLeast(1).toDouble()
        camera.setProjection(frame.fovDeg.toDouble(), aspect, .025, 150.0, Camera.Fov.VERTICAL)
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
        geometry: V141GeometryData,
        material: MaterialInstance,
        castShadow: Boolean,
        contactShadow: Boolean
    ): V141Mesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(geometry.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, V141GeometryData.VERTEX_BYTES)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, V141GeometryData.VERTEX_BYTES)
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
        return V141Mesh(entity, vb, ib, material)
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
        materialInstances.clear()
        V141PbrAssets.destroy(engine, turfMaps)
        V141SceneryAssets.destroy(engine, sceneryMaps)
        turfMaps = null
        sceneryMaps = null
        runCatching { engine.destroyMaterial(turfMaterial) }
        runCatching { engine.destroyMaterial(litMaterial) }
        runCatching { engine.destroyMaterial(ballMaterial) }
        runCatching { engine.destroyMaterial(skyMaterial) }
        runCatching { engine.destroyMaterial(treeMaterial) }
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

    private fun destroyMesh(mesh: V141Mesh) {
        runCatching { scene.removeEntity(mesh.entity) }
        runCatching { engine.destroyEntity(mesh.entity) }
        runCatching { EntityManager.get().destroy(mesh.entity) }
        runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
        runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        runCatching { engine.destroyMaterialInstance(mesh.material) }
        materialInstances.remove(mesh.material)
    }
}

private data class V141GeometryData(
    val vertices: ByteBuffer,
    val indices: ByteBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val bounds: Box
) {
    companion object { const val VERTEX_BYTES = 28 }
}

private class V141Builder {
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
        require(base + 3 < 65535) { "V141 mesh exceeds ushort index range" }
        add(a, normal)
        add(b, normal)
        add(c, normal)
        indices += base.toShort()
        indices += (base + 1).toShort()
        indices += (base + 2).toShort()
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 4 < 65535) { "V141 mesh exceeds ushort index range" }
        add(a, normal)
        add(b, normal)
        add(c, normal)
        add(d, normal)
        indices += base.toShort()
        indices += (base + 1).toShort()
        indices += (base + 2).toShort()
        indices += base.toShort()
        indices += (base + 2).toShort()
        indices += (base + 3).toShort()
    }

    private fun add(p: FloatArray, raw: FloatArray) {
        val n = normalize(raw)
        vertices += Vertex(p, n)
        minX = min(minX, p[0]); minY = min(minY, p[1]); minZ = min(minZ, p[2])
        maxX = max(maxX, p[0]); maxY = max(maxY, p[1]); maxZ = max(maxZ, p[2])
    }

    fun build(): V141GeometryData {
        require(vertices.isNotEmpty() && indices.isNotEmpty()) { "empty V141 geometry" }
        val vb = ByteBuffer.allocateDirect(vertices.size * V141GeometryData.VERTEX_BYTES).order(ByteOrder.nativeOrder())
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
        return V141GeometryData(
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

private object V141Geometry {
    fun groundHeight(settings: GreenSettings, d: Double, x: Double, y: Double): Float {
        val active = y in -1.5..(d * 1.80)
        val base = if (active) {
            GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.8, 4.8), y.coerceAtLeast(0.0)).toFloat()
        } else 0f
        val undulation = (.006 * sin(x * .41 + y * .13) + .004 * sin(y * .31 - x * .22)).toFloat()
        val sideT = ((abs(x) - 7.4) / 7.0).coerceIn(0.0, 1.0)
        val sideRise = (.24 * sideT.pow(1.45)).toFloat()
        val farT = ((y - (d + 9.0)) / 14.0).coerceIn(0.0, 1.0)
        val farRise = (.42 * farT.pow(1.30)).toFloat()
        return base - .045f + undulation + sideRise + farRise
    }

    fun courseGround(settings: GreenSettings, d: Double, density: Double): V141GeometryData {
        val cols = (58 * density).toInt().coerceIn(36, 62)
        val rows = (88 * density).toInt().coerceIn(52, 94)
        val xHalf = 14.5
        val yMin = -4.8
        val yMax = d + 22.0
        val b = V141Builder()
        for (r in 0 until rows) {
            val y0 = yMin + (yMax - yMin) * r / rows
            val y1 = yMin + (yMax - yMin) * (r + 1) / rows
            for (c in 0 until cols) {
                val x0 = -xHalf + 2.0 * xHalf * c / cols
                val x1 = -xHalf + 2.0 * xHalf * (c + 1) / cols
                val p0 = p(x0, y0, groundHeight(settings, d, x0, y0))
                val p1 = p(x1, y0, groundHeight(settings, d, x1, y0))
                val p2 = p(x1, y1, groundHeight(settings, d, x1, y1))
                val p3 = p(x0, y1, groundHeight(settings, d, x0, y1))
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    private fun halfWidthAt(y: Double, d: Double, fringe: Boolean): Double {
        val t = ((y + 1.0) / (d + 3.2)).coerceIn(0.0, 1.0)
        val pinch = .48 * sin(PI * t).pow(2.0)
        return 4.88 - pinch + if (fringe) .46 else 0.0
    }

    fun green(settings: GreenSettings, d: Double, density: Double, fringe: Boolean): V141GeometryData {
        val cols = (96 * density).toInt().coerceIn(64, 104)
        val rows = (102 * density).toInt().coerceIn(68, 108)
        val xHalf = if (fringe) 5.4 else 5.0
        val yMin = -1.35
        val yMax = d + 3.0
        val b = V141Builder()
        for (r in 0 until rows) {
            val y0 = yMin + (yMax - yMin) * r / rows
            val y1 = yMin + (yMax - yMin) * (r + 1) / rows
            for (c in 0 until cols) {
                val x0 = -xHalf + 2.0 * xHalf * c / cols
                val x1 = -xHalf + 2.0 * xHalf * (c + 1) / cols
                val xm = (x0 + x1) * .5
                val ym = (y0 + y1) * .5
                if (abs(xm) > halfWidthAt(ym, d, fringe)) continue
                if (!fringe && hypot(xm, ym - d) < .072) continue
                val lift = if (fringe) .014f else .020f
                val p0 = p(x0, y0, GreenTerrain.effectiveHeightAt(settings, x0.coerceIn(-4.8, 4.8), y0.coerceAtLeast(0.0)).toFloat() + lift)
                val p1 = p(x1, y0, GreenTerrain.effectiveHeightAt(settings, x1.coerceIn(-4.8, 4.8), y0.coerceAtLeast(0.0)).toFloat() + lift)
                val p2 = p(x1, y1, GreenTerrain.effectiveHeightAt(settings, x1.coerceIn(-4.8, 4.8), y1.coerceAtLeast(0.0)).toFloat() + lift)
                val p3 = p(x0, y1, GreenTerrain.effectiveHeightAt(settings, x0.coerceIn(-4.8, 4.8), y1.coerceAtLeast(0.0)).toFloat() + lift)
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    fun verticalQuad(cx: Float, y: Float, baseZ: Float, width: Float, height: Float): V141GeometryData {
        val b = V141Builder()
        val hw = width * .5f
        b.quad(
            p(cx - hw, y, baseZ),
            p(cx + hw, y, baseZ),
            p(cx + hw, y, baseZ + height),
            p(cx - hw, y, baseZ + height),
            floatArrayOf(0f, -1f, 0f)
        )
        return b.build()
    }

    fun box(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float): V141GeometryData {
        val b = V141Builder()
        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz
        val p000 = p(x0, y0, z0); val p100 = p(x1, y0, z0)
        val p110 = p(x1, y1, z0); val p010 = p(x0, y1, z0)
        val p001 = p(x0, y0, z1); val p101 = p(x1, y0, z1)
        val p111 = p(x1, y1, z1); val p011 = p(x0, y1, z1)
        b.quad(p000, p100, p101, p001, floatArrayOf(0f, -1f, 0f))
        b.quad(p110, p010, p011, p111, floatArrayOf(0f, 1f, 0f))
        b.quad(p010, p000, p001, p011, floatArrayOf(-1f, 0f, 0f))
        b.quad(p100, p110, p111, p101, floatArrayOf(1f, 0f, 0f))
        b.quad(p001, p101, p111, p011, floatArrayOf(0f, 0f, 1f))
        b.quad(p010, p110, p100, p000, floatArrayOf(0f, 0f, -1f))
        return b.build()
    }

    fun gableRoof(cx: Float, cy: Float, baseZ: Float, hx: Float, hy: Float, roofH: Float): V141GeometryData {
        val b = V141Builder()
        val lf = p(cx - hx, cy - hy, baseZ)
        val rf = p(cx + hx, cy - hy, baseZ)
        val lb = p(cx - hx, cy + hy, baseZ)
        val rb = p(cx + hx, cy + hy, baseZ)
        val lr = p(cx - hx, cy, baseZ + roofH)
        val rr = p(cx + hx, cy, baseZ + roofH)
        b.quad(lf, rf, rr, lr, floatArrayOf(0f, -roofH, hy))
        b.quad(rb, lb, lr, rr, floatArrayOf(0f, roofH, hy))
        b.tri(lf, lr, lb, floatArrayOf(-1f, 0f, .25f))
        b.tri(rf, rb, rr, floatArrayOf(1f, 0f, .25f))
        return b.build()
    }

    fun cupWall(cx: Float, cy: Float, topZ: Float, radius: Float, depth: Float, steps: Int): V141GeometryData {
        val b = V141Builder()
        val n = steps.coerceIn(24, 64)
        val bottom = topZ - depth
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val x0 = cx + cos(a0).toFloat() * radius
            val y0 = cy + sin(a0).toFloat() * radius
            val x1 = cx + cos(a1).toFloat() * radius
            val y1 = cy + sin(a1).toFloat() * radius
            val mid = (a0 + a1) * .5
            b.quad(
                p(x1, y1, topZ), p(x0, y0, topZ), p(x0, y0, bottom), p(x1, y1, bottom),
                floatArrayOf(-cos(mid).toFloat(), -sin(mid).toFloat(), 0f)
            )
        }
        return b.build()
    }

    fun disc(cx: Float, cy: Float, z: Float, radius: Float, steps: Int): V141GeometryData {
        val b = V141Builder()
        val n = steps.coerceIn(18, 64)
        val center = p(cx, cy, z)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(center, p(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, z), p(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, z), floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun ring(cx: Float, cy: Float, z: Float, inner: Float, outer: Float, steps: Int): V141GeometryData {
        val b = V141Builder()
        val n = steps.coerceIn(18, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val i0 = p(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner, z)
            val i1 = p(cx + cos(a1).toFloat() * inner, cy + sin(a1).toFloat() * inner, z)
            val o0 = p(cx + cos(a0).toFloat() * outer, cy + sin(a0).toFloat() * outer, z)
            val o1 = p(cx + cos(a1).toFloat() * outer, cy + sin(a1).toFloat() * outer, z)
            b.quad(i0, o0, o1, i1, floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun cylinder(cx: Float, cy: Float, baseZ: Float, radius: Float, height: Float, steps: Int): V141GeometryData {
        val b = V141Builder()
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

    fun ellipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, latRaw: Int, lonRaw: Int): V141GeometryData {
        val latN = latRaw.coerceIn(5, 24)
        val lonN = lonRaw.coerceIn(8, 36)
        val b = V141Builder()
        fun point(lat: Int, lon: Int): FloatArray {
            val phi = -PI / 2.0 + PI * lat / latN
            val theta = 2.0 * PI * lon / lonN
            return p(cx + (cos(phi) * cos(theta)).toFloat() * rx, cy + (cos(phi) * sin(theta)).toFloat() * ry, cz + sin(phi).toFloat() * rz)
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

    fun flag(cx: Float, cy: Float, topZ: Float): V141GeometryData {
        val b = V141Builder()
        val a = p(cx, cy, topZ)
        val bb = p(cx + .34f, cy, topZ - .09f)
        val c = p(cx, cy, topZ - .28f)
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
