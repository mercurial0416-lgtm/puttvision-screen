package com.puttvision.screen

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.View
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.filamat.MaterialBuilder
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V141 replaces V140's shader-only fake turf with actual GPU texture samplers.
 *
 * The maps are deterministic, original PuttVision assets generated once at renderer startup:
 * - sRGB albedo with fine blade/grain variation
 * - tangent-space normal map
 * - linear roughness map
 *
 * They are intentionally generated in-app instead of copying any Friends Screen texture/asset.
 */
object V141PbrAssets {
    data class TurfMaps(
        val albedo: Texture,
        val normal: Texture,
        val roughness: Texture,
        val sampler: TextureSampler
    )

    fun createTurfMaps(engine: Engine, sizeRaw: Int = 384): TurfMaps {
        val size = sizeRaw.coerceIn(256, 512)
        val albedoBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val normalBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val roughBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val albedoPixels = IntArray(size * size)
        val normalPixels = IntArray(size * size)
        val roughPixels = IntArray(size * size)

        fun h(x: Int, y: Int): Double {
            val fx = x.toDouble() / size
            val fy = y.toDouble() / size
            val broad = valueNoise(fx * 7.0, fy * 7.0, 0x141)
            val medium = valueNoise(fx * 29.0, fy * 29.0, 0x3141)
            val fine = valueNoise(fx * 97.0, fy * 97.0, 0x5141)
            val blade = sin((fy * 132.0 + fx * 7.0) * Math.PI * 2.0) * 0.035
            return broad * .40 + medium * .34 + fine * .26 + blade
        }

        for (y in 0 until size) {
            val fy = y.toDouble() / size
            for (x in 0 until size) {
                val fx = x.toDouble() / size
                val height = h(x, y)
                val macro = valueNoise(fx * 5.0, fy * 5.0, 0x7141)
                val micro = valueNoise(fx * 71.0, fy * 71.0, 0x9141)
                val mow = sin((fy * 10.0 + sin(fx * Math.PI * 2.0) * .03) * Math.PI * 2.0)

                val rr = (51.0 + (macro - .5) * 20.0 + (micro - .5) * 10.0 + mow * 3.0).toInt().coerceIn(24, 82)
                val gg = (124.0 + (macro - .5) * 34.0 + (micro - .5) * 17.0 + mow * 6.0).toInt().coerceIn(72, 176)
                val bb = (43.0 + (macro - .5) * 17.0 + (micro - .5) * 8.0 + mow * 2.0).toInt().coerceIn(20, 70)
                val index = y * size + x
                albedoPixels[index] = Color.rgb(rr, gg, bb)

                val xl = if (x == 0) size - 1 else x - 1
                val xr = if (x == size - 1) 0 else x + 1
                val yu = if (y == 0) size - 1 else y - 1
                val yd = if (y == size - 1) 0 else y + 1
                val dx = h(xr, y) - h(xl, y)
                val dy = h(x, yd) - h(x, yu)
                var nx = -dx * 3.7
                var ny = -dy * 3.7
                var nz = 1.0
                val nm = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6)
                nx /= nm
                ny /= nm
                nz /= nm
                normalPixels[index] = Color.rgb(
                    ((nx * .5 + .5) * 255.0).toInt().coerceIn(0, 255),
                    ((ny * .5 + .5) * 255.0).toInt().coerceIn(0, 255),
                    ((nz * .5 + .5) * 255.0).toInt().coerceIn(0, 255)
                )

                val rough = (212.0 + (height - .5) * 28.0 + (micro - .5) * 12.0).toInt().coerceIn(176, 242)
                roughPixels[index] = Color.rgb(rough, rough, rough)
            }
        }

        albedoBitmap.setPixels(albedoPixels, 0, size, 0, 0, size, size)
        normalBitmap.setPixels(normalPixels, 0, size, 0, 0, size, size)
        roughBitmap.setPixels(roughPixels, 0, size, 0, 0, size, size)

