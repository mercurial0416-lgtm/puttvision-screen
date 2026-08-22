extends "res://v155_modeled_safe.gd"

# V156: photoreal direction without a photographic scene card.
# Small material textures are mapped onto ACTUAL 3D green/stone/wood meshes.  The sky is a
# procedural gradient quad and all clubhouse/trees/fence/hills remain real geometry, so camera
# motion and cup/ball physics retain proper perspective/parallax.

var _v156_grass_tex: Texture2D
var _v156_stone_tex: Texture2D
var _v156_wood_tex: Texture2D
var _v156_sky: MeshInstance3D

func _v156_tex_material(texture: Texture2D, tint: Color, tiling: Vector2, roughness: float, specular_value: float = 0.12) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D tex : source_color, repeat_enable, filter_linear_mipmap;
uniform vec3 tint : source_color = vec3(1.0);
uniform vec2 tiling = vec2(1.0);
uniform float roughness_value = 0.85;
uniform float specular_value = 0.12;
void fragment(){
    vec3 c = texture(tex, UV * tiling).rgb;
    ALBEDO = c * tint;
    ROUGHNESS = roughness_value;
    SPECULAR = specular_value;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("tex", texture)
    material.set_shader_parameter("tint", Vector3(tint.r, tint.g, tint.b))
    material.set_shader_parameter("tiling", tiling)
    material.set_shader_parameter("roughness_value", roughness)
    material.set_shader_parameter("specular_value", specular_value)
    return material

func _v156_grass_material(tint: Color, tiling: Vector2, stripe_strength: float, detail_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D grass_tex : source_color, repeat_enable, filter_linear_mipmap;
uniform vec3 tint : source_color = vec3(0.82, 0.90, 0.78);
uniform vec2 tiling = vec2(2.2, 12.0);
uniform float stripe_strength = 0.040;
uniform float detail_strength = 0.018;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    vec2 uv = UV * tiling;
    vec3 photo = texture(grass_tex, uv).rgb;

    // Narrow alternating mowing direction; no giant flat stripes.
    float mow = 0.5 + 0.5 * sin(UV.y * 64.0 * 6.2831853);
    float band = mix(1.0 - stripe_strength, 1.0 + stripe_strength, smoothstep(0.22, 0.78, mow));

    float micro = (hash21(floor(UV * vec2(1500.0, 2900.0))) - 0.5) * detail_strength;
    float blades = sin(UV.x * 1040.0 + UV.y * 119.0) * 0.0045;
    vec3 c = photo * tint * (band + micro + blades);
    ALBEDO = c;
    ROUGHNESS = clamp(0.80 + (0.5 - mow) * 0.045, 0.75, 0.89);
    SPECULAR = 0.18;
    vec2 n = vec2(
        sin(UV.x * 1250.0 + UV.y * 49.0),
        cos(UV.y * 1510.0 + UV.x * 41.0)
    ) * 0.032;
    NORMAL_MAP = vec3(n * 0.5 + 0.5, 1.0);
    NORMAL_MAP_DEPTH = 0.15;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("grass_tex", _v156_grass_tex)
    material.set_shader_parameter("tint", Vector3(tint.r, tint.g, tint.b))
    material.set_shader_parameter("tiling", tiling)
    material.set_shader_parameter("stripe_strength", stripe_strength)
    material.set_shader_parameter("detail_strength", detail_strength)
    return material

func _build_materials() -> void:
    _v156_grass_tex = load("res://v155_assets/grass_albedo.jpg")
    _v156_stone_tex = load("res://v155_assets/stone_albedo.jpg")
    _v156_wood_tex = load("res://v155_assets/wood_albedo.jpg")

    # Material-map texture on live 3D green. Darker/less neon than V155.
    mat_green = _v156_grass_material(Color("#b6caa5"), Vector2(1.7, 8.0), 0.035, 0.016)
    mat_fringe = _v156_grass_material(Color("#93ad8a"), Vector2(2.0, 8.8), 0.026, 0.019)
    mat_rough = _v156_grass_material(Color("#708f72"), Vector2(2.5, 9.5), 0.016, 0.023)

    mat_white = _v155_mat(Color("#f4f2e9"), 0.52)
    mat_dark = _v155_mat(Color("#0c1314"), 0.92)
    mat_red = _v155_mat(Color("#cb202b"), 0.58)
    mat_house = _v156_tex_material(_v156_stone_tex, Color("#d7d0be"), Vector2(2.5, 2.0), 0.90, 0.07)
    mat_stone = _v156_tex_material(_v156_stone_tex, Color("#b9b09d"), Vector2(2.4, 2.2), 0.94, 0.06)
    mat_roof = _v155_mat(Color("#1e272b"), 0.86)
    mat_window = _v155_mat(Color("#173642"), 0.23, 0.04)

    _v155_stone_light = _v156_tex_material(_v156_stone_tex, Color("#dad2bf"), Vector2(2.8, 2.2), 0.92, 0.06)
    _v155_wood = _v156_tex_material(_v156_wood_tex, Color("#b78869"), Vector2(2.0, 1.0), 0.78, 0.10)
    _v155_wood_dark = _v156_tex_material(_v156_wood_tex, Color("#875f49"), Vector2(2.0, 1.0), 0.82, 0.08)
    _v155_glass = _v155_mat(Color("#16333d"), 0.18, 0.07)
    _v155_trim = _v155_mat(Color("#eee9db"), 0.66)
    _v155_bark = _v156_tex_material(_v156_wood_tex, Color("#846852"), Vector2(1.2, 2.7), 0.94, 0.04)

    # More natural foliage palette; many smaller clusters below create the leaf silhouette.
    _v155_leaf_a = _v155_mat(Color("#2b6034"), 0.92)
    _v155_leaf_b = _v155_mat(Color("#234f2c"), 0.94)
    _v155_leaf_c = _v155_mat(Color("#3a713d"), 0.90)
    _v155_hill_near = _v155_mat(Color("#3d6947"), 0.98)
    _v155_hill_far = _v155_mat(Color("#567760"), 0.99)
    _v155_cloud = _v155_mat(Color("#f0f5f5"), 1.0)
    _v155_flower_red = _v155_mat(Color("#9c4038"), 0.90)
    _v155_flower_yellow = _v155_mat(Color("#c9a44a"), 0.90)

    _v155_guide = _v155_mat(Color(0.76, 0.06, 0.055, 0.38), 1.0)
    _v155_guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _v155_guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V156Premium3DEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#3d91c8")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#d8e4e5")
    env.ambient_light_energy = 0.53
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 0.96
    env.adjustment_contrast = 1.12
    env.adjustment_saturation = 0.98
    env.fog_enabled = false
    env_node.environment = env
    add_child(env_node)

    _v156_build_sky()

    var sun := DirectionalLight3D.new()
    sun.name = "V156KeySun"
    sun.light_color = Color("#fff0d5")
    sun.light_energy = 0.94
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-48.0, -31.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.5
    camera.near = 0.016
    camera.far = 150.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _v156_build_sky() -> void:
    _v156_sky = MeshInstance3D.new()
    _v156_sky.name = "ProceduralBlueSkyBackdrop"
    var mesh := QuadMesh.new()
    mesh.size = Vector2(150.0, 68.0)
    _v156_sky.mesh = mesh
    _v156_sky.position = Vector3(0.0, 17.0, -72.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
void fragment(){
    vec3 zenith = vec3(0.085, 0.40, 0.69);
    vec3 horizon = vec3(0.46, 0.72, 0.84);
    float h = smoothstep(0.02, 0.93, UV.y);
    vec3 col = mix(zenith, horizon, h);
    float sun_glow = 1.0 - smoothstep(0.0, 0.30, distance(UV, vec2(0.72, 0.20)));
    col += vec3(0.14, 0.12, 0.075) * sun_glow;
    ALBEDO = col;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    _v156_sky.material_override = material
    add_child(_v156_sky)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "PuttingLabClubhouse3D"
    house.position = local_pos
    house.rotation_degrees.y = -4.0
    horizon_root.add_child(house)

    _v155_shadow(house, Vector3(0.10, 0.006, 0.45), Vector2(7.5, 2.8), 6.0, 0.18)
    _v155_box(house, Vector3(7.15, 0.09, 2.65), Vector3(0.10, 0.045, 0.0), _v155_stone_light)

    # Actual shell.
    _v155_box(house, Vector3(2.15, 1.58, 2.15), Vector3(-2.05, 0.84, 0.0), mat_stone)
    _v155_box(house, Vector3(4.55, 1.38, 2.13), Vector3(1.12, 0.74, 0.0), _v155_wood_dark)
    _v155_box(house, Vector3(7.40, 0.15, 2.82), Vector3(0.05, 1.82, 0.0), mat_roof, Vector3(0.0, 0.0, -7.0))
    _v155_box(house, Vector3(0.14, 1.66, 2.16), Vector3(3.37, 0.88, 0.0), mat_roof)

    # CAMERA IS ON +Z. V155 accidentally built this facade on -Z, so the preview showed the blank rear.
    # Put all reference-facing glass/stone/sign details on +Z.
    var front_z := 1.086
    for i in range(6):
        var x := -0.35 + float(i) * 0.61
        _v155_box(house, Vector3(0.53, 0.91, 0.030), Vector3(x, 0.78, front_z), _v155_glass)
        _v155_box(house, Vector3(0.034, 1.03, 0.045), Vector3(x + 0.30, 0.78, front_z + 0.020), _v155_trim)
    _v155_box(house, Vector3(3.75, 0.035, 0.045), Vector3(1.18, 1.28, front_z + 0.020), _v155_trim)

    # Warm vertical cedar fins on right pavilion.
    for i in range(8):
        var x := 0.35 + float(i) * 0.38
        _v155_box(house, Vector3(0.075, 1.31, 0.055), Vector3(x, 0.74, front_z + 0.050), _v155_wood)

    # Stone sign panel on left wing.
    _v155_box(house, Vector3(1.60, 0.80, 0.055), Vector3(-2.05, 0.94, front_z + 0.045), _v155_stone_light)
    var sign := Label3D.new()
    sign.name = "PuttingLabSign"
    sign.text = "PUTT VISION\nPUTTING LAB"
    sign.font_size = 72
    sign.pixel_size = 0.0030
    sign.modulate = Color("#293231")
    sign.outline_size = 0
    sign.position = Vector3(-2.05, 0.94, front_z + 0.082)
    sign.rotation_degrees = Vector3.ZERO
    sign.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sign.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    house.add_child(sign)

    # Deck/posts in front, with actual depth and parallax.
    _v155_box(house, Vector3(4.95, 0.07, 0.96), Vector3(0.90, 0.09, 1.48), _v155_wood)
    for x in [-1.05, 0.25, 1.55, 2.85]:
        _v155_box(house, Vector3(0.065, 1.14, 0.065), Vector3(float(x), 0.62, 1.43), _v155_trim)

    # Landscaping/flower bed near facade.
    for x in [-2.95, -2.58, -1.50, -1.08, -0.20, 0.35, 0.92, 1.48, 2.05, 2.63]:
        _v155_blob(house, Vector3(float(x), 0.21, 1.60), Vector3(0.30, 0.22, 0.28), _v155_leaf_b, 20, 10)
    for x in [-2.68, -1.32, 0.18, 1.22, 2.36]:
        _v155_blob(house, Vector3(float(x), 0.16, 1.81), Vector3(0.12, 0.09, 0.12), _v155_flower_red, 16, 8)
    for x in [-2.38, -0.93, 0.63, 1.69, 2.72]:
        _v155_blob(house, Vector3(float(x), 0.15, 1.80), Vector3(0.10, 0.08, 0.10), _v155_flower_yellow, 16, 8)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.name = "MatureTree3D"
    tree.position = pos
    horizon_root.add_child(tree)

    _v155_shadow(tree, Vector3(0.42 * scale_value, 0.006, 0.35 * scale_value), Vector2(2.0, 0.68) * scale_value, -28.0, 0.12)
    _v155_cylinder(tree, 0.088 * scale_value, 1.45 * scale_value, Vector3(0.0, 0.725 * scale_value, 0.0), _v155_bark, 20)

    # Branch hints stop the crown from reading as a green lollipop.
    for branch in [
        [Vector3(-0.18, 1.12, 0.00), -25.0],
        [Vector3(0.17, 1.24, 0.02), 24.0],
        [Vector3(0.00, 1.35, -0.12), 6.0]
    ]:
        var b := _v155_cylinder(tree, 0.035 * scale_value, 0.62 * scale_value, branch[0] * scale_value, _v155_bark, 14)
        b.rotation_degrees.z = float(branch[1])

    # Many smaller overlapping crowns = irregular silhouette and richer light break-up.
    var canopy := [
        [Vector3(-0.50,1.52,0.08), Vector3(0.38,0.48,0.34), _v155_leaf_b],
        [Vector3(-0.18,1.58,0.26), Vector3(0.42,0.50,0.36), _v155_leaf_a],
        [Vector3(0.22,1.55,0.22), Vector3(0.44,0.52,0.37), _v155_leaf_c],
        [Vector3(0.52,1.62,0.02), Vector3(0.37,0.46,0.33), _v155_leaf_b],
        [Vector3(-0.58,1.85,-0.06), Vector3(0.38,0.46,0.34), _v155_leaf_a],
        [Vector3(-0.26,1.93,-0.18), Vector3(0.44,0.53,0.37), _v155_leaf_b],
        [Vector3(0.12,1.92,0.04), Vector3(0.48,0.56,0.39), _v155_leaf_c],
        [Vector3(0.48,1.92,-0.12), Vector3(0.40,0.49,0.35), _v155_leaf_a],
        [Vector3(-0.35,2.20,0.05), Vector3(0.38,0.45,0.33), _v155_leaf_c],
        [Vector3(0.02,2.28,-0.04), Vector3(0.43,0.49,0.34), _v155_leaf_a],
        [Vector3(0.36,2.18,0.06), Vector3(0.36,0.43,0.31), _v155_leaf_b],
        [Vector3(-0.08,2.48,0.02), Vector3(0.34,0.39,0.29), _v155_leaf_c]
    ]
    for item in canopy:
        _v155_blob(tree, item[0] * scale_value, item[1] * scale_value, item[2], 28, 14)
