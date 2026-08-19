extends Node3D

# V143 is a clean-room Godot presentation layer. Android measurement + V135-V137 physics stay authoritative.
const BALL_RADIUS := 0.021335
const DEFAULT_DISTANCE := 5.0

var bridge
var ball: MeshInstance3D
var ball_marker: MeshInstance3D
var target_root: Node3D
var horizon_root: Node3D
var aim_line: MeshInstance3D
var camera: Camera3D
var camera_pos := Vector3(0.0, 0.48, 1.55)
var camera_look := Vector3(0.0, 0.10, -2.5)
var target_distance := DEFAULT_DISTANCE
var last_distance := -1.0
var last_cup_z := 0.02
var last_running := false
var last_phase := "NONE"
var distance_label: Label
var speed_label: Label
var stimp_label: Label
var wait_label: Label
var slope_label: Label
var result_label: Label

var mat_green: ShaderMaterial
var mat_fringe: ShaderMaterial
var mat_rough: ShaderMaterial
var mat_trunk: StandardMaterial3D
var mat_leaf_a: StandardMaterial3D
var mat_leaf_b: StandardMaterial3D
var mat_leaf_c: StandardMaterial3D
var mat_white: StandardMaterial3D
var mat_dark: StandardMaterial3D
var mat_red: StandardMaterial3D
var mat_house: StandardMaterial3D
var mat_roof: StandardMaterial3D
var mat_window: StandardMaterial3D

func _ready() -> void:
    bridge = Engine.get_singleton("PuttVisionBridge") if Engine.has_singleton("PuttVisionBridge") else null
    _build_materials()
    _build_environment()
    _build_course()
    _build_ball()
    _build_target()
    _build_horizon()
    _build_hud()
    _apply_snapshot(_snapshot(), true, 0.016)

func _process(delta: float) -> void:
    _apply_snapshot(_snapshot(), false, delta)

func _snapshot() -> Dictionary:
    if bridge != null:
        var parsed = JSON.parse_string(bridge.snapshotJson())
        if parsed is Dictionary:
            return parsed
    return {
        "holeDistance": DEFAULT_DISTANCE,
        "stimp": 2.8,
        "sideSlope": 0.0,
        "longSlope": 0.0,
        "ballX": 0.0,
        "ballY": 0.0,
        "ballZ": BALL_RADIUS,
        "speed": 0.0,
        "running": false,
        "holed": false,
        "lipOut": false,
        "cupPhase": "NONE",
        "cupZ": 0.02,
        "qw": 1.0,
        "qx": 0.0,
        "qy": 0.0,
        "qz": 0.0,
        "distanceToCup": DEFAULT_DISTANCE
    }

func _build_materials() -> void:
    mat_green = _grass_material(Color("#3d8d45"), 0.040, 0.022, 1.0)
    mat_fringe = _grass_material(Color("#2f7138"), 0.055, 0.030, 0.78)
    mat_rough = _grass_material(Color("#285d32"), 0.075, 0.045, 0.58)
    mat_trunk = _pbr(Color("#6c4b31"), 0.94, 0.0)
    mat_leaf_a = _pbr(Color("#2e6d34"), 0.93, 0.0)
    mat_leaf_b = _pbr(Color("#397c3d"), 0.92, 0.0)
    mat_leaf_c = _pbr(Color("#446f2f"), 0.94, 0.0)
    mat_white = _pbr(Color("#e7e8df"), 0.55, 0.0)
    mat_dark = _pbr(Color("#101515"), 0.76, 0.0)
    mat_red = _pbr(Color("#d7282f"), 0.45, 0.0)
    mat_house = _pbr(Color("#c9c3ad"), 0.82, 0.0)
    mat_roof = _pbr(Color("#4c4b49"), 0.88, 0.0)
    mat_window = _pbr(Color("#25404e"), 0.24, 0.05)

func _pbr(color: Color, roughness: float, metallic: float) -> StandardMaterial3D:
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = roughness
    material.metallic = metallic
    return material

