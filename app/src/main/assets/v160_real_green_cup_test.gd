extends "res://v159_real_green_roll.gd"

# V160: real-green presentation pass for the proven mobile-safe renderer.
# No full-scene photo card, HDRI, dynamic shadow map, Sprite3D impostor or anisotropic sampler.
# The course remains live 3D; the turf uses only high-frequency CC0 fibre detail plus procedural
# mowing direction / blade response, while the V158 alpha-card trees are replaced with real meshes.

func _v160_turf_material(
        base_color: Color,
        lane_strength: float,
        texture_scale: float,
        normal_depth: float,
        roughness_base: float,
        micro_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D turf_albedo : source_color, repeat_enable, filter_linear_mipmap;
uniform sampler2D turf_normal : hint_normal, repeat_enable, filter_linear_mipmap;
uniform sampler2D turf_roughness : repeat_enable, filter_linear_mipmap;
uniform vec3 base_color : source_color = vec3(0.21, 0.40, 0.18);
uniform float lane_strength = 0.055;
uniform float texture_scale = 92.0;
uniform float normal_depth = 0.050;
uniform float roughness_base = 0.82;
uniform float micro_strength = 0.030;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}
float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
void vertex() {
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment() {
    // The source lawn texture is deliberately sampled very densely: it contributes fibre only,
    // never the broad leaves that made V158/V159 look like a pale floor.
    vec2 tex_uv = UV * vec2(texture_scale * 0.72, texture_scale * 1.85);
    vec3 src = texture(turf_albedo, tex_uv).rgb;
    float src_luma = dot(src, vec3(0.2126, 0.7152, 0.0722));

    // Tight 65-75 cm mowing passes. Opposite blade direction changes both luminance and specular,
    // which is what makes a real putting green read as striped instead of painted polygons.
    float lane_wave = sin(UV.y * 62.0 * 6.2831853);
    float lane = smoothstep(-0.26, 0.26, lane_wave);
    float lane_luma = mix(1.0 - lane_strength, 1.0 + lane_strength, lane);

    float broad = (noise21(UV * vec2(31.0, 97.0)) - 0.5) * 0.020;
    float medium = (noise21(UV * vec2(173.0, 541.0)) - 0.5) * 0.022;
    float fine = (noise21(UV * vec2(690.0, 1720.0)) - 0.5) * micro_strength;
    float grain = (hash21(floor(UV * vec2(2500.0, 5900.0))) - 0.5) * 0.018;
    float blade_lines = sin(UV.x * 2350.0 + UV.y * 311.0) * 0.0042;
    float fibre = (src_luma - 0.50) * 0.042;

    float brightness = lane_luma + broad + medium + fine + grain + blade_lines + fibre;
    vec3 col = base_color * brightness;
    // tiny warm/cool blade-direction shift avoids the synthetic single-green look
    col += mix(vec3(-0.007, 0.004, -0.004), vec3(0.008, 0.009, -0.003), lane);
    ALBEDO = max(col, vec3(0.0));

    float rough_tex = texture(turf_roughness, tex_uv).r;
    ROUGHNESS = clamp(
        roughness_base + (rough_tex - 0.5) * 0.045 + mix(0.035, -0.030, lane),
        0.72, 0.93
    );
    SPECULAR = mix(0.13, 0.24, lane);

    vec3 ntex = texture(turf_normal, tex_uv).rgb;
    NORMAL_MAP = ntex;
    NORMAL_MAP_DEPTH = normal_depth;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    var a_path := "res://v143_assets/turf/albedo.png"
    var n_path := "res://v143_assets/turf/normal.png"
    var r_path := "res://v143_assets/turf/roughness.png"
    if ResourceLoader.exists(a_path) and ResourceLoader.exists(n_path) and ResourceLoader.exists(r_path):
        material.set_shader_parameter("turf_albedo", load(a_path))
        material.set_shader_parameter("turf_normal", load(n_path))
        material.set_shader_parameter("turf_roughness", load(r_path))
    else:
        return _v155_grass(base_color, lane_strength, micro_strength)
    material.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    material.set_shader_parameter("lane_strength", lane_strength)
    material.set_shader_parameter("texture_scale", texture_scale)
    material.set_shader_parameter("normal_depth", normal_depth)
    material.set_shader_parameter("roughness_base", roughness_base)
    material.set_shader_parameter("micro_strength", micro_strength)
    return material

func _build_materials() -> void:
    super._build_materials()
    # Bentgrass-like palette: substantially deeper than V159 so the green survives Android/TV
    # exposure without becoming the pale mint floor seen on the real device screenshot.
    mat_green = _v160_turf_material(Color("#396a32"), 0.060, 96.0, 0.048, 0.805, 0.031)
    mat_fringe = _v160_turf_material(Color("#315c2e"), 0.037, 58.0, 0.075, 0.850, 0.036)
    mat_rough = _v160_turf_material(Color("#274c29"), 0.020, 29.0, 0.115, 0.895, 0.042)

    # Background materials are also kept below clipping white; this prevents distant geometry from
    # reading as blank white blocks on bright Android displays.
    mat_white = _v155_mat(Color("#dddcd1"), 0.69)
    mat_stone = _v155_mat(Color("#9a927f"), 0.94)
    _v155_stone_light = _v155_mat(Color("#bdb5a3"), 0.94)
    _v155_trim = _v155_mat(Color("#d9d7cb"), 0.72)
    _v155_leaf_a = _v155_mat(Color("#2d6032"), 0.94)
    _v155_leaf_b = _v155_mat(Color("#244f2b"), 0.95)
    _v155_leaf_c = _v155_mat(Color("#3a6d38"), 0.92)

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V160NaturalEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#67aeda")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#cfded7")
    env.ambient_light_energy = 0.39
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 0.88
    env.adjustment_contrast = 1.17
    env.adjustment_saturation = 1.10
    env.fog_enabled = false
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "V160KeySun"
    sun.light_color = Color("#fff0d2")
    sun.light_energy = 1.08
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-43.0, -33.0, 0.0)
    add_child(sun)

    # Cheap fill light, shadowless and therefore mobile-safe, adds shape to the clubhouse and trees.
    var fill := DirectionalLight3D.new()
    fill.name = "V160SkyFill"
    fill.light_color = Color("#b8d5df")
    fill.light_energy = 0.22
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-58.0, 137.0, 0.0)
    add_child(fill)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.0
    camera.near = 0.014
    camera.far = 145.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    # V158 used alpha-card tree PNGs. On affected Android GPUs the cards can resolve as large white
    # rectangles, exactly like the user's V159 screenshot. V160 uses actual mesh canopy volumes only.
    var tree := Node3D.new()
    tree.name = "V160MatureTree3D"
    tree.position = pos
    horizon_root.add_child(tree)

    _v155_shadow(tree, Vector3(0.42 * scale_value, 0.006, 0.34 * scale_value), Vector2(2.35, 0.78) * scale_value, -27.0, 0.15)
    _v155_cylinder(tree, 0.100 * scale_value, 1.64 * scale_value, Vector3(0.0, 0.82 * scale_value, 0.0), _v155_bark, 22)

    var branch_a := _v155_cylinder(tree, 0.042 * scale_value, 0.82 * scale_value, Vector3(-0.18, 1.31, 0.0) * scale_value, _v155_bark, 16)
    branch_a.rotation_degrees.z = -31.0
    var branch_b := _v155_cylinder(tree, 0.040 * scale_value, 0.74 * scale_value, Vector3(0.20, 1.40, 0.03) * scale_value, _v155_bark, 16)
    branch_b.rotation_degrees.z = 35.0

    var canopy := [
        [Vector3(-0.55,1.64,0.02), Vector3(0.64,0.58,0.54), _v155_leaf_b],
        [Vector3( 0.48,1.67,0.02), Vector3(0.67,0.62,0.56), _v155_leaf_a],
        [Vector3(-0.12,1.92,0.12), Vector3(0.78,0.67,0.62), _v155_leaf_c],
        [Vector3( 0.20,2.22,0.03), Vector3(0.66,0.59,0.55), _v155_leaf_b],
        [Vector3(-0.48,2.18,-0.05),Vector3(0.58,0.54,0.50), _v155_leaf_a],
        [Vector3( 0.55,2.12,-0.08),Vector3(0.57,0.53,0.49), _v155_leaf_c],
        [Vector3(-0.58,1.92,0.30), Vector3(0.54,0.48,0.46), _v155_leaf_c],
        [Vector3( 0.52,1.88,0.31), Vector3(0.55,0.49,0.47), _v155_leaf_b],
        [Vector3(-0.05,1.72,-0.38),Vector3(0.66,0.56,0.50), _v155_leaf_a],
        [Vector3( 0.03,2.42,-0.04),Vector3(0.48,0.43,0.42), _v155_leaf_c]
    ]
    for item in canopy:
        _v155_blob(tree, item[0] * scale_value, item[1] * scale_value, item[2], 28, 14)

