extends "res://v163_open_grass.gd"

# V164: screen-golf green-reading presentation inspired by the visual language of
# commercial screen-golf putting UIs. Rendering only: Android V135-V137 remain
# the sole physics authority.

const V164_GREEN_WIDTH := 11.72
const V164_GREEN_DEPTH := 34.40
const V164_GREEN_CENTER_Z := -14.25

var _v164_grid: MeshInstance3D
var _v164_grid_mat: ShaderMaterial
var _v164_break_label: Label
var _v164_grid_label: Label
var _v164_grade_label: Label
var _v164_side_bar: ColorRect
var _v164_long_bar: ColorRect
var _v164_grid_strength := 0.62
var _v164_grid_enabled := true
var _v164_grid_mode := "MED"

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
uniform vec3 minor_color : source_color = vec3(0.63, 0.91, 0.92);
uniform vec3 major_color : source_color = vec3(0.88, 0.98, 0.98);
uniform vec3 flow_color : source_color = vec3(0.98, 0.88, 0.37);

varying vec2 grid_pos;

float line_axis(float coord, float spacing, float width_m) {
    float d = abs(fract(coord / spacing + 0.5) - 0.5) * spacing;
    return 1.0 - smoothstep(width_m, width_m * 1.85, d);
}

void vertex() {
    grid_pos = VERTEX.xz;
    VERTEX.y += VERTEX.x * side_slope + (-VERTEX.z) * long_slope;
}