func _grass_material(base: Color, macro_strength: float, fine_strength: float, brightness: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_color : source_color = vec3(0.20, 0.48, 0.24);
uniform float macro_strength = 0.04;
uniform float fine_strength = 0.02;
uniform float brightness = 1.0;
float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
float noise2(vec2 p){
    vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),f.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),f.x),f.y);
}
void fragment(){
    vec2 p = UV * vec2(110.0, 220.0);
    float macro = noise2(p * 0.055) - 0.5;
    float fine = noise2(p * 0.72) - 0.5;
    float mow = sin((UV.y * 96.0) + sin(UV.x * 8.0) * 0.35) * 0.5;
    float shade = 1.0 + macro * macro_strength + fine * fine_strength + mow * 0.018;
    ALBEDO = base_color * shade * brightness;
    ROUGHNESS = 0.86 + (noise2(p * 0.36) - 0.5) * 0.08;
    SPECULAR = 0.22;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("base_color", Vector3(base.r, base.g, base.b))
    material.set_shader_parameter("macro_strength", macro_strength)
    material.set_shader_parameter("fine_strength", fine_strength)
    material.set_shader_parameter("brightness", brightness)
    return material

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "WorldEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY
    var sky := Sky.new()
    var sky_mat := ProceduralSkyMaterial.new()
    sky_mat.sky_top_color = Color("#307bc1")
    sky_mat.sky_horizon_color = Color("#9ecbe0")
    sky_mat.ground_horizon_color = Color("#8db596")
    sky_mat.ground_bottom_color = Color("#375c42")
    sky_mat.sun_angle_max = 18.0
    sky_mat.sun_curve = 0.08
    sky.sky_material = sky_mat
    env.sky = sky
    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.ambient_light_energy = 0.72
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.fog_enabled = true
    env.fog_light_color = Color("#a5c7d0")
    env.fog_light_energy = 0.55
    env.fog_density = 0.0075
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "Sun"
    sun.light_color = Color("#fff1d2")
    sun.light_energy = 1.55
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 70.0
    sun.rotation_degrees = Vector3(-52.0, -28.0, 0.0)
    add_child(sun)

    var fill := DirectionalLight3D.new()
    fill.name = "SkyFill"
    fill.light_color = Color("#b6d8ff")
    fill.light_energy = 0.23
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-20.0, 145.0, 0.0)
    add_child(fill)

    camera = Camera3D.new()
    camera.name = "BroadcastCamera"
    camera.fov = 47.0
    camera.near = 0.025
    camera.far = 180.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    var rough := MeshInstance3D.new()
    rough.name = "Rough"
    var rough_mesh := PlaneMesh.new()
    rough_mesh.size = Vector2(44.0, 72.0)
    rough_mesh.subdivide_width = 16
    rough_mesh.subdivide_depth = 28
    rough.mesh = rough_mesh
    rough.material_override = mat_rough
    rough.position = Vector3(0.0, -0.028, -31.0)
    add_child(rough)

    var fringe := MeshInstance3D.new()
    fringe.name = "Fringe"
    var fringe_mesh := PlaneMesh.new()
    fringe_mesh.size = Vector2(13.6, 36.0)
    fringe_mesh.subdivide_width = 16
    fringe_mesh.subdivide_depth = 44
    fringe.mesh = fringe_mesh
    fringe.material_override = mat_fringe
    fringe.position = Vector3(0.0, -0.012, -15.0)
    add_child(fringe)

    var green := MeshInstance3D.new()
    green.name = "Green"
    var green_mesh := PlaneMesh.new()
    green_mesh.size = Vector2(11.8, 34.5)
    green_mesh.subdivide_width = 24
    green_mesh.subdivide_depth = 64
    green.mesh = green_mesh
    green.material_override = mat_green
    green.position = Vector3(0.0, 0.0, -14.25)
    add_child(green)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimLine"
    aim_line.material_override = mat_red
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "Ball"
    var mesh := SphereMesh.new()
    mesh.radius = BALL_RADIUS
    mesh.height = BALL_RADIUS * 2.0
    mesh.radial_segments = 32
    mesh.rings = 20
    ball.mesh = mesh
    var ball_mat := StandardMaterial3D.new()
    ball_mat.albedo_color = Color("#f5f5ef")
    ball_mat.roughness = 0.36
    ball_mat.metallic = 0.0
    ball.material_override = ball_mat
    add_child(ball)

    ball_marker = MeshInstance3D.new()
    ball_marker.name = "AlignmentDot"
    var marker_mesh := SphereMesh.new()
    marker_mesh.radius = 0.0034
    marker_mesh.height = 0.0068
    marker_mesh.radial_segments = 12
    marker_mesh.rings = 8
    ball_marker.mesh = marker_mesh
    ball_marker.material_override = mat_dark
    ball_marker.position = Vector3(0.0, BALL_RADIUS * 0.92, 0.0)
    ball.add_child(ball_marker)

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "Target"
    add_child(target_root)

    var rim := MeshInstance3D.new()
    rim.name = "CupRim"
    var rim_mesh := CylinderMesh.new()
    rim_mesh.top_radius = 0.064
    rim_mesh.bottom_radius = 0.064
    rim_mesh.height = 0.0035
    rim_mesh.radial_segments = 48
    rim.mesh = rim_mesh
    rim.material_override = mat_white
    rim.position.y = -0.002
    target_root.add_child(rim)

    var hole := MeshInstance3D.new()
    hole.name = "CupOpening"
    var hole_mesh := CylinderMesh.new()
    hole_mesh.top_radius = 0.052
    hole_mesh.bottom_radius = 0.052
    hole_mesh.height = 0.0045
    hole_mesh.radial_segments = 48
    hole.mesh = hole_mesh
    hole.material_override = mat_dark
    hole.position.y = 0.001
    target_root.add_child(hole)

    var pole := MeshInstance3D.new()
    pole.name = "PresentationPin"
    var pole_mesh := CylinderMesh.new()
    pole_mesh.top_radius = 0.0065
    pole_mesh.bottom_radius = 0.0065
    pole_mesh.height = 1.92
    pole_mesh.radial_segments = 16
    pole.mesh = pole_mesh
    pole.material_override = mat_white
    pole.position.y = 0.96
    target_root.add_child(pole)

    var flag := MeshInstance3D.new()
    flag.name = "Flag"
    var flag_mesh := BoxMesh.new()
    flag_mesh.size = Vector3(0.36, 0.20, 0.008)
    flag.mesh = flag_mesh
    flag.material_override = mat_red
    flag.position = Vector3(0.18, 1.72, 0.0)
    target_root.add_child(flag)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "HorizonSet"
    add_child(horizon_root)

    _build_clubhouse(Vector3(-3.6, 0.0, -2.0))
    _build_fence(-4.3)

    var tree_data = [
        [-9.2, -5.0, 1.22, 0], [-7.0, -4.5, 1.02, 1], [-5.2, -5.5, 1.18, 2],
        [5.0, -5.3, 1.12, 1], [6.8, -4.6, 1.26, 0], [8.9, -5.2, 1.05, 2],
        [-11.6, -7.8, 1.38, 1], [11.3, -7.5, 1.34, 0]
    ]
    for data in tree_data:
        _build_tree(Vector3(float(data[0]), 0.0, float(data[1])), float(data[2]), int(data[3]))

