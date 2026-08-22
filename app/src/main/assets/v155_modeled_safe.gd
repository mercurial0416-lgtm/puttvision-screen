extends "res://v151_mobile_safe.gd"

# V155 modeled premium renderer.
# This is a real 3D scene, not a photographic billboard. It keeps the proven crash-safe mobile
# path: no external HDRI/turf assets, no Sprite3D impostors, no anisotropic samplers,
# no ProceduralSkyMaterial and no dynamic shadows. Visual depth comes from actual 3D
# clubhouse / trees / fence / hills, procedural shaded turf, and cheap contact-shadow meshes.

var _v155_leaf_a: StandardMaterial3D
var _v155_leaf_b: StandardMaterial3D
var _v155_leaf_c: StandardMaterial3D
var _v155_bark: StandardMaterial3D
var _v155_wood: StandardMaterial3D
var _v155_wood_dark: StandardMaterial3D
var _v155_glass: StandardMaterial3D
var _v155_stone_light: StandardMaterial3D
var _v155_trim: StandardMaterial3D
var _v155_hill_near: StandardMaterial3D
var _v155_hill_far: StandardMaterial3D
var _v155_cloud: StandardMaterial3D
var _v155_flower_red: StandardMaterial3D
var _v155_flower_yellow: StandardMaterial3D
var _v155_guide: StandardMaterial3D
var _v155_ball_shadow: MeshInstance3D

func _v155_mat(color: Color, roughness: float = 0.88, metallic: float = 0.0) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_color = color
    m.roughness = roughness
    m.metallic = metallic
    return m

