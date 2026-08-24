extends "res://v161_cinematic_final.gd"

# V162: ultra-real putting presentation on the proven Android-safe renderer path.
# Physics remains authoritative in Android V135-V137. This layer only improves the live Godot view.
# Keep the crash-prone features disabled: no HDRI, dynamic shadow maps, alpha-card foliage,
# anisotropic filtering or external 3D models.

var _v162_ball_shadow: MeshInstance3D
var _v162_ball_shadow_mat: ShaderMaterial

func _build_materials() -> void:
    super._build_materials()

    # Tight bentgrass should read through micro-normal and fibre breakup, not arcade mowing stripes.
    if mat_green is ShaderMaterial:
        mat_green.set_shader_parameter("base_color", Vector3(0.175, 0.300, 0.128))
        mat_green.set_shader_parameter("lane_strength", 0.0025)
        mat_green.set_shader_parameter("texture_scale", 148.0)
        mat_green.set_shader_parameter("normal_depth", 0.078)
        mat_green.set_shader_parameter("roughness_base", 0.850)
        mat_green.set_shader_parameter("micro_strength", 0.024)
    if mat_fringe is ShaderMaterial:
        mat_fringe.set_shader_parameter("base_color", Vector3(0.145, 0.260, 0.125))
        mat_fringe.set_shader_parameter("lane_strength", 0.0020)
        mat_fringe.set_shader_parameter("texture_scale", 82.0)
        mat_fringe.set_shader_parameter("normal_depth", 0.110)
        mat_fringe.set_shader_parameter("roughness_base", 0.885)
        mat_fringe.set_shader_parameter("micro_strength", 0.030)
    if mat_rough is ShaderMaterial:
        mat_rough.set_shader_parameter("base_color", Vector3(0.105, 0.210, 0.110))
        mat_rough.set_shader_parameter("lane_strength", 0.0010)
        mat_rough.set_shader_parameter("texture_scale", 42.0)
        mat_rough.set_shader_parameter("normal_depth", 0.155)
        mat_rough.set_shader_parameter("roughness_base", 0.920)
        mat_rough.set_shader_parameter("micro_strength", 0.038)

    if _v155_guide != null:
        _v155_guide.albedo_color = Color(0.70, 0.045, 0.040, 0.065)

    _detail_leaf_a = _v161_leaf_material(Color("#293f2b"), Color("#5d6b50"), 2.0)
    _detail_leaf_b = _v161_leaf_material(Color("#213626"), Color("#4e5d44"), 7.0)
    _detail_leaf_c = _v161_leaf_material(Color("#324a35"), Color("#6d795f"), 13.0)

func _build_environment() -> void:
    super._build_environment()

    var env_node := get_node_or_null("V160NaturalFinishEnvironment") as WorldEnvironment
    if env_node != null and env_node.environment != null:
        var env := env_node.environment
        env.adjustment_brightness = 0.890
        env.adjustment_contrast = 1.080
        env.adjustment_saturation = 0.855
        env.ambient_light_energy = 0.340

    var sun := get_node_or_null("V160NaturalSun") as DirectionalLight3D
    if sun != null:
        sun.light_energy = 0.91
        sun.light_color = Color("#fff1d9")

    var fill := get_node_or_null("V160NaturalFill") as DirectionalLight3D
    if fill != null:
        fill.light_energy = 0.145

    var rim := get_node_or_null("V161WarmRim") as DirectionalLight3D
    if rim != null:
        rim.light_energy = 0.070

    _v162_build_clean_sky()

func _v162_build_clean_sky() -> void:
    var old_backdrop := get_node_or_null("V161CinematicSkyBackdrop") as MeshInstance3D
    if old_backdrop != null:
        old_backdrop.visible = false

    var backdrop := MeshInstance3D.new()
    backdrop.name = "V162NaturalSkyBackdrop"
    var quad := QuadMesh.new()
    quad.size = Vector2(170.0, 28.0)
    backdrop.mesh = quad
    backdrop.position = Vector3(0.0, 9.5, -64.0)

    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never;
