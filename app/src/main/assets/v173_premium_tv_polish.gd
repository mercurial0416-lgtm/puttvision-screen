extends "res://v172_final_scene_completion.gd"

# V173 premium TV polish. Rendering-only pass: Android V135-V137 / GreenTerrain remain authoritative.
# This layer brightens and separates turf materials, grounds the ball with a soft contact shadow,
# replaces the rigid flag prism with a lightweight animated cloth mesh, exposes a real-looking cup
# liner under the lip, and lets the low-poly foliage react to course lighting instead of rendering flat.

var _v173_ball_shadow: MeshInstance3D
var _v173_ball_shadow_mat: StandardMaterial3D
var _v173_flag_cloth: MeshInstance3D

func _build_materials() -> void:
    super._build_materials()

    # The previous pass was deliberately conservative and read too dark on a TV. Keep the exact
    # texture/normal pipeline but separate green, fringe and rough more clearly at broadcast range.
    if mat_green != null:
        mat_green.set_shader_parameter("tint", Vector3(0.286, 0.505, 0.294))
        mat_green.set_shader_parameter("brightness", 0.90)
        mat_green.set_shader_parameter("scan_mix", 0.58)
        mat_green.set_shader_parameter("tiling", Vector2(7.5, 22.0))
    if mat_fringe != null:
        mat_fringe.set_shader_parameter("tint", Vector3(0.235, 0.420, 0.260))
        mat_fringe.set_shader_parameter("brightness", 0.86)
        mat_fringe.set_shader_parameter("scan_mix", 0.53)
        mat_fringe.set_shader_parameter("tiling", Vector2(8.0, 20.0))
    if mat_rough != null:
        mat_rough.set_shader_parameter("tint", Vector3(0.190, 0.335, 0.220))
        mat_rough.set_shader_parameter("brightness", 0.82)
        mat_rough.set_shader_parameter("scan_mix", 0.46)

func _build_environment() -> void:
    super._build_environment()

    var env_node := get_node_or_null("WorldEnvironment") as WorldEnvironment
    if env_node != null and env_node.environment != null:
        env_node.environment.ambient_light_energy = 0.56
        env_node.environment.fog_light_energy = 0.24
        env_node.environment.fog_density = 0.0027

    var key_sun := get_node_or_null("KeySun") as DirectionalLight3D
    if key_sun != null:
        key_sun.light_energy = 0.88

    # Very soft opposite fill prevents card foliage and the clubhouse facade from crushing to black
    # while preserving the single-shadow mobile renderer path.
    var fill := DirectionalLight3D.new()
    fill.name = "V173SkyFill"
    fill.light_color = Color("#c8d7e2")
    fill.light_energy = 0.14
    fill.shadow_enabled = false
    fill.rotation_degrees = Vector3(-34.0, 142.0, 0.0)
    add_child(fill)

func _build_course() -> void:
    super._build_course()
    if _v163_green_blade_mat != null:
        _v163_green_blade_mat.set_shader_parameter("base_color", Vector3(0.205, 0.390, 0.215))
        _v163_green_blade_mat.set_shader_parameter("tip_color", Vector3(0.390, 0.555, 0.330))
    if _v163_fringe_blade_mat != null:
        _v163_fringe_blade_mat.set_shader_parameter("base_color", Vector3(0.175, 0.335, 0.195))
        _v163_fringe_blade_mat.set_shader_parameter("tip_color", Vector3(0.330, 0.485, 0.285))

func _build_ball() -> void:
    super._build_ball()

    _v173_ball_shadow_mat = StandardMaterial3D.new()
    _v173_ball_shadow_mat.albedo_color = Color(0.01, 0.02, 0.015, 0.22)
    _v173_ball_shadow_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _v173_ball_shadow_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED

    _v173_ball_shadow = MeshInstance3D.new()
    _v173_ball_shadow.name = "V173BallContactShadow"
    var shadow_mesh := CylinderMesh.new()
    shadow_mesh.top_radius = 0.032
    shadow_mesh.bottom_radius = 0.040
    shadow_mesh.height = 0.0012
    shadow_mesh.radial_segments = 32
    _v173_ball_shadow.mesh = shadow_mesh
    _v173_ball_shadow.material_override = _v173_ball_shadow_mat
    _v173_ball_shadow.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(_v173_ball_shadow)

func _v173_flag_mesh() -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var indices := PackedInt32Array()
    var cols := 9
    var rows := 4
    var width := 0.40
    var height := 0.22

    for iy in range(rows + 1):
        var fy: float = float(iy) / float(rows)
        for ix in range(cols + 1):
            var fx: float = float(ix) / float(cols)
            var top_drop: float = fx * 0.012
            var bottom_lift: float = fx * 0.025
            var y: float = lerp(-top_drop, -height + bottom_lift, fy)
            vertices.append(Vector3(fx * width, y, 0.0))
            normals.append(Vector3(0.0, 0.0, 1.0))
            uvs.append(Vector2(fx, fy))

    var stride := cols + 1
    for iy in range(rows):
        for ix in range(cols):
            var a: int = iy * stride + ix
            var b: int = a + 1
            var c: int = a + stride
            var d: int = c + 1
            indices.append_array(PackedInt32Array([a, c, b, b, c, d]))

    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _v173_flag_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode cull_disabled;
