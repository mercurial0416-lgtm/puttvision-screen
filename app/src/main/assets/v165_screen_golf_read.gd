extends "res://v164_friends_grid.gd"

# V165: exaggerated commercial screen-golf green read presentation.
# Presentation only. Android V135-V137 remain the sole physics authority.
# V165 upgrades the existing single V164 overlay shader instead of adding a
# stack of extra geometry so the Android/mobile render path stays predictable.

var _v165_panel: ColorRect
var _v165_aim_label: Label
var _v165_detail_label: Label
var _v165_read_intensity := 0.92
var _v165_enhanced_enabled := true
var _v165_recommended_offset := 0.0

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
uniform vec3 minor_color : source_color = vec3(0.63, 0.91, 0.92);
uniform vec3 major_color : source_color = vec3(0.88, 0.98, 0.98);
uniform vec3 flow_color : source_color = vec3(0.98, 0.88, 0.37);
uniform vec3 guide_color : source_color = vec3(1.00, 0.76, 0.12);

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
    float slope_pct = slope_mag * 100.0;
    vec2 downhill = slope_mag > 0.00005 ? slope_vec / slope_mag : vec2(0.0, 1.0);
    vec2 across = vec2(-downhill.y, downhill.x);
    float flow_amount = smoothstep(0.0015, 0.035, slope_mag);

    float travel = dot(grid_pos, downhill) * 5.15 - TIME * (0.82 + min(2.6, slope_mag * 52.0));
    float moving_band = pow(0.5 + 0.5 * sin(travel), 9.0);

    float cup_dist = distance(grid_pos, vec2(0.0, cup_local_z));
    float cup_focus = 1.0 - smoothstep(0.34, 1.40, cup_dist);
    float cup_ring = 1.0 - smoothstep(0.025, 0.080, abs(cup_dist - 0.54));
    float ball_dist = distance(grid_pos, ball_local);
    float ball_focus = 1.0 - smoothstep(0.28, 1.05, ball_dist);

    float corridor = 1.0 - smoothstep(0.10, 0.38, abs(grid_pos.x));
    float grid_alpha = minor_grid * 0.14 + major_grid * 0.46;
    grid_alpha *= mix(1.0, 0.76, corridor);
    grid_alpha *= (0.88 + cup_focus * 0.32 + ball_focus * 0.18);

    // 12.5 cm precision grid only around the cup. This makes the final meter
    // read like a dedicated screen-golf putting zone instead of cluttering the whole green.
    float micro_x = line_axis(grid_pos.x, 0.125, 0.0021);
    float micro_z = line_axis(grid_pos.y - cup_local_z, 0.125, 0.0021);
    float micro_grid = max(micro_x, micro_z) * cup_focus;

    // Elevation contour bands. Since the current course snapshot exposes a planar
    // side/long grade, these are real iso-height bands for that live plane.
    float elevation = grid_pos.x * side_slope + (-grid_pos.y) * long_slope;
    float contour_period = 0.0125;
    float contour_d = abs(fract(elevation / contour_period + 0.5) - 0.5) * contour_period;
    float contour = 1.0 - smoothstep(0.00045, 0.00145, contour_d);
    contour *= smoothstep(0.003, 0.012, slope_mag);

    // Animated downhill chevrons. Their direction and speed are driven directly by
    // the same live side/long slope used to deform the green.
    float arrow_along = dot(grid_pos, downhill);
    float arrow_cross = dot(grid_pos, across);
    float arrow_phase = fract(arrow_along / 0.68 - TIME * (0.32 + min(0.85, slope_mag * 18.0)));
    float arrow_a = abs(arrow_phase - 0.5) * 0.68;
    float arrow_c = abs(fract(arrow_cross / 0.78 + 0.5) - 0.5) * 0.78;
    float chevron = 1.0 - smoothstep(0.014, 0.036, abs(arrow_c - arrow_a * 0.72));
    chevron *= 1.0 - smoothstep(0.15, 0.24, arrow_a);
    chevron *= 1.0 - smoothstep(0.24, 0.34, arrow_c);
    chevron *= flow_amount;

    // Recommended read line: a smooth ball-to-cup arc with live break compensation.
    // It is only a presentation guide; the authoritative ball motion remains Android-side.
    float dz = cup_local_z - ball_local.y;
    float raw_t = abs(dz) > 0.001 ? (grid_pos.y - ball_local.y) / dz : -1.0;
    float t = clamp(raw_t, 0.0, 1.0);
    float path_window = smoothstep(-0.015, 0.025, raw_t) * (1.0 - smoothstep(0.975, 1.015, raw_t));
    float curve_x = mix(ball_local.x, 0.0, t) + recommended_offset * 4.0 * t * (1.0 - t);
    float guide_line = 1.0 - smoothstep(0.012, 0.038, abs(grid_pos.x - curve_x));
    float guide_dash = pow(0.5 + 0.5 * sin(t * max(abs(dz), 0.10) * 14.0 - TIME * 4.0), 8.0);
    float guide = guide_line * path_window * (0.58 + guide_dash * 0.42) * guide_fade;

    float flow_mask = max(minor_grid * 0.44, major_grid);
    float flow = flow_mask * moving_band * flow_amount * 0.58;

    vec3 base_grid = mix(minor_color, major_color, clamp(major_grid * 1.35, 0.0, 1.0));
    vec3 slope_cool = vec3(0.24, 0.92, 0.78);
    vec3 slope_mid = vec3(0.98, 0.88, 0.22);
    vec3 slope_hot = vec3(1.00, 0.34, 0.08);
    vec3 slope_color = mix(slope_cool, slope_mid, smoothstep(0.65, 2.00, slope_pct));
    slope_color = mix(slope_color, slope_hot, smoothstep(2.00, 4.00, slope_pct));

    vec3 color = mix(base_grid, flow_color, clamp(flow + cup_ring * 0.55, 0.0, 0.82));
    float enhanced_mix = clamp((contour * 0.50 + chevron * 0.92) * read_intensity * read_enabled, 0.0, 0.92);
    color = mix(color, slope_color, enhanced_mix);
    color = mix(color, guide_color, clamp(guide * 0.96 * read_enabled + cup_ring * 0.18, 0.0, 0.98));

    float edge_x = smoothstep(5.82, 5.45, abs(grid_pos.x));
    float edge_z = smoothstep(17.08, 16.40, abs(grid_pos.y));
    float edge_fade = edge_x * edge_z;

    float enhanced_alpha = (micro_grid * 0.18 + contour * 0.16 + chevron * 0.24) * read_intensity * read_enabled;
    float guide_alpha = guide * 0.72 * read_intensity * read_enabled;
    float alpha = (grid_alpha + flow * 0.34 + cup_ring * 0.16 + enhanced_alpha + guide_alpha) * strength * action_fade * edge_fade;

    ALBEDO = color;
    ALPHA = clamp(alpha, 0.0, 0.86);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 1
    return material

