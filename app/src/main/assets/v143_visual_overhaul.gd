extends "res://v143_tv.gd"

# V143 visual overhaul: keep Android measurement/physics authoritative and replace only
# the presentation layer. This subclass is intentionally isolated so the proven V143
# bridge and V135-V137 ball/cup solver remain untouched.

var premium_leaf: StandardMaterial3D
var premium_leaf_dark: StandardMaterial3D
var premium_bark: StandardMaterial3D
var premium_stone: StandardMaterial3D
var premium_deck: StandardMaterial3D
var premium_glass: StandardMaterial3D
var premium_warm_glass: StandardMaterial3D
var premium_trim: StandardMaterial3D
var guide_material: StandardMaterial3D

func _build_materials() -> void:
    super._build_materials()

    # The first V143 preview read as neon synthetic turf. Keep the CC0 texture detail,
    # but move the palette/roughness toward a broadcast-calibrated real green.
    mat_green = _premium_grass(Color("#31583a"), Vector2(7.0, 22.0), 0.74, 0.24)
    mat_fringe = _premium_grass(Color("#2c5034"), Vector2(8.0, 19.0), 0.68, 0.30)
    mat_rough = _premium_grass(Color("#27472f"), Vector2(12.0, 17.0), 0.60, 0.36)

    mat_house = _pbr(Color("#d7d2c4"), 0.84, 0.0)
    mat_roof = _pbr(Color("#2b3134"), 0.72, 0.02)
    mat_window = _pbr(Color("#24424e"), 0.20, 0.06)
    mat_stone = _pbr(Color("#77746a"), 0.92, 0.0)

    premium_leaf = _pbr(Color("#315f35"), 0.90, 0.0)
    premium_leaf_dark = _pbr(Color("#24482c"), 0.94, 0.0)
    premium_bark = _pbr(Color("#4f3b2d"), 0.98, 0.0)
    premium_stone = _pbr(Color("#69675f"), 0.96, 0.0)
    premium_deck = _pbr(Color("#7f684f"), 0.82, 0.0)
    premium_glass = _pbr(Color("#213d49"), 0.16, 0.08)
    premium_trim = _pbr(Color("#e9e7df"), 0.62, 0.0)

    premium_warm_glass = _pbr(Color("#4a4031"), 0.22, 0.02)
    premium_warm_glass.emission_enabled = true
    premium_warm_glass.emission = Color("#d7ad6f")
    premium_warm_glass.emission_energy_multiplier = 0.22

    guide_material = StandardMaterial3D.new()
    guide_material.albedo_color = Color(0.88, 0.18, 0.14, 0.56)
    guide_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    guide_material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _premium_grass(tint_color: Color, tiling: Vector2, brightness: float, texture_mix: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D albedo_tex : source_color, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D normal_tex : hint_normal, repeat_enable, filter_linear_mipmap_anisotropic;
uniform sampler2D rough_tex : repeat_enable, filter_linear_mipmap_anisotropic;
uniform vec3 tint : source_color = vec3(0.19, 0.35, 0.23);
uniform vec2 tiling = vec2(7.0, 22.0);
uniform float brightness = 0.74;
uniform float texture_mix = 0.24;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    vec2 uv = UV * tiling;
    vec3 texel = texture(albedo_tex, uv).rgb;
    float tex_luma = dot(texel, vec3(0.299, 0.587, 0.114));
    float broad = sin(UV.y * 36.0 + sin(UV.x * 5.0) * 0.28) * 0.022;
    float fine = (hash21(floor(uv * 21.0)) - 0.5) * 0.018;
    float texture_shape = mix(1.0, 0.82 + tex_luma * 0.36, texture_mix);
    vec3 base = tint * brightness * (texture_shape + broad + fine);
    float neutral = dot(base, vec3(0.299, 0.587, 0.114));
    ALBEDO = mix(vec3(neutral), base, 0.82);
    NORMAL_MAP = texture(normal_tex, uv).rgb;
    NORMAL_MAP_DEPTH = 0.26;
    ROUGHNESS = clamp(mix(0.90, texture(rough_tex, uv).r, 0.34), 0.78, 0.96);
    SPECULAR = 0.13;
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
    return material

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "PremiumWorldEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY

    # The previous HDRI was valid but read as a flat gray card in the CI reference frame.
    # A procedural clear-day sky is deterministic on desktop CI and Android/DeX.
    var sky := Sky.new()
    var sky_mat := ProceduralSkyMaterial.new()
    sky_mat.sky_top_color = Color("#2f70ad")
    sky_mat.sky_horizon_color = Color("#b8d2df")
    sky_mat.ground_bottom_color = Color("#263b31")
    sky_mat.ground_horizon_color = Color("#9eb0a4")
    sky_mat.sky_curve = 0.18
    sky_mat.ground_curve = 0.12
    sky_mat.sun_angle_max = 10.0
    sky_mat.sun_curve = 0.10
    sky_mat.sun_energy_multiplier = 1.35
    sky.sky_material = sky_mat
    env.sky = sky

    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.ambient_light_energy = 0.62
    env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.fog_enabled = true
    env.fog_light_color = Color("#c5d5d8")
    env.fog_light_energy = 0.34
    env.fog_density = 0.0019
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "PremiumKeySun"
    sun.light_color = Color("#fff1d6")
    sun.light_energy = 1.08
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 62.0
    sun.rotation_degrees = Vector3(-52.0, -28.0, 0.0)
    add_child(sun)

    var fill := DirectionalLight3D.new()
    fill.name = "SkyFill"
    fill.light_color = Color("#b7d2e3")
    fill.light_energy = 0.18
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-26.0, 142.0, 0.0)
    add_child(fill)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 45.0
    camera.near = 0.025
    camera.far = 180.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _build_course() -> void:
    # Preserve the proven large-surface geometry/slope mapping, but use the new turf and add
    # a restrained collar edge so the green no longer reads as one endless rectangular carpet.
    _course_plane("Rough", Vector2(46.0, 76.0), Vector3(0.0, -0.040, -32.0), mat_rough, 24, 46)
    _course_plane("Fringe", Vector2(14.6, 37.0), Vector3(0.0, -0.018, -15.3), mat_fringe, 22, 64)
    _course_plane("Green", Vector2(11.7, 34.8), Vector3(0.0, 0.0, -14.3), mat_green, 32, 90)

    # Low 3D shoulders break the perfectly flat horizon without interfering with ball physics.
    _landform(Vector3(-8.4, -0.24, -16.0), Vector3(7.8, 0.42, 17.0), mat_rough)
    _landform(Vector3(8.6, -0.25, -17.5), Vector3(8.0, 0.46, 18.0), mat_rough)

    aim_line = MeshInstance3D.new()
    aim_line.name = "AimGuide"
    aim_line.material_override = guide_material
    add_child(aim_line)
    _update_aim_line(DEFAULT_DISTANCE)

