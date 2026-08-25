extends "res://v165_screen_golf_read.gd"

# V166: true spatial green read.
# The Android GreenTerrain / GreenReadAdvisor payload is the source of truth.
# Godot only reconstructs the exact visual surface and physics trails.

var _v166_terrain_key := ""
var _v166_cols := 0
var _v166_rows := 0
var _v166_x_min := 0.0
var _v166_x_max := 0.0
var _v166_y_min := 0.0
var _v166_y_max := 0.0
var _v166_samples: Array = []
var _v166_terrain_ready := false
var _v166_fallback_side := 0.0
var _v166_fallback_long := 0.0

var _v166_predicted_path: MeshInstance3D
var _v166_actual_path: MeshInstance3D
var _v166_predicted_signature := ""
var _v166_actual_signature := ""
var _v166_compare_label: Label
var _v166_solver_ready := false

func _v164_make_grid_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;

uniform float side_slope = 0.0;
uniform float long_slope = 0.0;
uniform float cup_local_z = 9.25;
uniform vec2 ball_local = vec2(0.0, 14.25);
uniform float strength = 0.62;
uniform float action_fade = 1.0;
uniform float read_intensity = 0.92;
uniform float read_enabled = 1.0;
uniform float recommended_offset = 0.0;
uniform float guide_fade = 1.0;
uniform float terrain_ready = 0.0;
uniform vec3 minor_color : source_color = vec3(0.63, 0.91, 0.92);
uniform vec3 major_color : source_color = vec3(0.88, 0.98, 0.98);
uniform vec3 flow_color : source_color = vec3(0.98, 0.88, 0.37);

varying vec2 grid_pos;
varying float local_height;
varying vec2 local_slope_pct;

float line_axis_aa(float coord, float spacing, float width_m) {
    float d = abs(fract(coord / spacing + 0.5) - 0.5) * spacing;
    float aa = max(fwidth(coord) * 0.72, width_m * 0.40);
    return 1.0 - smoothstep(max(0.0, width_m - aa), width_m + aa, d);
}

void vertex() {
    grid_pos = VERTEX.xz;
    if (terrain_ready < 0.5) {
        VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
        local_height = VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
        local_slope_pct = vec2(side_slope * 100.0, long_slope * 100.0);
    } else {
        local_height = (COLOR.r - 0.5) * 4.0;
        local_slope_pct = vec2((COLOR.g - 0.5) * 24.0, (COLOR.b - 0.5) * 24.0);
    }
}

