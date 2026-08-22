extends "res://v143_tv.gd"

# V153 mobile-safe premium renderer.
# Keep the proven crash-safe resource path: no external turf/HDRI, no Sprite3D impostors,
# no anisotropic textures, no ProceduralSkyMaterial, and no dynamic shadows.
# Realism comes from shaded procedural materials, fake contact shadows, better scale/camera,
# and a cup that is visually flush with the actual terrain height.

var _leaf_a: StandardMaterial3D
var _leaf_b: StandardMaterial3D
var _leaf_c: StandardMaterial3D
var _bark: StandardMaterial3D
var _wood: StandardMaterial3D
var _warm_glass: StandardMaterial3D
var _trim: StandardMaterial3D
var _hill: StandardMaterial3D
var _hill_far: StandardMaterial3D
var _cloud: StandardMaterial3D
var _guide: StandardMaterial3D
var _ball_shadow: MeshInstance3D
var _sky_backdrop: MeshInstance3D

func _safe_grass(tint_color: Color, stripe_scale: float, micro_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 tint : source_color = vec3(0.24, 0.46, 0.25);
uniform float stripe_scale = 56.0;
uniform float micro_strength = 0.026;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
float hash22(vec2 p){ return fract(sin(dot(p, vec2(269.5,183.3))) * 43758.5453); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    float mow_wave = 0.5 + 0.5 * sin(UV.y * stripe_scale * 6.2831853);
    float mow = smoothstep(0.18, 0.82, mow_wave);
    float long_variation = sin(UV.x * 19.0 + UV.y * 7.0) * 0.010;
    float micro = (hash21(floor(UV * vec2(950.0, 1500.0))) - 0.5) * micro_strength;
    float blade = sin(UV.x * 520.0 + hash22(floor(UV * 120.0)) * 5.0) * 0.006;
    float sparkle = (hash22(floor(UV * vec2(1600.0, 2200.0))) - 0.5) * 0.006;
    float mow_light = mix(0.955, 1.045, mow);
    vec3 base = tint * (mow_light + long_variation + micro + blade + sparkle);
    ALBEDO = base;
    ROUGHNESS = clamp(0.84 + (0.5 - mow) * 0.055, 0.78, 0.92);
    SPECULAR = 0.16;
    vec2 ripple = vec2(
        sin(UV.x * 720.0 + UV.y * 37.0),
        cos(UV.y * 860.0 + UV.x * 31.0)
    ) * 0.035;
    NORMAL_MAP = vec3(ripple * 0.5 + 0.5, 1.0);
    NORMAL_MAP_DEPTH = 0.18;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("tint", Vector3(tint_color.r, tint_color.g, tint_color.b))
    material.set_shader_parameter("stripe_scale", stripe_scale)
    material.set_shader_parameter("micro_strength", micro_strength)
    return material

func _flat(color: Color, roughness: float = 0.90) -> StandardMaterial3D:
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = roughness
    material.metallic = 0.0
    return material

func _build_materials() -> void:
    # Denser mowing bands + shaded PBR response. This removes the giant pale polygon bands.
    mat_green = _safe_grass(Color("#3f7f47"), 58.0, 0.024)
    mat_fringe = _safe_grass(Color("#38723f"), 66.0, 0.028)
    mat_rough = _safe_grass(Color("#305f39"), 74.0, 0.032)

    mat_white = _pbr(Color("#f3f2ea"), 0.58, 0.0)
    mat_dark = _pbr(Color("#101515"), 0.96, 0.0)
    mat_red = _pbr(Color("#c8242e"), 0.62, 0.0)
    mat_house = _pbr(Color("#d0c9b9"), 0.84, 0.0)
    mat_roof = _pbr(Color("#252b2e"), 0.88, 0.0)
    mat_window = _pbr(Color("#254451"), 0.30, 0.03)
    mat_stone = _pbr(Color("#8d8779"), 0.94, 0.0)

    _leaf_a = _flat(Color("#2f6537"), 0.90)
    _leaf_b = _flat(Color("#28582f"), 0.92)
    _leaf_c = _flat(Color("#3a7140"), 0.88)
    _bark = _flat(Color("#574231"), 0.95)
    _wood = _flat(Color("#7a563e"), 0.82)
    _warm_glass = _flat(Color("#574a3a"), 0.34)
    _trim = _flat(Color("#eee9dc"), 0.66)
    _hill = _flat(Color("#386a49"), 0.96)
    _hill_far = _flat(Color("#4b7b5b"), 0.98)
    _cloud = _flat(Color("#eef4f5"), 1.0)

    _guide = _flat(Color(0.72, 0.12, 0.11, 0.36), 1.0)
    _guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V153MobilePremiumEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#7bb9dc")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#d9e6e8")
    env.ambient_light_energy = 0.58
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 1.01
    env.adjustment_contrast = 1.06
    env.adjustment_saturation = 1.03
    env_node.environment = env
    add_child(env_node)

    _build_safe_sky_backdrop()

    var sun := DirectionalLight3D.new()
    sun.name = "V153KeySun"
    sun.light_color = Color("#fff1d7")
    sun.light_energy = 1.18
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-47.0, -33.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 43.5
    camera.near = 0.018
    camera.far = 135.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_safe_sky_backdrop() -> void:
    _sky_backdrop = MeshInstance3D.new()
    _sky_backdrop.name = "V153SkyGradientBackdrop"
    var mesh := QuadMesh.new()
    mesh.size = Vector2(120.0, 46.0)
    _sky_backdrop.mesh = mesh
    _sky_backdrop.position = Vector3(0.0, 13.0, -64.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
void fragment(){
    vec3 horizon = vec3(0.50, 0.74, 0.86);
    vec3 zenith = vec3(0.23, 0.55, 0.78);
    float t = smoothstep(0.05, 0.94, 1.0 - UV.y);
    vec3 col = mix(horizon, zenith, t);
    float glow = smoothstep(0.24, 0.0, distance(UV, vec2(0.72, 0.22)));
    col += vec3(0.12, 0.10, 0.06) * glow;
    ALBEDO = col;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    _sky_backdrop.material_override = material
    add_child(_sky_backdrop)

func _build_course() -> void:
    # Keep the whole foreground as putting green so there is no giant horizontal material seam.
    _course_plane("Rough", Vector2(48.0, 82.0), Vector3(0.0, -0.030, -36.0), mat_rough, 4, 12)
    _course_plane("Fringe", Vector2(16.4, 46.0), Vector3(0.0, -0.010, -19.0), mat_fringe, 6, 22)
    _course_plane("Green", Vector2(12.8, 42.0), Vector3(0.0, 0.0, -17.5), mat_green, 12, 48)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimGuide"
    aim_line.material_override = _guide
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _update_aim_line(distance_m: float) -> void:
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.0028, 0.0012, max(0.30, distance_m - 0.42))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.0030, -distance_m * 0.50 + 0.10)

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "V153Ball"
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
float hash21(vec2 p){ return fract(sin(dot(p, vec2(37.1, 91.7))) * 43758.5453); }
void fragment(){
    vec2 g = UV * vec2(34.0, 18.0);
    vec2 cell = fract(g) - vec2(0.5);
    float radius = length(cell);
    float dimple = smoothstep(0.235, 0.105, radius);
    float grain = (hash21(floor(g)) - 0.5) * 0.012;
    ALBEDO = vec3(0.965, 0.963, 0.942) * (1.0 - dimple * 0.032 + grain);
    ROUGHNESS = 0.39;
    SPECULAR = 0.32;
    vec2 n = normalize(cell + vec2(0.0001)) * dimple * 0.11;
    NORMAL_MAP = vec3(n * 0.5 + 0.5, 1.0);
    NORMAL_MAP_DEPTH = 0.24;
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

    _ball_shadow = _soft_shadow("BallContactShadow", Vector2(0.070, 0.047), 0.34)
    add_child(_ball_shadow)

func _soft_shadow(name_value: String, size: Vector2, opacity: float) -> MeshInstance3D:
    var node := MeshInstance3D.new()
    node.name = name_value
    var mesh := QuadMesh.new()
    mesh.size = size
    node.mesh = mesh
    node.rotation_degrees.x = -90.0

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
uniform float opacity = 0.32;
void fragment(){
    vec2 p = (UV - vec2(0.5)) * vec2(1.0, 1.35);
    float r = length(p) * 2.0;
    float a = (1.0 - smoothstep(0.12, 1.0, r)) * opacity;
    ALBEDO = vec3(0.015, 0.020, 0.016);
    ALPHA = a;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("opacity", opacity)
    node.material_override = material
    return node

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "V153FlushCupAndPin"
    add_child(target_root)

    # Flat dark opening: it visually cuts the green without raising a black cylinder above it.
    var cup_void := MeshInstance3D.new()
    cup_void.name = "CupVoid"
    var void_mesh := QuadMesh.new()
    void_mesh.size = Vector2(0.112, 0.112)
    cup_void.mesh = void_mesh
    cup_void.rotation_degrees.x = -90.0
    cup_void.position.y = 0.0012

    var void_shader := Shader.new()
    void_shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
void fragment(){
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float r = length(p);
    if (r > 1.0) { discard; }
    float center = 1.0 - smoothstep(0.0, 0.90, r);
    float edge = smoothstep(0.70, 1.0, r);
    vec3 deep = vec3(0.004, 0.006, 0.005);
    vec3 inner = vec3(0.025, 0.032, 0.027);
    ALBEDO = mix(deep, inner, center * 0.38 + edge * 0.12);
    ROUGHNESS = 1.0;
}
"""
    var void_mat := ShaderMaterial.new()
    void_mat.shader = void_shader
    cup_void.material_override = void_mat
    target_root.add_child(cup_void)

    # Thin white liner exactly at turf level; no side wall can protrude above the green.
    var liner := MeshInstance3D.new()
    liner.name = "CupLiner"
    var liner_mesh := TorusMesh.new()
    liner_mesh.inner_radius = 0.0505
    liner_mesh.outer_radius = 0.0555
    liner_mesh.rings = 8
    liner_mesh.ring_segments = 48
    liner.mesh = liner_mesh
    liner.material_override = mat_white
    liner.position.y = 0.0017
    target_root.add_child(liner)

    # A recessed liner wall helps during the close-up camera but its top never rises above turf.
    var liner_wall := MeshInstance3D.new()
    liner_wall.name = "CupInnerWall"
    var wall_mesh := CylinderMesh.new()
    wall_mesh.top_radius = 0.0520
    wall_mesh.bottom_radius = 0.0510
    wall_mesh.height = 0.050
    wall_mesh.radial_segments = 48
    wall_mesh.cap_top = false
    wall_mesh.cap_bottom = false
    liner_wall.mesh = wall_mesh
    liner_wall.material_override = mat_white
    liner_wall.position.y = -0.0255
    target_root.add_child(liner_wall)

    # Regulation-looking flagstick with no gap at the base.
    _pin_segment(0.31, 0.62, mat_white)
    _pin_segment(0.72, 0.20, mat_red)
    _pin_segment(1.31, 0.98, mat_white)

    var flag := MeshInstance3D.new()
    flag.name = "Flag"
    var flag_mesh := PrismMesh.new()
    flag_mesh.size = Vector3(0.38, 0.21, 0.010)
    flag.mesh = flag_mesh
    flag.material_override = mat_red
    flag.position = Vector3(0.19, 1.78, 0.0)
    flag.rotation_degrees = Vector3(0.0, 90.0, 0.0)
    target_root.add_child(flag)

func _pin_segment(center_y: float, height: float, material: Material) -> void:
    var pole := MeshInstance3D.new()
    var pole_mesh := CylinderMesh.new()
    pole_mesh.top_radius = 0.0051
    pole_mesh.bottom_radius = 0.0051
    pole_mesh.height = height
    pole_mesh.radial_segments = 18
    pole.mesh = pole_mesh
    pole.material_override = material
    pole.position.y = center_y
    target_root.add_child(pole)

func _move_target(distance_m: float, cup_z: float) -> void:
    # Android publishes cupZ as terrainHeight + 0.020. The old renderer placed the whole cup at
    # cupZ, which physically lifted the rim ~2 cm above the green. Subtract that bridge offset.
    target_root.position = Vector3(0.0, cup_z - 0.020, -distance_m)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "V153PremiumHorizon"
    add_child(horizon_root)

    _build_premium_clubhouse(Vector3(-4.20, 0.0, -2.65))
    _build_fence(-4.15)

    # Layered shaded hills make the horizon read as depth rather than a single green cardboard blob.
    _blob(horizon_root, Vector3(-10.5, 0.02, -10.2), Vector3(12.0, 2.6, 3.5), _hill_far, 20, 10)
    _blob(horizon_root, Vector3(10.0, 0.00, -10.8), Vector3(12.5, 2.8, 3.7), _hill_far, 20, 10)
    _blob(horizon_root, Vector3(-8.8, 0.02, -7.9), Vector3(8.8, 2.0, 2.7), _hill, 20, 10)
    _blob(horizon_root, Vector3(9.0, 0.00, -8.4), Vector3(9.8, 2.2, 2.9), _hill, 20, 10)

    for pos in [
        Vector3(-10.0, 0.0, -5.2), Vector3(-8.5, 0.0, -5.8), Vector3(-6.9, 0.0, -5.1),
        Vector3(5.0, 0.0, -5.7), Vector3(6.7, 0.0, -5.1), Vector3(8.4, 0.0, -5.8), Vector3(10.3, 0.0, -5.2)
    ]:
        _build_safe_tree(pos, 1.0 + abs(pos.x) * 0.014)

    _build_cloud(Vector3(-6.0, 5.5, -13.0), 1.15)
    _build_cloud(Vector3(4.8, 6.2, -15.5), 1.38)
    _build_cloud(Vector3(9.0, 4.9, -14.0), 0.92)

func _build_premium_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "PracticePuttingLab"
    house.position = local_pos
    horizon_root.add_child(house)

    _box(house, Vector3(5.55, 1.02, 1.80), Vector3(0.0, 0.58, 0.0), mat_house)
    _box(house, Vector3(6.05, 0.12, 2.18), Vector3(0.05, 1.28, 0.0), mat_roof, Vector3(0.0, 0.0, -4.2))
    _box(house, Vector3(1.52, 0.95, 1.84), Vector3(-1.98, 0.65, 0.0), mat_stone)
    _box(house, Vector3(1.16, 0.07, 0.72), Vector3(-1.98, 0.94, -0.92), mat_dark)

    for i in range(6):
        var x := -1.02 + float(i) * 0.68
        _box(house, Vector3(0.54, 0.58, 0.028), Vector3(x, 0.67, -0.91), mat_window if i < 4 else _warm_glass)
        _box(house, Vector3(0.030, 0.65, 0.050), Vector3(x + 0.30, 0.67, -0.94), _trim)

    _box(house, Vector3(4.65, 0.065, 0.82), Vector3(0.23, 0.065, -1.23), _wood)
    for x in [-1.02, 0.10, 1.22]:
        _box(house, Vector3(0.055, 0.88, 0.055), Vector3(float(x), 0.50, -1.20), _trim)

    # Sign block modeled as geometry instead of text/font resources.
    _box(house, Vector3(2.30, 0.24, 0.035), Vector3(0.58, 1.05, -0.94), mat_dark)
    for x in [-0.32, 0.00, 0.32, 0.64, 0.96, 1.28]:
        _box(house, Vector3(0.15, 0.035, 0.012), Vector3(float(x), 1.05, -0.965), _trim)

func _build_safe_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.position = pos
    horizon_root.add_child(tree)
    _box(tree, Vector3(0.14, 1.28, 0.14) * scale_value, Vector3(0.0, 0.64 * scale_value, 0.0), _bark)
    _blob(tree, Vector3(-0.30, 1.32, 0.06) * scale_value, Vector3(0.58, 0.72, 0.46) * scale_value, _leaf_b)
    _blob(tree, Vector3(0.28, 1.38, -0.02) * scale_value, Vector3(0.62, 0.76, 0.50) * scale_value, _leaf_a)
    _blob(tree, Vector3(-0.08, 1.72, 0.02) * scale_value, Vector3(0.70, 0.78, 0.52) * scale_value, _leaf_c)
    _blob(tree, Vector3(0.38, 1.78, 0.02) * scale_value, Vector3(0.50, 0.64, 0.42) * scale_value, _leaf_b)
    _blob(tree, Vector3(-0.36, 1.86, -0.02) * scale_value, Vector3(0.48, 0.62, 0.42) * scale_value, _leaf_a)
    _blob(tree, Vector3(0.02, 2.10, 0.00) * scale_value, Vector3(0.52, 0.62, 0.42) * scale_value, _leaf_c)

func _build_cloud(pos: Vector3, scale_value: float) -> void:
    var cloud_root := Node3D.new()
    cloud_root.position = pos
    horizon_root.add_child(cloud_root)
    _blob(cloud_root, Vector3(-0.62, -0.02, 0.0), Vector3(0.90, 0.28, 0.21) * scale_value, _cloud, 18, 8)
    _blob(cloud_root, Vector3(-0.12, 0.13, 0.0), Vector3(0.92, 0.38, 0.23) * scale_value, _cloud, 18, 8)
    _blob(cloud_root, Vector3(0.52, 0.02, 0.0), Vector3(0.86, 0.30, 0.21) * scale_value, _cloud, 18, 8)
    _blob(cloud_root, Vector3(0.12, -0.03, 0.0), Vector3(1.20, 0.24, 0.18) * scale_value, _cloud, 18, 8)

func _blob(parent: Node3D, pos: Vector3, scale_value: Vector3, material: Material, radial: int = 18, rings_value: int = 9) -> void:
    var node := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 1.0
    mesh.height = 2.0
    mesh.radial_segments = radial
    mesh.rings = rings_value
    node.mesh = mesh
    node.material_override = material
    node.position = pos
    node.scale = scale_value
    parent.add_child(node)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 43.5
    var cup_ground_y: float = last_cup_z - 0.020
    var cup_world := Vector3(0.0, cup_ground_y + 0.008, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.72

    if cup_action:
        # Lower, tighter cup camera: reads as a real hole cut into the green instead of a floating prop.
        desired_pos = cup_world + Vector3(0.72, 0.255, 0.67)
        desired_look = cup_world + Vector3(0.0, 0.012, 0.0)
        desired_fov = 37.5
    elif running:
        var forward_to_cup := cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.42 + Vector3(0.0, 0.38, 0.0)
        var lead: float = min(1.35, max(0.38, distance_to_cup * 0.36))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.034, 0.0)
        desired_fov = 41.5
    else:
        desired_pos = Vector3(0.0, 0.48, 1.58)
        var look_distance: float = min(6.1, max(2.55, target_distance * 0.60))
        desired_look = Vector3(0.0, 0.065, -look_distance)
        desired_fov = 43.5

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (7.0 if cup_action else 4.9))
        var look_alpha: float = 1.0 - exp(-delta * (8.4 if cup_action else 5.8))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 4.8))
    camera.look_at(camera_look, Vector3.UP)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if _ball_shadow != null and ball != null:
        var phase: String = str(s.get("cupPhase", "NONE"))
        var holed: bool = bool(s.get("holed", false))
        _ball_shadow.visible = not holed and phase != "DROP" and phase != "SETTLED"
        if _ball_shadow.visible:
            var ground_y: float = ball.position.y - BALL_RADIUS + 0.0010
            _ball_shadow.position = Vector3(ball.position.x + 0.006, ground_y, ball.position.z + 0.004)

func _panel_style(color: Color, radius: int, border_color: Color = Color.TRANSPARENT, border_width: int = 0) -> StyleBoxFlat:
    var style := StyleBoxFlat.new()
    style.bg_color = color
    style.corner_radius_top_left = radius
    style.corner_radius_top_right = radius
    style.corner_radius_bottom_left = radius
    style.corner_radius_bottom_right = radius
    if border_width > 0:
        style.border_width_left = border_width
        style.border_width_top = border_width
        style.border_width_right = border_width
        style.border_width_bottom = border_width
        style.border_color = border_color
    return style

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 20
    add_child(layer)
    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    var slope_panel := Panel.new()
    slope_panel.position = Vector2(22, 18)
    slope_panel.size = Vector2(350, 48)
    slope_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.025, 0.055, 0.070, 0.90), 12))
    root.add_child(slope_panel)
    slope_label = _label(slope_panel, Vector2(18, 0), Vector2(320, 48), 16, Color("#f1f5f4"))
    slope_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT

    var top_panel := Panel.new()
    top_panel.set_anchors_preset(Control.PRESET_CENTER_TOP)
    top_panel.position = Vector2(-300, 16)
    top_panel.size = Vector2(600, 58)
    top_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.025, 0.050, 0.065, 0.92), 13))
    root.add_child(top_panel)
    distance_label = _label(top_panel, Vector2(0, 0), Vector2(220, 58), 20, Color("#f4d564"))
    stimp_label = _label(top_panel, Vector2(220, 0), Vector2(170, 58), 18, Color("#72d799"))
    speed_label = _label(top_panel, Vector2(390, 0), Vector2(210, 58), 18, Color("#f3f5f4"))

    var divider_a := ColorRect.new()
    divider_a.color = Color(1, 1, 1, 0.20)
    divider_a.position = Vector2(220, 15)
    divider_a.size = Vector2(1, 28)
    top_panel.add_child(divider_a)
    var divider_b := ColorRect.new()
    divider_b.color = Color(1, 1, 1, 0.20)
    divider_b.position = Vector2(390, 15)
    divider_b.size = Vector2(1, 28)
    top_panel.add_child(divider_b)

    var wait_bg := Panel.new()
    wait_bg.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
    wait_bg.position = Vector2(-50, -58)
    wait_bg.size = Vector2(100, 34)
    wait_bg.add_theme_stylebox_override("panel", _panel_style(Color(0.03, 0.05, 0.055, 0.72), 8))
    root.add_child(wait_bg)
    wait_label = _label(wait_bg, Vector2.ZERO, Vector2(100, 34), 14, Color("#f0f2ef"))
    wait_label.text = "WAIT"

    result_label = _label(root, Vector2(0, 90), Vector2(560, 54), 28, Color("#78e1a4"))
    result_label.set_anchors_preset(Control.PRESET_CENTER_TOP)
    result_label.position.x = -280
    result_label.visible = false