func _build_tree(pos: Vector3, scale_value: float, palette: int) -> void:
    var root := Node3D.new()
    root.position = pos
    root.scale = Vector3.ONE * scale_value
    horizon_root.add_child(root)

    var trunk := MeshInstance3D.new()
    var trunk_mesh := CylinderMesh.new()
    trunk_mesh.top_radius = 0.12
    trunk_mesh.bottom_radius = 0.18
    trunk_mesh.height = 2.45
    trunk_mesh.radial_segments = 10
    trunk.mesh = trunk_mesh
    trunk.material_override = mat_trunk
    trunk.position.y = 1.225
    root.add_child(trunk)

    var leaf_mat := mat_leaf_a
    if palette == 1:
        leaf_mat = mat_leaf_b
    elif palette == 2:
        leaf_mat = mat_leaf_c

    var clusters = [
        Vector4(-0.52, 2.35, 0.05, 0.88), Vector4(0.45, 2.42, -0.02, 0.92),
        Vector4(-0.05, 2.93, 0.06, 0.98), Vector4(-0.75, 2.90, 0.12, 0.70),
        Vector4(0.72, 2.90, -0.10, 0.74), Vector4(0.12, 3.52, 0.0, 0.72)
    ]
    for c in clusters:
        var leaves := MeshInstance3D.new()
        var leaf_mesh := SphereMesh.new()
        leaf_mesh.radius = c.w
        leaf_mesh.height = c.w * 1.65
        leaf_mesh.radial_segments = 14
        leaf_mesh.rings = 9
        leaves.mesh = leaf_mesh
        leaves.material_override = leaf_mat
        leaves.position = Vector3(c.x, c.y, c.z)
        leaves.scale = Vector3(1.05, 0.82, 0.90)
        root.add_child(leaves)

