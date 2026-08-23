extends "res://v161_premium_environment.gd"

# V161 surface/detail polish. Opaque procedural materials only: no alpha foliage cards,
# no HDRI, no dynamic shadows, no extra texture fetches. This mainly removes the toy-like
# flat colors from tree canopies, trunks, timber, stone and roof geometry.

func _v161_noise_material(base: Color, alt: Color, scale_value: float, roughness_value: float, seed: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_color : source_color;
uniform vec3 alt_color : source_color;
uniform float scale_value = 24.0;
uniform float roughness_value = 0.90;
uniform float seed = 0.0;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7)) + seed) * 43758.5453123); }
float noise21(vec2 p){
    vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
    float a=hash21(i); float b=hash21(i+vec2(1.0,0.0));
    float c=hash21(i+vec2(0.0,1.0)); float d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    vec2 p=UV*scale_value;
    float n=noise21(p)*0.62 + noise21(p*2.87+vec2(7.1,3.4))*0.27 + noise21(p*7.2)*0.11;
    float fleck=(hash21(floor(p*5.0))-0.5)*0.05;
    vec3 col=mix(base_color,alt_color,smoothstep(0.22,0.82,n));
    col*=1.0+fleck;
    ALBEDO=max(col,vec3(0.0));
    ROUGHNESS=clamp(roughness_value+(0.5-n)*0.055,0.55,0.98);
    SPECULAR=0.10;
}
"""
    var m := ShaderMaterial.new()
    m.shader = shader
    m.set_shader_parameter("base_color", Vector3(base.r, base.g, base.b))
    m.set_shader_parameter("alt_color", Vector3(alt.r, alt.g, alt.b))
    m.set_shader_parameter("scale_value", scale_value)
    m.set_shader_parameter("roughness_value", roughness_value)
    m.set_shader_parameter("seed", seed)
    return m

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
    vec2 p=UV*vec2(38.0,25.0);
    float large=noise21(p*0.55);
    float fine=noise21(p*3.1+vec2(2.7,8.9));
    float cluster=smoothstep(0.18,0.86,large*0.72+fine*0.28);
    float tiny=(hash21(floor(p*7.0))-0.5)*0.065;
    vec3 col=mix(base_color,light_color,cluster);
    col*=0.96+tiny;
    ALBEDO=max(col,vec3(0.0));
    ROUGHNESS=0.91+(0.5-fine)*0.045;
    SPECULAR=0.07;
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
    float a=hash21(i),b=hash21(i+vec2(1,0)),c=hash21(i+vec2(0,1)),d=hash21(i+vec2(1,1));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    float warp=(noise21(UV*vec2(5.0,13.0))-0.5)*5.0;
    float ridge=0.5+0.5*sin(UV.x*118.0+warp+sin(UV.y*47.0)*0.9);
    float knots=noise21(UV*vec2(21.0,34.0));
    vec3 dark=vec3(0.17,0.105,0.063);
    vec3 light=vec3(0.34,0.235,0.145);
    vec3 col=mix(dark,light,ridge*0.48+knots*0.38);
    ALBEDO=col;
    ROUGHNESS=0.95;
    SPECULAR=0.05;
}
"""
    var m := ShaderMaterial.new()
    m.shader = shader
    return m

func _build_materials() -> void:
    super._build_materials()

    _v155_leaf_a = _v161_leaf_material(Color("#26472b"), Color("#506f45"), 2.0)
    _v155_leaf_b = _v161_leaf_material(Color("#1f3d26"), Color("#405f39"), 7.0)
    _v155_leaf_c = _v161_leaf_material(Color("#315331"), Color("#5d7d50"), 13.0)
    _v155_bark = _v161_bark_material()

    _v155_wood = _v161_noise_material(Color("#75472f"), Color("#a06b47"), 34.0, 0.84, 4.0)
    _v155_wood_dark = _v161_noise_material(Color("#4c2f22"), Color("#704733"), 28.0, 0.88, 8.0)
    mat_stone = _v161_noise_material(Color("#847d70"), Color("#aaa08d"), 16.0, 0.95, 12.0)
    _v155_stone_light = _v161_noise_material(Color("#aaa18f"), Color("#c8bfad"), 18.0, 0.94, 17.0)
    mat_roof = _v161_noise_material(Color("#171e20"), Color("#2d3535"), 48.0, 0.90, 20.0)

func _build_environment() -> void:
    super._build_environment()

    # Very cheap shadowless side key. It separates branch/trunk silhouettes and facade depth
    # without returning to dynamic shadow maps.
    var rim := DirectionalLight3D.new()
    rim.name = "V161WarmRim"
    rim.light_color = Color("#f5d5b0")
    rim.light_energy = 0.12
    rim.shadow_enabled = false
    rim.rotation_degrees = Vector3(-22.0, 118.0, 0.0)
    add_child(rim)
