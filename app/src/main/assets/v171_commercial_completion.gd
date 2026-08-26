extends "res://v169_live_green_shape_final.gd"

# V171 commercial-completion pass.
# Android V135-V137/GreenTerrain/GreenReadAdvisor stay authoritative. This layer:
# - uses an exact curved profile strip instead of cell-center clipping at green edges,
# - moves grass height + profile masking to the GPU using the Android terrain field,
# - adds a result replay camera with progressive actual-vs-solver trails,
# - reports the physical GREEN/FRINGE/ROUGH zone, and
# - adds lightweight atmospheric course depth without external native dependencies.

var _v171_height_texture: ImageTexture
var _v171_last_running := false
var _v171_replay_remaining := 0.0
var _v171_replay_duration := 2.8
var _v171_replay_actual: Array = []
var _v171_replay_predicted: Array = []

func _v163_blade_material(base_color: Color, tip_color: Color, wind_strength: float) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, world_vertex_coords;

uniform vec3 base_color : source_color = vec3(0.17, 0.34, 0.16);
uniform vec3 tip_color : source_color = vec3(0.38, 0.52, 0.31);
uniform float wind_strength = 0.00018;
uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
uniform float terrain_ready = 0.0;
uniform sampler2D terrain_height_tex : filter_linear, repeat_disable;
uniform vec4 terrain_bounds = vec4(-8.6, 8.6, -3.0, 31.5);
uniform float shape_family = 0.0;
uniform vec2 surface_size = vec2(11.8, 34.5);
uniform float surface_center_y = 14.25;
uniform float shape_expansion = 0.0;
uniform float root_base_y = 0.0009;

varying float blade_height;
varying float blade_random;
varying vec2 physics_pos;

float hash21(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }

vec2 shape_limits(float nz){
    float z = clamp(nz, -1.0, 1.0);
    float t = (z + 1.0) * 0.5;
    float ellipse = sqrt(max(0.0, 1.0 - z * z));
    float center = 0.0;
    float width = ellipse * 0.92;
    int family = int(floor(shape_family + 0.5));
    if (family == 1) {
        center = -0.11 + t * 0.17 + sin(t * PI) * 0.035;
        width = ellipse * (0.79 + (1.0 - t) * 0.10);
    } else if (family == 2) {
        center = sin(t * PI * 1.25) * 0.055;
        width = ellipse * (0.90 + sin(t * PI) * 0.055);
    } else if (family == 3) {
        center = sin(t * PI * 1.40) * 0.025;
        width = ellipse * 0.78;
    } else if (family == 4) {
        center = -0.08 + t * 0.15 - sin(t * PI * 1.15) * 0.035;
        width = ellipse * (0.80 + t * 0.09);
    } else if (family == 5) {
        center = sin((t - 0.12) * PI * 1.55) * 0.10;
        width = ellipse * (0.82 + sin(t * PI * 2.0) * 0.045);
    }
    width = max(0.0, width);
    return vec2(center - width, center + width);
}

void vertex(){
    float tip = clamp(UV.y, 0.0, 1.0);
    float original_y = VERTEX.y;
    physics_pos = vec2(VERTEX.x, -VERTEX.z);

    if (terrain_ready > 0.5) {
        vec2 terrain_uv = vec2(
            (physics_pos.x - terrain_bounds.x) / max(0.001, terrain_bounds.y - terrain_bounds.x),
            (physics_pos.y - terrain_bounds.z) / max(0.001, terrain_bounds.w - terrain_bounds.z)
        );
        float h = textureLod(terrain_height_tex, clamp(terrain_uv, vec2(0.0), vec2(1.0)), 0.0).r;
        float blade_local_y = original_y - root_base_y;
        VERTEX.y = h + root_base_y + blade_local_y;
    } else {
        VERTEX.y += VERTEX.x * side_slope + physics_pos.y * long_slope;
    }

    float hsh = hash21(floor(VERTEX.xz * 23.0));
    float gust = sin(TIME * 0.58 + VERTEX.x * 1.35 + VERTEX.z * 1.02 + hsh * 6.2831853);
    float flutter = sin(TIME * 1.47 + VERTEX.z * 2.55 + hsh * 9.7) * 0.22;
    float bend = (gust * 0.80 + flutter * 0.20) * wind_strength * tip * tip;
    VERTEX.x += bend * 0.68;
    VERTEX.z += bend * 0.30;
    blade_height = tip;
    blade_random = hsh;
}