        val albedo = textureFromBitmap(engine, albedoBitmap, Texture.InternalFormat.SRGB8_A8)
        val normal = textureFromBitmap(engine, normalBitmap, Texture.InternalFormat.RGBA8)
        val roughness = textureFromBitmap(engine, roughBitmap, Texture.InternalFormat.RGBA8)
        albedoBitmap.recycle()
        normalBitmap.recycle()
        roughBitmap.recycle()

        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.REPEAT
        ).also { it.anisotropy = 8.0f }
        return TurfMaps(albedo, normal, roughness, sampler)
    }

    fun buildTurfMaterial(engine: Engine): Material {
        val source = """
            void material(inout MaterialInputs material) {
                vec3 p = getUserWorldPosition();
                vec2 uv = p.xy * materialParams.tileScale;
                vec3 n = texture(materialParams_normalMap, uv).xyz * 2.0 - 1.0;
                n.xy *= materialParams.normalStrength;
                material.normal = normalize(n);
                prepareMaterial(material);

                vec3 tex = texture(materialParams_albedoMap, uv).rgb;
                float rough = texture(materialParams_roughnessMap, uv).r;
                float broad = 0.985 + 0.018 * sin(p.y * 0.41) + 0.012 * cos(p.x * 0.37 + p.y * 0.13);
                material.baseColor.rgb = tex * materialParams.tint * broad;
                material.roughness = clamp(rough, 0.66, 0.96);
                material.reflectance = 0.18;
                material.metallic = 0.0;
            }
        """.trimIndent()
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("PV141 PBR Turf")
            .shading(MaterialBuilder.Shading.LIT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "tint")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "tileScale")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "normalStrength")
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.MEDIUM,
                "albedoMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.MEDIUM,
                "normalMap"
            )
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.MEDIUM,
                "roughnessMap"
            )
            .material(source)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build(engine)
        check(pkg.isValid) { "V141 PBR turf material compile failed" }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    fun createTurfInstance(
        material: Material,
        maps: TurfMaps,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        tileScale: Float,
        normalStrength: Float
    ): MaterialInstance = material.createInstance().also { mi ->
        mi.setParameter("tint", tintR, tintG, tintB)
        mi.setParameter("tileScale", tileScale)
        mi.setParameter("normalStrength", normalStrength)
        mi.setParameter("albedoMap", maps.albedo, maps.sampler)
        mi.setParameter("normalMap", maps.normal, maps.sampler)
        mi.setParameter("roughnessMap", maps.roughness, maps.sampler)
    }

    fun configureView(view: View) {
        view.isPostProcessingEnabled = true

        val ao = view.ambientOcclusionOptions
        ao.enabled = true
        ao.radius = .34f
        ao.intensity = 1.15f
        ao.power = 1.15f
        ao.quality = View.QualityLevel.HIGH
        ao.lowPassFilter = View.QualityLevel.MEDIUM
        ao.upsampling = View.QualityLevel.HIGH
        view.ambientOcclusionOptions = ao

        val bloom = view.bloomOptions
        bloom.enabled = true
        bloom.strength = .055f
        bloom.threshold = true
        bloom.highlight = 9.5f
        view.bloomOptions = bloom

        val vignette = view.vignetteOptions
        vignette.enabled = true
        vignette.midPoint = .74f
        vignette.roundness = .86f
        vignette.feather = .42f
        view.vignetteOptions = vignette

        val taa = view.temporalAntiAliasingOptions
        taa.enabled = true
        taa.filterHistory = true
        taa.filterInput = true
        taa.upscaling = 1.0f
        view.temporalAntiAliasingOptions = taa
    }

    fun destroy(engine: Engine, maps: TurfMaps?) {
        if (maps == null) return
        runCatching { engine.destroyTexture(maps.albedo) }
        runCatching { engine.destroyTexture(maps.normal) }
        runCatching { engine.destroyTexture(maps.roughness) }
    }

    private fun textureFromBitmap(engine: Engine, bitmap: Bitmap, format: Texture.InternalFormat): Texture {
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

    private fun valueNoise(x: Double, y: Double, seed: Int): Double {
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
        val ab = a + (b - a) * sx
        val cd = c + (d - c) * sx
        return ab + (cd - ab) * sy
    }

    private fun hash01(x: Int, y: Int, seed: Int): Double {
        var n = x * 0x1f123bb5 + y * 0x6c8e9cf5 + seed * 0x45d9f3b
        n = (n xor (n ushr 16)) * 0x45d9f3b
        n = (n xor (n ushr 16)) * 0x45d9f3b
        n = n xor (n ushr 16)
        return (n and 0x7fffffff).toDouble() / Int.MAX_VALUE.toDouble()
    }
}
