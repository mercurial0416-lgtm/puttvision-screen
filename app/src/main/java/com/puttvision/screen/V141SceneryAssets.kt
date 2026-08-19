package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.filamat.MaterialBuilder
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Original runtime-generated scenery textures used by the V141 clean-room presentation. */
object V141SceneryAssets {
    data class Maps(
        val sky: Texture,
        val tree: Texture,
        val sampler: TextureSampler
    )

    fun create(engine: Engine): Maps {
        val skyBitmap = buildSkyBitmap(768, 384)
        val treeBitmap = buildTreeBitmap(320, 512)
        val sky = upload(engine, skyBitmap, Texture.InternalFormat.SRGB8_A8)
        val tree = upload(engine, treeBitmap, Texture.InternalFormat.SRGB8_A8)
        skyBitmap.recycle()
        treeBitmap.recycle()
        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE
        ).also { it.anisotropy = 4.0f }
        return Maps(sky, tree, sampler)
    }

    fun buildSkyMaterial(engine: Engine): Material {
        val source = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec3 p = getUserWorldPosition();
                float u = clamp((p.x - materialParams.centerX) / materialParams.width + 0.5, 0.0, 1.0);
                float v = clamp((p.z - materialParams.baseZ) / materialParams.height, 0.0, 1.0);
                vec4 sky = texture(materialParams_skyMap, vec2(u, v));
                material.baseColor = vec4(sky.rgb * materialParams.exposureTint, 1.0);
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV141 Textured Sky")
            .shading(MaterialBuilder.Shading.UNLIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "centerX")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "baseZ")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "width")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "height")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "exposureTint")
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.MEDIUM,
                "skyMap"
            )
            .culling(MaterialBuilder.CullingMode.NONE)
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V141 sky material compile failed" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    fun buildTreeMaterial(engine: Engine): Material {
        val source = """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec3 p = getUserWorldPosition();
                float u = clamp((p.x - materialParams.centerX) / materialParams.width + 0.5, 0.0, 1.0);
                float v = clamp((p.z - materialParams.baseZ) / materialParams.height, 0.0, 1.0);
                vec4 tex = texture(materialParams_treeMap, vec2(u, v));
                material.baseColor = vec4(tex.rgb * materialParams.tint, tex.a);
                material.roughness = 0.92;
                material.reflectance = 0.10;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV141 Tree Billboard")
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "centerX")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "baseZ")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "width")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "height")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "tint")
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.MEDIUM,
                "treeMap"
            )
            .blending(MaterialBuilder.BlendingMode.MASKED)
            .maskThreshold(.30f)
            .doubleSided(true)
            .culling(MaterialBuilder.CullingMode.NONE)
            .transparentShadow(true)
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V141 tree material compile failed" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    fun skyInstance(
        material: Material,
        maps: Maps,
        centerX: Float,
        baseZ: Float,
        width: Float,
        height: Float
    ): MaterialInstance = material.createInstance().also {
        it.setParameter("centerX", centerX)
        it.setParameter("baseZ", baseZ)
        it.setParameter("width", width)
        it.setParameter("height", height)
        it.setParameter("exposureTint", 1.0f, 1.0f, 1.0f)
        it.setParameter("skyMap", maps.sky, maps.sampler)
    }

    fun treeInstance(
        material: Material,
        maps: Maps,
        centerX: Float,
        baseZ: Float,
        width: Float,
        height: Float,
        r: Float,
        g: Float,
        b: Float
    ): MaterialInstance = material.createInstance().also {
        it.setParameter("centerX", centerX)
        it.setParameter("baseZ", baseZ)
        it.setParameter("width", width)
        it.setParameter("height", height)
        it.setParameter("tint", r, g, b)
        it.setParameter("treeMap", maps.tree, maps.sampler)
    }

    fun destroy(engine: Engine, maps: Maps?) {
        if (maps == null) return
        runCatching { engine.destroyTexture(maps.sky) }
        runCatching { engine.destroyTexture(maps.tree) }
    }

    private fun buildSkyBitmap(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val v = y.toDouble() / (height - 1).coerceAtLeast(1)
            // Bitmap top is sky zenith; bottom is a warm, slightly hazy horizon.
            val horizon = v
            for (x in 0 until width) {
                val u = x.toDouble() / width
                var r = lerp(37.0, 137.0, horizon)
                var g = lerp(93.0, 183.0, horizon)
                var b = lerp(182.0, 222.0, horizon)

                val cloudField = cloudDensity(u, v)
                if (cloudField > 0.0) {
                    val a = (cloudField * .82).coerceIn(0.0, .82)
                    r = lerp(r, 245.0, a)
                    g = lerp(g, 247.0, a)
                    b = lerp(b, 249.0, a)
                }
                val haze = ((v - .72) / .28).coerceIn(0.0, 1.0) * .14
                r = lerp(r, 223.0, haze)
                g = lerp(g, 229.0, haze)
                b = lerp(b, 231.0, haze)
                pixels[y * width + x] = Color.rgb(r.toInt(), g.toInt(), b.toInt())
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun cloudDensity(u: Double, v: Double): Double {
        // Several broad cloud banks with multi-octave erosion; original and deterministic.
        val banks = listOf(
            doubleArrayOf(.18, .38, .17, .070),
            doubleArrayOf(.31, .31, .12, .055),
            doubleArrayOf(.68, .34, .18, .073),
            doubleArrayOf(.84, .28, .13, .060)
        )
        var shape = 0.0
        for (b in banks) {
            val dx = wrapDelta(u - b[0]) / b[2]
            val dy = (v - b[1]) / b[3]
            shape = max(shape, exp(-(dx * dx + dy * dy) * 1.25))
        }
        if (shape < .08) return 0.0
        val noise = .58 * noise(u * 13.0, v * 13.0, 141) +
            .28 * noise(u * 31.0, v * 31.0, 3141) +
            .14 * noise(u * 71.0, v * 71.0, 5141)
        return ((shape * 1.20 + noise * .52) - .52).coerceIn(0.0, 1.0)
    }

    private fun buildTreeBitmap(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val lobes = listOf(
            doubleArrayOf(.50, .31, .28, .22),
            doubleArrayOf(.31, .39, .20, .19),
            doubleArrayOf(.69, .40, .21, .20),
            doubleArrayOf(.42, .52, .24, .20),
            doubleArrayOf(.60, .53, .25, .21),
            doubleArrayOf(.50, .19, .19, .16)
        )
        for (y in 0 until height) {
            val v = y.toDouble() / (height - 1).coerceAtLeast(1)
            for (x in 0 until width) {
                val u = x.toDouble() / (width - 1).coerceAtLeast(1)
                var crown = 0.0
                for (l in lobes) {
                    val dx = (u - l[0]) / l[2]
                    val dy = (v - l[1]) / l[3]
                    crown = max(crown, 1.0 - sqrt(dx * dx + dy * dy))
                }
                val edgeNoise = noise(u * 35.0, v * 35.0, 8141) * .20 - .10
                crown += edgeNoise
                val trunkHalf = .032 + (1.0 - v).coerceIn(0.0, .35) * .025
                val trunk = v > .48 && v < .97 && kotlin.math.abs(u - .50) < trunkHalf
                val index = y * width + x
                if (crown > .02 && v < .72) {
                    val leafNoise = noise(u * 53.0, v * 53.0, 10141)
                    val light = (.72 + leafNoise * .28).coerceIn(.65, 1.0)
                    val r = (52 * light).toInt().coerceIn(25, 75)
                    val g = (118 * light).toInt().coerceIn(58, 148)
                    val b = (42 * light).toInt().coerceIn(20, 66)
                    val alpha = ((crown * 2.6).coerceIn(0.0, 1.0) * 255).toInt()
                    pixels[index] = Color.argb(alpha, r, g, b)
                } else if (trunk) {
                    pixels[index] = Color.argb(255, 91, 61, 35)
                } else {
                    pixels[index] = Color.TRANSPARENT
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun upload(engine: Engine, bitmap: Bitmap, format: Texture.InternalFormat): Texture {
        val texture = Texture.Builder()
            .width(bitmap.width)
            .height(bitmap.height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(format)
            .build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap)
        return texture
    }

    private fun noise(x: Double, y: Double, seed: Int): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3.0 - 2.0 * fx)
        val sy = fy * fy * (3.0 - 2.0 * fy)
        val a = hash01(x0, y0, seed)
        val b = hash01(x0 + 1, y0, seed)
        val c = hash01(x0, y0 + 1, seed)
        val d = hash01(x0 + 1, y0 + 1, seed)
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy)
    }

    private fun hash01(x: Int, y: Int, seed: Int): Double {
        var n = x * 0x1f123bb5 + y * 0x6c8e9cf5 + seed * 0x45d9f3b
        n = (n xor (n ushr 16)) * 0x45d9f3b
        n = (n xor (n ushr 16)) * 0x45d9f3b
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toDouble() / Int.MAX_VALUE.toDouble()
    }

    private fun wrapDelta(x: Double): Double = when {
        x > .5 -> x - 1.0
        x < -.5 -> x + 1.0
        else -> x
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
