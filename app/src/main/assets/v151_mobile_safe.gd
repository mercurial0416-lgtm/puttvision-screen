extends "res://v143_tv.gd"

# V152 mobile-safe premium renderer.
# Physics/snapshots stay authoritative in Android. This layer deliberately avoids every resource
# class that caused the V143 real-device SIGSEGV: no external turf textures, panorama/HDRI,
# anisotropic samplers, Sprite3D impostors, procedural-sky material, or dynamic shadows.
# Visual depth comes from cheap procedural fragment math and ordinary meshes only.

var _leaf_a: StandardMaterial3D
var _leaf_b: StandardMaterial3D
var _bark: StandardMaterial3D
var _wood: StandardMaterial3D
var _warm_glass: StandardMaterial3D
var _trim: StandardMaterial3D
var _hill: StandardMaterial3D
var _cloud: StandardMaterial3D
var _guide: StandardMaterial3D

func _safe_grass(tint_color: Color, stripe_scale: float, micro_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded;
uniform vec3 tint : source_color = vec3(0.25, 0.47, 0.24);
uniform float stripe_scale = 10.0;
uniform float micro_strength = 0.035;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    float stripe = step(0.5, fract(UV.y * stripe_scale));
    float stripe_light = mix(0.925, 1.055, stripe);
    float broad = sin(UV.x * 20.0 + UV.y * 4.0) * 0.010;
    float micro = (hash21(floor(UV * vec2(420.0, 820.0))) - 0.5) * micro_strength;
    float edge = smoothstep(0.0, 0.18, UV.y) * smoothstep(1.0, 0.82, UV.y);
    vec3 base = tint * (stripe_light + broad + micro);
    ALBEDO = base * mix(0.98, 1.02, edge);
    ROUGHNESS = 0.94;
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
    # Bright but not neon: visible mowing bands + micro variation replace the old single-color fill.
    mat_green = _safe_grass(Color("#4f883f"), 11.0, 0.038)
    mat_fringe = _safe_grass(Color("#3f733a"), 15.0, 0.046)
    mat_rough = _safe_grass(Color("#315f35"), 19.0, 0.055)

    mat_white = _pbr(Color("#f2f1ea"), 0.70, 0.0)
    mat_dark = _pbr(Color("#11191b"), 0.92, 0.0)
    mat_red = _pbr(Color("#d12830"), 0.66, 0.0)
    mat_house = _pbr(Color("#c9c3b4"), 0.90, 0.0)
    mat_roof = _pbr(Color("#20292d"), 0.90, 0.0)
    mat_window = _pbr(Color("#203b45"), 0.35, 0.0)
    mat_stone = _pbr(Color("#817c70"), 0.96, 0.0)

    _leaf_a = _flat(Color("#315f32"))
    _leaf_b = _flat(Color("#244b2a"))
    _bark = _flat(Color("#503b2c"))
    _wood = _flat(Color("#76513a"), 0.82)
    _warm_glass = _flat(Color("#5b4932"), 0.40)
    _trim = _flat(Color("#e7e2d5"), 0.72)
    _hill = _flat(Color("#386246"), 0.96)
    _cloud = _flat(Color("#e6f0f3"), 1.0)
    _guide = _flat(Color("#b93b35"), 1.0)
    _guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V152MobilePremiumEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#4e97cb")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#e4edf0")
    env.ambient_light_energy = 0.82
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env_node.environment = env
    add_child(env_node)

    # Keep one cheap key light; shadows remain disabled on the crash-sensitive Android path.
    var sun := DirectionalLight3D.new()
    sun.name = "V152KeySun"
    sun.light_color = Color("#fff0d4")
    sun.light_energy = 1.02
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-46.0, -34.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 45.0
    camera.near = 0.020
    camera.far = 135.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    _course_plane("Rough", Vector2(46.0, 76.0), Vector3(0.0, -0.038, -33.0), mat_rough, 3, 10)
    _course_plane("Fringe", Vector2(14.6, 38.0), Vector3(0.0, -0.016, -15.8), mat_fringe, 3, 12)
    _course_plane("Green", Vector2(12.2, 36.0), Vector3(0.0, 0.0, -14.7), mat_green, 6, 24)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimGuide"
    aim_line.material_override = _guide
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _update_aim_line(distance_m: float) -> void:
    # Target image uses a restrained center guide; keep it thin enough not to read as a laser.
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.006, 0.0016, max(0.30, distance_m - 0.28))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.004, -distance_m * 0.50 + 0.05)

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "V152Ball"
    var mesh := SphereMesh.new()
    mesh.radius = BALL_RADIUS
    mesh.height = BALL_RADIUS * 2.0
    mesh.radial_segments = 36
    mesh.rings = 20
    ball.mesh = mesh

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(37.1, 91.7))) * 43758.5453); }
void fragment(){
    vec2 g = UV * vec2(30.0, 16.0);
    float dimple = smoothstep(0.24, 0.13, length(fract(g) - vec2(0.5)));
    float grain = (hash21(floor(g)) - 0.5) * 0.018;
    ALBEDO = vec3(0.955, 0.952, 0.925) * (1.0 - dimple * 0.045 + grain);
    ROUGHNESS = 0.46;
}
"""
    var ball_mat := ShaderMaterial.new()
    ball_mat.shader = shader
    ball.material_override = ball_mat
    add_child(ball)

    ball_marker = MeshInstance3D.new()
    ball_marker.name = "AlignmentDot"
    var marker_mesh := SphereMesh.new()
    marker_mesh.radius = 0.0030
    marker_mesh.height = 0.0050
    marker_mesh.radial_segments = 10
    marker_mesh.rings = 6
    ball_marker.mesh = marker_mesh
    ball_marker.material_override = mat_dark
    ball_marker.position = Vector3(0.0, BALL_RADIUS * 0.94, 0.0)
    ball.add_child(ball_marker)

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "V152CupAndPin"
    add_child(target_root)

    # Actual open cup: dark recessed cylinder + thin bright liner. The old white filled disc is gone.
    var cup := MeshInstance3D.new()
    var cup_mesh := CylinderMesh.new()
    cup_mesh.top_radius = 0.054
    cup_mesh.bottom_radius = 0.052
    cup_mesh.height = 0.080
    cup_mesh.radial_segments = 48
    cup.mesh = cup_mesh
    cup.material_override = mat_dark
    cup.position.y = -0.041
    target_root.add_child(cup)

    var liner := MeshInstance3D.new()
    var liner_mesh := TorusMesh.new()
    liner_mesh.inner_radius = 0.050
    liner_mesh.outer_radius = 0.058
    liner_mesh.rings = 10
    liner_mesh.ring_segments = 40
    liner.mesh = liner_mesh
    liner.material_override = mat_white
    liner.position.y = -0.002
    target_root.add_child(liner)

    # Target reference: white upper pin, red lower pin.
    _pin_segment(0.52, 0.52, mat_red)
    _pin_segment(1.38, 1.20, mat_white)

    var flag := MeshInstance3D.new()
    var flag_mesh := PrismMesh.new()
    flag_mesh.size = Vector3(0.36, 0.20, 0.010)
    flag.mesh = flag_mesh
    flag.material_override = mat_red
    flag.position = Vector3(0.18, 1.72, 0.0)
    flag.rotation_degrees = Vector3(0.0, 90.0, 0.0)
    target_root.add_child(flag)

func _pin_segment(center_y: float, height: float, material: Material) -> void:
    var pole := MeshInstance3D.new()
    var pole_mesh := CylinderMesh.new()
    pole_mesh.top_radius = 0.0056
    pole_mesh.bottom_radius = 0.0056
    pole_mesh.height = height
    pole_mesh.radial_segments = 16
    pole.mesh = pole_mesh
    pole.material_override = material
    pole.position.y = center_y
    target_root.add_child(pole)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "V152PremiumHorizon"
    add_child(horizon_root)

    _build_premium_clubhouse(Vector3(-4.25, 0.0, -2.55))
    _build_fence(-3.95)

    # Mesh-only rolling hills, trees and clouds. These preserve the mobile-safe path while removing
    # the empty blue-card look of V151.
    _blob(horizon_root, Vector3(-10.0, 0.05, -8.0), Vector3(9.0, 2.1, 2.8), _hill)
    _blob(horizon_root, Vector3(8.8, 0.02, -8.8), Vector3(10.5, 2.4, 3.0), _hill)

    for pos in [
        Vector3(-10.0, 0.0, -5.1), Vector3(-8.3, 0.0, -5.6), Vector3(-6.8, 0.0, -5.0),
        Vector3(5.2, 0.0, -5.5), Vector3(6.9, 0.0, -5.0), Vector3(8.6, 0.0, -5.7), Vector3(10.2, 0.0, -5.2)
    ]:
        _build_safe_tree(pos, 1.0 + abs(pos.x) * 0.018)

    _build_cloud(Vector3(-5.8, 5.2, -13.0), 1.2)
    _build_cloud(Vector3(4.8, 6.0, -15.5), 1.45)
    _build_cloud(Vector3(9.0, 4.7, -14.0), 0.95)

func _build_premium_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "PracticePuttingLab"
    house.position = local_pos
    horizon_root.add_child(house)

    _box(house, Vector3(5.4, 0.95, 1.75), Vector3(0.0, 0.56, 0.0), mat_house)
    _box(house, Vector3(5.9, 0.13, 2.10), Vector3(0.05, 1.25, 0.0), mat_roof, Vector3(0.0, 0.0, -5.0))
    _box(house, Vector3(1.55, 0.92, 1.80), Vector3(-1.92, 0.64, 0.0), mat_stone)
    _box(house, Vector3(1.15, 0.08, 0.72), Vector3(-1.92, 0.91, -0.91), mat_dark)

    for i in range(5):
        var x := -0.95 + float(i) * 0.73
        _box(house, Vector3(0.58, 0.57, 0.03), Vector3(x, 0.66, -0.89), mat_window if i < 3 else _warm_glass)
        _box(house, Vector3(0.035, 0.63, 0.045), Vector3(x + 0.32, 0.66, -0.91), _trim)

    _box(house, Vector3(4.5, 0.07, 0.78), Vector3(0.24, 0.07, -1.18), _wood)
    for x in [-0.95, 0.20, 1.35]:
        _box(house, Vector3(0.06, 0.86, 0.06), Vector3(float(x), 0.49, -1.16), _trim)

func _build_safe_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.position = pos
    horizon_root.add_child(tree)
    _box(tree, Vector3(0.16, 1.20, 0.16) * scale_value, Vector3(0.0, 0.60 * scale_value, 0.0), _bark)
    _blob(tree, Vector3(-0.18, 1.35, 0.0) * scale_value, Vector3(0.72, 0.90, 0.58) * scale_value, _leaf_a)
    _blob(tree, Vector3(0.25, 1.55, -0.03) * scale_value, Vector3(0.68, 0.82, 0.55) * scale_value, _leaf_b)
    _blob(tree, Vector3(0.02, 1.88, 0.02) * scale_value, Vector3(0.58, 0.72, 0.50) * scale_value, _leaf_a)

func _build_cloud(pos: Vector3, scale_value: float) -> void:
    var cloud_root := Node3D.new()
    cloud_root.position = pos
    horizon_root.add_child(cloud_root)
    _blob(cloud_root, Vector3(-0.52, 0.00, 0.0), Vector3(0.85, 0.32, 0.20) * scale_value, _cloud)
    _blob(cloud_root, Vector3(0.00, 0.13, 0.0), Vector3(1.00, 0.42, 0.22) * scale_value, _cloud)
    _blob(cloud_root, Vector3(0.62, 0.00, 0.0), Vector3(0.78, 0.30, 0.18) * scale_value, _cloud)

func _blob(parent: Node3D, pos: Vector3, scale_value: Vector3, material: Material) -> void:
    var node := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 1.0
    mesh.height = 2.0
    mesh.radial_segments = 16
    mesh.rings = 8
    node.mesh = mesh
    node.material_override = material
    node.position = pos
    node.scale = scale_value
    parent.add_child(node)

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

    # Top-left slope pill.
    var slope_panel := Panel.new()
    slope_panel.position = Vector2(22, 18)
    slope_panel.size = Vector2(350, 48)
    slope_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.025, 0.055, 0.070, 0.90), 12))
    root.add_child(slope_panel)
    slope_label = _label(slope_panel, Vector2(18, 0), Vector2(320, 48), 16, Color("#f1f5f4"))
    slope_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT

    # Main top-center pill, matching the approved target reference.
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
