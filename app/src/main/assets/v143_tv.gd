extends Node3D

# Clean-room TV presentation. Android HFR + V135-V137 remain the only physics authority.
const BALL_RADIUS := 0.021335
const DEFAULT_DISTANCE := 5.0
const TURF_ALBEDO := "res://v143_assets/turf/albedo.png"
const TURF_NORMAL := "res://v143_assets/turf/normal.png"
const TURF_ROUGH := "res://v143_assets/turf/roughness.png"
const TREE_A := "res://v143_assets/trees/tree_small_02.png"
const TREE_B := "res://v143_assets/trees/island_tree_03.png"
const HDRI := "res://v143_assets/environment/environment.hdr"

var bridge
var ball: MeshInstance3D
var ball_marker: MeshInstance3D
var target_root: Node3D
var horizon_root: Node3D
var aim_line: MeshInstance3D
var camera: Camera3D
var camera_pos := Vector3(0.0, 0.43, 1.52)
var camera_look := Vector3(0.0, 0.08, -2.65)
var target_distance := DEFAULT_DISTANCE
var last_distance := -1.0
var last_cup_z := 0.02
var distance_label: Label
var speed_label: Label
var stimp_label: Label
var wait_label: Label
var slope_label: Label
var result_label: Label

var mat_green: ShaderMaterial
var mat_fringe: ShaderMaterial
var mat_rough: ShaderMaterial
var mat_white: StandardMaterial3D
var mat_dark: StandardMaterial3D
var mat_red: StandardMaterial3D
var mat_house: StandardMaterial3D
var mat_roof: StandardMaterial3D
var mat_window: StandardMaterial3D
var mat_stone: StandardMaterial3D

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
    var turf_albedo: Texture2D = load(TURF_ALBEDO)
    var turf_normal: Texture2D = load(TURF_NORMAL)
    var turf_rough: Texture2D = load(TURF_ROUGH)
    mat_green = _grass_material(turf_albedo, turf_normal, turf_rough, Color("#376f3d"), Vector2(6.0, 18.0), 0.78, 0.42)
    mat_fringe = _grass_material(turf_albedo, turf_normal, turf_rough, Color("#315f37"), Vector2(7.0, 16.0), 0.70, 0.55)
    mat_rough = _grass_material(turf_albedo, turf_normal, turf_rough, Color("#294f31"), Vector2(11.0, 18.0), 0.62, 0.68)
    mat_white = _pbr(Color("#ecece6"), 0.58, 0.0)
    mat_dark = _pbr(Color("#111818"), 0.88, 0.0)
    mat_red = _pbr(Color("#c92b32"), 0.55, 0.0)
    mat_house = _pbr(Color("#b9b09d"), 0.90, 0.0)
    mat_roof = _pbr(Color("#41464a"), 0.92, 0.0)
    mat_window = _pbr(Color("#263e48"), 0.25, 0.02)
    mat_stone = _pbr(Color("#817d70"), 0.96, 0.0)

func _pbr(color: Color, roughness: float, metallic: float) -> StandardMaterial3D:
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = roughness
    material.metallic = metallic
    return material

