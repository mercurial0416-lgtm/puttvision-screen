extends "res://v166_true_physics_green_read.gd"

# V167: broadcast-quality presentation cleanup.
# Rendering only. Android V135-V137 and GreenReadAdvisor remain authoritative.
# The goal is to remove the remaining technical-demo look without adding risky
# Android render features (no external 3D models, dynamic shadow maps or SSR).

func _build_materials() -> void:
    super._build_materials()

    # Lift the green out of the near-black broadcast floor while preserving the
    # fine bentgrass breakup introduced in V162. A tiny mowing signal gives the
    # eye scale without returning to arcade-bright stripes.
    if mat_green is ShaderMaterial:
        mat_green.set_shader_parameter("base_color", Vector3(0.190, 0.335, 0.150))
        mat_green.set_shader_parameter("lane_strength", 0.0040)
        mat_green.set_shader_parameter("texture_scale", 162.0)
        mat_green.set_shader_parameter("normal_depth", 0.088)
        mat_green.set_shader_parameter("roughness_base", 0.835)
        mat_green.set_shader_parameter("micro_strength", 0.027)
    if mat_fringe is ShaderMaterial:
        mat_fringe.set_shader_parameter("base_color", Vector3(0.150, 0.270, 0.132))
        mat_fringe.set_shader_parameter("lane_strength", 0.0025)
        mat_fringe.set_shader_parameter("roughness_base", 0.875)
    if mat_rough is ShaderMaterial:
        mat_rough.set_shader_parameter("base_color", Vector3(0.112, 0.218, 0.118))
        mat_rough.set_shader_parameter("roughness_base", 0.915)

    # Reduce the toy-like color separation in the clubhouse.
    if mat_house is StandardMaterial3D:
        mat_house.albedo_color = Color("#aaa597")
        mat_house.roughness = 0.94
    if mat_roof is StandardMaterial3D:
        mat_roof.albedo_color = Color("#32383b")
        mat_roof.roughness = 0.90
    if mat_window is StandardMaterial3D:
        mat_window.albedo_color = Color("#20333a")
        mat_window.roughness = 0.34
    if mat_stone is StandardMaterial3D:
        mat_stone.albedo_color = Color("#77766f")
        mat_stone.roughness = 0.97

    # Less saturated leaf palette; the extra V167 instances below provide shape.
    _detail_leaf_a = _v161_leaf_material(Color("#263729"), Color("#526047"), 2.0)
    _detail_leaf_b = _v161_leaf_material(Color("#203124"), Color("#465441"), 7.0)
    _detail_leaf_c = _v161_leaf_material(Color("#304133"), Color("#606b55"), 13.0)

func _build_environment() -> void:
    super._build_environment()

    var env_node := get_node_or_null("V160NaturalFinishEnvironment") as WorldEnvironment
    if env_node != null and env_node.environment != null:
        var env := env_node.environment
        env.adjustment_brightness = 0.940
        env.adjustment_contrast = 1.045
        env.adjustment_saturation = 0.805
        env.ambient_light_energy = 0.385

    var sun := get_node_or_null("V160NaturalSun") as DirectionalLight3D
    if sun != null:
        sun.light_energy = 0.96
        sun.light_color = Color("#fff0d9")

    _v167_build_vignette()