uniform vec3 flag_red : source_color = vec3(0.72, 0.045, 0.055);
uniform vec3 flag_light : source_color = vec3(0.96, 0.13, 0.12);
varying float cloth_light;
void vertex(){
    float u = UV.x;
    float primary = sin(TIME * 2.25 - u * 5.8);
    float secondary = sin(TIME * 3.70 - u * 9.2 + UV.y * 1.4);
    VERTEX.z += (primary * 0.020 + secondary * 0.006) * u * u;
    VERTEX.y += sin(TIME * 1.75 - u * 4.0) * 0.0045 * u;
    cloth_light = 0.5 + 0.5 * primary;
}
void fragment(){
    float edge = smoothstep(0.0, 0.18, UV.x) * (1.0 - smoothstep(0.92, 1.0, UV.x) * 0.10);
    ALBEDO = mix(flag_red, flag_light, cloth_light * 0.34 + UV.y * 0.08) * mix(0.88, 1.0, edge);
    ROUGHNESS = 0.78;
    SPECULAR = 0.12;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _build_target() -> void:
    super._build_target()

    # Hide the inherited rigid prism flag but keep its proven pole/cup placement.
    for child in target_root.get_children():
        if child is MeshInstance3D:
            var mesh_instance := child as MeshInstance3D
            if mesh_instance.mesh is PrismMesh:
                mesh_instance.visible = false

    # White plastic liner segments sit just below the turf lip, leaving the center physically dark.
    var liner_root := Node3D.new()
    liner_root.name = "V173CupLiner"
    target_root.add_child(liner_root)
    for i in range(18):
        var angle: float = TAU * float(i) / 18.0
        var segment := MeshInstance3D.new()
        var segment_mesh := BoxMesh.new()
        segment_mesh.size = Vector3(0.018, 0.052, 0.0032)
        segment.mesh = segment_mesh
        segment.material_override = mat_white
        segment.position = Vector3(cos(angle) * 0.052, -0.028, sin(angle) * 0.052)
        segment.rotation_degrees.y = -rad_to_deg(angle)
        segment.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
        liner_root.add_child(segment)

    _v173_flag_cloth = MeshInstance3D.new()
    _v173_flag_cloth.name = "V173AnimatedFlagCloth"
    _v173_flag_cloth.mesh = _v173_flag_mesh()
    _v173_flag_cloth.material_override = _v173_flag_material()
    _v173_flag_cloth.position = Vector3(0.005, 1.79, 0.0)
    _v173_flag_cloth.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    target_root.add_child(_v173_flag_cloth)

func _v172_leaf_material(phase: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode cull_disabled;
uniform vec3 leaf_dark : source_color = vec3(0.105, 0.18, 0.12);
uniform vec3 leaf_light : source_color = vec3(0.32, 0.43, 0.27);
uniform float phase = 0.0;
varying float leaf_variation;
float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
void vertex(){
    float wind = sin(TIME * 0.47 + VERTEX.x * 0.73 + VERTEX.z * 0.61 + phase);
    VERTEX.x += wind * 0.007 * smoothstep(0.28, 1.0, UV.y);
    VERTEX.z += sin(TIME * 0.31 + VERTEX.y * 1.9 + phase * 1.7) * 0.003 * UV.y;
    leaf_variation = hash21(floor((UV + vec2(phase * 0.13)) * vec2(17.0, 13.0)));
}
void fragment(){
    vec2 p = (UV - vec2(0.5)) * 2.0;
    float ellipse = length(p * vec2(0.72, 1.0));
    float edge = 1.0 - smoothstep(0.70, 1.01, ellipse);
    float breakup = hash21(floor(UV * vec2(11.0, 9.0)) + phase * 2.3);
    if (edge < 0.30 || (breakup < 0.075 && ellipse > 0.30)) discard;
    float vertical = smoothstep(0.0, 1.0, UV.y);
    float variation = clamp(vertical * 0.42 + leaf_variation * 0.34, 0.0, 1.0);
    ALBEDO = mix(leaf_dark, leaf_light, variation);
    ROUGHNESS = 0.90;
    SPECULAR = 0.10;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    var hue: float = fmod(abs(phase) * 0.071, 0.07)
    material.set_shader_parameter("leaf_dark", Vector3(0.100 + hue * 0.20, 0.170 + hue * 0.42, 0.115 + hue * 0.16))
    material.set_shader_parameter("leaf_light", Vector3(0.285 + hue * 0.28, 0.390 + hue * 0.36, 0.235 + hue * 0.22))
    material.set_shader_parameter("phase", phase)
    return material

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if _v173_ball_shadow == null:
        return

    var bx: float = float(s.get("ballX", 0.0))
    var by: float = float(s.get("ballY", 0.0))
    var bz: float = float(s.get("ballZ", BALL_RADIUS))
    var surface_y: float = bz - BALL_RADIUS
    if _v166_terrain_ready:
        surface_y = _v166_sample(bx, by).x + 0.0010
    _v173_ball_shadow.position = Vector3(bx, surface_y, -by)

    var air_gap: float = max(0.0, bz - BALL_RADIUS - surface_y)
    var shadow_alpha: float = lerp(0.22, 0.055, clamp(air_gap / 0.30, 0.0, 1.0))
    _v173_ball_shadow_mat.albedo_color = Color(0.01, 0.02, 0.015, shadow_alpha)
    var shadow_scale: float = 1.0 + clamp(air_gap * 1.2, 0.0, 0.32)
    _v173_ball_shadow.scale = Vector3(shadow_scale, 1.0, shadow_scale)
