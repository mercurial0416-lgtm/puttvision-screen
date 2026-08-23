extends "res://v161_surface_detail.gd"

# Final V161 composition pass: softer mow contrast, richer sky depth and small architectural
# reflections. All additions are opaque/procedural and remain on the mobile-safe renderer path.

func _build_materials() -> void:
    super._build_materials()
    if mat_green is ShaderMaterial:
        mat_green.set_shader_parameter("lane_strength", 0.024)
        mat_green.set_shader_parameter("normal_depth", 0.066)
        mat_green.set_shader_parameter("roughness_base", 0.825)
    if mat_fringe is ShaderMaterial:
        mat_fringe.set_shader_parameter("lane_strength", 0.016)
    if mat_rough is ShaderMaterial:
        mat_rough.set_shader_parameter("lane_strength", 0.008)

    # Brighter cool glass reads as actual glazing instead of black openings.
    mat_window.albedo_color = Color("#315663")
    mat_window.roughness = 0.18
    mat_window.metallic = 0.07

func _build_environment() -> void:
    super._build_environment()
    _v161_build_cinematic_sky_backdrop()

func _v161_build_cinematic_sky_backdrop() -> void:
    # A distant procedural quad makes the vertical sky gradient visible in the broadcast camera.
    # It sits behind all gameplay geometry and never writes depth.
    var backdrop := MeshInstance3D.new()
    backdrop.name = "V161CinematicSkyBackdrop"
    var quad := QuadMesh.new()
    quad.size = Vector2(160.0, 24.0)
    backdrop.mesh = quad
    backdrop.position = Vector3(0.0, 9.0, -65.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
float hash21(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise21(vec2 p){
    vec2 i=floor(p);vec2 f=fract(p);f=f*f*(3.0-2.0*f);
    float a=hash21(i),b=hash21(i+vec2(1.0,0.0)),c=hash21(i+vec2(0.0,1.0)),d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    float y=clamp(UV.y,0.0,1.0);
    vec3 horizon=vec3(0.67,0.79,0.83);
    vec3 upper=vec3(0.28,0.57,0.75);
    vec3 col=mix(horizon,upper,smoothstep(0.08,0.92,y));

    // Warm atmospheric glow rather than a hard cartoon sun disc.
    float glow=1.0-smoothstep(0.03,0.34,length(UV-vec2(0.72,0.76)));
    col+=vec3(0.13,0.095,0.050)*glow*0.34;

    // Very faint high-altitude variation breaks the perfectly flat digital sky.
    float n=noise21(UV*vec2(7.0,3.0)+vec2(3.1,8.2));
    float wisps=smoothstep(0.66,0.86,n)*smoothstep(0.44,0.94,y);
    col=mix(col,vec3(0.82,0.87,0.87),wisps*0.075);
    ALBEDO=col;
}
"""
    var mat := ShaderMaterial.new()
    mat.shader = shader
    backdrop.material_override = mat
    add_child(backdrop)

func _v155_build_clubhouse(local_pos: Vector3) -> void:
    super._v155_build_clubhouse(local_pos)
    var house := horizon_root.get_node_or_null("ReferencePuttingLabClubhouse3D") as Node3D
    if house == null:
        return

    # Thin cool highlights emulate reflected sky on the glazing without transparency/refraction.
    var glass_glint := _v155_mat(Color("#789aa0"), 0.22, 0.10)
    for i in range(7):
        var x := -0.18 + float(i) * 0.54
        _v155_box(house, Vector3(0.34, 0.018, 0.010), Vector3(x, 1.08, 1.088), glass_glint)

    # Roof edge and terrace nosing catch just enough light to stop silhouette flattening.
    var edge := _v155_mat(Color("#777c78"), 0.60, 0.08)
    _v155_box(house, Vector3(7.60, 0.026, 0.030), Vector3(0.05, 1.80, 1.36), edge)
    _v155_box(house, Vector3(5.05, 0.025, 0.032), Vector3(0.94, 0.095, 2.15), _v155_stone_light)