void fragment(){
    float local_z = surface_center_y - physics_pos.y;
    float nz = local_z / max(0.001, surface_size.y * 0.5);
    if (abs(nz) > 1.0 + shape_expansion) discard;
    vec2 limits = shape_limits(clamp(nz, -1.0, 1.0));
    float nx = physics_pos.x / max(0.001, surface_size.x * 0.5);
    if (nx < limits.x - shape_expansion || nx > limits.y + shape_expansion) discard;

    float t = smoothstep(0.0, 1.0, blade_height);
    vec3 col = mix(base_color, tip_color, t * 0.70);
    float root_ao = mix(0.90, 1.0, smoothstep(0.04, 0.64, t));
    ALBEDO = col * root_ao * (0.98 + blade_random * 0.045);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("base_color", Vector3(base_color.r, base_color.g, base_color.b))
    material.set_shader_parameter("tip_color", Vector3(tip_color.r, tip_color.g, tip_color.b))
    material.set_shader_parameter("wind_strength", wind_strength)
    return material

func _v166_surface_mesh(size: Vector2, sub_x: int, sub_z: int, world_z_origin: float, encode_read: bool) -> ArrayMesh:
    if size.x >= 20.0:
        return super._v166_surface_mesh(size, sub_x, sub_z, world_z_origin, encode_read)

    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var colors := PackedColorArray()
    var indices := PackedInt32Array()
    var columns: int = sub_x + 1
    var expansion := 0.025 if size.x > 13.0 else 0.0

    for iz in range(sub_z + 1):
        var fz: float = float(iz) / float(max(1, sub_z))
        var local_z: float = lerp(-size.y * 0.5, size.y * 0.5, fz)
        var nz: float = local_z / max(0.001, size.y * 0.5)
        var limits := _v169_shape_limits(clamp(nz, -1.0, 1.0))
        var left_x: float = (limits.x - expansion) * size.x * 0.5
        var right_x: float = (limits.y + expansion) * size.x * 0.5
        var physics_y: float = -(world_z_origin + local_z)
        for ix in range(sub_x + 1):
            var fx: float = float(ix) / float(max(1, sub_x))
            var x: float = lerp(left_x, right_x, fx)
            var terrain := _v166_sample(x, physics_y)
            vertices.append(Vector3(x, terrain.x, local_z))
            normals.append(Vector3(terrain.y * 0.01, 1.0, -terrain.z * 0.01).normalized())
            uvs.append(Vector2(fx, fz))
            if encode_read:
                colors.append(Color(
                    clamp(terrain.x / 4.0 + 0.5, 0.0, 1.0),
                    clamp(terrain.y / 24.0 + 0.5, 0.0, 1.0),
                    clamp(terrain.z / 24.0 + 0.5, 0.0, 1.0),
                    1.0
                ))
            else:
                colors.append(Color.WHITE)

    for iz in range(sub_z):
        for ix in range(sub_x):
            var a: int = iz * columns + ix
            var b: int = a + 1
            var c: int = a + columns
            var d: int = c + 1
            indices.append_array(PackedInt32Array([a, c, b, b, c, d]))

    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_TEX_UV] = uvs
    arrays[Mesh.ARRAY_COLOR] = colors
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

# V171 keeps MultiMesh X/Z transforms immutable. Height grounding and profile clipping are handled
# in one GPU vertex/fragment pass, eliminating the 15k-instance CPU rewrite on every green switch.
func _v166_ground_open_grass() -> void:
    pass

func _v171_configure_grass_materials() -> void:
    var family: float = float(posmod(_v169_profile_id, 6))
    if _v163_green_blade_mat != null:
        _v163_green_blade_mat.set_shader_parameter("shape_family", family)
        _v163_green_blade_mat.set_shader_parameter("surface_size", Vector2(11.8, 34.5))
        _v163_green_blade_mat.set_shader_parameter("surface_center_y", 14.25)
        _v163_green_blade_mat.set_shader_parameter("shape_expansion", 0.0)
        _v163_green_blade_mat.set_shader_parameter("root_base_y", 0.0009)
    if _v163_fringe_blade_mat != null:
        _v163_fringe_blade_mat.set_shader_parameter("shape_family", family)
        _v163_fringe_blade_mat.set_shader_parameter("surface_size", Vector2(13.8, 36.0))
        _v163_fringe_blade_mat.set_shader_parameter("surface_center_y", 15.0)
        _v163_fringe_blade_mat.set_shader_parameter("shape_expansion", 0.035)
        _v163_fringe_blade_mat.set_shader_parameter("root_base_y", -0.0106)

func _v171_update_height_texture() -> void:
    if not _v166_terrain_ready or _v166_cols < 2 or _v166_rows < 2 or _v166_samples.size() < _v166_cols * _v166_rows * 3:
        return
    var image := Image.create(_v166_cols, _v166_rows, false, Image.FORMAT_RF)
    for row in range(_v166_rows):
        for col in range(_v166_cols):
            var sample_index: int = (row * _v166_cols + col) * 3
            var h: float = float(_v166_samples[sample_index])
            image.set_pixel(col, row, Color(h, 0.0, 0.0, 1.0))
    _v171_height_texture = ImageTexture.create_from_image(image)
    var bounds := Vector4(_v166_x_min, _v166_x_max, _v166_y_min, _v166_y_max)
    for mat_variant in [_v163_green_blade_mat, _v163_fringe_blade_mat]:
        var mat := mat_variant as ShaderMaterial
        if mat != null:
            mat.set_shader_parameter("terrain_height_tex", _v171_height_texture)
            mat.set_shader_parameter("terrain_bounds", bounds)
            mat.set_shader_parameter("terrain_ready", 1.0)
    _v171_configure_grass_materials()