func _v155_grass(base_color: Color, stripe_strength: float, micro_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_color : source_color = vec3(0.25, 0.50, 0.24);
uniform float stripe_strength = 0.045;
uniform float micro_strength = 0.026;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
float noise21(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f*f*(3.0-2.0*f);
    float a = hash21(i);
    float b = hash21(i+vec2(1.0,0.0));
    float c = hash21(i+vec2(0.0,1.0));
    float d = hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    float band_wave = 0.5 + 0.5 * sin(UV.y * 92.0 * 6.2831853);
    float band = mix(1.0 - stripe_strength, 1.0 + stripe_strength, smoothstep(0.20, 0.80, band_wave));
    float broad = noise21(UV * vec2(34.0, 120.0)) - 0.5;
    float medium = noise21(UV * vec2(170.0, 480.0)) - 0.5;
    float micro = hash21(floor(UV * vec2(1300.0, 2800.0))) - 0.5;
    float blades = sin(UV.x * 980.0 + UV.y * 143.0) * 0.006;
    float brightness = band + broad * 0.030 + medium * 0.018 + micro * micro_strength + blades;
    vec3 col = base_color * brightness;
    col += vec3(0.010, 0.016, 0.007) * max(0.0, broad);
    ALBEDO = col;
    ROUGHNESS = clamp(0.82 + (0.5 - band_wave) * 0.045, 0.76, 0.90);
    SPECULAR = 0.18;
    vec2 n = vec2(
        sin(UV.x * 1180.0 + UV.y * 51.0),
        cos(UV.y * 1510.0 + UV.x * 43.0)
    ) * 0.040;
    NORMAL_MAP = vec3(n * 0.5 + 0.5, 1.0);
    NORMAL_MAP_DEPTH = 0.18;
}
"""
    var m := ShaderMaterial.new()
    m.shader = shader
    m.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    m.set_shader_parameter("stripe_strength", stripe_strength)
    m.set_shader_parameter("micro_strength", micro_strength)
    return m

func _build_materials() -> void:
    mat_green = _v155_grass(Color("#4b8f45"), 0.050, 0.024)
    mat_fringe = _v155_grass(Color("#3f7b3e"), 0.038, 0.028)
    mat_rough = _v155_grass(Color("#326538"), 0.024, 0.034)

    mat_white = _v155_mat(Color("#f5f3ea"), 0.54)
    mat_dark = _v155_mat(Color("#0d1415"), 0.92)
    mat_red = _v155_mat(Color("#cc202b"), 0.58)
    mat_house = _v155_mat(Color("#d2c8b5"), 0.82)
    mat_roof = _v155_mat(Color("#20282c"), 0.86)
    mat_window = _v155_mat(Color("#203d48"), 0.26, 0.02)
    mat_stone = _v155_mat(Color("#8c8576"), 0.94)

    _v155_leaf_a = _v155_mat(Color("#2f6b38"), 0.90)
    _v155_leaf_b = _v155_mat(Color("#265d31"), 0.92)
    _v155_leaf_c = _v155_mat(Color("#3e7b42"), 0.88)
    _v155_bark = _v155_mat(Color("#5b4330"), 0.95)
    _v155_wood = _v155_mat(Color("#83573a"), 0.82)
    _v155_wood_dark = _v155_mat(Color("#5f3d2a"), 0.86)
    _v155_glass = _v155_mat(Color("#173641"), 0.22, 0.05)
    _v155_stone_light = _v155_mat(Color("#b2aa97"), 0.95)
    _v155_trim = _v155_mat(Color("#eee9dc"), 0.68)
    _v155_hill_near = _v155_mat(Color("#3f7248"), 0.98)
    _v155_hill_far = _v155_mat(Color("#557f62"), 0.99)
    _v155_cloud = _v155_mat(Color("#f0f5f6"), 1.0)
    _v155_flower_red = _v155_mat(Color("#a83a35"), 0.90)
    _v155_flower_yellow = _v155_mat(Color("#d0a844"), 0.90)

    _v155_guide = _v155_mat(Color(0.78, 0.07, 0.06, 0.44), 1.0)
    _v155_guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _v155_guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V155ModeledEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#78bce2")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#dfe9e8")
    env.ambient_light_energy = 0.60
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 1.01
    env.adjustment_contrast = 1.08
    env.adjustment_saturation = 1.05
    env.fog_enabled = true
    env.fog_light_color = Color("#b9d4dc")
    env.fog_light_energy = 0.18
    env.fog_density = 0.0018
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "V155KeySun"
    sun.light_color = Color("#fff0d6")
    sun.light_energy = 1.18
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-48.0, -31.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.5
    camera.near = 0.016
    camera.far = 145.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    _course_plane("Rough", Vector2(52.0, 90.0), Vector3(0.0, -0.034, -40.0), mat_rough, 6, 16)
    _course_plane("Fringe", Vector2(17.6, 50.0), Vector3(0.0, -0.012, -21.0), mat_fringe, 8, 28)
    _course_plane("Green", Vector2(13.6, 46.0), Vector3(0.0, 0.0, -19.2), mat_green, 16, 64)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimGuide"
    aim_line.material_override = _v155_guide
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _update_aim_line(distance_m: float) -> void:
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.0030, 0.0011, max(0.30, distance_m - 0.42))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.0032, -distance_m * 0.50 + 0.10)

func _v155_box(parent: Node3D, size: Vector3, pos: Vector3, material: Material, rot: Vector3 = Vector3.ZERO) -> MeshInstance3D:
    var n := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    n.mesh = mesh
    n.material_override = material
    n.position = pos
    n.rotation_degrees = rot
    parent.add_child(n)
    return n

func _v155_cylinder(parent: Node3D, radius: float, height: float, pos: Vector3, material: Material, radial: int = 20) -> MeshInstance3D:
    var n := MeshInstance3D.new()
    var mesh := CylinderMesh.new()
    mesh.top_radius = radius
    mesh.bottom_radius = radius * 1.03
    mesh.height = height
    mesh.radial_segments = radial
    n.mesh = mesh
    n.material_override = material
    n.position = pos
    parent.add_child(n)
    return n

func _v155_blob(parent: Node3D, pos: Vector3, scale_value: Vector3, material: Material, radial: int = 24, rings_value: int = 12) -> MeshInstance3D:
    var n := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 1.0
    mesh.height = 2.0
    mesh.radial_segments = radial
    mesh.rings = rings_value
    n.mesh = mesh
    n.material_override = material
    n.position = pos
    n.scale = scale_value
    parent.add_child(n)
    return n

func _v155_shadow(parent: Node3D, pos: Vector3, size: Vector2, rotation_y: float, opacity: float = 0.18) -> void:
    var q := MeshInstance3D.new()
    var mesh := QuadMesh.new()
    mesh.size = size
    q.mesh = mesh
    q.position = pos
    q.rotation_degrees = Vector3(-90.0, rotation_y, 0.0)
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
uniform float opacity = 0.18;
void fragment(){
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float edge = max(abs(p.x), abs(p.y));
    float a = (1.0 - smoothstep(0.45, 1.0, edge)) * opacity;
    ALBEDO = vec3(0.015,0.022,0.016);
    ALPHA = a;
}
"""
    var mat := ShaderMaterial.new()
    mat.shader = shader
    mat.set_shader_parameter("opacity", opacity)
    q.material_override = mat
    parent.add_child(q)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "V155Real3DHorizon"
    add_child(horizon_root)

    _v155_build_hills()
    _v155_build_clubhouse(Vector3(-4.8, 0.0, -1.8))
    _v155_build_fence(-3.7)

    var trees := [
        [Vector3(-10.8, 0.0, -4.2), 1.40], [Vector3(-8.7, 0.0, -4.8), 1.10],
        [Vector3(-6.8, 0.0, -5.4), 0.95], [Vector3(3.8, 0.0, -4.8), 1.05],
        [Vector3(5.5, 0.0, -4.2), 1.25], [Vector3(7.4, 0.0, -5.0), 1.12],
        [Vector3(9.4, 0.0, -4.3), 1.36], [Vector3(11.3, 0.0, -5.7), 1.00],
        [Vector3(-12.5, 0.0, -8.7), 1.25], [Vector3(12.8, 0.0, -8.6), 1.32]
    ]
    for data in trees:
        _v155_build_tree(data[0], float(data[1]))

    _v155_build_cloud(Vector3(-6.5, 5.4, -15.0), 1.20)
    _v155_build_cloud(Vector3(3.9, 6.2, -17.5), 1.42)
    _v155_build_cloud(Vector3(9.2, 4.8, -15.8), 0.92)

func _move_horizon(distance_m: float) -> void:
    if horizon_root != null:
        horizon_root.position = Vector3(0.0, 0.0, -(distance_m + 5.4))

func _v155_build_hills() -> void:
    _v155_blob(horizon_root, Vector3(-11.5, -0.2, -12.5), Vector3(13.0, 3.3, 4.7), _v155_hill_far, 28, 14)
    _v155_blob(horizon_root, Vector3(11.0, -0.3, -13.0), Vector3(14.0, 3.6, 5.0), _v155_hill_far, 28, 14)
    _v155_blob(horizon_root, Vector3(-8.8, -0.1, -8.8), Vector3(9.2, 2.4, 3.4), _v155_hill_near, 28, 14)
    _v155_blob(horizon_root, Vector3(9.2, -0.1, -9.3), Vector3(10.0, 2.6, 3.6), _v155_hill_near, 28, 14)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "PuttingLabClubhouse3D"
    house.position = local_pos
    horizon_root.add_child(house)

    _v155_shadow(house, Vector3(0.25, 0.006, -0.65), Vector2(7.2, 2.5), -7.0, 0.17)
    _v155_box(house, Vector3(6.8, 0.10, 2.55), Vector3(0.2, 0.05, -0.25), _v155_stone_light)
    _v155_box(house, Vector3(2.05, 1.55, 2.10), Vector3(-2.05, 0.82, 0.0), mat_stone)
    _v155_box(house, Vector3(4.45, 1.35, 2.08), Vector3(1.10, 0.72, 0.0), _v155_wood_dark)
    _v155_box(house, Vector3(7.25, 0.16, 2.65), Vector3(0.0, 1.78, 0.0), mat_roof, Vector3(0.0, 0.0, -7.2))
    _v155_box(house, Vector3(0.14, 1.62, 2.15), Vector3(3.30, 0.85, 0.0), mat_roof)

    for i in range(9):
        var x := -0.20 + float(i) * 0.38
        _v155_box(house, Vector3(0.075, 1.32, 0.05), Vector3(x, 0.74, -1.075), _v155_wood)

    for i in range(6):
        var x := -0.25 + float(i) * 0.58
        _v155_box(house, Vector3(0.50, 0.90, 0.035), Vector3(x, 0.78, -1.08), _v155_glass)
        _v155_box(house, Vector3(0.030, 1.02, 0.05), Vector3(x + 0.28, 0.78, -1.10), _v155_trim)
    _v155_box(house, Vector3(3.65, 0.035, 0.05), Vector3(1.20, 1.27, -1.10), _v155_trim)

    _v155_box(house, Vector3(1.55, 0.78, 0.055), Vector3(-2.05, 0.93, -1.075), _v155_stone_light)
    var sign := Label3D.new()
    sign.name = "PuttingLabSign"
    sign.text = "PUTT VISION\nPUTTING LAB"
    sign.font_size = 72
    sign.pixel_size = 0.0030
    sign.modulate = Color("#293231")
    sign.outline_size = 0
    sign.position = Vector3(-2.05, 0.93, -1.115)
    sign.rotation_degrees = Vector3(0.0, 180.0, 0.0)
    sign.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sign.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    house.add_child(sign)

    _v155_box(house, Vector3(4.7, 0.07, 0.95), Vector3(0.95, 0.09, -1.48), _v155_wood)
    for x in [-0.9, 0.45, 1.80, 3.05]:
        _v155_box(house, Vector3(0.07, 1.16, 0.07), Vector3(float(x), 0.63, -1.43), _v155_trim)

    for x in [-2.9, -2.55, -1.45, -1.10, 0.0, 0.45, 1.05, 1.55, 2.10, 2.65]:
        _v155_blob(house, Vector3(float(x), 0.22, -1.58), Vector3(0.30, 0.24, 0.28), _v155_leaf_b, 18, 9)
    for x in [-2.65, -1.25, 0.25, 1.30, 2.40]:
        _v155_blob(house, Vector3(float(x), 0.17, -1.82), Vector3(0.13, 0.10, 0.13), _v155_flower_red, 14, 7)
    for x in [-2.35, -0.95, 0.70, 1.75, 2.72]:
        _v155_blob(house, Vector3(float(x), 0.16, -1.80), Vector3(0.11, 0.09, 0.11), _v155_flower_yellow, 14, 7)

func _v155_build_fence(local_z: float) -> void:
    var fence := Node3D.new()
    fence.name = "WhiteCourseFence3D"
    fence.position.z = local_z
    horizon_root.add_child(fence)
    for x in range(-12, 13, 2):
        _v155_box(fence, Vector3(0.055, 0.76, 0.055), Vector3(float(x), 0.38, 0.0), mat_white)
    for y in [0.25, 0.55]:
        _v155_box(fence, Vector3(24.0, 0.055, 0.055), Vector3(0.0, float(y), 0.0), mat_white)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.name = "MatureTree3D"
    tree.position = pos
    horizon_root.add_child(tree)

    _v155_shadow(tree, Vector3(0.45 * scale_value, 0.006, 0.42 * scale_value), Vector2(2.15, 0.72) * scale_value, -28.0, 0.13)
    _v155_cylinder(tree, 0.105 * scale_value, 1.55 * scale_value, Vector3(0.0, 0.775 * scale_value, 0.0), _v155_bark, 18)

    var canopy := [
        [Vector3(-0.38, 1.58, 0.06), Vector3(0.58, 0.68, 0.48), _v155_leaf_b],
        [Vector3(0.34, 1.62, -0.03), Vector3(0.62, 0.72, 0.50), _v155_leaf_a],
        [Vector3(-0.06, 1.95, 0.05), Vector3(0.72, 0.78, 0.55), _v155_leaf_c],
        [Vector3(0.46, 2.02, 0.04), Vector3(0.50, 0.62, 0.43), _v155_leaf_b],
        [Vector3(-0.48, 2.08, -0.05), Vector3(0.50, 0.62, 0.43), _v155_leaf_a],
        [Vector3(0.02, 2.34, 0.00), Vector3(0.56, 0.64, 0.44), _v155_leaf_c],
        [Vector3(0.12, 1.65, 0.34), Vector3(0.58, 0.62, 0.45), _v155_leaf_a],
        [Vector3(-0.24, 1.80, -0.32), Vector3(0.54, 0.62, 0.43), _v155_leaf_b]
    ]
    for item in canopy:
        _v155_blob(tree, item[0] * scale_value, item[1] * scale_value, item[2], 24, 12)

func _v155_build_cloud(pos: Vector3, scale_value: float) -> void:
    var cloud := Node3D.new()
    cloud.position = pos
    horizon_root.add_child(cloud)
    _v155_blob(cloud, Vector3(-0.66, -0.02, 0.0), Vector3(0.94, 0.27, 0.24) * scale_value, _v155_cloud, 22, 10)
    _v155_blob(cloud, Vector3(-0.12, 0.16, 0.0), Vector3(0.98, 0.39, 0.27) * scale_value, _v155_cloud, 22, 10)
    _v155_blob(cloud, Vector3(0.55, 0.03, 0.0), Vector3(0.90, 0.29, 0.23) * scale_value, _v155_cloud, 22, 10)
    _v155_blob(cloud, Vector3(0.10, -0.06, 0.0), Vector3(1.30, 0.23, 0.21) * scale_value, _v155_cloud, 22, 10)

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "V155Ball"
    var mesh := SphereMesh.new()
    mesh.radius = BALL_RADIUS
    mesh.height = BALL_RADIUS * 2.0
    mesh.radial_segments = 40
    mesh.rings = 24
    ball.mesh = mesh

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(37.1,91.7))) * 43758.5453); }
void fragment(){
    vec2 g = UV * vec2(34.0,18.0);
    vec2 cell = fract(g)-vec2(0.5);
    float r = length(cell);
    float dimple = smoothstep(0.235,0.105,r);
    float grain = (hash21(floor(g))-0.5)*0.010;
    ALBEDO = vec3(0.965,0.963,0.945)*(1.0-dimple*0.032+grain);
    ROUGHNESS = 0.38;
    SPECULAR = 0.32;
    vec2 n = normalize(cell+vec2(0.0001))*dimple*0.11;
    NORMAL_MAP = vec3(n*0.5+0.5,1.0);
    NORMAL_MAP_DEPTH = 0.23;
}
"""
    var ball_mat := ShaderMaterial.new()
    ball_mat.shader = shader
    ball.material_override = ball_mat
    add_child(ball)

    ball_marker = MeshInstance3D.new()
    ball_marker.name = "AlignmentDot"
    var marker_mesh := SphereMesh.new()
    marker_mesh.radius = 0.0026
    marker_mesh.height = 0.0046
    marker_mesh.radial_segments = 10
    marker_mesh.rings = 6
    ball_marker.mesh = marker_mesh
    ball_marker.material_override = mat_dark
    ball_marker.position = Vector3(0.0, BALL_RADIUS * 0.95, 0.0)
    ball.add_child(ball_marker)

    _v155_ball_shadow = _v155_soft_ball_shadow()
    add_child(_v155_ball_shadow)

func _v155_soft_ball_shadow() -> MeshInstance3D:
    var q := MeshInstance3D.new()
    q.name = "BallContactShadow"
    var mesh := QuadMesh.new()
    mesh.size = Vector2(0.070, 0.046)
    q.mesh = mesh
    q.rotation_degrees.x = -90.0
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
void fragment(){
    vec2 p=(UV-vec2(0.5))*vec2(1.0,1.35);
    float r=length(p)*2.0;
    float a=(1.0-smoothstep(0.10,1.0,r))*0.30;
    ALBEDO=vec3(0.015,0.020,0.016);
    ALPHA=a;
}
"""
    var mat := ShaderMaterial.new()
    mat.shader = shader
    q.material_override = mat
    return q

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "V155RegulationCup3D"
    add_child(target_root)

    var opening := MeshInstance3D.new()
    opening.name = "CupOpening"
    var om := QuadMesh.new()
    om.size = Vector2(0.110, 0.110)
    opening.mesh = om
    opening.rotation_degrees.x = -90.0
    opening.position.y = 0.0009
    var os := Shader.new()
    os.code = """
shader_type spatial;
render_mode unshaded,cull_disabled;
void fragment(){
    vec2 p=(UV-vec2(0.5))*2.0;
    float r=length(p);
    if(r>1.0){discard;}
    float edge=smoothstep(0.72,0.98,r);
    ALBEDO=mix(vec3(0.002,0.003,0.002),vec3(0.060,0.044,0.030),edge*0.70);
}
"""
    var omat := ShaderMaterial.new()
    omat.shader = os
    opening.material_override = omat
    target_root.add_child(opening)

    var earth := MeshInstance3D.new()
    var earth_mesh := TorusMesh.new()
    earth_mesh.inner_radius = 0.0496
    earth_mesh.outer_radius = 0.0552
    earth_mesh.rings = 10
    earth_mesh.ring_segments = 56
    earth.mesh = earth_mesh
    earth.material_override = _v155_mat(Color("#493426"), 0.92)
    earth.position.y = 0.00125
    target_root.add_child(earth)

    var wall := MeshInstance3D.new()
    var wall_mesh := CylinderMesh.new()
    wall_mesh.top_radius = 0.0490
    wall_mesh.bottom_radius = 0.0475
    wall_mesh.height = 0.052
    wall_mesh.radial_segments = 56
    wall_mesh.cap_top = false
    wall_mesh.cap_bottom = false
    wall.mesh = wall_mesh
    wall.material_override = mat_white
    wall.position.y = -0.0265
    target_root.add_child(wall)

    var inner := MeshInstance3D.new()
    var inner_mesh := TorusMesh.new()
    inner_mesh.inner_radius = 0.0425
    inner_mesh.outer_radius = 0.0482
    inner_mesh.rings = 8
    inner_mesh.ring_segments = 52
    inner.mesh = inner_mesh
    inner.material_override = mat_white
    inner.position.y = -0.0030
    target_root.add_child(inner)

    _pin_segment(0.34, 0.68, mat_red)
    _pin_segment(1.23, 1.10, mat_white)

    var flag := MeshInstance3D.new()
    flag.name = "Flag"
    var fm := PrismMesh.new()
    fm.size = Vector3(0.39, 0.22, 0.010)
    flag.mesh = fm
    flag.material_override = mat_red
    flag.position = Vector3(0.195, 1.80, 0.0)
    flag.rotation_degrees = Vector3(0.0, 90.0, 0.0)
    target_root.add_child(flag)