void fragment() {
    float minor_x = line_axis(grid_pos.x, 0.25, 0.0032);
    float minor_z = line_axis(grid_pos.y, 0.25, 0.0032);
    float major_x = line_axis(grid_pos.x, 1.00, 0.0105);
    float major_z = line_axis(grid_pos.y, 1.00, 0.0105);
    float minor_grid = max(minor_x, minor_z);
    float major_grid = max(major_x, major_z);

    vec2 slope_vec = vec2(-side_slope, long_slope);
    float slope_mag = length(slope_vec);
    vec2 downhill = slope_mag > 0.00005 ? slope_vec / slope_mag : vec2(0.0, 1.0);
    float travel = dot(grid_pos, downhill) * 5.15 - TIME * (0.82 + min(2.6, slope_mag * 52.0));
    float moving_band = pow(0.5 + 0.5 * sin(travel), 9.0);
    float flow_amount = smoothstep(0.0015, 0.035, slope_mag);

    float cup_dist = distance(grid_pos, vec2(0.0, cup_local_z));
    float cup_focus = 1.0 - smoothstep(0.34, 1.36, cup_dist);
    float cup_ring = 1.0 - smoothstep(0.025, 0.080, abs(cup_dist - 0.54));
    float ball_dist = distance(grid_pos, ball_local);
    float ball_focus = 1.0 - smoothstep(0.28, 1.05, ball_dist);

    float corridor = 1.0 - smoothstep(0.10, 0.38, abs(grid_pos.x));
    float grid_alpha = minor_grid * 0.14 + major_grid * 0.46;
    grid_alpha *= mix(1.0, 0.76, corridor);
    grid_alpha *= (0.88 + cup_focus * 0.32 + ball_focus * 0.18);

    float flow_mask = max(minor_grid * 0.44, major_grid);
    float flow = flow_mask * moving_band * flow_amount * 0.58;
    vec3 base_grid = mix(minor_color, major_color, clamp(major_grid * 1.35, 0.0, 1.0));
    vec3 color = mix(base_grid, flow_color, clamp(flow + cup_ring * 0.55, 0.0, 0.82));

    float edge_x = smoothstep(5.82, 5.45, abs(grid_pos.x));
    float edge_z = smoothstep(17.08, 16.40, abs(grid_pos.y));
    float edge_fade = edge_x * edge_z;
    float alpha = (grid_alpha + flow * 0.34 + cup_ring * 0.16) * strength * action_fade * edge_fade;

    ALBEDO = color;
    ALPHA = clamp(alpha, 0.0, 0.78);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 1
    return material

func _v164_build_grid() -> void:
    _v164_grid = MeshInstance3D.new()
    _v164_grid.name = "V164FriendsGreenGrid"
    var mesh := PlaneMesh.new()
    mesh.size = Vector2(V164_GREEN_WIDTH, V164_GREEN_DEPTH)
    mesh.subdivide_width = 30
    mesh.subdivide_depth = 86
    _v164_grid.mesh = mesh
    _v164_grid_mat = _v164_make_grid_material()
    _v164_grid.material_override = _v164_grid_mat
    _v164_grid.position = Vector3(0.0, 0.0026, V164_GREEN_CENTER_Z)
    _v164_grid.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
    add_child(_v164_grid)

func _v164_tune_aim_line() -> void:
    if aim_line == null:
        return
    var material := StandardMaterial3D.new()
    material.albedo_color = Color(0.98, 0.83, 0.22, 0.92)
    material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    material.no_depth_test = false
    material.render_priority = 2
    aim_line.material_override = material

func _build_course() -> void:
    super._build_course()
    _v164_build_grid()
    _v164_tune_aim_line()

func _v164_label(parent: Node, pos: Vector2, size: Vector2, font_size: int, color: Color, align: HorizontalAlignment = HORIZONTAL_ALIGNMENT_LEFT) -> Label:
    var label := Label.new()
    label.position = pos
    label.size = size
    label.horizontal_alignment = align
    label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    label.add_theme_font_size_override("font_size", font_size)
    label.add_theme_color_override("font_color", color)
    label.add_theme_color_override("font_shadow_color", Color(0.0, 0.0, 0.0, 0.72))
    label.add_theme_constant_override("shadow_offset_x", 1)
    label.add_theme_constant_override("shadow_offset_y", 1)
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    parent.add_child(label)
    return label

func _v164_bar(parent: Node, pos: Vector2, size: Vector2, color: Color) -> ColorRect:
    var bar := ColorRect.new()
    bar.position = pos
    bar.size = size
    bar.color = color
    bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
    parent.add_child(bar)
    return bar

func _build_hud() -> void:
    super._build_hud()
    var layer := CanvasLayer.new()
    layer.name = "V164FriendsReadHUD"
    layer.layer = 22
    add_child(layer)

    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    var panel := ColorRect.new()
    panel.name = "GreenReadPanel"
    panel.position = Vector2(1454, 24)
    panel.size = Vector2(426, 126)
    panel.color = Color(0.022, 0.032, 0.038, 0.88)
    panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(panel)

    var accent := ColorRect.new()
    accent.position = Vector2(0, 0)
    accent.size = Vector2(6, 126)
    accent.color = Color(0.58, 0.88, 0.84, 0.96)
    accent.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_child(accent)

    var title := _v164_label(panel, Vector2(18, 5), Vector2(180, 30), 17, Color(0.71, 0.94, 0.90, 1.0))
    title.text = "GREEN READ"
    _v164_grid_label = _v164_label(panel, Vector2(218, 5), Vector2(188, 30), 14, Color(0.83, 0.87, 0.86, 0.88), HORIZONTAL_ALIGNMENT_RIGHT)
    _v164_break_label = _v164_label(panel, Vector2(18, 36), Vector2(388, 30), 20, Color(0.98, 0.91, 0.52, 1.0))
    _v164_grade_label = _v164_label(panel, Vector2(18, 67), Vector2(388, 24), 14, Color(0.86, 0.90, 0.89, 0.92))

    var side_track := ColorRect.new()
    side_track.position = Vector2(18, 100)
    side_track.size = Vector2(184, 7)
    side_track.color = Color(1.0, 1.0, 1.0, 0.12)
    panel.add_child(side_track)
    _v164_side_bar = _v164_bar(side_track, Vector2(92, 0), Vector2(1, 7), Color(0.63, 0.91, 0.92, 0.96))

    var long_track := ColorRect.new()
    long_track.position = Vector2(222, 100)
    long_track.size = Vector2(184, 7)
    long_track.color = Color(1.0, 1.0, 1.0, 0.12)
    panel.add_child(long_track)
    _v164_long_bar = _v164_bar(long_track, Vector2(92, 0), Vector2(1, 7), Color(0.98, 0.83, 0.38, 0.96))

func _v164_strength_from_snapshot(s: Dictionary) -> float:
    var mode_text: String = str(s.get("gridMode", "")).strip_edges().to_lower()
    if mode_text == "off":
        _v164_grid_mode = "OFF"
        return 0.0
    if mode_text == "low" or mode_text == "weak":
        _v164_grid_mode = "LOW"
        return 0.38
    if mode_text == "high" or mode_text == "strong":
        _v164_grid_mode = "HIGH"
        return 0.88
    if mode_text == "medium" or mode_text == "med":
        _v164_grid_mode = "MED"
        return 0.62

    var raw: Variant = s.get("gridStrength", 0.62)
    if raw is int or raw is float:
        var numeric: float = clamp(float(raw), 0.0, 1.0)
        if numeric < 0.12:
            _v164_grid_mode = "OFF"
        elif numeric < 0.50:
            _v164_grid_mode = "LOW"
        elif numeric < 0.78:
            _v164_grid_mode = "MED"
        else:
            _v164_grid_mode = "HIGH"
        return numeric

    var text: String = str(raw).strip_edges().to_lower()
    if text == "off":
        _v164_grid_mode = "OFF"
        return 0.0
    if text == "low" or text == "weak":
        _v164_grid_mode = "LOW"
        return 0.38
    if text == "high" or text == "strong":
        _v164_grid_mode = "HIGH"
        return 0.88
    _v164_grid_mode = "MED"
    return 0.62

func _v164_update_read_hud(side_pct: float, long_pct: float) -> void:
    if _v164_break_label == null:
        return

    var side_abs: float = abs(side_pct)
    var break_text := "STRAIGHT"
    if side_abs >= 0.03:
        break_text = "BREAK L %.2f%%" % side_abs if side_pct > 0.0 else "BREAK R %.2f%%" % side_abs

    var grade_text := "FLAT"
    if long_pct > 0.03:
        grade_text = "UPHILL %.2f%%" % abs(long_pct)
    elif long_pct < -0.03:
        grade_text = "DOWNHILL %.2f%%" % abs(long_pct)

    _v164_break_label.text = break_text
    _v164_grade_label.text = "%s   |   SIDE %+.2f%%   F/B %+.2f%%" % [grade_text, side_pct, long_pct]
    _v164_grid_label.text = "GRID %s  0.25 / 1.00m" % _v164_grid_mode

    if _v164_side_bar != null:
        var side_px: float = clamp(side_pct / 4.0, -1.0, 1.0) * 86.0
        _v164_side_bar.position.x = 92.0 + min(0.0, side_px)
        _v164_side_bar.size.x = max(1.0, abs(side_px))
    if _v164_long_bar != null:
        var long_px: float = clamp(long_pct / 4.0, -1.0, 1.0) * 86.0
        _v164_long_bar.position.x = 92.0 + min(0.0, long_px)
        _v164_long_bar.size.x = max(1.0, abs(long_px))

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)

    var side_pct: float = float(s.get("sideSlope", 0.0))
    var long_pct: float = float(s.get("longSlope", 0.0))
    var side: float = side_pct * 0.01
    var longitudinal: float = long_pct * 0.01
    _v164_grid_enabled = bool(s.get("gridEnabled", true))
    _v164_grid_strength = _v164_strength_from_snapshot(s)
    if _v164_grid_strength <= 0.01:
        _v164_grid_enabled = false

    if _v164_grid != null:
        _v164_grid.visible = _v164_grid_enabled
    if _v164_grid_mat != null:
        _v164_grid_mat.set_shader_parameter("side_slope", side)
        _v164_grid_mat.set_shader_parameter("long_slope", longitudinal)
        _v164_grid_mat.set_shader_parameter("cup_local_z", V164_GREEN_CENTER_Z * -1.0 - target_distance)
        var ball_local := Vector2(float(s.get("ballX", 0.0)), V164_GREEN_CENTER_Z * -1.0 - float(s.get("ballY", 0.0)))
        _v164_grid_mat.set_shader_parameter("ball_local", ball_local)
        _v164_grid_mat.set_shader_parameter("strength", _v164_grid_strength)
        _v164_grid_mat.set_shader_parameter("action_fade", 0.72 if bool(s.get("running", false)) else 1.0)

    _v164_update_read_hud(side_pct, long_pct)