func _grass_material(albedo: Texture2D, normal: Texture2D, roughness: Texture2D, tint: Color, tiling: Vector2, brightness: float, scan_mix: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D albedo_tex : source_color, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D normal_tex : hint_normal, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D rough_tex : repeat_enable, filter_linear_mipmap_anisotropic;
uniform vec3 tint : source_color = vec3(0.22, 0.43, 0.24);
uniform vec2 tiling = vec2(6.0, 18.0);
uniform float brightness = 0.78;
uniform float scan_mix = 0.42;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    vec2 uv = UV * tiling;
    vec3 scan = texture(albedo_tex, uv).rgb;
    float lum = dot(scan, vec3(0.299, 0.587, 0.114));
    float micro = (hash21(floor(uv * 32.0)) - 0.5) * 0.025;
    float mow = sin(UV.y * 105.0 + sin(UV.x * 8.0) * 0.30) * 0.013;
    float scan_shape = mix(1.0, 0.72 + lum * 0.52, scan_mix);
    ALBEDO = tint * brightness * (scan_shape + micro + mow);
    NORMAL_MAP = texture(normal_tex, uv).rgb;
    NORMAL_MAP_DEPTH = 0.34;
    ROUGHNESS = clamp(mix(0.88, texture(rough_tex, uv).r, 0.50), 0.72, 0.96);
    SPECULAR = 0.18;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("albedo_tex", albedo)
    material.set_shader_parameter("normal_tex", normal)
    material.set_shader_parameter("rough_tex", roughness)
    material.set_shader_parameter("tint", Vector3(tint.r, tint.g, tint.b))
    material.set_shader_parameter("tiling", tiling)
    material.set_shader_parameter("brightness", brightness)
    material.set_shader_parameter("scan_mix", scan_mix)
    return material

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "WorldEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY
    var sky := Sky.new()
    var panorama := PanoramaSkyMaterial.new()
    panorama.panorama = load(HDRI)
    panorama.energy_multiplier = 0.72
    sky.sky_material = panorama
    env.sky = sky
    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.ambient_light_energy = 0.48
    env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.fog_enabled = true
    env.fog_light_color = Color("#a7bbc0")
    env.fog_light_energy = 0.28
    env.fog_density = 0.0032
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "KeySun"
    sun.light_color = Color("#fff3db")
    sun.light_energy = 0.78
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 55.0
    sun.rotation_degrees = Vector3(-48.0, -32.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 46.5
    camera.near = 0.025
    camera.far = 180.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    _course_plane("Rough", Vector2(42.0, 72.0), Vector3(0.0, -0.032, -31.0), mat_rough, 22, 42)
    _course_plane("Fringe", Vector2(13.8, 36.0), Vector3(0.0, -0.014, -15.0), mat_fringe, 20, 60)
    _course_plane("Green", Vector2(11.8, 34.5), Vector3(0.0, 0.0, -14.25), mat_green, 30, 86)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimLine"
    var aim_mat := StandardMaterial3D.new()
    aim_mat.albedo_color = Color(0.72, 0.08, 0.075, 0.72)
    aim_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    aim_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    aim_line.material_override = aim_mat
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _course_plane(name_value: String, size: Vector2, pos: Vector3, material: Material, sub_x: int, sub_z: int) -> void:
    var node := MeshInstance3D.new()
    node.name = name_value
    var mesh := PlaneMesh.new()
    mesh.size = size
    mesh.subdivide_width = sub_x
    mesh.subdivide_depth = sub_z
    node.mesh = mesh
    node.material_override = material
    node.position = pos
    add_child(node)

func _build_ball() -> void:
    ball = MeshInstance3D.new()
    ball.name = "Ball"
    var mesh := SphereMesh.new()
    mesh.radius = BALL_RADIUS
    mesh.height = BALL_RADIUS * 2.0
    mesh.radial_segments = 40
    mesh.rings = 24
    ball.mesh = mesh
    var ball_mat := StandardMaterial3D.new()
    ball_mat.albedo_color = Color("#f5f4ed")
    ball_mat.roughness = 0.30
    ball_mat.metallic = 0.0
    ball.material_override = ball_mat
    add_child(ball)

    ball_marker = MeshInstance3D.new()
    ball_marker.name = "AlignmentDot"
    var marker_mesh := SphereMesh.new()
    marker_mesh.radius = 0.0032
    marker_mesh.height = 0.0064
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

    var cup_shadow := MeshInstance3D.new()
    var shadow_mesh := CylinderMesh.new()
    shadow_mesh.top_radius = 0.056
    shadow_mesh.bottom_radius = 0.056
    shadow_mesh.height = 0.0030
    shadow_mesh.radial_segments = 64
    cup_shadow.mesh = shadow_mesh
    cup_shadow.material_override = mat_dark
    cup_shadow.position.y = 0.0005
    target_root.add_child(cup_shadow)

    var rim := MeshInstance3D.new()
    var rim_mesh := CylinderMesh.new()
    rim_mesh.top_radius = 0.062
    rim_mesh.bottom_radius = 0.062
    rim_mesh.height = 0.0018
    rim_mesh.radial_segments = 64
    rim.mesh = rim_mesh
    rim.material_override = mat_white
    rim.position.y = -0.0015
    target_root.add_child(rim)

    var pole := MeshInstance3D.new()
    var pole_mesh := CylinderMesh.new()
    pole_mesh.top_radius = 0.0060
    pole_mesh.bottom_radius = 0.0060
    pole_mesh.height = 1.86
    pole_mesh.radial_segments = 18
    pole.mesh = pole_mesh
    pole.material_override = mat_white
    pole.position.y = 0.93
    target_root.add_child(pole)

    var flag := MeshInstance3D.new()
    var flag_mesh := PrismMesh.new()
    flag_mesh.size = Vector3(0.36, 0.20, 0.01)
    flag.mesh = flag_mesh
    flag.material_override = mat_red
    flag.position = Vector3(0.18, 1.68, 0.0)
    flag.rotation_degrees = Vector3(0.0, 90.0, 0.0)
    target_root.add_child(flag)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "HorizonSet"
    add_child(horizon_root)
    _build_clubhouse(Vector3(-3.8, 0.0, -2.3))
    _build_fence(-3.65)

    var tree_data = [
        [TREE_A, -10.6, -5.9, 4.8, false, 0.82],
        [TREE_B, -8.0, -5.2, 5.4, true, 0.76],
        [TREE_A, -5.9, -6.8, 4.3, true, 0.74],
        [TREE_B, 4.8, -6.1, 5.8, false, 0.78],
        [TREE_A, 7.4, -5.2, 4.7, true, 0.80],
        [TREE_B, 10.4, -6.4, 6.1, false, 0.73],
        [TREE_A, -13.8, -9.2, 6.0, false, 0.68],
        [TREE_B, 14.2, -9.0, 6.6, true, 0.67]
    ]
    for data in tree_data:
        _build_tree_impostor(str(data[0]), Vector3(float(data[1]), 0.0, float(data[2])), float(data[3]), bool(data[4]), float(data[5]))

func _build_tree_impostor(path: String, pos: Vector3, height_m: float, mirror: bool, tone: float) -> void:
    var texture: Texture2D = load(path)
    var sprite := Sprite3D.new()
    sprite.name = "TreeImpostor"
    sprite.texture = texture
    sprite.pixel_size = height_m / float(max(1, texture.get_height()))
    sprite.position = pos + Vector3(0.0, height_m * 0.50, 0.0)
    sprite.flip_h = mirror
    sprite.billboard = BaseMaterial3D.BILLBOARD_ENABLED
    sprite.alpha_cut = SpriteBase3D.ALPHA_CUT_OPAQUE_PREPASS
    sprite.shaded = true
    sprite.double_sided = true
    sprite.modulate = Color(tone, min(1.0, tone * 1.04), tone * 0.96, 1.0)
    horizon_root.add_child(sprite)

func _build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "Clubhouse"
    house.position = local_pos
    horizon_root.add_child(house)

    _box(house, Vector3(4.8, 1.34, 1.62), Vector3(0.0, 0.67, 0.0), mat_house)
    _box(house, Vector3(5.25, 0.13, 1.98), Vector3(0.0, 1.48, 0.0), mat_roof, Vector3(0.0, 0.0, -8.0))
    _box(house, Vector3(1.45, 0.62, 1.68), Vector3(-1.65, 1.48, 0.0), mat_stone)
    for i in range(5):
        _box(house, Vector3(0.58, 0.49, 0.025), Vector3(-1.55 + i * 0.78, 0.79, -0.823), mat_window)
    _box(house, Vector3(1.22, 0.10, 0.72), Vector3(0.55, 1.02, -0.88), mat_roof)

func _box(parent: Node3D, size: Vector3, pos: Vector3, material: Material, rot: Vector3 = Vector3.ZERO) -> MeshInstance3D:
    var node := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    node.mesh = mesh
    node.material_override = material
    node.position = pos
    node.rotation_degrees = rot
    parent.add_child(node)
    return node

func _build_fence(local_z: float) -> void:
    var fence := Node3D.new()
    fence.name = "Fence"
    fence.position.z = local_z
    horizon_root.add_child(fence)
    for x in range(-9, 10, 2):
        _box(fence, Vector3(0.045, 0.70, 0.045), Vector3(float(x), 0.35, 0.0), mat_white)
    for y in [0.24, 0.50]:
        _box(fence, Vector3(18.0, 0.045, 0.045), Vector3(0.0, float(y), 0.0), mat_white)

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 20
    add_child(layer)
    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    var top_panel := ColorRect.new()
    top_panel.color = Color(0.035, 0.045, 0.052, 0.78)
    top_panel.position = Vector2(708, 18)
    top_panel.size = Vector2(504, 62)
    root.add_child(top_panel)
    distance_label = _label(top_panel, Vector2(0, 0), Vector2(184, 62), 22, Color("#efd267"))
    stimp_label = _label(top_panel, Vector2(184, 0), Vector2(138, 62), 18, Color("#9fd67e"))
    speed_label = _label(top_panel, Vector2(322, 0), Vector2(182, 62), 19, Color("#f1f2ef"))

    slope_label = _label(root, Vector2(40, 32), Vector2(420, 42), 17, Color(0.96, 0.97, 0.95, 0.88))
    slope_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT

    var wait_bg := ColorRect.new()
    wait_bg.color = Color(0.08, 0.075, 0.065, 0.70)
    wait_bg.position = Vector2(906, 958)
    wait_bg.size = Vector2(108, 40)
    root.add_child(wait_bg)
    wait_label = _label(root, Vector2(906, 958), Vector2(108, 40), 17, Color("#f1eee2"))
    wait_label.text = "WAIT"

    result_label = _label(root, Vector2(630, 116), Vector2(660, 64), 32, Color.WHITE)
    result_label.visible = false

func _label(parent: Node, pos: Vector2, size: Vector2, font_size: int, color: Color) -> Label:
    var label := Label.new()
    label.position = pos
    label.size = size
    label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    label.add_theme_font_size_override("font_size", font_size)
    label.add_theme_color_override("font_color", color)
    label.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.66))
    label.add_theme_constant_override("shadow_offset_x", 1)
    label.add_theme_constant_override("shadow_offset_y", 1)
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    parent.add_child(label)
    return label

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    target_distance = clamp(float(s.get("holeDistance", DEFAULT_DISTANCE)), 0.5, 30.0)
    var cup_z: float = float(s.get("cupZ", 0.02))
    var side: float = float(s.get("sideSlope", 0.0)) * 0.01
    var long_slope: float = float(s.get("longSlope", 0.0)) * 0.01
    mat_green.set_shader_parameter("side_slope", side)
    mat_green.set_shader_parameter("long_slope", long_slope)
    mat_fringe.set_shader_parameter("side_slope", side)
    mat_fringe.set_shader_parameter("long_slope", long_slope)
    mat_rough.set_shader_parameter("side_slope", side)
    mat_rough.set_shader_parameter("long_slope", long_slope)

    if abs(target_distance - last_distance) > 0.005 or abs(cup_z - last_cup_z) > 0.002:
        _move_target(target_distance, cup_z)
        _move_horizon(target_distance)
        _update_aim_line(target_distance)
        last_distance = target_distance
        last_cup_z = cup_z

    var ball_world := Vector3(float(s.get("ballX", 0.0)), float(s.get("ballZ", BALL_RADIUS)), -float(s.get("ballY", 0.0)))
    ball.position = ball_world
    var q := Quaternion(float(s.get("qx", 0.0)), float(s.get("qz", 0.0)), -float(s.get("qy", 0.0)), float(s.get("qw", 1.0)))
    if q.length_squared() > 0.2:
        ball.quaternion = q.normalized()

    var running: bool = bool(s.get("running", false))
    var phase: String = str(s.get("cupPhase", "NONE"))
    var holed: bool = bool(s.get("holed", false))
    var lip_out: bool = bool(s.get("lipOut", false))
    var speed: float = float(s.get("speed", 0.0))
    var distance_to_cup: float = float(s.get("distanceToCup", target_distance))
    _update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
    _update_hud(s, running, holed, lip_out, speed)