func _v166_refresh_terrain(key: String) -> void:
    var before_key: String = _v166_terrain_key
    super._v166_refresh_terrain(key)
    if _v166_terrain_ready and (_v166_terrain_key != before_key or _v171_height_texture == null):
        _v171_update_height_texture()

func _v171_build_ridge(name_value: String, z_value: float, base_y: float, height: float, phase: float, color: Color) -> void:
    var vertices := PackedVector3Array()
    var indices := PackedInt32Array()
    var segments := 36
    for i in range(segments + 1):
        var t: float = float(i) / float(segments)
        var x: float = lerp(-38.0, 38.0, t)
        var wave: float = sin(t * TAU * 2.2 + phase) * 0.34 + sin(t * TAU * 5.1 + phase * 1.7) * 0.12
        var top_y: float = base_y + height * (0.72 + wave)
        var local_z: float = z_value + sin(t * TAU * 1.4 + phase) * 1.4
        vertices.append(Vector3(x, -0.18, local_z))
        vertices.append(Vector3(x, top_y, local_z))
    for i in range(segments):
        var a: int = i * 2
        indices.append_array(PackedInt32Array([a, a + 1, a + 2, a + 2, a + 1, a + 3]))
    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    var ridge := MeshInstance3D.new()
    ridge.name = name_value
    ridge.mesh = mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = color
    material.roughness = 1.0
    material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    ridge.material_override = material
    ridge.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(ridge)

func _build_course() -> void:
    super._build_course()
    _v171_configure_grass_materials()
    _v171_build_ridge("V171FarRidge", -59.0, 0.42, 1.25, 0.4, Color("#3c4a40"))
    _v171_build_ridge("V171MidRidge", -52.0, 0.32, 0.92, 1.7, Color("#344238"))
    _v171_build_ridge("V171NearRidge", -45.0, 0.22, 0.60, 2.9, Color("#2d3c32"))

func _v171_partial_points(points: Array, progress: float) -> Array:
    if points.size() <= 2 or progress >= 0.999:
        return points.duplicate()
    var count: int = clamp(int(ceil(float(points.size()) * progress)), 2, points.size())
    return points.slice(0, count)

func _process(delta: float) -> void:
    super._process(delta)
    if _v171_replay_remaining <= 0.0:
        return
    _v171_replay_remaining = max(0.0, _v171_replay_remaining - delta)
    var progress: float = 1.0 - _v171_replay_remaining / _v171_replay_duration
    progress = smoothstep(0.0, 1.0, clamp(progress, 0.0, 1.0))
    if _v166_predicted_path != null and _v171_replay_predicted.size() >= 2:
        _v166_predicted_path.mesh = _v166_ribbon_mesh(_v171_replay_predicted, 0.020)
        _v166_predicted_path.visible = true
    if _v166_actual_path != null and _v171_replay_actual.size() >= 2:
        _v166_actual_path.mesh = _v166_ribbon_mesh(_v171_partial_points(_v171_replay_actual, progress), 0.015)
        _v166_actual_path.visible = true
    if _v171_replay_remaining <= 0.0 and _v166_actual_path != null and _v171_replay_actual.size() >= 2:
        _v166_actual_path.mesh = _v166_ribbon_mesh(_v171_replay_actual, 0.015)

func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    if _v171_replay_remaining <= 0.0 or _v171_replay_actual.size() < 2:
        super._update_camera(ball_world, running, phase, distance_to_cup, immediate, delta)
        return

    var first := _v171_replay_actual[0] as Vector2
    var last := _v171_replay_actual[_v171_replay_actual.size() - 1] as Vector2
    var mid := (first + last) * 0.5
    var span: float = max(2.0, first.distance_to(last))
    var desired_look := Vector3(mid.x, _v166_sample(mid.x, mid.y).x + 0.025, -mid.y)
    var desired_pos := desired_look + Vector3(1.15, 1.20 + min(0.75, span * 0.08), 2.25 + min(1.45, span * 0.18))
    var pos_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 4.8)
    var look_alpha: float = 1.0 if immediate else 1.0 - exp(-delta * 5.6)
    camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
    camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, 39.0, 1.0 if immediate else min(1.0, delta * 4.2))
    camera.look_at(camera_look, Vector3.UP)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    var was_running: bool = _v171_last_running
    super._apply_snapshot(s, immediate, delta)

    _v171_configure_grass_materials()
    var running: bool = bool(s.get("running", false))
    _v171_last_running = running
    _v171_replay_actual = _v166_parse_trail(s.get("actualTrail", []))
    _v171_replay_predicted = _v166_parse_trail(s.get("predictedTrail", []))

    if running:
        _v171_replay_remaining = 0.0
    elif was_running and _v171_replay_actual.size() >= 2:
        _v171_replay_remaining = _v171_replay_duration

    if _v165_detail_label != null:
        var zone: String = str(s.get("surfaceZone", "GREEN"))
        _v165_detail_label.text += "  |  %s" % zone
