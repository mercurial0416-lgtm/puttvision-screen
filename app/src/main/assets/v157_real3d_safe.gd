extends "res://v155_modeled_safe.gd"

# V157 real-3D premium pass.
# No scene photograph/billboard.  The green, clubhouse, fence, trees, hills, cup and ball are
# rendered as actual geometry.  Tiny stone/wood files are material maps only.  The renderer keeps
# the proven mobile-safe restrictions: no HDRI, Sprite3D, anisotropic sampling,
# ProceduralSkyMaterial, or dynamic shadows.

var _v157_stone_tex: Texture2D
var _v157_wood_tex: Texture2D
var _v157_sky: MeshInstance3D

func _v157_tex(texture: Texture2D, tint: Color, roughness: float, uv_scale: Vector3 = Vector3.ONE) -> StandardMaterial3D:
    var material := StandardMaterial3D.new()
    material.albedo_texture = texture
    material.albedo_color = tint
    material.roughness = roughness
    material.metallic = 0.0
    material.uv1_scale = uv_scale
    material.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
    return material

func _v157_grass(base_color: Color, stripe_strength: float, micro_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_color : source_color = vec3(0.22, 0.42, 0.20);
uniform float stripe_strength = 0.035;
uniform float micro_strength = 0.020;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123); }
float noise21(vec2 p){
    vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
    float a=hash21(i); float b=hash21(i+vec2(1.0,0.0));
    float c=hash21(i+vec2(0.0,1.0)); float d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void vertex(){
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}
void fragment(){
    // Dense mower direction with broad organic variation; no huge neon bands.
    float mow_wave = 0.5 + 0.5 * sin(UV.y * 86.0 * 6.2831853);
    float mow = mix(1.0-stripe_strength, 1.0+stripe_strength, smoothstep(0.18,0.82,mow_wave));
    float broad = noise21(UV * vec2(28.0,96.0)) - 0.5;
    float mid = noise21(UV * vec2(180.0,520.0)) - 0.5;
    float grain = hash21(floor(UV * vec2(1450.0,2900.0))) - 0.5;
    float blade = sin(UV.x*1120.0 + UV.y*127.0) * 0.0045;
    float light = mow + broad*0.035 + mid*0.018 + grain*micro_strength + blade;
    ALBEDO = base_color * light;
    ROUGHNESS = clamp(0.83 + (0.5-mow_wave)*0.040, 0.77, 0.91);
    SPECULAR = 0.16;
    vec2 n = vec2(sin(UV.x*1240.0+UV.y*47.0), cos(UV.y*1490.0+UV.x*39.0))*0.030;
    NORMAL_MAP = vec3(n*0.5+0.5,1.0);
    NORMAL_MAP_DEPTH = 0.14;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    material.set_shader_parameter("stripe_strength", stripe_strength)
    material.set_shader_parameter("micro_strength", micro_strength)
    return material

func _build_materials() -> void:
    _v157_stone_tex = load("res://v155_assets/stone_albedo.jpg")
    _v157_wood_tex = load("res://v155_assets/wood_albedo.jpg")

    # The target reference is a natural medium green, not fluorescent lime.
    mat_green = _v157_grass(Color("#376f36"), 0.030, 0.017)
    mat_fringe = _v157_grass(Color("#315f32"), 0.023, 0.021)
    mat_rough = _v157_grass(Color("#294f30"), 0.016, 0.026)

    mat_white = _v155_mat(Color("#f4f2e9"), 0.52)
    mat_dark = _v155_mat(Color("#0c1314"), 0.92)
    mat_red = _v155_mat(Color("#c91f2a"), 0.58)
    mat_house = _v157_tex(_v157_stone_tex, Color("#d5cdb9"), 0.91, Vector3(2.4,2.0,1.0))
    mat_stone = _v157_tex(_v157_stone_tex, Color("#b6ad99"), 0.94, Vector3(2.2,2.0,1.0))
    mat_roof = _v155_mat(Color("#1e272a"), 0.88)
    mat_window = _v155_mat(Color("#163741"), 0.23, 0.04)

    _v155_stone_light = _v157_tex(_v157_stone_tex, Color("#d9d0bd"), 0.92, Vector3(2.5,2.1,1.0))
    _v155_wood = _v157_tex(_v157_wood_tex, Color("#b17859"), 0.80, Vector3(2.0,1.0,1.0))
    _v155_wood_dark = _v157_tex(_v157_wood_tex, Color("#85543d"), 0.84, Vector3(2.0,1.0,1.0))
    _v155_glass = _v155_mat(Color("#173640"), 0.18, 0.07)
    _v155_trim = _v155_mat(Color("#eee9dc"), 0.66)
    _v155_bark = _v157_tex(_v157_wood_tex, Color("#6a4b36"), 0.95, Vector3(1.4,2.6,1.0))

    _v155_leaf_a = _v155_mat(Color("#285a31"), 0.93)
    _v155_leaf_b = _v155_mat(Color("#214b2a"), 0.95)
    _v155_leaf_c = _v155_mat(Color("#356b3b"), 0.91)
    _v155_hill_near = _v155_mat(Color("#3d6847"), 0.99)
    _v155_hill_far = _v155_mat(Color("#54745f"), 0.99)
    _v155_cloud = _v155_mat(Color("#f1f5f5"), 1.0)
    _v155_flower_red = _v155_mat(Color("#9f4239"), 0.91)
    _v155_flower_yellow = _v155_mat(Color("#c7a44a"), 0.91)

    _v155_guide = _v155_mat(Color(0.75,0.055,0.050,0.36), 1.0)
    _v155_guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _v155_guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V157Real3DEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#438fc1")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#dce7e8")
    env.ambient_light_energy = 0.52
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 0.96
    env.adjustment_contrast = 1.10
    env.adjustment_saturation = 0.98
    env.fog_enabled = false
    env_node.environment = env
    add_child(env_node)

    _v157_build_sky()

    var sun := DirectionalLight3D.new()
    sun.name = "V157KeySun"
    sun.light_color = Color("#fff0d4")
    sun.light_energy = 0.93
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-48.0,-31.0,0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.5
    camera.near = 0.016
    camera.far = 150.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _v157_build_sky() -> void:
    _v157_sky = MeshInstance3D.new()
    _v157_sky.name = "V157BlueSky"
    var mesh := QuadMesh.new()
    mesh.size = Vector2(150.0,68.0)
    _v157_sky.mesh = mesh
    _v157_sky.position = Vector3(0.0,17.0,-72.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded,cull_disabled;
void fragment(){
    vec3 top = vec3(0.075,0.36,0.65);
    vec3 bottom = vec3(0.45,0.72,0.85);
    float y = smoothstep(0.02,0.96,UV.y);
    vec3 col = mix(top,bottom,y);
    float glow = 1.0-smoothstep(0.0,0.28,distance(UV,vec2(0.74,0.18)));
    col += vec3(0.13,0.11,0.07)*glow;
    ALBEDO=col;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    _v157_sky.material_override = material
    add_child(_v157_sky)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "PuttingLabClubhouse3D"
    house.position = local_pos
    house.rotation_degrees.y = -4.0
    horizon_root.add_child(house)

    _v155_shadow(house, Vector3(0.10,0.006,0.45), Vector2(7.5,2.8), 6.0, 0.18)
    _v155_box(house, Vector3(7.15,0.09,2.65), Vector3(0.10,0.045,0.0), _v155_stone_light)

    # Stone wing and warm timber/glass pavilion.
    _v155_box(house, Vector3(2.15,1.58,2.15), Vector3(-2.05,0.84,0.0), mat_stone)
    _v155_box(house, Vector3(4.55,1.38,2.13), Vector3(1.12,0.74,0.0), _v155_wood_dark)
    _v155_box(house, Vector3(7.40,0.15,2.82), Vector3(0.05,1.82,0.0), mat_roof, Vector3(0.0,0.0,-7.0))
    _v155_box(house, Vector3(0.14,1.66,2.16), Vector3(3.37,0.88,0.0), mat_roof)

    # Camera sits on +Z. Put the detailed facade on the visible +Z face.
    var front_z := 1.086
    for i in range(6):
        var x := -0.35 + float(i)*0.61
        _v155_box(house, Vector3(0.53,0.91,0.030), Vector3(x,0.78,front_z), _v155_glass)
        _v155_box(house, Vector3(0.034,1.03,0.045), Vector3(x+0.30,0.78,front_z+0.020), _v155_trim)
    _v155_box(house, Vector3(3.75,0.035,0.045), Vector3(1.18,1.28,front_z+0.020), _v155_trim)

    for i in range(8):
        var x := 0.35 + float(i)*0.38
        _v155_box(house, Vector3(0.075,1.31,0.055), Vector3(x,0.74,front_z+0.050), _v155_wood)

    # Actual 3D sign instead of image text baked into a background.
    _v155_box(house, Vector3(1.60,0.80,0.055), Vector3(-2.05,0.94,front_z+0.045), _v155_stone_light)
    var sign := Label3D.new()
    sign.name = "PuttingLabSign"
    sign.text = "PUTT VISION\nPUTTING LAB"
    sign.font_size = 72
    sign.pixel_size = 0.0030
    sign.modulate = Color("#27302f")
    sign.outline_size = 0
    sign.position = Vector3(-2.05,0.94,front_z+0.082)
    sign.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sign.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    house.add_child(sign)

    _v155_box(house, Vector3(4.95,0.07,0.96), Vector3(0.90,0.09,1.48), _v155_wood)
    for x in [-1.05,0.25,1.55,2.85]:
        _v155_box(house, Vector3(0.065,1.14,0.065), Vector3(float(x),0.62,1.43), _v155_trim)

    # Low shrub/flower bed like the target clubhouse frontage.
    for x in [-2.95,-2.58,-1.50,-1.08,-0.20,0.35,0.92,1.48,2.05,2.63]:
        _v155_blob(house, Vector3(float(x),0.21,1.60), Vector3(0.30,0.22,0.28), _v155_leaf_b, 20,10)
    for x in [-2.68,-1.32,0.18,1.22,2.36]:
        _v155_blob(house, Vector3(float(x),0.16,1.81), Vector3(0.12,0.09,0.12), _v155_flower_red, 16,8)
    for x in [-2.38,-0.93,0.63,1.69,2.72]:
        _v155_blob(house, Vector3(float(x),0.15,1.80), Vector3(0.10,0.08,0.10), _v155_flower_yellow, 16,8)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.name = "MatureTree3D"
    tree.position = pos
    horizon_root.add_child(tree)

    _v155_shadow(tree, Vector3(0.42*scale_value,0.006,0.35*scale_value), Vector2(2.0,0.68)*scale_value, -28.0, 0.12)
    _v155_cylinder(tree, 0.088*scale_value, 1.45*scale_value, Vector3(0.0,0.725*scale_value,0.0), _v155_bark, 20)

    # Visible branches plus many small crown clusters remove the old lollipop/blob silhouette.
    for branch in [[Vector3(-0.18,1.12,0.00),-25.0],[Vector3(0.17,1.24,0.02),24.0],[Vector3(0.00,1.35,-0.12),6.0]]:
        var b := _v155_cylinder(tree, 0.035*scale_value, 0.62*scale_value, branch[0]*scale_value, _v155_bark, 14)
        b.rotation_degrees.z = float(branch[1])

    var canopy := [
        [Vector3(-0.50,1.52,0.08),Vector3(0.38,0.48,0.34),_v155_leaf_b],
        [Vector3(-0.18,1.58,0.26),Vector3(0.42,0.50,0.36),_v155_leaf_a],
        [Vector3(0.22,1.55,0.22),Vector3(0.44,0.52,0.37),_v155_leaf_c],
        [Vector3(0.52,1.62,0.02),Vector3(0.37,0.46,0.33),_v155_leaf_b],
        [Vector3(-0.58,1.85,-0.06),Vector3(0.38,0.46,0.34),_v155_leaf_a],
        [Vector3(-0.26,1.93,-0.18),Vector3(0.44,0.53,0.37),_v155_leaf_b],
        [Vector3(0.12,1.92,0.04),Vector3(0.48,0.56,0.39),_v155_leaf_c],
        [Vector3(0.48,1.92,-0.12),Vector3(0.40,0.49,0.35),_v155_leaf_a],
        [Vector3(-0.35,2.20,0.05),Vector3(0.38,0.45,0.33),_v155_leaf_c],
        [Vector3(0.02,2.28,-0.04),Vector3(0.43,0.49,0.34),_v155_leaf_a],
        [Vector3(0.36,2.18,0.06),Vector3(0.36,0.43,0.31),_v155_leaf_b],
        [Vector3(-0.08,2.48,0.02),Vector3(0.34,0.39,0.29),_v155_leaf_c]
    ]
    for item in canopy:
        _v155_blob(tree, item[0]*scale_value, item[1]*scale_value, item[2], 28,14)
