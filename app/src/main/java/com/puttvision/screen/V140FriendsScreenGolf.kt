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
 * V140 is a clean-room reconstruction of the publicly visible Friends Screen putting composition.
 * No Kakao VX code, meshes, textures, logos, characters, binaries, or private parameters are used.
 *
 * Unlike V139, this is an independent Filament world rather than a HUD/tone patch over V138.
 */
object V140FriendsScreenGolfFactory {
    fun create(context: Context, game: GameEngine): View =
        runCatching { V140Stage(context, game) }
            .getOrElse {
                V140FriendsRuntime.lastFailure = it.message ?: it.javaClass.simpleName
                V138CommercialScreenGolfFactory.create(context, game)
            }
}

object V140FriendsRuntime {
    @Volatile var lastFailure: String? = null
}

private data class V140SceneKey(
    val profile: Int,
    val distance100: Int,
    val side100: Int,
    val long100: Int,
    val customHash: Int,
    val flagstickIn: Boolean,
    val tier: V24RenderTier
)

private data class V140Mesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance
)

private class V140Stage(context: Context, game: GameEngine) : FrameLayout(context) {
    private val surface = SurfaceView(context)
    private val controller: V140Controller

    init {
        // Deep blue public-reference sky while Filament is attaching.
        setBackgroundColor(Color.rgb(42, 94, 177))
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        controller = V140Controller(context.applicationContext, surface, game)
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

private class V140Controller(
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
    private val materialInstances = mutableListOf<MaterialInstance>()
    private val sceneMeshes = mutableListOf<V140Mesh>()
    private var ballMesh: V140Mesh? = null
    private var markerMesh: V140Mesh? = null
    private var sceneKey: V140SceneKey? = null
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
            V140FriendsRuntime.lastFailure = t.message ?: t.javaClass.simpleName
        } finally {
            if (running && !destroyed) choreographer.postFrameCallback(this)
        }
    }