# Replace the obvious spherical canopy clusters with more, smaller, irregular
# MultiMesh leaves. Triangle count stays in the same mobile-safe order because
# each primitive is deliberately lower-poly than V162.
func _v162_leaf_multimesh(parent: Node3D, material: Material, s: float, count: int, phase: float, y_base: float, radius: float) -> void:
    var leaf_mesh := SphereMesh.new()
    leaf_mesh.radius = 0.5
    leaf_mesh.height = 1.0
    leaf_mesh.radial_segments = 10
    leaf_mesh.rings = 5

    var mm := MultiMesh.new()
    mm.transform_format = MultiMesh.TRANSFORM_3D
    mm.mesh = leaf_mesh
    var dense_count: int = int(round(float(count) * 1.72))
    mm.instance_count = dense_count

    for i in range(dense_count):
        var fi: float = float(i)
        var angle: float = deg_to_rad(fmod(fi * 137.507 + phase * 51.0, 360.0))
        var shell: float = 0.34 + float((i * 11) % 17) * 0.036
        var x: float = cos(angle) * radius * shell
        var z: float = sin(angle) * radius * (0.68 + float(i % 4) * 0.035) * shell
        var y: float = y_base + sin(fi * 1.37 + phase) * 0.20 + float(i % 7) * 0.035
        var sx: float = 0.235 + float((i * 3) % 9) * 0.014
        var sy: float = 0.175 + float((i * 5) % 8) * 0.013
        var sz: float = 0.245 + float((i * 7) % 9) * 0.014
        var basis := Basis.IDENTITY
        basis = basis.rotated(Vector3.UP, angle * 0.43 + phase)
        basis = basis.rotated(Vector3.RIGHT, sin(fi * 0.91 + phase) * 0.20)
        basis = basis.rotated(Vector3.FORWARD, cos(fi * 0.53 + phase) * 0.12)
        basis = basis.scaled(Vector3(sx * s, sy * s, sz * s))
        mm.set_instance_transform(i, Transform3D(basis, Vector3(x, y, z) * s))

    var instance := MultiMeshInstance3D.new()
    instance.name = "V167NaturalLeafScatter"
    instance.multimesh = mm
    instance.material_override = material
    instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    parent.add_child(instance)

# A commercial putting overlay should support the surface, not turn the whole
# green into graph paper. Keep exact V166 terrain/flow data but de-emphasize the
# outer field and reserve strong detail for ball/cup/read corridor.
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
uniform vec3 minor_color : source_color = vec3(0.54, 0.78, 0.77);
uniform vec3 major_color : source_color = vec3(0.76, 0.90, 0.88);
uniform vec3 flow_color : source_color = vec3(0.98, 0.79, 0.25);

varying vec2 grid_pos;
varying float local_height;
varying vec2 local_slope_pct;