func _landform(pos: Vector3, scale_value: Vector3, material: Material) -> void:
    var node := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 1.0
    mesh.height = 2.0
    mesh.radial_segments = 32
    mesh.rings = 12
    node.mesh = mesh
    node.material_override = material
    node.position = pos
    node.scale = scale_value
    add_child(node)

func _update_aim_line(distance_m: float) -> void:
    aim_line.mesh = null
    aim_line.position = Vector3.ZERO
    for child in aim_line.get_children():
        aim_line.remove_child(child)
        child.queue_free()

    # Fine segmented guide rather than the old thick red laser beam.
    var usable := max(0.45, distance_m - 0.34)
    var pitch := 0.28
    var dash_len := 0.12
    var count := int(floor(usable / pitch))
    for i in range(count):
        var z := -0.24 - float(i) * pitch
        if abs(z) > distance_m - 0.18:
            break
        var dash := MeshInstance3D.new()
        dash.name = "GuideDash"
        var mesh := BoxMesh.new()
        mesh.size = Vector3(0.006, 0.0016, dash_len)
        dash.mesh = mesh
        dash.material_override = guide_material
        dash.position = Vector3(0.0, 0.0042, z)
        aim_line.add_child(dash)

func _build_horizon() -> void:
    horizon_root = Node3D.new()
    horizon_root.name = "PremiumHorizon"
    add_child(horizon_root)

    _build_pavilion(Vector3(-3.85, 0.0, -2.75))
    _build_fence(-4.05)

    # Real 3D shrubs in the mid-ground provide parallax and shadow contact.
    for shrub in [
        Vector3(-7.7, 0.0, -4.2), Vector3(-6.7, 0.0, -4.4),
        Vector3(5.3, 0.0, -4.5), Vector3(6.4, 0.0, -4.2), Vector3(7.6, 0.0, -4.6)
    ]:
        _build_shrub(shrub, 0.62 + abs(shrub.x) * 0.012)

    # Keep photographic CC0 foliage only in the far field where impostors are appropriate.
    var tree_data = [
        [TREE_A, -10.9, -7.2, 4.5, false, 0.82],
        [TREE_A, -8.7, -8.4, 5.0, true, 0.78],
        [TREE_A, -6.7, -9.8, 4.7, false, 0.76],
        [TREE_A, 5.9, -8.8, 4.8, true, 0.79],
        [TREE_A, 8.3, -7.5, 5.3, false, 0.80],
        [TREE_A, 10.7, -9.4, 5.7, true, 0.74],
        [TREE_A, -13.5, -12.5, 5.8, true, 0.70],
        [TREE_A, 13.8, -12.0, 6.0, false, 0.70]
    ]
    for data in tree_data:
        _build_tree_impostor(str(data[0]), Vector3(float(data[1]), 0.0, float(data[2])), float(data[3]), bool(data[4]), float(data[5]))