func _move_target(distance_m: float, cup_z: float) -> void:
    target_root.position = Vector3(0.0, cup_z - 0.020, -distance_m)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 42.5
    var cup_ground_y: float = last_cup_z - 0.020
    var cup_world := Vector3(0.0, cup_ground_y + 0.006, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.74

    if cup_action:
        desired_pos = cup_world + Vector3(0.06, 0.285, 0.76)
        desired_look = cup_world + Vector3(0.0, 0.018, -0.012)
        desired_fov = 38.0
    elif running:
        var forward_to_cup := cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.38 + Vector3(0.0, 0.35, 0.0)
        var lead: float = min(1.35, max(0.40, distance_to_cup * 0.36))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.030, 0.0)
        desired_fov = 40.8
    else:
        desired_pos = Vector3(0.0, 0.40, 1.54)
        var look_distance: float = min(6.1, max(2.70, target_distance * 0.60))
        desired_look = Vector3(0.0, 0.060, -look_distance)
        desired_fov = 42.5

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (7.2 if cup_action else 5.0))
        var look_alpha: float = 1.0 - exp(-delta * (8.7 if cup_action else 6.0))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 5.0))
    camera.look_at(camera_look, Vector3.UP)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if _v155_ball_shadow != null and ball != null:
        var phase: String = str(s.get("cupPhase", "NONE"))
        var holed: bool = bool(s.get("holed", false))
        _v155_ball_shadow.visible = not holed and phase != "DROP" and phase != "SETTLED"
        if _v155_ball_shadow.visible:
            var ground_y: float = ball.position.y - BALL_RADIUS + 0.0010
            _v155_ball_shadow.position = Vector3(ball.position.x + 0.006, ground_y, ball.position.z + 0.004)
