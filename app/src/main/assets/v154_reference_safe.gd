extends "res://v151_mobile_safe.gd"

# V154 reference-matched renderer.
# Keep V151/V153's proven crash-safe Godot path, but replace the low-poly horizon and synthetic
# turf look with small runtime-decoded reference plates. We still do NOT use HDRI, Sprite3D,
# anisotropic samplers, ProceduralSkyMaterial or dynamic shadows.

var _v154_backdrop_texture: Texture2D
var _v154_grass_texture: Texture2D

func _read_chunks(paths: Array[String]) -> String:
    var encoded := ""
    for path in paths:
        encoded += FileAccess.get_file_as_string(path).strip_edges()
    return encoded

func _decode_reference_jpg(paths: Array[String]) -> Texture2D:
    var raw: PackedByteArray = Marshalls.base64_to_raw(_read_chunks(paths))
    var image := Image.new()
    var err := image.load_jpg_from_buffer(raw)
    if err != OK:
        push_error("V154 reference JPG decode failed: %s" % err)
        return null
    image.generate_mipmaps()
    return ImageTexture.create_from_image(image)

func _build_materials() -> void:
    _v154_grass_texture = _decode_reference_jpg([
        "res://v154_assets/grass_0.b64",
        "res://v154_assets/grass_1.b64"
    ])

    mat_green = _reference_grass(Color("#eef7dd"), Vector2(2.6, 10.0), 0.050)
    mat_fringe = _reference_grass(Color("#d4e9cb"), Vector2(3.0, 11.0), 0.044)
    mat_rough = _reference_grass(Color("#b7d1b3"), Vector2(3.6, 12.0), 0.034)

    mat_white = _pbr(Color("#f5f3eb"), 0.50, 0.0)
    mat_dark = _pbr(Color("#090e0d"), 0.95, 0.0)
    mat_red = _pbr(Color("#ca1f2b"), 0.55, 0.0)
    mat_house = _pbr(Color("#d4cbb8"), 0.82, 0.0)
    mat_roof = _pbr(Color("#202527"), 0.86, 0.0)
    mat_window = _pbr(Color("#1d3a46"), 0.28, 0.02)
    mat_stone = _pbr(Color("#8a8375"), 0.92, 0.0)

    _guide = _flat(Color(0.80, 0.09, 0.08, 0.46), 1.0)
    _guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _reference_grass(tint_mul: Color, tiling: Vector2, stripe_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D photo_tex : source_color, repeat_enable, filter_linear_mipmap;
uniform vec3 tint_mul : source_color = vec3(1.0);
uniform vec2 tiling = vec2(2.6, 10.0);
uniform float stripe_strength = 0.05;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    vec2 uv = UV * tiling;
    vec3 photo = texture(photo_tex, uv).rgb;
    float mow = 0.5 + 0.5 * sin(UV.y * 42.0 * 6.2831853);
    float blade = (hash21(floor(UV * vec2(1450.0, 2300.0))) - 0.5) * 0.032;
    float band = mix(1.0 - stripe_strength, 1.0 + stripe_strength, smoothstep(0.20, 0.80, mow));
    ALBEDO = photo * tint_mul * (band + blade);
    ROUGHNESS = 0.84;
    SPECULAR = 0.19;
    vec2 n = vec2(
        sin(UV.x * 900.0 + UV.y * 41.0),
        cos(UV.y * 1120.0 + UV.x * 37.0)
    ) * 0.040;
    NORMAL_MAP = vec3(n * 0.5 + 0.5, 1.0);
    NORMAL_MAP_DEPTH = 0.16;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("photo_tex", _v154_grass_texture)
    material.set_shader_parameter("tint_mul", Vector3(tint_mul.r, tint_mul.g, tint_mul.b))
    material.set_shader_parameter("tiling", tiling)
    material.set_shader_parameter("stripe_strength", stripe_strength)
    return material

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V154ReferenceEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#75b9df")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#dce8e8")
    env.ambient_light_energy = 0.64
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 1.00
    env.adjustment_contrast = 1.08
    env.adjustment_saturation = 1.06
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "V154KeySun"
    sun.light_color = Color("#fff2d8")
    sun.light_energy = 1.12
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-46.0, -31.0, 0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.0
    camera.near = 0.016
    camera.far = 140.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    # One uninterrupted reference-textured playable corridor; no giant pale polygon bands.
    _course_plane("Rough", Vector2(50.0, 86.0), Vector3(0.0, -0.032, -38.0), mat_rough, 4, 12)
    _course_plane("Fringe", Vector2(17.0, 48.0), Vector3(0.0, -0.011, -20.0), mat_fringe, 6, 22)
    _course_plane("Green", Vector2(13.2, 44.0), Vector3(0.0, 0.0, -18.3), mat_green, 14, 56)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimGuide"
    aim_line.material_override = _guide
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _update_aim_line(distance_m: float) -> void:
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.0032, 0.0011, max(0.30, distance_m - 0.38))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.0032, -distance_m * 0.50 + 0.08)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "V154PhotographicBackdrop"
    add_child(horizon_root)

    _v154_backdrop_texture = _decode_reference_jpg([
        "res://v154_assets/backdrop_0.b64",
        "res://v154_assets/backdrop_1.b64",
        "res://v154_assets/backdrop_2.b64",
        "res://v154_assets/backdrop_3.b64"
    ])

    var photo := MeshInstance3D.new()
    photo.name = "PuttingLabReferenceBackdrop"
    var mesh := QuadMesh.new()
    mesh.size = Vector2(22.4, 6.30)
    photo.mesh = mesh
    photo.position = Vector3(0.0, 2.74, -13.8)

    var material := StandardMaterial3D.new()
    material.albedo_texture = _v154_backdrop_texture
    material.roughness = 1.0
    material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    material.cull_mode = BaseMaterial3D.CULL_DISABLED
    photo.material_override = material
    horizon_root.add_child(photo)