func _build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "Clubhouse"
    house.position = local_pos
    horizon_root.add_child(house)

    var body := MeshInstance3D.new()
    var body_mesh := BoxMesh.new()
    body_mesh.size = Vector3(4.6, 1.35, 1.55)
    body.mesh = body_mesh
    body.material_override = mat_house
    body.position.y = 0.68
    house.add_child(body)

    var roof_left := MeshInstance3D.new()
    var roof_mesh_l := BoxMesh.new()
    roof_mesh_l.size = Vector3(2.55, 0.12, 1.85)
    roof_left.mesh = roof_mesh_l
    roof_left.material_override = mat_roof
    roof_left.position = Vector3(-1.10, 1.52, 0.0)
    roof_left.rotation_degrees.z = 18.0
    house.add_child(roof_left)

    var roof_right := MeshInstance3D.new()
    var roof_mesh_r := BoxMesh.new()
    roof_mesh_r.size = Vector3(2.55, 0.12, 1.85)
    roof_right.mesh = roof_mesh_r
    roof_right.material_override = mat_roof
    roof_right.position = Vector3(1.10, 1.52, 0.0)
    roof_right.rotation_degrees.z = -18.0
    house.add_child(roof_right)

    for i in range(5):
        var window := MeshInstance3D.new()
        var window_mesh := BoxMesh.new()
        window_mesh.size = Vector3(0.58, 0.50, 0.025)
        window.mesh = window_mesh
        window.material_override = mat_window
        window.position = Vector3(-1.55 + i * 0.78, 0.82, -0.79)
        house.add_child(window)

func _build_fence(local_z: float) -> void:
    var fence := Node3D.new()
    fence.name = "Fence"
    fence.position.z = local_z
    horizon_root.add_child(fence)
    for x in range(-9, 10, 2):
        var post := MeshInstance3D.new()
        var post_mesh := BoxMesh.new()
        post_mesh.size = Vector3(0.055, 0.78, 0.055)
        post.mesh = post_mesh
        post.material_override = mat_white
        post.position = Vector3(float(x), 0.39, 0.0)
        fence.add_child(post)
    for y in [0.30, 0.58]:
        var rail := MeshInstance3D.new()
        var rail_mesh := BoxMesh.new()
        rail_mesh.size = Vector3(18.0, 0.055, 0.055)
        rail.mesh = rail_mesh
        rail.material_override = mat_white
        rail.position = Vector3(0.0, y, 0.0)
        fence.add_child(rail)

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 20
    add_child(layer)

    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    var top_panel := ColorRect.new()
    top_panel.color = Color(0.045, 0.060, 0.070, 0.82)
    top_panel.position = Vector2(720, 20)
    top_panel.size = Vector2(480, 58)
    top_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(top_panel)

    distance_label = _label(top_panel, Vector2(0, 0), Vector2(175, 58), 22, Color("#f3d96a"))
    stimp_label = _label(top_panel, Vector2(175, 0), Vector2(135, 58), 20, Color("#9fe67d"))
    speed_label = _label(top_panel, Vector2(310, 0), Vector2(170, 58), 20, Color.WHITE)

    slope_label = _label(root, Vector2(42, 36), Vector2(390, 44), 18, Color("#f1f3f4"))
    slope_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT

    wait_label = _label(root, Vector2(904, 958), Vector2(112, 42), 18, Color.WHITE)
    wait_label.text = "WAIT"
    var wait_bg := ColorRect.new()
    wait_bg.color = Color(0.10, 0.09, 0.08, 0.72)
    wait_bg.position = Vector2(904, 958)
    wait_bg.size = Vector2(112, 42)
    wait_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(wait_bg)
    root.move_child(wait_bg, wait_label.get_index())

    result_label = _label(root, Vector2(640, 118), Vector2(640, 62), 30, Color.WHITE)
    result_label.visible = false

