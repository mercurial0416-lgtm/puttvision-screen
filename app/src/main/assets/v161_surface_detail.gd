extends "res://v161_premium_environment.gd"

# V161 surface/detail polish. Keep inherited typed StandardMaterial3D fields untouched;
# custom ShaderMaterials are used directly by the V161 tree geometry instead.

var _detail_leaf_a: ShaderMaterial
var _detail_leaf_b: ShaderMaterial
var _detail_leaf_c: ShaderMaterial
var _detail_bark: ShaderMaterial

func _v161_leaf_material(base: Color, light: Color, seed: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_color : source_color;
uniform vec3 light_color : source_color;
uniform float seed = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p,vec2(41.37,289.11))+seed)*43758.5453); }
float noise21(vec2 p){
    vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
    float a=hash21(i); float b=hash21(i+vec2(1.0,0.0));
    float c=hash21(i+vec2(0.0,1.0)); float d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    vec2 p=UV*vec2(42.0,29.0);
    float large=noise21(p*0.48);
    float mid=noise21(p*1.85+vec2(5.2,2.8));
    float fine=noise21(p*5.4+vec2(2.7,8.9));
    float cluster=smoothstep(0.16,0.88,large*0.58+mid*0.29+fine*0.13);
    float speck=(hash21(floor(p*7.0))-0.5)*0.060;
    vec3 col=mix(base_color,light_color,cluster);
    col*=0.96+speck;
    ALBEDO=max(col,vec3(0.0));
    ROUGHNESS=clamp(0.91+(0.5-fine)*0.050,0.82,0.97);
    SPECULAR=0.065;
}
"""
    var m := ShaderMaterial.new()
    m.shader = shader
    m.set_shader_parameter("base_color", Vector3(base.r,base.g,base.b))
    m.set_shader_parameter("light_color", Vector3(light.r,light.g,light.b))
    m.set_shader_parameter("seed", seed)
    return m

func _v161_bark_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
float hash21(vec2 p){return fract(sin(dot(p,vec2(17.7,91.3)))*43758.5453);}
float noise21(vec2 p){
    vec2 i=floor(p);vec2 f=fract(p);f=f*f*(3.0-2.0*f);
    float a=hash21(i),b=hash21(i+vec2(1.0,0.0)),c=hash21(i+vec2(0.0,1.0)),d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    float warp=(noise21(UV*vec2(5.0,13.0))-0.5)*5.0;
    float ridge=0.5+0.5*sin(UV.x*118.0+warp+sin(UV.y*47.0)*0.9);
    float knots=noise21(UV*vec2(21.0,34.0));
    vec3 dark=vec3(0.17,0.105,0.063);
    vec3 light=vec3(0.34,0.235,0.145);
    ALBEDO=mix(dark,light,ridge*0.48+knots*0.38);
    ROUGHNESS=0.95;
    SPECULAR=0.05;
}
"""
    var m := ShaderMaterial.new()
    m.shader = shader
    return m

func _build_materials() -> void:
    super._build_materials()
    _detail_leaf_a = _v161_leaf_material(Color("#24442a"), Color("#58734b"), 2.0)
    _detail_leaf_b = _v161_leaf_material(Color("#1c3823"), Color("#48633e"), 7.0)
    _detail_leaf_c = _v161_leaf_material(Color("#2d4d2f"), Color("#688356"), 13.0)
    _detail_bark = _v161_bark_material()

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.name = "V161DetailedMatureTree3D"
    tree.position = pos
    tree.rotation_degrees.y = fmod(abs(pos.x * 41.0 + pos.z * 17.0), 360.0)
    horizon_root.add_child(tree)

    var s := scale_value
    _v155_shadow(tree, Vector3(0.38 * s, 0.006, 0.34 * s), Vector2(2.45, 0.82) * s, -26.0, 0.14)
    _v155_cylinder(tree, 0.095 * s, 1.68 * s, Vector3(0.0, 0.84 * s, 0.0), _detail_bark, 22)

    var b1 := _v155_cylinder(tree, 0.035 * s, 0.92 * s, Vector3(-0.18, 1.30, 0.00) * s, _detail_bark, 14)
    b1.rotation_degrees = Vector3(18.0, 12.0, -34.0)
    var b2 := _v155_cylinder(tree, 0.033 * s, 0.86 * s, Vector3(0.19, 1.36, 0.02) * s, _detail_bark, 14)
    b2.rotation_degrees = Vector3(-12.0, 31.0, 37.0)
    var b3 := _v155_cylinder(tree, 0.027 * s, 0.66 * s, Vector3(0.02, 1.57, -0.13) * s, _detail_bark, 12)
    b3.rotation_degrees = Vector3(39.0, -18.0, 8.0)

    var canopy := [
        [Vector3(-0.63,1.60, 0.05), Vector3(0.47,0.41,0.39), _detail_leaf_b],
        [Vector3(-0.35,1.76, 0.30), Vector3(0.50,0.43,0.40), _detail_leaf_a],
        [Vector3( 0.02,1.62, 0.40), Vector3(0.50,0.42,0.40), _detail_leaf_c],
        [Vector3( 0.43,1.69, 0.26), Vector3(0.52,0.45,0.42), _detail_leaf_b],
        [Vector3( 0.66,1.84,-0.02), Vector3(0.46,0.41,0.38), _detail_leaf_a],
        [Vector3( 0.48,1.73,-0.34), Vector3(0.45,0.40,0.38), _detail_leaf_c],
        [Vector3( 0.04,1.70,-0.43), Vector3(0.54,0.44,0.40), _detail_leaf_b],
        [Vector3(-0.43,1.78,-0.31), Vector3(0.48,0.41,0.39), _detail_leaf_a],
        [Vector3(-0.66,1.94,-0.09), Vector3(0.43,0.39,0.36), _detail_leaf_c],
        [Vector3(-0.36,2.08, 0.19), Vector3(0.53,0.46,0.42), _detail_leaf_b],
        [Vector3( 0.03,2.02, 0.28), Vector3(0.58,0.49,0.44), _detail_leaf_a],
        [Vector3( 0.40,2.09, 0.13), Vector3(0.51,0.45,0.41), _detail_leaf_c],
        [Vector3( 0.50,2.16,-0.19), Vector3(0.45,0.40,0.37), _detail_leaf_b],
        [Vector3( 0.08,2.21,-0.31), Vector3(0.51,0.44,0.40), _detail_leaf_a],
        [Vector3(-0.38,2.22,-0.20), Vector3(0.46,0.41,0.37), _detail_leaf_c],
        [Vector3(-0.24,2.42, 0.04), Vector3(0.48,0.42,0.39), _detail_leaf_b],
        [Vector3( 0.18,2.48, 0.02), Vector3(0.44,0.39,0.36), _detail_leaf_a],
        [Vector3( 0.00,2.66,-0.02), Vector3(0.34,0.31,0.29), _detail_leaf_c]
    ]
    for item in canopy:
        _v155_blob(tree, item[0] * s, item[1] * s, item[2], 22, 11)

func _build_environment() -> void:
    super._build_environment()
    var rim := DirectionalLight3D.new()
    rim.name = "V161WarmRim"
    rim.light_color = Color("#f5d5b0")
    rim.light_energy = 0.12
    rim.shadow_enabled = false
    rim.rotation_degrees = Vector3(-22.0, 118.0, 0.0)
    add_child(rim)