    private fun buildMaterials() {
        MaterialBuilder.init()
        try {
            turfMaterial = buildTurfMaterial()
            litMaterial = buildLitMaterial("PV140 Reference Props", .68f, .24f)
            ballMaterial = buildLitMaterial("PV140 Ball", .16f, .52f)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun buildTurfMaterial(): Material {
        val source = """
            void material(inout MaterialInputs material) {
                vec3 p = getUserWorldPosition();
                float n1 = sin(p.x * 118.0 + p.y * 27.0);
                float n2 = cos(p.y * 149.0 - p.x * 19.0);
                float n3 = sin((p.x + p.y) * 213.0);
                material.normal = normalize(vec3(
                    (n1 + .40 * n3) * .029,
                    (n2 - .35 * n3) * .029,
                    1.0
                ));
                prepareMaterial(material);
                float macro = .014 * sin(p.x * .55 + p.y * .23)
                            + .012 * cos(p.y * .83 - p.x * .31);
                float fine = .010 * n1 * n2 + .005 * n3;
                float mow = .005 * sin(p.y * 5.2 + .14 * sin(p.x * 1.1));
                material.baseColor.rgb = materialParams.baseColor * (.985 + macro + fine + mow);
                material.roughness = .86 + .045 * (.5 + .5 * n2);
                material.reflectance = .19;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV140 Fine Turf")
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V140 turf material compile failed" }
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
        check(pkg.isValid) { "V140 material compile failed: $name" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun instance(material: Material, r: Float, g: Float, b: Float): MaterialInstance =
        material.createInstance().also {
            it.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
            materialInstances += it
        }

    private fun setupLighting() {
        skybox = Skybox.Builder().color(.16f, .38f, .75f, 1f).build(engine).also { scene.skybox = it }

        sun = EntityManager.get().create()
        val daylight = Colors.cct(5_650f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(daylight[0], daylight[1], daylight[2])
            .intensity(112_000f)
            .direction(-.30f, -.56f, -.94f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        fill = EntityManager.get().create()
        val skylight = Colors.cct(7_400f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(skylight[0], skylight[1], skylight[2])
            .intensity(19_000f)
            .direction(.48f, .18f, -.76f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)
        camera.setExposure(9.65f, 1f / 125f, 100f)
    }

    private fun setupBall() {
        ballMesh = createRenderable(
            V140Geometry.ellipsoid(0f, 0f, 0f, .021335f, .021335f, .021335f, 20, 30),
            instance(ballMaterial, .965f, .968f, .952f),
            castShadow = true,
            contactShadow = true
        )
        markerMesh = createRenderable(
            V140Geometry.ellipsoid(0f, 0f, .0200f, .0038f, .0038f, .0011f, 7, 12),
            instance(litMaterial, .035f, .036f, .034f),
            castShadow = false,
            contactShadow = false
        )
    }

    private fun syncScene() {
        val settings = game.settings.copy()
        val d = settings.holeDistanceM.takeIf { it.isFinite() }?.coerceIn(.5, 30.0) ?: 5.0
        val tier = V24TvQualityRuntime.snapshot(context).tier
        val key = V140SceneKey(
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
            V24RenderTier.PERFORMANCE -> .64
        }

        sceneMeshes += createRenderable(
            V140Geometry.courseGround(settings, d, density),
            instance(turfMaterial, .115f, .320f, .070f),
            castShadow = false,
            contactShadow = false
        )
        sceneMeshes += createRenderable(
            V140Geometry.green(settings, d, density, fringe = true),
            instance(turfMaterial, .180f, .435f, .095f),
            castShadow = false,
            contactShadow = false
        )
        sceneMeshes += createRenderable(
            V140Geometry.green(settings, d, density, fringe = false),
            instance(turfMaterial, .255f, .565f, .125f),
            castShadow = false,
            contactShadow = false
        )

        addReferenceBackdrop(settings, d, tier)
        addCup(settings, d)
    }

    private fun addReferenceBackdrop(settings: GreenSettings, d: Double, tier: V24RenderTier) {
        val houseY = (d + 6.6).toFloat()
        val houseX = -3.25f
        val houseGround = V140Geometry.groundHeight(settings, d, houseX.toDouble(), houseY.toDouble())

        sceneMeshes += createRenderable(
            V140Geometry.box(houseX, houseY, houseGround + .62f, 1.72f, .82f, .62f),
            instance(litMaterial, .80f, .79f, .72f), true, true
        )
        sceneMeshes += createRenderable(
            V140Geometry.gableRoof(houseX, houseY, houseGround + 1.24f, 1.92f, .96f, .48f),
            instance(litMaterial, .27f, .26f, .29f), true, true
        )
        sceneMeshes += createRenderable(
            V140Geometry.box(houseX - 1.05f, houseY + .10f, houseGround + 1.78f, .16f, .16f, .46f),
            instance(litMaterial, .84f, .83f, .76f), true, true
        )
        for (i in 0..3) {
            val wx = houseX - 1.10f + i * .73f
            sceneMeshes += createRenderable(
                V140Geometry.box(wx, houseY - .835f, houseGround + .72f, .22f, .028f, .25f),
                instance(litMaterial, .085f, .135f, .155f), false, false
            )
        }

        val fenceY = (d + 3.35).toFloat()
        val fenceGround = V140Geometry.groundHeight(settings, d, 0.0, fenceY.toDouble())
        for (x in -6..6 step 2) {
            sceneMeshes += createRenderable(
                V140Geometry.box(x.toFloat(), fenceY, fenceGround + .34f, .035f, .035f, .34f),
                instance(litMaterial, .90f, .90f, .85f), true, true
            )
        }
        for (z in listOf(.24f, .48f)) {
            sceneMeshes += createRenderable(
                V140Geometry.box(0f, fenceY, fenceGround + z, 6.05f, .030f, .028f),
                instance(litMaterial, .90f, .90f, .85f), true, false
            )
        }

        val flowerCount = when (tier) {
            V24RenderTier.HIGH -> 28
            V24RenderTier.BALANCED -> 20
            V24RenderTier.PERFORMANCE -> 12
        }
        for (i in 0 until flowerCount) {
            val t = if (flowerCount <= 1) .5f else i.toFloat() / (flowerCount - 1)
            val x = -5.4f + 10.8f * t
            val y = fenceY - .30f + .08f * sin(i * 1.71f)
            val g = V140Geometry.groundHeight(settings, d, x.toDouble(), y.toDouble())
            val tone = when (i % 4) {
                0 -> floatArrayOf(.86f, .25f, .47f)
                1 -> floatArrayOf(.96f, .72f, .82f)
                2 -> floatArrayOf(.92f, .90f, .72f)
                else -> floatArrayOf(.70f, .22f, .44f)
            }
            sceneMeshes += createRenderable(
                V140Geometry.ellipsoid(x, y, g + .16f, .10f, .08f, .10f, 6, 10),
                instance(litMaterial, tone[0], tone[1], tone[2]), true, false
            )
        }

        val treeSpecs = listOf(
            floatArrayOf(-6.0f, (d + 5.1).toFloat(), 1.15f, .08f, .28f, .07f),
            floatArrayOf(-4.7f, (d + 7.1).toFloat(), 1.45f, .10f, .34f, .08f),
            floatArrayOf(-.7f, (d + 6.2).toFloat(), 1.35f, .10f, .36f, .09f),
            floatArrayOf(1.55f, (d + 6.9).toFloat(), 1.30f, .09f, .34f, .08f),
            floatArrayOf(3.25f, (d + 5.8).toFloat(), 1.28f, .44f, .13f, .12f),
            floatArrayOf(5.0f, (d + 7.0).toFloat(), 1.45f, .10f, .31f, .08f),
            floatArrayOf(6.2f, (d + 5.0).toFloat(), 1.10f, .08f, .28f, .07f)
        )
        treeSpecs.forEachIndexed { index, a ->
            val x = a[0]
            val y = a[1]
            val scale = a[2]
            val g = V140Geometry.groundHeight(settings, d, x.toDouble(), y.toDouble())
            sceneMeshes += createRenderable(
                V140Geometry.cylinder(x, y, g, .075f * scale, .92f * scale, 12),
                instance(litMaterial, .20f, .12f, .055f), true, true
            )
            val crownZ = g + 1.12f * scale
            val lobes = listOf(-.38f to .02f, .34f to .02f, -.05f to .28f)
            lobes.forEachIndexed { li, pair ->
                val redTree = index == 4
                val r = if (redTree) a[3] + li * .025f else a[3] + li * .008f
                val gg = if (redTree) a[4] + li * .010f else a[4] + li * .014f
                val bb = if (redTree) a[5] + li * .014f else a[5] + li * .006f
                sceneMeshes += createRenderable(
                    V140Geometry.ellipsoid(
                        x + pair.first * scale,
                        y + pair.second * scale,
                        crownZ + li * .17f * scale,
                        .72f * scale,
                        .56f * scale,
                        .72f * scale,
                        9,
                        15
                    ),
                    instance(litMaterial, r, gg, bb), true, false
                )
            }
        }

        val cloudY = (d + 18.0).toFloat()
        val clouds = listOf(
            floatArrayOf(-5.0f, cloudY, 4.4f, 1.7f, .58f, .48f),
            floatArrayOf(-3.6f, cloudY + .4f, 4.65f, 1.25f, .52f, .42f),
            floatArrayOf(3.8f, cloudY + .2f, 4.9f, 1.8f, .62f, .50f),
            floatArrayOf(5.2f, cloudY + .5f, 5.15f, 1.15f, .48f, .40f)
        )
        clouds.forEach { c ->
            sceneMeshes += createRenderable(
                V140Geometry.ellipsoid(c[0], c[1], c[2], c[3], c[4], c[5], 8, 16),
                instance(litMaterial, .94f, .95f, .97f), false, false
            )
        }
    }

    private fun addCup(settings: GreenSettings, d: Double) {
        val surfaceZ = GreenTerrain.effectiveHeightAt(settings, 0.0, d).toFloat() + .020f
        val depth = V135RigidBallPhysics.CUP_DEPTH_M.toFloat()
        sceneMeshes += createRenderable(
            V140Geometry.cupWall(0f, d.toFloat(), surfaceZ - .001f, .054f, depth, 48),
            instance(litMaterial, .87f, .88f, .84f), false, true
        )
        sceneMeshes += createRenderable(
            V140Geometry.disc(0f, d.toFloat(), surfaceZ - depth + .002f, .052f, 48),
            instance(litMaterial, .010f, .012f, .010f), false, false
        )
        sceneMeshes += createRenderable(
            V140Geometry.ring(0f, d.toFloat(), surfaceZ - .003f, .047f, .054f, 48),
            instance(litMaterial, .82f, .83f, .79f), false, false
        )

        // Public putting still shows the flag in the hole; rendering follows the physical setting.
        if (settings.flagstickIn) {
            sceneMeshes += createRenderable(
                V140Geometry.cylinder(0f, d.toFloat(), surfaceZ - depth, .0065f, 1.95f, 14),
                instance(litMaterial, .88f, .89f, .86f), true, true
            )
            sceneMeshes += createRenderable(
                V140Geometry.flag(0f, d.toFloat(), surfaceZ + 1.80f),
                instance(litMaterial, .78f, .035f, .030f), true, false
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
        geometry: V140GeometryData,
        material: MaterialInstance,
        castShadow: Boolean,
        contactShadow: Boolean
    ): V140Mesh {
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(geometry.vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, V140GeometryData.VERTEX_BYTES)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, V140GeometryData.VERTEX_BYTES)
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
        return V140Mesh(entity, vb, ib, material)
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
        runCatching { engine.destroyMaterial(turfMaterial) }
        runCatching { engine.destroyMaterial(litMaterial) }
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

    private fun destroyMesh(mesh: V140Mesh) {
        runCatching { scene.removeEntity(mesh.entity) }
        runCatching { engine.destroyEntity(mesh.entity) }
        runCatching { EntityManager.get().destroy(mesh.entity) }
        runCatching { engine.destroyVertexBuffer(mesh.vertexBuffer) }
        runCatching { engine.destroyIndexBuffer(mesh.indexBuffer) }
        runCatching { engine.destroyMaterialInstance(mesh.material) }
        materialInstances.remove(mesh.material)
    }
}

private data class V140GeometryData(
    val vertices: ByteBuffer,
    val indices: ByteBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val bounds: Box
) {
    companion object { const val VERTEX_BYTES = 28 }
}

private class V140Builder {
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
        require(base + 3 < 65535) { "V140 mesh exceeds ushort index range" }
        add(a, normal); add(b, normal); add(c, normal)
        indices += base.toShort(); indices += (base + 1).toShort(); indices += (base + 2).toShort()
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, normal: FloatArray) {
        val base = vertices.size
        require(base + 4 < 65535) { "V140 mesh exceeds ushort index range" }
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

    fun build(): V140GeometryData {
        require(vertices.isNotEmpty() && indices.isNotEmpty()) { "empty V140 geometry" }
        val vb = ByteBuffer.allocateDirect(vertices.size * V140GeometryData.VERTEX_BYTES).order(ByteOrder.nativeOrder())
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
        return V140GeometryData(
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

private object V140Geometry {
    fun groundHeight(settings: GreenSettings, d: Double, x: Double, y: Double): Float {
        val greenInfluence = y in -1.0..(d * 1.72)
        val base = if (greenInfluence) {
            GreenTerrain.effectiveHeightAt(settings, x.coerceIn(-4.8, 4.8), y.coerceAtLeast(0.0)).toFloat()
        } else 0f
        val noise = (.008 * sin(x * .49 + y * .15) + .006 * sin(y * .35 - x * .25)).toFloat()
        val sideT = ((abs(x) - 6.4) / 7.0).coerceIn(0.0, 1.0)
        val sideRise = (.28 * sideT.pow(1.45)).toFloat()
        val farT = ((y - (d + 8.0)) / 15.0).coerceIn(0.0, 1.0)
        val farRise = (.52 * farT.pow(1.35)).toFloat()
        val cupDist = hypot(x, y - d)
        val cupSink = if (cupDist < .11) -.13f else 0f
        return base - .045f + noise + sideRise + farRise + cupSink
    }

    fun courseGround(settings: GreenSettings, d: Double, density: Double): V140GeometryData {
        val cols = (56 * density).toInt().coerceIn(34, 60)
        val rows = (84 * density).toInt().coerceIn(48, 90)
        val xHalf = 13.0
        val yMin = -4.5
        val yMax = d + 21.0
        val b = V140Builder()
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
        val centerPinch = .52 * sin(PI * t).pow(2.0)
        val base = 4.85 - centerPinch
        return base + if (fringe) .48 else 0.0
    }

    fun green(settings: GreenSettings, d: Double, density: Double, fringe: Boolean): V140GeometryData {
        val cols = (92 * density).toInt().coerceIn(60, 100)
        val rows = (98 * density).toInt().coerceIn(64, 104)
        val xHalf = if (fringe) 5.4 else 5.0
        val yMin = -1.35
        val yMax = d + 3.0
        val b = V140Builder()
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
                val z0 = GreenTerrain.effectiveHeightAt(settings, x0.coerceIn(-4.8, 4.8), y0.coerceAtLeast(0.0)).toFloat() + if (fringe) .014f else .020f
                val z1 = GreenTerrain.effectiveHeightAt(settings, x1.coerceIn(-4.8, 4.8), y0.coerceAtLeast(0.0)).toFloat() + if (fringe) .014f else .020f
                val z2 = GreenTerrain.effectiveHeightAt(settings, x1.coerceIn(-4.8, 4.8), y1.coerceAtLeast(0.0)).toFloat() + if (fringe) .014f else .020f
                val z3 = GreenTerrain.effectiveHeightAt(settings, x0.coerceIn(-4.8, 4.8), y1.coerceAtLeast(0.0)).toFloat() + if (fringe) .014f else .020f
                val p0 = p(x0, y0, z0); val p1 = p(x1, y0, z1)
                val p2 = p(x1, y1, z2); val p3 = p(x0, y1, z3)
                b.quad(p0, p1, p2, p3, normalFromQuad(p0, p1, p3))
            }
        }
        return b.build()
    }

    fun box(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float): V140GeometryData {
        val b = V140Builder()
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

    fun gableRoof(cx: Float, cy: Float, baseZ: Float, hx: Float, hy: Float, roofH: Float): V140GeometryData {
        val b = V140Builder()
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

    fun cupWall(cx: Float, cy: Float, topZ: Float, radius: Float, depth: Float, steps: Int): V140GeometryData {
        val b = V140Builder()
        val n = steps.coerceIn(24, 64)
        val bottomZ = topZ - depth
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            val x0 = cx + cos(a0).toFloat() * radius
            val y0 = cy + sin(a0).toFloat() * radius
            val x1 = cx + cos(a1).toFloat() * radius
            val y1 = cy + sin(a1).toFloat() * radius
            val mid = (a0 + a1) * .5
            b.quad(
                p(x1, y1, topZ), p(x0, y0, topZ), p(x0, y0, bottomZ), p(x1, y1, bottomZ),
                floatArrayOf(-cos(mid).toFloat(), -sin(mid).toFloat(), 0f)
            )
        }
        return b.build()
    }

    fun disc(cx: Float, cy: Float, cz: Float, radius: Float, steps: Int): V140GeometryData {
        val b = V140Builder()
        val center = p(cx, cy, cz)
        val n = steps.coerceIn(18, 64)
        for (i in 0 until n) {
            val a0 = 2.0 * PI * i / n
            val a1 = 2.0 * PI * (i + 1) / n
            b.tri(center, p(cx + cos(a0).toFloat() * radius, cy + sin(a0).toFloat() * radius, cz), p(cx + cos(a1).toFloat() * radius, cy + sin(a1).toFloat() * radius, cz), floatArrayOf(0f, 0f, 1f))
        }
        return b.build()
    }

    fun ring(cx: Float, cy: Float, cz: Float, inner: Float, outer: Float, steps: Int): V140GeometryData {
        val b = V140Builder()
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

    fun cylinder(cx: Float, cy: Float, baseZ: Float, radius: Float, height: Float, steps: Int): V140GeometryData {
        val b = V140Builder()
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

    fun ellipsoid(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, latRaw: Int, lonRaw: Int): V140GeometryData {
        val latN = latRaw.coerceIn(5, 22)
        val lonN = lonRaw.coerceIn(8, 34)
        val b = V140Builder()
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
            val a = point(lat, lon); val bb = point(lat + 1, lon)
            val c = point(lat + 1, nl); val d = point(lat, nl)
            b.tri(a, bb, c, normal(a)); b.tri(a, c, d, normal(a))
        }
        return b.build()
    }

    fun flag(cx: Float, cy: Float, topZ: Float): V140GeometryData {
        val b = V140Builder()
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