float line_axis_aa(float coord, float spacing, float width_m) {
    float d = abs(fract(coord / spacing + 0.5) - 0.5) * spacing;
    float aa = max(fwidth(coord) * 0.80, width_m * 0.48);
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
    float px = max(fwidth(grid_pos.x), fwidth(grid_pos.y));
    float minor_lod = 1.0 - smoothstep(0.022, 0.075, px);
    float micro_lod = 1.0 - smoothstep(0.009, 0.036, px);

    float minor_grid = max(
        line_axis_aa(grid_pos.x, 0.25, 0.0025),
        line_axis_aa(grid_pos.y, 0.25, 0.0025)
    ) * minor_lod;
    float major_grid = max(
        line_axis_aa(grid_pos.x, 1.00, 0.0080),
        line_axis_aa(grid_pos.y, 1.00, 0.0080)
    );

    float lateral_focus = mix(0.24, 1.0, 1.0 - smoothstep(1.15, 4.60, abs(grid_pos.x)));
    float ball_dist = distance(grid_pos, ball_local);
    float cup_dist = distance(grid_pos, vec2(0.0, cup_local_z));
    float endpoint_focus = max(
        1.0 - smoothstep(0.30, 1.30, ball_dist),
        1.0 - smoothstep(0.32, 1.45, cup_dist)
    );
    float field_focus = clamp(lateral_focus + endpoint_focus * 0.22, 0.0, 1.0);

    vec2 slope_vec = vec2(local_slope_pct.x, -local_slope_pct.y);
    float slope_pct = length(slope_vec);
    vec2 downhill = slope_pct > 0.0005 ? slope_vec / slope_pct : vec2(0.0, -1.0);
    vec2 across = vec2(-downhill.y, downhill.x);
    float flow_amount = smoothstep(0.22, 1.35, slope_pct);

    float travel = dot(grid_pos, downhill) * 4.55 - TIME * (0.62 + min(2.1, slope_pct * 0.42));
    float moving_band = pow(0.5 + 0.5 * sin(travel), 11.0);
    float flow = max(minor_grid * 0.25, major_grid * 0.72) * moving_band * flow_amount;

    float cup_focus = 1.0 - smoothstep(0.28, 1.12, cup_dist);
    float cup_ring = 1.0 - smoothstep(0.026, 0.072, abs(cup_dist - 0.54));
    float micro_grid = max(
        line_axis_aa(grid_pos.x, 0.125, 0.0017),
        line_axis_aa(grid_pos.y - cup_local_z, 0.125, 0.0017)
    ) * cup_focus * micro_lod;

    float contour_period = 0.0125;
    float contour_d = abs(fract(local_height / contour_period + 0.5) - 0.5) * contour_period;
    float contour_aa = max(fwidth(local_height) * 1.40, 0.00032);
    float contour = 1.0 - smoothstep(0.00046, 0.00046 + contour_aa, contour_d);
    contour *= smoothstep(0.35, 1.10, slope_pct);

    float arrow_along = dot(grid_pos, downhill);
    float arrow_cross = dot(grid_pos, across);
    float arrow_phase = fract(arrow_along / 0.82 - TIME * (0.22 + min(0.90, slope_pct * 0.10)));
    float arrow_a = abs(arrow_phase - 0.5) * 0.82;
    float arrow_c = abs(fract(arrow_cross / 0.96 + 0.5) - 0.5) * 0.96;
    float chevron = 1.0 - smoothstep(0.012, 0.032 + px * 0.30, abs(arrow_c - arrow_a * 0.70));
    chevron *= 1.0 - smoothstep(0.16, 0.25, arrow_a);
    chevron *= 1.0 - smoothstep(0.27, 0.38, arrow_c);
    chevron *= flow_amount;

    vec3 base_grid = mix(minor_color, major_color, clamp(major_grid * 1.20, 0.0, 1.0));
    vec3 slope_cool = vec3(0.26, 0.82, 0.70);
    vec3 slope_mid = vec3(0.96, 0.78, 0.22);
    vec3 slope_hot = vec3(0.96, 0.34, 0.12);
    vec3 slope_color = mix(slope_cool, slope_mid, smoothstep(0.75, 2.20, slope_pct));
    slope_color = mix(slope_color, slope_hot, smoothstep(2.20, 4.00, slope_pct));

    vec3 color = mix(base_grid, flow_color, clamp(flow * 0.64 + cup_ring * 0.38, 0.0, 0.70));
    color = mix(color, slope_color, clamp((contour * 0.34 + chevron * 0.66) * read_intensity * read_enabled, 0.0, 0.70));

    float edge_x = 1.0 - smoothstep(5.25, 5.84, abs(grid_pos.x));
    float edge_z = 1.0 - smoothstep(16.35, 17.10, abs(grid_pos.y));
    float edge_fade = edge_x * edge_z;
    float grid_alpha = minor_grid * 0.055 + major_grid * 0.255;
    float read_alpha = micro_grid * 0.080 + contour * 0.105 + chevron * 0.150 + flow * 0.115 + cup_ring * 0.105;
    float alpha = (grid_alpha + read_alpha * read_intensity * read_enabled) * strength * action_fade * field_focus * edge_fade;

    ALBEDO = color;
    ALPHA = clamp(alpha, 0.0, 0.55);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 1
    return material

func _build_course() -> void:
    super._build_course()
    # The straight reference line was visually reading like a laser. Solver and
    # actual paths remain strong; the neutral center reference becomes secondary.
    if aim_line != null:
        var reference_mat := StandardMaterial3D.new()
        reference_mat.albedo_color = Color(0.92, 0.88, 0.58, 0.34)
        reference_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
        reference_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
        reference_mat.no_depth_test = false
        reference_mat.render_priority = 2
        aim_line.material_override = reference_mat

func _update_aim_line(distance_m: float) -> void:
    if aim_line == null:
        return
    var mesh := BoxMesh.new()
    mesh.size = Vector3(0.0045, 0.0014, max(0.3, distance_m - 0.20))
    aim_line.mesh = mesh
    aim_line.position = Vector3(0.0, 0.0038, -distance_m * 0.5)

func _build_target() -> void:
    super._build_target()
    if target_root == null:
        return

    # The inherited PrismMesh is almost edge-on to the broadcast camera.
    for child_node in target_root.get_children():
        if child_node is MeshInstance3D:
            var mesh_node := child_node as MeshInstance3D
            if mesh_node.mesh is PrismMesh:
                mesh_node.visible = false

    _v167_build_flag_cloth()
    _v167_build_cup_depth()

func _v167_build_flag_cloth() -> void:
    var vertices := PackedVector3Array()
    var normals := PackedVector3Array()
    var indices := PackedInt32Array()
    var columns := 5
    for i in range(columns):
        var t: float = float(i) / float(columns - 1)
        var x: float = t * 0.40
        var wave: float = sin(t * PI * 1.35) * 0.015
        vertices.append(Vector3(x, 0.105, wave))
        vertices.append(Vector3(x, -0.105, wave + sin(t * PI * 2.0) * 0.004))
        normals.append(Vector3(0.0, 0.0, 1.0))
        normals.append(Vector3(0.0, 0.0, 1.0))
    for i in range(columns - 1):
        var a: int = i * 2
        var b: int = a + 1
        var c: int = a + 2
        var d: int = a + 3
        indices.append_array(PackedInt32Array([a, b, c, c, b, d]))

    var arrays := []
    arrays.resize(Mesh.ARRAY_MAX)
    arrays[Mesh.ARRAY_VERTEX] = vertices
    arrays[Mesh.ARRAY_NORMAL] = normals
    arrays[Mesh.ARRAY_INDEX] = indices
    var mesh := ArrayMesh.new()
    mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)

    var flag := MeshInstance3D.new()
    flag.name = "V167BroadcastFlagCloth"
    flag.mesh = mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = Color("#c93437")
    material.roughness = 0.62
    material.cull_mode = BaseMaterial3D.CULL_DISABLED
    flag.material_override = material
    flag.position = Vector3(0.008, 1.675, 0.006)
    flag.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    target_root.add_child(flag)