func _build_pavilion(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "ModernClubhouse"
    house.position = local_pos
    horizon_root.add_child(house)

    # Main mass, stone plinth and floating roof.
    _box(house, Vector3(5.25, 0.92, 1.72), Vector3(0.0, 0.68, 0.0), mat_house)
    _box(house, Vector3(5.35, 0.22, 1.80), Vector3(0.0, 0.11, 0.0), premium_stone)
    _box(house, Vector3(5.85, 0.12, 2.18), Vector3(0.04, 1.28, -0.02), mat_roof, Vector3(0.0, 0.0, -4.5))

    # Glass facade with mullions and a warm lounge zone.
    for i in range(6):
        var x := -1.95 + float(i) * 0.78
        _box(house, Vector3(0.63, 0.62, 0.035), Vector3(x, 0.70, -0.882), premium_glass if i < 4 else premium_warm_glass)
        _box(house, Vector3(0.035, 0.68, 0.06), Vector3(x + 0.34, 0.70, -0.90), premium_trim)

    # Entry, terrace, canopy and columns give the silhouette actual depth.
    _box(house, Vector3(1.02, 0.70, 0.05), Vector3(2.08, 0.67, -0.89), mat_window)
    _box(house, Vector3(4.45, 0.08, 0.90), Vector3(0.15, 0.08, -1.27), premium_deck)
    _box(house, Vector3(3.15, 0.09, 1.00), Vector3(0.35, 1.05, -1.10), mat_roof)
    for x in [-1.05, 0.15, 1.35]:
        _box(house, Vector3(0.07, 0.95, 0.07), Vector3(float(x), 0.56, -1.20), premium_trim)

    # Side stone blade and chimney break the box profile.
    _box(house, Vector3(0.58, 1.55, 1.82), Vector3(-2.25, 0.78, 0.02), premium_stone)
    _box(house, Vector3(0.32, 1.95, 0.44), Vector3(-2.02, 1.10, 0.38), premium_stone)

func _build_shrub(pos: Vector3, radius: float) -> void:
    var root := Node3D.new()
    root.name = "Shrub"
    root.position = pos
    horizon_root.add_child(root)
    for i in range(5):
        var leaf := MeshInstance3D.new()
        var mesh := SphereMesh.new()
        mesh.radius = radius
        mesh.height = radius * 1.65
        mesh.radial_segments = 18
        mesh.rings = 10
        leaf.mesh = mesh
        leaf.material_override = premium_leaf if i % 2 == 0 else premium_leaf_dark
        var angle := float(i) * TAU / 5.0
        leaf.position = Vector3(cos(angle) * radius * 0.45, radius * (0.58 + 0.10 * (i % 2)), sin(angle) * radius * 0.28)
        leaf.scale = Vector3(1.0, 0.78, 0.72)
        root.add_child(leaf)

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 20
    add_child(layer)
    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    # Single calm broadcast ribbon. ASCII labels keep CI and Android rendering deterministic.
    var top_panel := ColorRect.new()
    top_panel.color = Color(0.025, 0.034, 0.038, 0.84)
    top_panel.position = Vector2(670, 22)
    top_panel.size = Vector2(580, 70)
    root.add_child(top_panel)

    var accent := ColorRect.new()
    accent.color = Color("#d5b85f")
    accent.position = Vector2(0, 66)
    accent.size = Vector2(580, 4)
    top_panel.add_child(accent)

    distance_label = _label(top_panel, Vector2(10, 0), Vector2(200, 66), 22, Color("#f1d36e"))
    stimp_label = _label(top_panel, Vector2(210, 0), Vector2(170, 66), 18, Color("#b8d99a"))
    speed_label = _label(top_panel, Vector2(380, 0), Vector2(190, 66), 19, Color("#f5f5ef"))

    var slope_bg := ColorRect.new()
    slope_bg.color = Color(0.025, 0.034, 0.038, 0.58)
    slope_bg.position = Vector2(34, 34)
    slope_bg.size = Vector2(392, 44)
    root.add_child(slope_bg)
    slope_label = _label(root, Vector2(48, 34), Vector2(364, 44), 16, Color(0.94, 0.96, 0.94, 0.92))
    slope_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT

    var wait_bg := ColorRect.new()
    wait_bg.color = Color(0.025, 0.034, 0.038, 0.74)
    wait_bg.position = Vector2(905, 958)
    wait_bg.size = Vector2(110, 38)
    root.add_child(wait_bg)
    wait_label = _label(root, Vector2(905, 958), Vector2(110, 38), 16, Color("#f2efe5"))
    wait_label.text = "READY"

    result_label = _label(root, Vector2(620, 124), Vector2(680, 66), 32, Color.WHITE)
    result_label.visible = false

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    distance_label.text = "TARGET  %.1fm" % target_distance
    stimp_label.text = "GREEN  %.1fm" % float(s.get("stimp", 2.8))
    speed_label.text = "%.2f m/s" % speed if running else "PUTTER"
    var side: float = float(s.get("sideSlope", 0.0))
    var long_slope: float = float(s.get("longSlope", 0.0))
    slope_label.text = "BREAK   L/R %+.2f%%    F/B %+.2f%%" % [side, long_slope]
    wait_label.text = "ROLL" if running else "READY"
    result_label.visible = holed or lip_out
    if holed:
        result_label.text = "HOLE IN"
        result_label.add_theme_color_override("font_color", Color("#ffe58a"))
    elif lip_out:
        result_label.text = "LIP OUT"
        result_label.add_theme_color_override("font_color", Color("#ffb4a8"))
