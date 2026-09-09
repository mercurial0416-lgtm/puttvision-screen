extends "res://v143_final_scene.gd"

# Reference-frame driven final pass. Keep the bridge/physics untouched and make the
# renderer read like a lit golf surface instead of a uniformly green procedural plane.

func _build_materials() -> void:
    super._build_materials()
    mat_green = _broadcast_grass(Color("#345f3d"), Vector2(7.5, 24.0), 0.78, 0.22, 0.060)
    mat_fringe = _broadcast_grass(Color("#2d5336"), Vector2(9.0, 20.0), 0.70, 0.30, 0.038)
    mat_rough = _broadcast_grass(Color("#284a31"), Vector2(12.0, 18.0), 0.62, 0.38, 0.018)

func _broadcast_grass(tint_color: Color, tiling: Vector2, brightness: float, texture_mix: float, stripe_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D albedo_tex : source_color, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D normal_tex : hint_normal, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D rough_tex : repeat_enable, filter_linear_mipmap_anisotropic;
uniform vec3 tint : source_color = vec3(0.20, 0.36, 0.24);
uniform vec2 tiling = vec2(7.5, 24.0);
uniform float brightness = 0.78;
uniform float texture_mix = 0.22;
uniform float stripe_strength = 0.06;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
float terrain_relief(vec2 uv){
    float side = uv.x * 2.0 - 1.0;
    float shoulder = pow(abs(side), 1.75);
    float depth = smoothstep(0.07, 0.92, uv.y);
    float center_guard = smoothstep(0.10, 0.72, abs(side));
    float broad_roll = sin(uv.y * 10.2 + side * 1.4) * 0.018;
    float secondary_roll = sin(uv.y * 21.0 - side * 3.2) * 0.007;
    return shoulder * depth * 0.105 + (broad_roll + secondary_roll) * depth * mix(0.10, 1.0, center_guard);
}
void vertex(){
    // Keep the putting line nearly flat while lifting the shoulders and distant surface.
    // This is presentation-only geometry; Android remains authoritative for putt physics.
    float relief = terrain_relief(UV);
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope + relief;

    // The previous pass displaced vertices but left the turf normal pointing straight up,
    // so the broad rolls barely reacted to the key light. Rebuild a conservative geometric
    // normal from UV derivatives so TV-distance terrain reads through light, not fake albedo.
    const float eps = 0.003;
    float dh_du = (terrain_relief(vec2(clamp(UV.x + eps, 0.0, 1.0), UV.y)) - terrain_relief(vec2(clamp(UV.x - eps, 0.0, 1.0), UV.y))) / (2.0 * eps);
    float dh_dv = (terrain_relief(vec2(UV.x, clamp(UV.y + eps, 0.0, 1.0))) - terrain_relief(vec2(UV.x, clamp(UV.y - eps, 0.0, 1.0)))) / (2.0 * eps);
    float dh_dx = side_slope + dh_du / 11.0;
    float dh_dz = -long_slope - dh_dv / 64.0;
    NORMAL = normalize(vec3(-dh_dx, 1.0, -dh_dz));
}
void fragment(){
    vec2 uv = UV * tiling;
    vec3 texel = texture(albedo_tex, uv).rgb;
    float tex_luma = dot(texel, vec3(0.299, 0.587, 0.114));

    // Broad mower lanes are deliberately screen-readable at 1080p, but remain subtle.
    // A second long-axis modulation prevents the green from looking like a flat striped mat.
    float lane = floor(UV.x * 10.0);
    float lane_sign = mod(lane, 2.0) < 1.0 ? -1.0 : 1.0;
    float feather = smoothstep(0.03, 0.14, abs(fract(UV.x * 10.0) - 0.5));
    float mow = lane_sign * stripe_strength * mix(0.55, 1.0, feather);
    float long_wave = sin(UV.y * 22.0 + sin(UV.x * 6.0) * 0.45) * 0.012;

    // Macro variation survives mipmapping while the CC0 scan supplies close texture.
    float macro = (hash21(floor(UV * vec2(28.0, 74.0))) - 0.5) * 0.020;
    float texture_shape = mix(1.0, 0.82 + tex_luma * 0.36, texture_mix);
    vec3 base = tint * brightness * (texture_shape + mow + long_wave + macro);

    // Alternate cut direction changes perceived sheen, not just albedo.
    float neutral = dot(base, vec3(0.299, 0.587, 0.114));
    ALBEDO = mix(vec3(neutral), base, 0.86);
    NORMAL_MAP = texture(normal_tex, uv).rgb;
    NORMAL_MAP_DEPTH = 0.30;
    ROUGHNESS = clamp(mix(0.90, texture(rough_tex, uv).r, 0.38) - mow * 0.55, 0.72, 0.96);
    SPECULAR = 0.15;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("albedo_tex", load(TURF_ALBEDO))
    material.set_shader_parameter("normal_tex", load(TURF_NORMAL))
    material.set_shader_parameter("rough_tex", load(TURF_ROUGH))
    material.set_shader_parameter("tint", Vector3(tint_color.r, tint_color.g, tint_color.b))
    material.set_shader_parameter("tiling", tiling)
    material.set_shader_parameter("brightness", brightness)
    material.set_shader_parameter("texture_mix", texture_mix)
    material.set_shader_parameter("stripe_strength", stripe_strength)
    return material

func _build_course() -> void:
    # Let the base class create the gameplay-facing nodes (including the aim line), then replace
    # only the three rectangular presentation meshes. Physics remains Android authoritative.
    super._build_course()
    _replace_course_plane("Rough", -0.032, 3.5, -67.0, 19.0, 23.0, mat_rough, 0.55)
    _replace_course_plane("Fringe", -0.014, 2.8, -33.5, 6.7, 8.2, mat_fringe, 1.35)
    _replace_course_plane("Green", 0.0, 2.45, -31.7, 5.35, 6.7, mat_green, 2.15)
    _build_visual_bunker(Vector3(-6.15, -0.020, -8.9), Vector2(2.25, 1.05), -8.0)
    _build_visual_bunker(Vector3(6.45, -0.021, -13.6), Vector2(1.75, 0.86), 14.0)

func _replace_course_plane(name_value: String, y_value: float, z_near: float, z_far: float, near_half_width: float, far_half_width: float, material: Material, phase: float) -> void:
    var old_node := get_node_or_null(name_value)
    if old_node != null:
        remove_child(old_node)
        old_node.queue_free()

    var node := MeshInstance3D.new()
    node.name = name_value
    node.mesh = _organic_course_mesh(y_value, z_near, z_far, near_half_width, far_half_width, phase)
    node.material_override = material
    add_child(node)

func _organic_course_mesh(y_value: float, z_near: float, z_far: float, near_half_width: float, far_half_width: float, phase: float) -> ArrayMesh:
    const LONG_STEPS := 56
    const WIDTH_STEPS := 14
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var indices := PackedInt32Array()

    for zi in range(LONG_STEPS + 1):
        var t: float = float(zi) / float(LONG_STEPS)
        var z: float = lerp(z_near, z_far, t)
        var edge_noise: float = sin(t * TAU * 1.20 + phase) * 0.42 + sin(t * TAU * 3.35 + phase * 0.7) * 0.20
        var half_width: float = lerp(near_half_width, far_half_width, t) + edge_noise
        var center_x: float = (sin(t * PI * 1.35 + phase * 0.28) * 0.54 + sin(t * TAU * 2.30 + phase) * 0.16) * min(1.0, t * 3.0)

        for xi in range(WIDTH_STEPS + 1):
            var u: float = float(xi) / float(WIDTH_STEPS)
            var side: float = u * 2.0 - 1.0
            var edge_rounding: float = 0.94 + 0.06 * cos(side * PI)
            var x: float = center_x + side * half_width * edge_rounding
            vertices.append(Vector3(x, y_value, z))
            normals.append(Vector3.UP)
            uvs.append(Vector2(u, t))

    var row: int = WIDTH_STEPS + 1
    for zi in range(LONG_STEPS):
        for xi in range(WIDTH_STEPS):
            var a: int = zi * row + xi
            var b: int = a + 1
            var c: int = a + row
            var d: int = c + 1
            indices.append(a)
            indices.append(c)
            indices.append(b)
            indices.append(b)
            indices.append(c)
            indices.append(d)

    var arrays := []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _build_visual_bunker(pos: Vector3, footprint: Vector2, yaw_degrees: float) -> void:
    var root := Node3D.new()
    root.name = "VisualBunker"
    root.position = pos
    root.rotation_degrees.y = yaw_degrees
    add_child(root)

    # A lowered sand floor plus raised turf lip reads as a bunker instead of a beige decal.
    var lip := MeshInstance3D.new()
    lip.name = "BunkerLip"
    var lip_mesh := CylinderMesh.new()
    lip_mesh.top_radius = 1.10
    lip_mesh.bottom_radius = 1.16
    lip_mesh.height = 0.055
    lip_mesh.radial_segments = 64
    lip.mesh = lip_mesh
    lip.position.y = 0.018
    lip.scale = Vector3(footprint.x, 0.50, footprint.y)
    lip.material_override = _pbr(Color("#39563a"), 0.94, 0.0)
    root.add_child(lip)

    var sand := MeshInstance3D.new()
    sand.name = "BunkerSand"
    var sand_mesh := CylinderMesh.new()
    sand_mesh.top_radius = 1.0
    sand_mesh.bottom_radius = 1.03
    sand_mesh.height = 0.038
    sand_mesh.radial_segments = 64
    sand.mesh = sand_mesh
    sand.position.y = -0.018
    sand.scale = Vector3(footprint.x * 0.92, 0.32, footprint.y * 0.92)
    sand.material_override = _pbr(Color("#c7b78e"), 0.98, 0.0)
    root.add_child(sand)

func _build_environment() -> void:
    # Lower ambient and a stronger oblique key produce readable contact/depth on the clubhouse,
    # flag and ball while ACES protects highlights. The compatibility CI frame and Android mobile
    # renderer use the same deterministic setup.
    var env_node := WorldEnvironment.new()
    env_node.name = "FinalBroadcastEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY

    var sky := Sky.new()
    var sky_mat := ProceduralSkyMaterial.new()
    sky_mat.sky_top_color = Color("#2d6fae")
    sky_mat.sky_horizon_color = Color("#b9d3e0")
    sky_mat.ground_bottom_color = Color("#22372c")
    sky_mat.ground_horizon_color = Color("#92a89b")
    sky_mat.sky_curve = 0.20
    sky_mat.ground_curve = 0.12
    sky_mat.sun_angle_max = 8.0
    sky_mat.sun_curve = 0.10
    sky.sky_material = sky_mat
    env.sky = sky

    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.ambient_light_energy = 0.43
    env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.fog_enabled = true
    env.fog_light_color = Color("#c7d8dd")
    env.fog_light_energy = 0.25
    env.fog_density = 0.0016
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "FinalKeySun"
    sun.light_color = Color("#fff0d2")
    sun.light_energy = 1.34
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 70.0
    sun.rotation_degrees = Vector3(-43.0, -48.0, 0.0)
    add_child(sun)

    var fill := DirectionalLight3D.new()
    fill.name = "FinalSkyFill"
    fill.light_color = Color("#a9cbe1")
    fill.light_energy = 0.10
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-24.0, 136.0, 0.0)
    add_child(fill)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 43.5
    camera.near = 0.025
    camera.far = 180.0
    add_child(camera)
    camera.position = Vector3(0.0, 0.39, 1.46)
    camera.look_at(Vector3(0.0, 0.075, -2.90), Vector3.UP)

    _build_sky_dome()

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

    vec2 sun_uv = vec2(0.70, 0.31);
    float sun_dist = distance(UV, sun_uv);
    float glow = exp(-sun_dist * 24.0) * 0.18;
    sky += vec3(1.0, 0.78, 0.48) * glow;

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