func _v167_build_cup_depth() -> void:
    var cup := MeshInstance3D.new()
    cup.name = "V167CupInterior"
    var cup_mesh := CylinderMesh.new()
    cup_mesh.top_radius = 0.0515
    cup_mesh.bottom_radius = 0.0515
    cup_mesh.height = 0.060
    cup_mesh.radial_segments = 48
    cup.mesh = cup_mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = Color("#101514")
    material.roughness = 0.98
    cup.material_override = material
    cup.position = Vector3(0.0, -0.030, 0.0)
    cup.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    target_root.add_child(cup)

func _build_hud() -> void:
    super._build_hud()

    # Remove the older duplicated green-read card and keep one authoritative
    # solver/read card. Top-center retains distance / stimp / live speed only.
    var old_panel := find_child("GreenReadPanel", true, false)
    if old_panel is CanvasItem:
        (old_panel as CanvasItem).visible = false

    if slope_label != null:
        slope_label.visible = false
    if wait_label != null:
        wait_label.visible = false
    _v167_hide_legacy_top_left(self)

    if _v165_panel != null:
        _v165_panel.position = Vector2(1438, 24)
        _v165_panel.size = Vector2(442, 112)
        _v165_panel.color = Color(0.018, 0.026, 0.030, 0.76)
        if _v165_aim_label != null:
            _v165_aim_label.position = Vector2(18, 6)
            _v165_aim_label.size = Vector2(404, 32)
            _v165_aim_label.add_theme_font_size_override("font_size", 18)
        if _v165_detail_label != null:
            _v165_detail_label.position = Vector2(18, 38)
            _v165_detail_label.size = Vector2(404, 26)
            _v165_detail_label.add_theme_font_size_override("font_size", 12)
        if _v166_compare_label != null:
            _v166_compare_label.position = Vector2(18, 76)
            _v166_compare_label.size = Vector2(404, 26)
            _v166_compare_label.add_theme_font_size_override("font_size", 12)

        for child_node in _v165_panel.get_children():
            if child_node is ColorRect:
                var rect := child_node as ColorRect
                if rect.position.x <= 1.0 and rect.size.x <= 8.0:
                    rect.size.y = 112.0
                elif rect.size.y <= 2.0 and rect.size.x > 200.0:
                    rect.position = Vector2(18, 70)
                    rect.size.x = 404.0
                    rect.color.a = 0.08

