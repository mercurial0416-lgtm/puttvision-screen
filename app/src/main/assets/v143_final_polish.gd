extends "res://v143_final_scene.gd"

# Final compatibility-safe sky polish driven by the rendered 1080p reference frame.
# The previous procedural cloud bands were too large at sphere UV scale; keep the sky
# photographic in value structure but deliberately cloud-light for a clean golf broadcast look.

func _build_sky_dome() -> void:
    var dome := MeshInstance3D.new()
    dome.name = "FinalSkyDome"
    var mesh := SphereMesh.new()
    mesh.radius = 95.0
    mesh.height = 190.0
    mesh.radial_segments = 64
    mesh.rings = 32
    dome.mesh = mesh
    dome.position = Vector3(0.0, -14.0, -24.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_front;
void fragment(){
    float v = clamp(UV.y, 0.0, 1.0);
    float horizon = 1.0 - abs(v - 0.50) * 2.0;
    horizon = pow(clamp(horizon, 0.0, 1.0), 1.45);

    vec3 zenith = vec3(0.105, 0.34, 0.62);
    vec3 horizon_blue = vec3(0.56, 0.74, 0.83);
    vec3 sky = mix(zenith, horizon_blue, horizon * 0.88);

    // Small warm atmospheric glow, not a hard arcade sun disc.
    vec2 sun_uv = vec2(0.70, 0.31);
    float sun_dist = distance(UV, sun_uv);
    float glow = exp(-sun_dist * 24.0) * 0.18;
    sky += vec3(1.0, 0.78, 0.48) * glow;

    // Very subtle high-altitude haze; no large repeating cloud bands.
    float haze = sin(UV.x * 71.0 + UV.y * 17.0) * sin(UV.x * 43.0 - UV.y * 29.0);
    haze = smoothstep(0.82, 0.98, haze * 0.5 + 0.5) * 0.035 * horizon;
    sky = mix(sky, vec3(0.91, 0.94, 0.95), haze);

    ALBEDO = sky;
    EMISSION = sky * 0.20;
    ROUGHNESS = 1.0;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    dome.material_override = material
    add_child(dome)