func _build_hud() -> void:
    super._build_hud()

    var layer := CanvasLayer.new()
    layer.name = "V165ScreenGolfReadHUD"
    layer.layer = 23
    add_child(layer)

    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    _v165_panel = ColorRect.new()
    _v165_panel.name = "V165PuttReadPanel"
    _v165_panel.position = Vector2(1454, 158)
    _v165_panel.size = Vector2(426, 72)
    _v165_panel.color = Color(0.020, 0.028, 0.033, 0.86)
    _v165_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(_v165_panel)

    var accent := ColorRect.new()
    accent.position = Vector2(0, 0)
    accent.size = Vector2(6, 72)
    accent.color = Color(1.0, 0.70, 0.10, 0.96)
    accent.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v165_panel.add_child(accent)

    _v165_aim_label = _v164_label(_v165_panel, Vector2(18, 4), Vector2(388, 32), 19, Color(1.0, 0.84, 0.34, 1.0))
    _v165_detail_label = _v164_label(_v165_panel, Vector2(18, 36), Vector2(388, 26), 13, Color(0.76, 0.88, 0.86, 0.92))

func _v165_read_level(side_pct: float, long_pct: float) -> String:
    var magnitude: float = sqrt(side_pct * side_pct + long_pct * long_pct)
    if magnitude < 0.55:
        return "SOFT"
    if magnitude < 1.55:
        return "MED"
    if magnitude < 2.75:
        return "STRONG"
    return "EXTREME"

func _v165_recommended_aim(s: Dictionary, side_pct: float, long_pct: float) -> float:
    # Prefer an authoritative upstream recommendation whenever one is supplied.
    for key in ["recommendedAimOffsetM", "aimOffsetM", "breakAimM"]:
        if s.has(key):
            var raw: Variant = s.get(key)
            if raw is int or raw is float:
                return clamp(float(raw), -2.20, 2.20)

    # Visual fallback only. It never feeds the physics engine. Remaining distance squared
    # gives the familiar stronger long-putt read while keeping short putts restrained.
    var ball_y: float = float(s.get("ballY", 0.0))
    var remaining: float = clamp(abs(target_distance - ball_y), 0.35, 18.0)
    var grade_boost: float = 1.0 + clamp(abs(long_pct) * 0.035, 0.0, 0.22)
    return clamp(side_pct * remaining * remaining * 0.0044 * grade_boost, -1.80, 1.80)

func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    if _v165_aim_label == null:
        return

    var side_abs: float = abs(side_pct)
    var aim_text := "AIM CENTER"
    if abs(_v165_recommended_offset) >= 0.015:
        var aim_dir := "R" if side_pct > 0.0 else "L"
        aim_text = "AIM %s %.2f m" % [aim_dir, abs(_v165_recommended_offset)]

    var break_dir := "STRAIGHT"
    if side_abs >= 0.03:
        break_dir = "BREAK L" if side_pct > 0.0 else "BREAK R"

    _v165_aim_label.text = "%s   |   %s" % [aim_text, _v165_read_level(side_pct, long_pct)]
    _v165_detail_label.text = "%s %.2f%%   |   LIVE FLOW | CONTOUR | CUP 0.125m" % [break_dir, side_abs]

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)

    var side_pct: float = float(s.get("sideSlope", 0.0))
    var long_pct: float = float(s.get("longSlope", 0.0))
    _v165_enhanced_enabled = bool(s.get("enhancedReadEnabled", true))

    var raw_intensity: Variant = s.get("readIntensity", 0.92)
    if raw_intensity is int or raw_intensity is float:
        _v165_read_intensity = clamp(float(raw_intensity), 0.0, 1.0)
    else:
        _v165_read_intensity = 0.92

    _v165_recommended_offset = _v165_recommended_aim(s, side_pct, long_pct)
    var running: bool = bool(s.get("running", false))

    if _v164_grid_mat != null:
        _v164_grid_mat.set_shader_parameter("read_enabled", 1.0 if _v165_enhanced_enabled else 0.0)
        _v164_grid_mat.set_shader_parameter("read_intensity", _v165_read_intensity)
        _v164_grid_mat.set_shader_parameter("recommended_offset", _v165_recommended_offset)
        _v164_grid_mat.set_shader_parameter("guide_fade", 0.18 if running else 1.0)

    if _v165_panel != null:
        _v165_panel.visible = _v165_enhanced_enabled and _v164_grid_enabled

    _v165_update_hud(side_pct, long_pct)
