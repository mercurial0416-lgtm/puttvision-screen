extends "res://v155_modeled_safe.gd"

# V158: reference-matched live 3D. No full-scene photo card.
# Build-time CC0 turf/tree assets are mapped onto real course/tree meshes; HDRI, Sprite3D,
# anisotropic sampling and dynamic shadows remain disabled for the proven mobile-safe path.

var _turf_a: Texture2D
var _turf_n: Texture2D
var _turf_r: Texture2D
var _tree_a: Texture2D
var _tree_b: Texture2D
var _tree_mat_a: StandardMaterial3D
var _tree_mat_b: StandardMaterial3D

func _tree_mat(tex: Texture2D) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_texture = tex
    m.albedo_color = Color(0.95, 0.97, 0.93, 1.0)
    m.roughness = 0.93
    m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA_SCISSOR
    m.alpha_scissor_threshold = 0.28
    m.cull_mode = BaseMaterial3D.CULL_DISABLED
    return m

func _turf_mat(tint: Color, stripes: float) -> ShaderMaterial:
    var s := Shader.new()
    s.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform sampler2D a : source_color, repeat_enable, filter_linear_mipmap;
uniform sampler2D n : hint_normal, repeat_enable, filter_linear_mipmap;
uniform sampler2D r : repeat_enable, filter_linear_mipmap;
uniform vec3 tint : source_color;
uniform float stripes = 0.05;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
float h(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void vertex(){VERTEX.y += VERTEX.x*side_slope + (-VERTEX.z)*long_slope;}
void fragment(){
  vec2 u=vec2(UV.x*13.0,UV.y*38.0);
  float lum=dot(texture(a,u).rgb,vec3(.299,.587,.114));
  float mow=.5+.5*sin(UV.y*34.0*6.2831853);
  float band=mix(1.0-stripes,1.0+stripes,smoothstep(.18,.82,mow));
  float grain=(h(floor(UV*vec2(1800.0,4200.0)))-.5)*.018;
  ALBEDO=tint*(band+(lum-.5)*.20+grain);
  ROUGHNESS=clamp(.77+texture(r,u).r*.15+(.5-mow)*.025,.74,.92);
  SPECULAR=.17;
  NORMAL_MAP=texture(n,u).xyz;
  NORMAL_MAP_DEPTH=.20;
}
"""
    var m := ShaderMaterial.new()
    m.shader = s
    m.set_shader_parameter("a", _turf_a)
    m.set_shader_parameter("n", _turf_n)
    m.set_shader_parameter("r", _turf_r)
    m.set_shader_parameter("tint", Vector3(tint.r,tint.g,tint.b))
    m.set_shader_parameter("stripes", stripes)
    return m

func _build_materials() -> void:
    _turf_a = load("res://v143_assets/turf/albedo.png")
    _turf_n = load("res://v143_assets/turf/normal.png")
    _turf_r = load("res://v143_assets/turf/roughness.png")
    _tree_a = load("res://v143_assets/trees/tree_small_02.png")
    _tree_b = load("res://v143_assets/trees/island_tree_03.png")

    mat_green = _turf_mat(Color("#506a34"), 0.046)
    mat_fringe = _turf_mat(Color("#486130"), 0.031)
    mat_rough = _turf_mat(Color("#3f5631"), 0.016)
    mat_white = _v155_mat(Color("#f4f2e9"),0.54)
    mat_dark = _v155_mat(Color("#090d0d"),0.95)
    mat_red = _v155_mat(Color("#cd202b"),0.58)
    mat_house = _v155_mat(Color("#d8d3c7"),0.90)
    mat_stone = _v155_mat(Color("#b8ae99"),0.95)
    mat_roof = _v155_mat(Color("#202628"),0.88)
    mat_window = _v155_mat(Color("#193642"),0.24,0.04)

    _v155_stone_light = _v155_mat(Color("#d4cab6"),0.95)
    _v155_wood = _v155_mat(Color("#996247"),0.84)
    _v155_wood_dark = _v155_mat(Color("#70432f"),0.88)
    _v155_glass = _v155_mat(Color("#173743"),0.19,0.06)
    _v155_trim = _v155_mat(Color("#eee9da"),0.66)
    _v155_bark = _v155_mat(Color("#5b4432"),0.96)
    _v155_leaf_a = _v155_mat(Color("#365c35"),0.94)
    _v155_leaf_b = _v155_mat(Color("#2d512f"),0.95)
    _v155_leaf_c = _v155_mat(Color("#426d3d"),0.93)
    _v155_hill_near = _v155_mat(Color("#526e46"),0.99)
    _v155_hill_far = _v155_mat(Color("#647a59"),0.99)
    _v155_cloud = _v155_mat(Color("#f3f5f4"),1.0)
    _v155_flower_red = _v155_mat(Color("#8b4039"),0.94)
    _v155_flower_yellow = _v155_mat(Color("#ae9152"),0.94)
    _v155_guide = _v155_mat(Color(0.76,0.055,0.05,0.32),1.0)
    _v155_guide.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _v155_guide.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    _tree_mat_a = _tree_mat(_tree_a)
    _tree_mat_b = _tree_mat(_tree_b)

func _build_environment() -> void:
    var env_node := WorldEnvironment.new()
    env_node.name = "V158NaturalEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("#4b9bd0")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("#dce7e2")
    env.ambient_light_energy = 0.43
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.adjustment_enabled = true
    env.adjustment_brightness = 0.93
    env.adjustment_contrast = 1.08
    env.adjustment_saturation = 0.92
    env.fog_enabled = false
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.name = "V158KeySun"
    sun.light_color = Color("#fff1d9")
    sun.light_energy = 0.86
    sun.shadow_enabled = false
    sun.rotation_degrees = Vector3(-46.0,-31.0,0.0)
    add_child(sun)

    camera = Camera3D.new()
    camera.name = "PuttingBroadcastCamera"
    camera.fov = 42.0
    camera.near = 0.016
    camera.far = 145.0
    add_child(camera)
    camera.position = camera_pos
    camera.look_at(camera_look, Vector3.UP)

func _v155_build_cloud(_pos: Vector3, _scale_value: float) -> void:
    pass

func _v155_build_hills() -> void:
    _v155_blob(horizon_root,Vector3(-11.5,-0.8,-12.8),Vector3(13.5,3.0,5.2),_v155_hill_far,48,24)
    _v155_blob(horizon_root,Vector3(11.0,-0.9,-13.2),Vector3(14.5,3.2,5.5),_v155_hill_far,48,24)
    _v155_blob(horizon_root,Vector3(-8.8,-0.75,-9.1),Vector3(9.5,2.2,3.9),_v155_hill_near,48,24)
    _v155_blob(horizon_root,Vector3(9.2,-0.8,-9.5),Vector3(10.2,2.3,4.0),_v155_hill_near,48,24)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var root := Node3D.new()
    root.name = "PhotorealTreeMesh"
    root.position = pos
    horizon_root.add_child(root)
    var height := 2.75 * scale_value
    _v155_shadow(root,Vector3(0.18*scale_value,0.006,0.30*scale_value),Vector2(2.2,0.70)*scale_value,-22.0,0.14)

    var q := MeshInstance3D.new()
    var mesh := QuadMesh.new()
    mesh.size = Vector2(height*0.70,height)
    q.mesh = mesh
    q.material_override = _tree_mat_a if int(abs(pos.x)*10.0)%2 == 0 else _tree_mat_b
    q.position = Vector3(0.0,height*0.50,0.0)
    root.add_child(q)

    var side := MeshInstance3D.new()
    var mesh2 := QuadMesh.new()
    mesh2.size = Vector2(height*0.64,height*0.98)
    side.mesh = mesh2
    side.material_override = q.material_override
    side.position = Vector3(0.0,height*0.49,-0.035)
    side.rotation_degrees.y = 15.0
    root.add_child(side)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    var house := Node3D.new()
    house.name = "ReferencePuttingLabClubhouse3D"
    house.position = local_pos
    house.rotation_degrees.y = -3.0
    horizon_root.add_child(house)
    _v155_shadow(house,Vector3(0.10,0.006,0.40),Vector2(7.8,3.0),5.0,0.20)

    _v155_box(house,Vector3(2.30,1.72,2.10),Vector3(-2.08,0.86,0.0),mat_stone)
    _v155_box(house,Vector3(4.72,1.38,2.08),Vector3(1.23,0.70,0.0),_v155_wood_dark)
    _v155_box(house,Vector3(7.55,0.14,2.86),Vector3(0.05,1.84,0.0),mat_roof,Vector3(0.0,0.0,-6.8))
    _v155_box(house,Vector3(0.13,1.72,2.12),Vector3(3.51,0.87,0.0),mat_roof)

    var z := 1.068
    var joint := _v155_mat(Color("#8c8578"),0.97)
    for yy in [0.34,0.68,1.02,1.36]:
        _v155_box(house,Vector3(2.22,0.016,0.022),Vector3(-2.08,float(yy),z+0.003),joint)
    for xx in [-2.72,-2.10,-1.48]:
        _v155_box(house,Vector3(0.016,1.58,0.022),Vector3(float(xx),0.84,z+0.003),joint)

    for i in range(7):
        var x := -0.18 + float(i)*0.54
        _v155_box(house,Vector3(0.46,0.90,0.032),Vector3(x,0.77,z),_v155_glass)
        _v155_box(house,Vector3(0.030,1.02,0.043),Vector3(x+0.255,0.77,z+0.020),_v155_trim)
    _v155_box(house,Vector3(4.05,0.034,0.045),Vector3(1.34,1.27,z+0.020),_v155_trim)
    for i in range(11):
        _v155_box(house,Vector3(0.052,1.30,0.058),Vector3(0.10+float(i)*0.31,0.72,z+0.048),_v155_wood)

    _v155_box(house,Vector3(1.62,0.82,0.055),Vector3(-2.08,0.96,z+0.045),_v155_stone_light)
    var sign := Label3D.new()
    sign.text = "PUTT VISION\nPUTTING LAB"
    sign.font_size = 68
    sign.pixel_size = 0.00285
    sign.modulate = Color("#3b413e")
    sign.outline_size = 0
    sign.position = Vector3(-2.08,0.96,z+0.083)
    sign.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sign.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    house.add_child(sign)

    _v155_box(house,Vector3(5.10,0.065,0.96),Vector3(0.94,0.085,1.48),_v155_wood)
    var hedge := _v155_mat(Color("#315334"),0.96)
    for x in [-2.90,-2.52,-1.55,-1.05,-0.38,0.17,0.70,1.22,1.78,2.34,2.88]:
        _v155_blob(house,Vector3(float(x),0.19,1.60),Vector3(0.36,0.18,0.25),hedge,20,10)