float hash21(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise21(vec2 p){
    vec2 i=floor(p); vec2 f=fract(p); f=f*f*(3.0-2.0*f);
    float a=hash21(i), b=hash21(i+vec2(1.0,0.0));
    float c=hash21(i+vec2(0.0,1.0)), d=hash21(i+vec2(1.0,1.0));
    return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);
}
void fragment(){
    float y=clamp(1.0-UV.y,0.0,1.0);
    vec3 horizon=vec3(0.62,0.755,0.800);
    vec3 middle=vec3(0.275,0.535,0.720);
    vec3 upper=vec3(0.120,0.320,0.570);
    vec3 col=mix(horizon,middle,smoothstep(0.05,0.58,y));
    col=mix(col,upper,smoothstep(0.52,1.0,y));

    float warm=1.0-smoothstep(0.04,0.42,length((UV-vec2(0.70,0.47))*vec2(0.70,1.0)));
    col+=vec3(0.085,0.060,0.030)*warm*0.26;

    float n=noise21(UV*vec2(9.0,4.0)+vec2(4.3,1.7));
    float haze=smoothstep(0.62,0.84,n)*(1.0-smoothstep(0.55,0.94,y));
    col=mix(col,vec3(0.77,0.82,0.83),haze*0.045);
    ALBEDO=col;
}
"""
    var mat := ShaderMaterial.new()
    mat.shader = shader
    backdrop.material_override = mat
    add_child(backdrop)

func _v162_leaf_multimesh(parent: Node3D, material: Material, s: float, count: int, phase: float, y_base: float, radius: float) -> void:
    var leaf_mesh := SphereMesh.new()
    leaf_mesh.radius = 0.5
    leaf_mesh.height = 1.0
    leaf_mesh.radial_segments = 14
    leaf_mesh.rings = 7

    var mm := MultiMesh.new()
    mm.transform_format = MultiMesh.TRANSFORM_3D
    mm.mesh = leaf_mesh
    mm.instance_count = count

    for i in range(count):
        var fi: float = float(i)
        var angle: float = deg_to_rad(fmod(fi * 137.507 + phase * 43.0, 360.0))
        var spread: float = 0.40 + float((i * 7) % 11) * 0.056
        var x: float = cos(angle) * radius * spread
        var z: float = sin(angle) * radius * 0.77 * spread
        var y: float = y_base + sin(fi * 1.71 + phase) * 0.17 + float(i % 5) * 0.050
        var sx: float = 0.34 + float((i * 3) % 7) * 0.023
        var sy: float = 0.28 + float((i * 5) % 6) * 0.021
        var sz: float = 0.33 + float((i * 2) % 7) * 0.024
        var basis := Basis.IDENTITY
        basis = basis.rotated(Vector3.UP, angle * 0.37 + phase)
        basis = basis.rotated(Vector3.RIGHT, sin(fi * 0.93 + phase) * 0.12)
        basis = basis.scaled(Vector3(sx * s, sy * s, sz * s))
        mm.set_instance_transform(i, Transform3D(basis, Vector3(x, y, z) * s))

    var instance := MultiMeshInstance3D.new()
    instance.name = "V162LeafClusterMultiMesh"
    instance.multimesh = mm
    instance.material_override = material
    parent.add_child(instance)

func _v155_build_tree(pos: Vector3, scale_value: float) -> void:
    var tree := Node3D.new()
    tree.name = "V162NaturalMatureTree3D"
    tree.position = pos
    tree.rotation_degrees.y = fmod(abs(pos.x * 41.0 + pos.z * 17.0), 360.0)
    horizon_root.add_child(tree)

    var s: float = scale_value
    _v155_shadow(tree, Vector3(0.40 * s, 0.006, 0.35 * s), Vector2(2.65, 0.88) * s, -25.0, 0.13)
    _v155_cylinder(tree, 0.078 * s, 1.70 * s, Vector3(0.0, 0.85 * s, 0.0), _detail_bark, 18)

    var b1 := _v155_cylinder(tree, 0.029 * s, 0.90 * s, Vector3(-0.16, 1.28, 0.01) * s, _detail_bark, 12)
    b1.rotation_degrees = Vector3(18.0, 12.0, -35.0)
    var b2 := _v155_cylinder(tree, 0.028 * s, 0.84 * s, Vector3(0.17, 1.35, 0.02) * s, _detail_bark, 12)
    b2.rotation_degrees = Vector3(-13.0, 31.0, 38.0)
    var b3 := _v155_cylinder(tree, 0.023 * s, 0.65 * s, Vector3(0.02, 1.52, -0.13) * s, _detail_bark, 10)
    b3.rotation_degrees = Vector3(39.0, -18.0, 8.0)
    var b4 := _v155_cylinder(tree, 0.019 * s, 0.54 * s, Vector3(-0.05, 1.66, 0.06) * s, _detail_bark, 10)
    b4.rotation_degrees = Vector3(-21.0, 54.0, -19.0)
    var b5 := _v155_cylinder(tree, 0.018 * s, 0.50 * s, Vector3(0.08, 1.70, -0.04) * s, _detail_bark, 10)
    b5.rotation_degrees = Vector3(24.0, -42.0, 22.0)

    _v162_leaf_multimesh(tree, _detail_leaf_b, s, 26, 0.35, 1.66, 0.96)
    _v162_leaf_multimesh(tree, _detail_leaf_a, s, 24, 1.75, 1.96, 0.82)
    _v162_leaf_multimesh(tree, _detail_leaf_c, s, 20, 3.15, 2.24, 0.63)

func _build_horizon() -> void:
    super._build_horizon()

    # Auto-renamed repeated nodes carry numeric suffixes, so remove by prefix rather than equality.
    for child_node in horizon_root.get_children():
        if child_node is Node3D:
            var child := child_node as Node3D
            var node_name := String(child.name)
            if node_name.begins_with("V161VolumetricMeshCloud") or node_name.begins_with("V161DistantConifer"):
                child.visible = false

func _v162_ball_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
float hash21(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void fragment(){
    vec2 grid_uv=UV*vec2(54.0,28.0);
    float row=floor(grid_uv.y);
    grid_uv.x+=mod(row,2.0)*0.5;
    vec2 cell=fract(grid_uv)-vec2(0.5);
    cell.x*=0.82;
    float r=length(cell);
    float dimple=1.0-smoothstep(0.205,0.315,r);
    float bowl=dimple*smoothstep(0.315,0.055,r);
    float rim=dimple*(1.0-smoothstep(0.205,0.275,r))*smoothstep(0.105,0.205,r);
    float micro=(hash21(floor(UV*vec2(720.0,360.0)))-0.5)*0.010;
    vec3 ivory=vec3(0.930,0.925,0.895);
    ALBEDO=ivory*(1.0+micro-bowl*0.050+rim*0.018);
    vec2 dir=cell/max(r,0.0001);
    vec2 inward=-dir*dimple*(1.0-smoothstep(0.045,0.285,r))*0.70;
    vec3 dimple_normal=normalize(vec3(inward,1.0));
    NORMAL_MAP=dimple_normal*0.5+0.5;
    NORMAL_MAP_DEPTH=0.34;
    ROUGHNESS=clamp(0.265+bowl*0.085+micro*0.8,0.23,0.39);
    SPECULAR=0.34;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _v162_shadow_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
uniform float strength=0.24;
void fragment(){
    vec2 p=(UV-vec2(0.5))*vec2(2.0,2.0);
    float r=length(vec2(p.x,p.y*1.55));
    if(r>1.0){discard;}
    float alpha=1.0-smoothstep(0.08,1.0,r);
    alpha=alpha*alpha*strength;
    ALBEDO=vec3(0.010,0.016,0.008);
    ALPHA=alpha;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("strength", 0.24)
    return material

func _build_ball() -> void:
    super._build_ball()
    var smooth_ball := SphereMesh.new()
    smooth_ball.radius = BALL_RADIUS
    smooth_ball.height = BALL_RADIUS * 2.0
    smooth_ball.radial_segments = 64
    smooth_ball.rings = 40
    ball.mesh = smooth_ball
    ball.material_override = _v162_ball_material()

    _v162_ball_shadow = MeshInstance3D.new()
    _v162_ball_shadow.name = "V162BallContactShadow"
    var shadow_mesh := QuadMesh.new()
    shadow_mesh.size = Vector2(0.092, 0.064)
    _v162_ball_shadow.mesh = shadow_mesh
    _v162_ball_shadow.rotation_degrees.x = -90.0
    _v162_ball_shadow_mat = _v162_shadow_material()
    _v162_ball_shadow.material_override = _v162_ball_shadow_mat
    add_child(_v162_ball_shadow)

func _v162_cup_liner_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled;
void fragment(){
    vec2 p=(UV-vec2(0.5))*2.0;
    float r=length(p);
    if(r>0.965||r<0.690){discard;}
    float front=smoothstep(-0.78,0.72,p.y);
    float outer_edge=1.0-smoothstep(0.885,0.965,r);
    float inner_edge=smoothstep(0.690,0.765,r);
    vec3 shade=mix(vec3(0.55,0.56,0.53),vec3(0.90,0.90,0.84),front);
    shade*=0.84+0.16*outer_edge*inner_edge;
    ALBEDO=shade;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _build_target() -> void:
    super._build_target()
    var liner := MeshInstance3D.new()
    liner.name = "V162RecessedCupLinerLip"
    var liner_mesh := QuadMesh.new()
    liner_mesh.size = Vector2(0.116, 0.116)
    liner.mesh = liner_mesh
    liner.rotation_degrees.x = -90.0
    liner.position.y = 0.00145
    liner.material_override = _v162_cup_liner_material()
    target_root.add_child(liner)

    var ferrule := MeshInstance3D.new()
    ferrule.name = "V162FlagstickGroundFerrule"
    var ferrule_mesh := CylinderMesh.new()
    ferrule_mesh.top_radius = 0.0090
    ferrule_mesh.bottom_radius = 0.0105
    ferrule_mesh.height = 0.010
    ferrule_mesh.radial_segments = 24
    ferrule.mesh = ferrule_mesh
    ferrule.position.y = 0.004
    var ferrule_mat := StandardMaterial3D.new()
    ferrule_mat.albedo_color = Color("#3e4543")
    ferrule_mat.roughness = 0.46
    ferrule_mat.metallic = 0.18
    ferrule.material_override = ferrule_mat
    target_root.add_child(ferrule)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.70
    if not cup_action:
        camera.position.y += 0.11 if not running else 0.085
        camera.fov = min(47.0, camera.fov + 1.8)
        camera.look_at(camera_look + Vector3(0.0, 0.012, 0.0), Vector3.UP)

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    super._update_hud(s, running, holed, lip_out, speed)
    distance_label.text = "TARGET %.1fm" % target_distance
    stimp_label.text = "STIMP %.1fm" % float(s.get("stimp", 2.8))
    var side: float = float(s.get("sideSlope", 0.0))
    var long_slope: float = float(s.get("longSlope", 0.0))
    slope_label.text = "BREAK L/R %+.2f%%   SLOPE F/B %+.2f%%" % [side, long_slope]

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if _v162_ball_shadow == null or _v162_ball_shadow_mat == null:
        return

    var phase := str(s.get("cupPhase", "NONE"))
    var speed := float(s.get("speed", 0.0))
    var cup_action := phase == "DROP" or phase == "SETTLED"
    _v162_ball_shadow.visible = not cup_action
    if _v162_ball_shadow.visible:
        _v162_ball_shadow.position = Vector3(ball.position.x, ball.position.y - BALL_RADIUS + 0.00125, ball.position.z)
        var strength: float = 0.235 + min(0.035, max(0.0, speed) * 0.012)
        if phase == "RIM":
            strength *= 0.58
        _v162_ball_shadow_mat.set_shader_parameter("strength", strength)