func _move_horizon(_distance_m: float) -> void:
    # A photographic far plate must stay fixed while hole distance changes.
    if horizon_root != null:
        horizon_root.position = Vector3.ZERO

func _build_target() -> void:
    target_root = Node3D.new()
    target_root.name = "V154ReferenceCupAndPin"
    add_child(target_root)

    # Regulation 108 mm opening. Black cavity + dark cut edge + recessed white liner.
    var cup_void := MeshInstance3D.new()
    cup_void.name = "CupVoid"
    var void_mesh := QuadMesh.new()
    void_mesh.size = Vector2(0.110, 0.110)
    cup_void.mesh = void_mesh
    cup_void.rotation_degrees.x = -90.0
    cup_void.position.y = 0.0010
    var void_shader := Shader.new()
    void_shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
void fragment(){
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float r = length(p);
    if (r > 1.0) { discard; }
    float edge = smoothstep(0.64, 0.98, r);
    ALBEDO = mix(vec3(0.002,0.003,0.002), vec3(0.055,0.037,0.024), edge * 0.60);
}
"""
    var void_mat := ShaderMaterial.new()
    void_mat.shader = void_shader
    cup_void.material_override = void_mat
    target_root.add_child(cup_void)

    var outer_lip := MeshInstance3D.new()
    outer_lip.name = "DarkCupLip"
    var lip_mesh := TorusMesh.new()
    lip_mesh.inner_radius = 0.0495
    lip_mesh.outer_radius = 0.0555
    lip_mesh.rings = 10
    lip_mesh.ring_segments = 56
    outer_lip.mesh = lip_mesh
    outer_lip.material_override = _pbr(Color("#463326"), 0.88, 0.0)
    outer_lip.position.y = 0.0014
    target_root.add_child(outer_lip)

    var liner_wall := MeshInstance3D.new()
    liner_wall.name = "WhiteCupLiner"
    var wall_mesh := CylinderMesh.new()
    wall_mesh.top_radius = 0.0490
    wall_mesh.bottom_radius = 0.0478
    wall_mesh.height = 0.052
    wall_mesh.radial_segments = 56
    wall_mesh.cap_top = false
    wall_mesh.cap_bottom = false
    liner_wall.mesh = wall_mesh
    liner_wall.material_override = mat_white
    liner_wall.position.y = -0.0265
    target_root.add_child(liner_wall)

    var inner_ring := MeshInstance3D.new()
    inner_ring.name = "WhiteInnerRim"
    var inner_mesh := TorusMesh.new()
    inner_mesh.inner_radius = 0.0430
    inner_mesh.outer_radius = 0.0485
    inner_mesh.rings = 8
    inner_mesh.ring_segments = 52
    inner_ring.mesh = inner_mesh
    inner_ring.material_override = mat_white
    inner_ring.position.y = -0.0030
    target_root.add_child(inner_ring)

    # Approved reference: red lower stick, white upper stick.
    _pin_segment(0.34, 0.68, mat_red)
    _pin_segment(1.23, 1.10, mat_white)

    var flag := MeshInstance3D.new()
    flag.name = "Flag"
    var flag_mesh := PrismMesh.new()
    flag_mesh.size = Vector3(0.39, 0.22, 0.010)
    flag.mesh = flag_mesh
    flag.material_override = mat_red
    flag.position = Vector3(0.195, 1.80, 0.0)
    flag.rotation_degrees = Vector3(0.0, 90.0, 0.0)
    target_root.add_child(flag)

func _move_target(distance_m: float, cup_z: float) -> void:
    # Bridge publishes terrain height + 20 mm; remove it so the rim is truly flush with turf.
    target_root.position = Vector3(0.0, cup_z - 0.020, -distance_m)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 42.0
    var cup_ground_y: float = last_cup_z - 0.020
    var cup_world := Vector3(0.0, cup_ground_y + 0.006, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.72

    if cup_action:
        # Same composition as the supplied preview: centered flag, low close cup view.
        desired_pos = cup_world + Vector3(0.055, 0.275, 0.73)
        desired_look = cup_world + Vector3(0.0, 0.020, -0.010)
        desired_fov = 38.5
    elif running:
        var forward_to_cup := cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.34 + Vector3(0.0, 0.34, 0.0)
        var lead: float = min(1.35, max(0.40, distance_to_cup * 0.36))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.030, 0.0)
        desired_fov = 40.5
    else:
        desired_pos = Vector3(0.0, 0.38, 1.52)
        var look_distance: float = min(6.0, max(2.65, target_distance * 0.60))
        desired_look = Vector3(0.0, 0.060, -look_distance)
        desired_fov = 42.0

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (7.4 if cup_action else 5.0))
        var look_alpha: float = 1.0 - exp(-delta * (8.8 if cup_action else 6.0))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 5.0))
    camera.look_at(camera_look, Vector3.UP)