void fragment() {
    float pixel_world = max(fwidth(grid_pos.x), fwidth(grid_pos.y));
    float minor_lod = 1.0 - smoothstep(0.030, 0.100, pixel_world);
    float micro_lod = 1.0 - smoothstep(0.010, 0.045, pixel_world);

    float minor_x = line_axis_aa(grid_pos.x, 0.25, 0.0032) * minor_lod;
    float minor_z = line_axis_aa(grid_pos.y, 0.25, 0.0032) * minor_lod;
    float major_x = line_axis_aa(grid_pos.x, 1.00, 0.0105);
    float major_z = line_axis_aa(grid_pos.y, 1.00, 0.0105);
    float minor_grid = max(minor_x, minor_z);
    float major_grid = max(major_x, major_z);

    // +side means downhill to render +X. +long means downhill toward the cup,
    // which is render -Z because Android +Y maps to Godot -Z.
    vec2 slope_vec = vec2(local_slope_pct.x, -local_slope_pct.y);
    float slope_pct = length(slope_vec);
    vec2 downhill = slope_pct > 0.0005 ? slope_vec / slope_pct : vec2(0.0, -1.0);
    vec2 across = vec2(-downhill.y, downhill.x);
    float flow_amount = smoothstep(0.15, 1.20, slope_pct);

    float travel = dot(grid_pos, downhill) * 5.15 - TIME * (0.80 + min(2.8, slope_pct * 0.55));
    float moving_band = pow(0.5 + 0.5 * sin(travel), 9.0);

    float cup_dist = distance(grid_pos, vec2(0.0, cup_local_z));
    float cup_focus = 1.0 - smoothstep(0.34, 1.40, cup_dist);
    float cup_ring = 1.0 - smoothstep(0.025, 0.080, abs(cup_dist - 0.54));
    float ball_dist = distance(grid_pos, ball_local);
    float ball_focus = 1.0 - smoothstep(0.28, 1.05, ball_dist);

    float corridor = 1.0 - smoothstep(0.10, 0.38, abs(grid_pos.x));
    float grid_alpha = minor_grid * 0.14 + major_grid * 0.46;
    grid_alpha *= mix(1.0, 0.76, corridor);
    grid_alpha *= 0.88 + cup_focus * 0.32 + ball_focus * 0.18;

    // Cup-only 12.5 cm micro grid. Derivative LOD naturally removes it before
    // it becomes sub-pixel shimmer on distant TV pixels.
    float micro_x = line_axis_aa(grid_pos.x, 0.125, 0.0021);
    float micro_z = line_axis_aa(grid_pos.y - cup_local_z, 0.125, 0.0021);
    float micro_grid = max(micro_x, micro_z) * cup_focus * micro_lod;

    // True contour lines use the Android physical elevation interpolated in the
    // reconstructed mesh, not one global side/long plane.
    float contour_period = 0.0125;
    float contour_d = abs(fract(local_height / contour_period + 0.5) - 0.5) * contour_period;
    float contour_aa = max(fwidth(local_height) * 1.35, 0.00030);
    float contour = 1.0 - smoothstep(0.00050, 0.00050 + contour_aa, contour_d);
    contour *= smoothstep(0.25, 0.95, slope_pct);

    // Every fragment gets its own local downhill vector, so crowns, bowls and
    // ridges produce visibly different arrow directions across the same green.
    float arrow_along = dot(grid_pos, downhill);
    float arrow_cross = dot(grid_pos, across);
    float arrow_phase = fract(arrow_along / 0.68 - TIME * (0.30 + min(1.15, slope_pct * 0.13)));
    float arrow_a = abs(arrow_phase - 0.5) * 0.68;
    float arrow_c = abs(fract(arrow_cross / 0.78 + 0.5) - 0.5) * 0.78;
    float chevron = 1.0 - smoothstep(0.014, 0.036 + pixel_world * 0.35, abs(arrow_c - arrow_a * 0.72));
    chevron *= 1.0 - smoothstep(0.15, 0.24, arrow_a);
    chevron *= 1.0 - smoothstep(0.24, 0.34, arrow_c);
    chevron *= flow_amount;

    float flow_mask = max(minor_grid * 0.44, major_grid);
    float flow = flow_mask * moving_band * flow_amount * 0.58;

    vec3 base_grid = mix(minor_color, major_color, clamp(major_grid * 1.35, 0.0, 1.0));
    vec3 slope_cool = vec3(0.24, 0.92, 0.78);
    vec3 slope_mid = vec3(0.98, 0.88, 0.22);
    vec3 slope_hot = vec3(1.00, 0.34, 0.08);
    vec3 slope_color = mix(slope_cool, slope_mid, smoothstep(0.65, 2.00, slope_pct));
    slope_color = mix(slope_color, slope_hot, smoothstep(2.00, 4.00, slope_pct));

    vec3 color = mix(base_grid, flow_color, clamp(flow + cup_ring * 0.55, 0.0, 0.82));
    float enhanced_mix = clamp((contour * 0.54 + chevron * 0.92) * read_intensity * read_enabled, 0.0, 0.92);
    color = mix(color, slope_color, enhanced_mix);

    float edge_x = smoothstep(5.82, 5.45, abs(grid_pos.x));
    float edge_z = smoothstep(17.08, 16.40, abs(grid_pos.y));
    float edge_fade = edge_x * edge_z;
    float enhanced_alpha = (micro_grid * 0.18 + contour * 0.17 + chevron * 0.24) * read_intensity * read_enabled;
    float alpha = (grid_alpha + flow * 0.34 + cup_ring * 0.16 + enhanced_alpha) * strength * action_fade * edge_fade;

    ALBEDO = color;
    ALPHA = clamp(alpha, 0.0, 0.84);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 1
    return material

func _build_course() -> void:
    super._build_course()
    _v166_predicted_path = MeshInstance3D.new()
    _v166_predicted_path.name = "V166PhysicsPredictedTrail"
    _v166_predicted_path.material_override = _v166_path_material(Color(1.0, 0.73, 0.08, 0.93), 3)
    _v166_predicted_path.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(_v166_predicted_path)

    _v166_actual_path = MeshInstance3D.new()
    _v166_actual_path.name = "V166ActualTrail"
    _v166_actual_path.material_override = _v166_path_material(Color(0.32, 0.94, 1.0, 0.86), 4)
    _v166_actual_path.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(_v166_actual_path)

func _build_hud() -> void:
    super._build_hud()
    if _v165_panel == null:
        return
    _v165_panel.size.y = 104.0
    var divider := ColorRect.new()
    divider.position = Vector2(18, 66)
    divider.size = Vector2(388, 1)
    divider.color = Color(1.0, 1.0, 1.0, 0.10)
    divider.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v165_panel.add_child(divider)
    _v166_compare_label = _v164_label(_v165_panel, Vector2(18, 68), Vector2(388, 30), 12, Color(0.72, 0.92, 0.95, 0.94))
    _v166_compare_label.text = "TRUE 3D | PHYSICS READ"

func _v166_path_material(path_color: Color, priority: int) -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode unshaded, cull_disabled, blend_mix, depth_draw_never;
uniform vec4 path_color : source_color = vec4(1.0, 0.75, 0.1, 0.9);
void fragment(){
    ALBEDO = path_color.rgb;
    ALPHA = path_color.a;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.set_shader_parameter("path_color", path_color)
    material.render_priority = priority
    return material

func _v166_raw_sample(col: int, row: int) -> Vector3:
    if not _v166_terrain_ready:
        return Vector3.ZERO
    var c: int = clampi(col, 0, _v166_cols - 1)
    var r: int = clampi(row, 0, _v166_rows - 1)
    var index: int = (r * _v166_cols + c) * 3
    if index + 2 >= _v166_samples.size():
        return Vector3.ZERO
    return Vector3(float(_v166_samples[index]), float(_v166_samples[index + 1]), float(_v166_samples[index + 2]))

func _v166_sample(x: float, physics_y: float) -> Vector3:
    if not _v166_terrain_ready:
        var h := -0.01 * _v166_fallback_side * x - 0.01 * _v166_fallback_long * physics_y
        return Vector3(h, _v166_fallback_side, _v166_fallback_long)
    var ux: float = clamp((x - _v166_x_min) / max(0.001, _v166_x_max - _v166_x_min), 0.0, 1.0) * float(_v166_cols - 1)
    var uy: float = clamp((physics_y - _v166_y_min) / max(0.001, _v166_y_max - _v166_y_min), 0.0, 1.0) * float(_v166_rows - 1)
    var x0: int = int(floor(ux))
    var y0: int = int(floor(uy))
    var x1: int = min(x0 + 1, _v166_cols - 1)
    var y1: int = min(y0 + 1, _v166_rows - 1)
    var fx: float = ux - float(x0)
    var fy: float = uy - float(y0)
    var a := _v166_raw_sample(x0, y0).lerp(_v166_raw_sample(x1, y0), fx)
    var b := _v166_raw_sample(x0, y1).lerp(_v166_raw_sample(x1, y1), fx)
    return a.lerp(b, fy)

func _v166_surface_mesh(size: Vector2, sub_x: int, sub_z: int, world_z_origin: float, encode_read: bool) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var uvs := PackedVector2Array()
    var colors := PackedColorArray()
    var indices := PackedInt32Array()
    var columns: int = sub_x + 1

    for iz in range(sub_z + 1):
        var fz: float = float(iz) / float(max(1, sub_z))
        var local_z: float = lerp(-size.y * 0.5, size.y * 0.5, fz)
        var physics_y: float = -(world_z_origin + local_z)
        for ix in range(sub_x + 1):
            var fx: float = float(ix) / float(max(1, sub_x))
            var x: float = lerp(-size.x * 0.5, size.x * 0.5, fx)
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

func _v166_rebuild_surface(node_name: String, size: Vector2, sub_x: int, sub_z: int) -> void:
    var node := get_node_or_null(node_name) as MeshInstance3D
    if node == null:
        return
    node.mesh = _v166_surface_mesh(size, sub_x, sub_z, node.position.z, false)

func _v166_ground_open_grass() -> void:
    var grass_nodes: Array = [_v163_green_blades, _v163_fringe_blades]
    for grass_node_variant in grass_nodes:
        var grass_node := grass_node_variant as MultiMeshInstance3D
        if grass_node == null or grass_node.multimesh == null:
            continue
        var base_y: float = 0.0009 if grass_node == _v163_green_blades else -0.0106
        var mm := grass_node.multimesh
        for i in range(mm.instance_count):
            var transform := mm.get_instance_transform(i)
            var world_x: float = grass_node.position.x + transform.origin.x
            var physics_y: float = -(grass_node.position.z + transform.origin.z)
            transform.origin.y = _v166_sample(world_x, physics_y).x + base_y
            mm.set_instance_transform(i, transform)

func _v166_refresh_terrain(key: String) -> void:
    if key == "" or key == _v166_terrain_key or bridge == null or not bridge.has_method("terrainFieldJson"):
        return
    var parsed = JSON.parse_string(bridge.terrainFieldJson())
    if not (parsed is Dictionary):
        return
    var field := parsed as Dictionary
    var cols: int = int(field.get("cols", 0))
    var rows: int = int(field.get("rows", 0))
    var samples_variant: Variant = field.get("samples", [])
    if cols < 2 or rows < 2 or not (samples_variant is Array):
        return
    var samples_array := samples_variant as Array
    if samples_array.size() < cols * rows * 3:
        return

    _v166_cols = cols
    _v166_rows = rows
    _v166_x_min = float(field.get("xMin", -8.6))
    _v166_x_max = float(field.get("xMax", 8.6))
    _v166_y_min = float(field.get("yMin", -3.0))
    _v166_y_max = float(field.get("yMax", 31.5))
    _v166_samples = samples_array
    _v166_terrain_ready = true
    _v166_terrain_key = key

    _v166_rebuild_surface("Green", Vector2(11.8, 34.5), 30, 86)
    _v166_rebuild_surface("Fringe", Vector2(13.8, 36.0), 20, 60)
    _v166_rebuild_surface("Rough", Vector2(42.0, 72.0), 22, 42)
    if _v164_grid != null:
        _v164_grid.mesh = _v166_surface_mesh(Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH), 30, 86, V164_GREEN_CENTER_Z, true)
    _v166_ground_open_grass()
    if _v164_grid_mat != null:
        _v164_grid_mat.set_shader_parameter("terrain_ready", 1.0)

func _v166_parse_trail(raw: Variant) -> Array:
    var points: Array = []
    if not (raw is Array):
        return points
    for entry_variant in raw as Array:
        if entry_variant is Array:
            var entry := entry_variant as Array
            if entry.size() >= 2:
                points.append(Vector2(float(entry[0]), float(entry[1])))
    return points

func _v166_trail_signature(points: Array) -> String:
    if points.is_empty():
        return "0"
    var last := points[points.size() - 1] as Vector2
    return "%d:%d:%d" % [points.size(), int(round(last.x * 1000.0)), int(round(last.y * 1000.0))]

func _v166_ribbon_mesh(points: Array, width: float) -> ArrayMesh:
    var vertices := PackedVector3Array()
    var indices := PackedInt32Array()
    if points.size() < 2:
        return ArrayMesh.new()
    var half_width: float = width * 0.5
    for i in range(points.size() - 1):
        var a := points[i] as Vector2
        var b := points[i + 1] as Vector2
        var render_dir := Vector2(b.x - a.x, -(b.y - a.y))
        if render_dir.length_squared() < 0.0000001:
            continue
        render_dir = render_dir.normalized()
        var perp := Vector2(-render_dir.y, render_dir.x) * half_width
        var ah: float = _v166_sample(a.x, a.y).x + 0.0075
        var bh: float = _v166_sample(b.x, b.y).x + 0.0075
        var base: int = vertices.size()
        vertices.append(Vector3(a.x + perp.x, ah, -a.y + perp.y))
        vertices.append(Vector3(a.x - perp.x, ah, -a.y - perp.y))
        vertices.append(Vector3(b.x + perp.x, bh, -b.y + perp.y))
        vertices.append(Vector3(b.x - perp.x, bh, -b.y - perp.y))
        indices.append_array(PackedInt32Array([base, base + 2, base + 1, base + 1, base + 2, base + 3]))
    var arrays: Array = []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    if vertices.size() >= 4:
        mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
    return mesh

func _v166_update_paths(s: Dictionary) -> void:
    var predicted := _v166_parse_trail(s.get("predictedTrail", []))
    var actual := _v166_parse_trail(s.get("actualTrail", []))
    var predicted_sig := _v166_trail_signature(predicted)
    var actual_sig := _v166_trail_signature(actual)

    if predicted_sig != _v166_predicted_signature and _v166_predicted_path != null:
        _v166_predicted_path.mesh = _v166_ribbon_mesh(predicted, 0.020)
        _v166_predicted_path.visible = predicted.size() >= 2
        _v166_predicted_signature = predicted_sig
    if actual_sig != _v166_actual_signature and _v166_actual_path != null:
        _v166_actual_path.mesh = _v166_ribbon_mesh(actual, 0.013)
        _v166_actual_path.visible = actual.size() >= 2
        _v166_actual_signature = actual_sig

    if aim_line != null:
        aim_line.visible = predicted.size() < 2

func _v165_recommended_aim(s: Dictionary, _side_pct: float, _long_pct: float) -> float:
    # V166 never invents a physics line. Until the Android inverse solver is ready,
    # the UI explicitly says SOLVING and the old heuristic guide stays absent.
    if s.has("recommendedAimOffsetM"):
        var raw: Variant = s.get("recommendedAimOffsetM")
        if raw is int or raw is float:
            return clamp(float(raw), -2.20, 2.20)
    return 0.0

func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    if _v165_aim_label == null:
        return
    var side_abs: float = abs(side_pct)
    var aim_text := "AIM CENTER"
    if not _v166_solver_ready:
        aim_text = "PHYSICS READ SOLVING"
    elif abs(_v165_recommended_offset) >= 0.015:
        # Fixed V165 sign bug: direction follows the solved offset itself, not the
        # sign of the coarse global side slope.
        var aim_dir := "R" if _v165_recommended_offset > 0.0 else "L"
        aim_text = "AIM %s %.2f m" % [aim_dir, abs(_v165_recommended_offset)]

    var break_dir := "STRAIGHT"
    if side_abs >= 0.03:
        break_dir = "BREAK L" if side_pct > 0.0 else "BREAK R"
    _v165_aim_label.text = "%s   |   %s" % [aim_text, _v165_read_level(side_pct, long_pct)]
    _v165_detail_label.text = "%s %.2f%%   |   LOCAL FLOW | TRUE CONTOUR" % [break_dir, side_abs]

func _v166_delta_word(value: float, positive: String, negative: String, deadband: float = 1.0) -> String:
    if abs(value) < deadband:
        return "ON %.1fcm" % abs(value)
    return "%s %.1fcm" % [positive if value > 0.0 else negative, abs(value)]

func _v166_update_compare_hud(s: Dictionary, running: bool) -> void:
    if _v166_compare_label == null:
        return
    if not _v166_solver_ready:
        _v166_compare_label.text = "TRUE 3D | PHYSICS SOLVER WORKING..." if bool(s.get("readPending", false)) else "TRUE 3D | WAITING FOR PHYSICS READ"
        return
    var solver_miss: float = float(s.get("solverMissCm", 0.0))
    var reliable: bool = bool(s.get("solverReliable", true))
    if running and s.has("currentLineDeltaCm"):
        var track: float = float(s.get("currentLineDeltaCm", 0.0))
        _v166_compare_label.text = "TRACK %+.1fcm | SOLVER %.1fcm%s" % [track, solver_miss, "" if reliable else " CHECK"]
    elif s.has("readLineDeltaCm") and s.has("paceDeltaCm"):
        var lateral: float = float(s.get("readLineDeltaCm", 0.0))
        var pace: float = float(s.get("paceDeltaCm", 0.0))
        _v166_compare_label.text = "%s | %s" % [
            _v166_delta_word(lateral, "RIGHT", "LEFT"),
            _v166_delta_word(pace, "LONG", "SHORT")
        ]
    else:
        _v166_compare_label.text = "PHYSICS PATH %.2fm/s | SOLVER %.1fcm%s" % [
            float(s.get("recommendedBallSpeedMps", 0.0)), solver_miss, "" if reliable else " CHECK"
        ]

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)

    _v166_fallback_side = float(s.get("sideSlope", 0.0))
    _v166_fallback_long = float(s.get("longSlope", 0.0))
    _v166_refresh_terrain(str(s.get("terrainKey", "")))

    # Once the exact geometry is reconstructed, disable the inherited global-plane
    # displacement everywhere to avoid applying the same slope twice.
    if _v166_terrain_ready:
        for material_variant in [mat_green, mat_fringe, mat_rough]:
            var material := material_variant as ShaderMaterial
            if material != null:
                material.set_shader_parameter("side_slope", 0.0)
                material.set_shader_parameter("long_slope", 0.0)
        if _v163_green_blade_mat != null:
            _v163_green_blade_mat.set_shader_parameter("side_slope", 0.0)
            _v163_green_blade_mat.set_shader_parameter("long_slope", 0.0)
        if _v163_fringe_blade_mat != null:
            _v163_fringe_blade_mat.set_shader_parameter("side_slope", 0.0)
            _v163_fringe_blade_mat.set_shader_parameter("long_slope", 0.0)
        if _v164_grid_mat != null:
            _v164_grid_mat.set_shader_parameter("terrain_ready", 1.0)

    _v166_solver_ready = s.has("recommendedAimOffsetM") and s.has("predictedTrail")
    _v166_update_paths(s)
    _v165_update_hud(float(s.get("sideSlope", 0.0)), float(s.get("longSlope", 0.0)))
    _v166_update_compare_hud(s, bool(s.get("running", false)))
