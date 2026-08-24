extends "res://v161_cinematic_final.gd"

# V162: ultra-real putting presentation on the proven Android-safe renderer path.
# Physics remains authoritative in Android V135-V137. This layer only improves the live Godot view.
# Keep the crash-prone features disabled: no HDRI, dynamic shadow maps, alpha-card foliage,
# anisotropic filtering or external 3D models.

var _v162_ball_shadow: MeshInstance3D
var _v162_ball_shadow_mat: ShaderMaterial

func _build_materials() -> void:
    super._build_materials()

    # Real bentgrass reads mostly through very small normal/roughness changes, not loud green bands.
    # Preserve the existing V160 texture path while tightening fibre scale and reducing game-like mow.
    if mat_green is ShaderMaterial:
        mat_green.set_shader_parameter("base_color", Vector3(0.192, 0.348, 0.154))
        mat_green.set_shader_parameter("lane_strength", 0.015)
        mat_green.set_shader_parameter("texture_scale", 132.0)
        mat_green.set_shader_parameter("normal_depth", 0.074)
        mat_green.set_shader_parameter("roughness_base", 0.836)
        mat_green.set_shader_parameter("micro_strength", 0.026)
    if mat_fringe is ShaderMaterial:
        mat_fringe.set_shader_parameter("base_color", Vector3(0.162, 0.304, 0.143))
        mat_fringe.set_shader_parameter("lane_strength", 0.010)
        mat_fringe.set_shader_parameter("texture_scale", 78.0)
        mat_fringe.set_shader_parameter("normal_depth", 0.105)
        mat_fringe.set_shader_parameter("roughness_base", 0.875)
        mat_fringe.set_shader_parameter("micro_strength", 0.032)
    if mat_rough is ShaderMaterial:
        mat_rough.set_shader_parameter("base_color", Vector3(0.124, 0.246, 0.132))
        mat_rough.set_shader_parameter("lane_strength", 0.006)
        mat_rough.set_shader_parameter("texture_scale", 40.0)
        mat_rough.set_shader_parameter("normal_depth", 0.150)
        mat_rough.set_shader_parameter("roughness_base", 0.912)
        mat_rough.set_shader_parameter("micro_strength", 0.041)

func _build_environment() -> void:
    super._build_environment()

    # Pull the grading away from oversaturated mobile-game green while retaining TV readability.
    var env_node := get_node_or_null("V160NaturalFinishEnvironment") as WorldEnvironment
    if env_node != null and env_node.environment != null:
        var env := env_node.environment
        env.adjustment_brightness = 0.875
        env.adjustment_contrast = 1.105
        env.adjustment_saturation = 0.900
        env.ambient_light_energy = 0.345

    var sun := get_node_or_null("V160NaturalSun") as DirectionalLight3D
    if sun != null:
        sun.light_energy = 0.94
        sun.light_color = Color("#fff1d9")

    var fill := get_node_or_null("V160NaturalFill") as DirectionalLight3D
    if fill != null:
        fill.light_energy = 0.155

    var rim := get_node_or_null("V161WarmRim") as DirectionalLight3D
    if rim != null:
        rim.light_energy = 0.085

func _v162_ball_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void fragment() {
    // Offset alternate rows to approximate the staggered dimple packing of a real golf ball.
    vec2 grid_uv = UV * vec2(54.0, 28.0);
    float row = floor(grid_uv.y);
    grid_uv.x += mod(row, 2.0) * 0.5;
    vec2 cell = fract(grid_uv) - vec2(0.5);
    cell.x *= 0.82;
    float r = length(cell);

    float dimple = 1.0 - smoothstep(0.205, 0.315, r);
    float bowl = dimple * smoothstep(0.315, 0.055, r);
    float rim = dimple * (1.0 - smoothstep(0.205, 0.275, r)) * smoothstep(0.105, 0.205, r);

    float micro = (hash21(floor(UV * vec2(720.0, 360.0))) - 0.5) * 0.010;
    vec3 ivory = vec3(0.930, 0.925, 0.895);
    vec3 col = ivory * (1.0 + micro - bowl * 0.050 + rim * 0.018);
    ALBEDO = col;

    // Tangent-space normal perturbation gives the dimples depth without expensive geometry.
    vec2 dir = cell / max(r, 0.0001);
    vec2 inward = -dir * dimple * (1.0 - smoothstep(0.045, 0.285, r)) * 0.70;
    vec3 dimple_normal = normalize(vec3(inward, 1.0));
    NORMAL_MAP = dimple_normal * 0.5 + 0.5;
    NORMAL_MAP_DEPTH = 0.34;

    ROUGHNESS = clamp(0.265 + bowl * 0.085 + micro * 0.8, 0.23, 0.39);
    SPECULAR = 0.34;
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
uniform float strength = 0.24;
void fragment() {
    vec2 p = (UV - vec2(0.5)) * vec2(2.0, 2.0);
    float r = length(vec2(p.x, p.y * 1.55));
    if (r > 1.0) { discard; }
    float alpha = (1.0 - smoothstep(0.08, 1.0, r));
    alpha = alpha * alpha * strength;
    ALBEDO = vec3(0.010, 0.016, 0.008);
    ALPHA = alpha;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("strength", 0.24)
    return material

func _build_ball() -> void:
    super._build_ball()

    # One ball is cheap enough to smooth geometrically; dimples themselves remain shader-based.
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
void fragment() {
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float r = length(p);
    if (r > 0.965 || r < 0.690) { discard; }

    float front = smoothstep(-0.78, 0.72, p.y);
    float outer_edge = 1.0 - smoothstep(0.885, 0.965, r);
    float inner_edge = smoothstep(0.690, 0.765, r);
    vec3 shade = mix(vec3(0.55, 0.56, 0.53), vec3(0.90, 0.90, 0.84), front);
    shade *= 0.84 + 0.16 * outer_edge * inner_edge;
    ALBEDO = shade;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _build_target() -> void:
    super._build_target()

    # Recessed liner lip: annulus only, so the inherited dark cup interior remains genuinely open.
    var liner := MeshInstance3D.new()
    liner.name = "V162RecessedCupLinerLip"
    var liner_mesh := QuadMesh.new()
    liner_mesh.size = Vector2(0.116, 0.116)
    liner.mesh = liner_mesh
    liner.rotation_degrees.x = -90.0
    liner.position.y = 0.00145
    liner.material_override = _v162_cup_liner_material()
    target_root.add_child(liner)

    # A tiny dark ferrule/ground junction stops the flagstick from looking pasted onto the cup.
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

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)

    if _v162_ball_shadow == null or _v162_ball_shadow_mat == null:
        return

    var phase := str(s.get("cupPhase", "NONE"))
    var speed := float(s.get("speed", 0.0))
    var cup_action := phase == "DROP" or phase == "SETTLED"

    # The Android solver supplies the physical center height, so center-radius is the correct local
    # contact plane even on a sloped green. Hide the fake contact shadow once the ball drops in-cup.
    _v162_ball_shadow.visible = not cup_action
    if _v162_ball_shadow.visible:
        _v162_ball_shadow.position = Vector3(
            ball.position.x,
            ball.position.y - BALL_RADIUS + 0.00125,
            ball.position.z
        )
        var strength := 0.235 + min(0.035, max(0.0, speed) * 0.012)
        if phase == "RIM":
            strength *= 0.58
        _v162_ball_shadow_mat.set_shader_parameter("strength", strength)