func _v167_hide_legacy_top_left(node: Node) -> void:
    for child_node in node.get_children():
        if child_node is ColorRect:
            var rect := child_node as ColorRect
            if (
                rect.position.x <= 42.0 and rect.position.y <= 42.0
                and rect.size.x >= 250.0 and rect.size.x <= 520.0
                and rect.size.y >= 36.0 and rect.size.y <= 92.0
            ):
                rect.visible = false
        _v167_hide_legacy_top_left(child_node)

func _v167_build_vignette() -> void:
    var layer := CanvasLayer.new()
    layer.name = "V167LensFinish"
    layer.layer = 19
    add_child(layer)
    var rect := ColorRect.new()
    rect.set_anchors_preset(Control.PRESET_FULL_RECT)
    rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
    rect.color = Color.WHITE
    var shader := Shader.new()
    shader.code = """
shader_type canvas_item;
render_mode unshaded;
void fragment(){
    vec2 p=(UV-vec2(0.5))*2.0;
    float edge=smoothstep(0.58,1.28,length(p*vec2(0.83,1.0)));
    float bottom=smoothstep(0.72,1.0,UV.y);
    COLOR=vec4(0.008,0.012,0.010,clamp(edge*0.095+bottom*0.025,0.0,0.115));
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    rect.material = material
    layer.add_child(rect)

# Higher, slightly longer-lens framing makes the green read as a real putting
# surface instead of a giant floor plane. Cup-action camera still gets close.
func _update_camera(ball_world: Vector3, running: bool, phase: String, distance_to_cup: float, immediate: bool, delta: float) -> void:
    var desired_pos: Vector3
    var desired_look: Vector3
    var desired_fov: float = 42.0
    var cup_world := Vector3(0.0, last_cup_z + 0.026, -target_distance)
    var cup_action: bool = phase == "RIM" or phase == "DROP" or phase == "SETTLED" or distance_to_cup < 0.78

    if cup_action:
        desired_pos = cup_world + Vector3(0.88, 0.50, 0.86)
        desired_look = cup_world + Vector3(0.0, 0.025, 0.0)
        desired_fov = 38.5
    elif running:
        var forward_to_cup := cup_world - ball_world
        forward_to_cup.y = 0.0
        if forward_to_cup.length() < 0.01:
            forward_to_cup = Vector3(0.0, 0.0, -1.0)
        forward_to_cup = forward_to_cup.normalized()
        desired_pos = ball_world - forward_to_cup * 1.68 + Vector3(0.0, 0.54, 0.0)
        var lead: float = min(1.65, max(0.48, distance_to_cup * 0.40))
        desired_look = ball_world + forward_to_cup * lead + Vector3(0.0, 0.035, 0.0)
        desired_fov = 41.5
    else:
        desired_pos = Vector3(0.0, 0.62, 1.72)
        var look_distance: float = min(6.3, max(2.9, target_distance * 0.68))
        desired_look = Vector3(0.0, 0.055, -look_distance)
        desired_fov = 42.0

    if immediate:
        camera_pos = desired_pos
        camera_look = desired_look
    else:
        var pos_alpha: float = 1.0 - exp(-delta * (6.2 if cup_action else 4.3))
        var look_alpha: float = 1.0 - exp(-delta * (7.4 if cup_action else 5.0))
        camera_pos = camera_pos.lerp(desired_pos, pos_alpha)
        camera_look = camera_look.lerp(desired_look, look_alpha)
    camera.position = camera_pos
    camera.fov = lerp(camera.fov, desired_fov, 1.0 if immediate else min(1.0, delta * 4.0))
    camera.look_at(camera_look, Vector3.UP)