func _move_target(distance_m: float, cup_z: float) -> void:
    target_root.position = Vector3(0.0, cup_z, -distance_m)

func _move_horizon(distance_m: float) -> void:
    horizon_root.position = Vector3(0.0, 0.0, -(distance_m + 6.7))

func _update_aim_line(distance_m: float) -> void:
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.010, 0.0022, max(0.3, distance_m - 0.20))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.004, -distance_m * 0.5)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 46.5
    var cup_world := Vector3(0.0, last_cup_z + 0.026, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.78

    if cup_action:
        desired_pos = cup_world + Vector3(1.02, 0.39, 0.92)
        desired_look = cup_world + Vector3(0.0, 0.030, 0.0)
        desired_fov = 40.5
    elif running:
        var forward_to_cup := cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.52 + Vector3(0.0, 0.43, 0.0)
        var lead: float = min(1.45, max(0.42, distance_to_cup * 0.38))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.045, 0.0)
        desired_fov = 44.0
    else:
        desired_pos = Vector3(0.0, 0.42, 1.46)
        var look_distance: float = min(6.0, max(2.2, target_distance * 0.56))
        desired_look = Vector3(0.0, 0.085, -look_distance)
        desired_fov = 46.5

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (6.6 if cup_action else 4.7))
        var look_alpha: float = 1.0 - exp(-delta * (8.0 if cup_action else 5.5))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 4.3))
    camera.look_at(camera_look, Vector3.UP)

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    distance_label.text = "목표 %.1fm" % target_distance
    stimp_label.text = "그린 %.1fm" % float(s.get("stimp", 2.8))
    speed_label.text = "%.2f m/s" % speed if running else "PUTTER"
    var side: float = float(s.get("sideSlope", 0.0))
    var long_slope: float = float(s.get("longSlope", 0.0))
    slope_label.text = "경사  L/R %+.2f%%   F/B %+.2f%%" % [side, long_slope]
    wait_label.text = "ROLL" if running else "WAIT"
    result_label.visible = holed or lip_out
    if holed:
        result_label.text = "HOLE IN"
        result_label.add_theme_color_override("font_color", Color("#ffe58a"))
    elif lip_out:
        result_label.text = "LIP OUT"
        result_label.add_theme_color_override("font_color", Color("#ffb4a8"))