func _v155_build_hills() -> void:
    # Lower, layered horizon leaves sky and trees readable instead of forming flat green walls.
    _v155_blob(horizon_root, Vector3(-12.0,-1.15,-13.1), Vector3(14.2,2.35,5.5), _v155_hill_far, 36, 18)
    _v155_blob(horizon_root, Vector3(12.1,-1.18,-13.6), Vector3(14.7,2.45,5.7), _v155_hill_far, 36, 18)
    _v155_blob(horizon_root, Vector3(-8.7,-0.98,-9.5), Vector3(9.4,1.75,4.0), _v155_hill_near, 36, 18)
    _v155_blob(horizon_root, Vector3(9.4,-1.02,-9.8), Vector3(10.1,1.82,4.1), _v155_hill_near, 36, 18)

func _build_course() -> void:
    super._build_course()
    # Subtle darker collar under the playable green gives a readable green/fringe boundary while
    # staying below the physics surface. It is geometry, not a screenshot/decal trick.
    var collar := MeshInstance3D.new()
    collar.name = "V160GreenCollarDepth"
    var mesh := BoxMesh.new()
    mesh.size = Vector3(14.05, 0.010, 46.45)
    collar.mesh = mesh
    collar.position = Vector3(0.0, -0.010, -19.20)
    collar.material_override = _v155_mat(Color("#2b542b"), 0.91)
    add_child(collar)

func _build_target() -> void:
    super._build_target()
    # A soft, flush soil/occlusion ring makes the 108 mm opening visually cut into the turf.
    var ao := MeshInstance3D.new()
    ao.name = "V160CupGroundAO"
    var qm := QuadMesh.new()
    qm.size = Vector2(0.145, 0.145)
    ao.mesh = qm
    ao.rotation_degrees.x = -90.0
    ao.position.y = 0.00105
    var sh := Shader.new()
    sh.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
void fragment(){
    vec2 p=(UV-vec2(0.5))*2.0;
    float r=length(p);
    if(r>1.0 || r<0.67){discard;}
    float a=(1.0-smoothstep(0.67,1.0,r))*0.23;
    ALBEDO=vec3(0.025,0.035,0.018);
    ALPHA=a;
}
"""
    var m := ShaderMaterial.new()
    m.shader = sh
    ao.material_override = m
    target_root.add_child(ao)