func _label(parent: Node, pos: Vector2, size: Vector2, font_size: int, color: Color) -> Label:
    var label := Label.new()
    label.position = pos
    label.size = size
    label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    label.add_theme_font_size_override("font_size", font_size)
    label.add_theme_color_override("font_color", color)
    label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.65))
    label.add_theme_constant_override("shadow_offset_x", 1)
    label.add_theme_constant_override("shadow_offset_y", 1)
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    parent.add_child(label)
    return label

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    target_distance = clamp(float(s.get("holeDistance", DEFAULT_DISTANCE)), 0.5, 30.0)
    var cup_z := float(s.get("cupZ", 0.02))
    if abs(target_distance - last_distance) > 0.005 or abs(cup_z - last_cup_z) > 0.002:
        _move_target(target_distance, cup_z)
        _move_horizon(target_distance)
        _update_aim_line(target_distance)
        last_distance = target_distance
        last_cup_z = cup_z

    var ball_world := Vector3(
        float(s.get("ballX", 0.0)),
        float(s.get("ballZ", BALL_RADIUS)),
        -float(s.get("ballY", 0.0))
    )
    ball.position = ball_world
    var q := Quaternion(
        float(s.get("qx", 0.0)),
        float(s.get("qz", 0.0)),
        -float(s.get("qy", 0.0)),
        float(s.get("qw", 1.0))
    )
    if q.length_squared() > 0.2:
        ball.quaternion = q.normalized()

    var running := bool(s.get("running", false))
    var phase := str(s.get("cupPhase", "NONE"))
    var holed := bool(s.get("holed", false))
    var lip_out := bool(s.get("lipOut", false))
    var speed := float(s.get("speed", 0.0))
    var distance_to_cup := float(s.get("distanceToCup", target_distance))

    _update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
    _update_hud(s, running, holed, lip_out, speed)

    last_running = running
    last_phase = phase

func _move_target(distance_m: float, cup_z: float) -> void:
    target_root.position = Vector3(0.0, cup_z, -distance_m)

func _move_horizon(distance_m: float) -> void:
    horizon_root.position = Vector3(0.0, 0.0, -(distance_m + 7.0))

func _update_aim_line(distance_m: float) -> void:
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.012, 0.003, max(0.3, distance_m - 0.16))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.004, -distance_m * 0.5)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov := 47.0

    var cup_world := Vector3(0.0, last_cup_z + 0.03, -target_distance)
    var cup_action := phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.85

    if cup_action:
        desired_pos = cup_world + Vector3(1.18, 0.46, 1.05)
        desired_look = cup_world + Vector3(0.0, 0.035, 0.0)
        desired_fov = 42.0
    elif running:
        var forward_to_cup := (cup_world - ball_world)
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.65 + Vector3(0.0, 0.50, 0.0)
        desired_look = ball_world + forward_to_cup * min(1.55, max(0.45, distance_to_cup * 0.40)) + Vector3(0.0, 0.055, 0.0)
        desired_fov = 45.0
    else:
        desired_pos = Vector3(0.0, 0.47, 1.48)
        var look_distance := min(6.4, max(2.0, target_distance * 0.54))
        desired_look = Vector3(0.0, 0.10, -look_distance)
        desired_fov = 47.0

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha := 1.0 - exp(-delta * (6.8 if cup_action else 4.8))
        var look_alpha := 1.0 - exp(-delta * (8.2 if cup_action else 5.7))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)

    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 4.5))
    camera.look_at(camera_look, Vector3.UP)

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    distance_label.text = "목표 %.1fm" % target_distance
    stimp_label.text = "그린 %.1fm" % float(s.get("stimp", 2.8))
    speed_label.text = "%.2f m/s" % speed if running else "PUTTER"
    var side := float(s.get("sideSlope", 0.0))
    var long_slope := float(s.get("longSlope", 0.0))
    slope_label.text = "경사  L/R %+.2f%%   F/B %+.2f%%" % [side, long_slope]
    wait_label.text = "ROLL" if running else "WAIT"
    result_label.visible = holed or lip_out
    if holed:
        result_label.text = "HOLE IN"
        result_label.add_theme_color_override("font_color", Color("#ffe788"))
    elif lip_out:
        result_label.text = "LIP OUT"
        result_label.add_theme_color_override("font_color", Color("#ffb2a6"))
