extends "res://v167_final_polish.gd"

# V168: commercial screen-golf grid scale/read presentation.
# The physical 1.0 m grid stays metrically correct, but the presentation no longer
# reads like a giant checkerboard. Quarter-metre information becomes short scale
# ticks, the cup gets a local precision grid, and animated downhill beads carry
# most of the break information. Android V135-V137 remain authoritative.

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
uniform vec3 grid_color : source_color = vec3(0.72, 0.88, 0.86);
uniform vec3 tick_color : source_color = vec3(0.60, 0.80, 0.78);
uniform vec3 flow_cool : source_color = vec3(0.30, 0.92, 0.76);
uniform vec3 flow_mid : source_color = vec3(0.98, 0.82, 0.25);
uniform vec3 flow_hot : source_color = vec3(1.00, 0.38, 0.12);

varying vec2 grid_pos;
varying float local_height;
varying vec2 local_slope_pct;

float line_axis_aa(float coord, float spacing, float width_m) {
    float d = abs(fract(coord / spacing + 0.5) - 0.5) * spacing;
    float aa = max(fwidth(coord) * 0.82, width_m * 0.52);
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

    // Metric backbone: one square is still exactly 1.0 m. The line is intentionally
    // thin and low-alpha so scale is available without dominating the green.
    float major_x = line_axis_aa(grid_pos.x, 1.0, 0.0044);
    float major_z = line_axis_aa(grid_pos.y, 1.0, 0.0044);
    float major_grid = max(major_x, major_z);

    float ball_dist = distance(grid_pos, ball_local);
    float cup_dist = distance(grid_pos, vec2(0.0, cup_local_z));
    float ball_focus = 1.0 - smoothstep(0.30, 1.15, ball_dist);
    float cup_focus = 1.0 - smoothstep(0.30, 1.32, cup_dist);
    float endpoint_focus = max(ball_focus, cup_focus);

    // Quarter-metre information is shown as short ruler ticks attached to the
    // one-metre lines, instead of a second full checkerboard across the whole green.
    float quarter_x = line_axis_aa(grid_pos.x, 0.25, 0.0017);
    float quarter_z = line_axis_aa(grid_pos.y, 0.25, 0.0017);
    float near_major_x = line_axis_aa(grid_pos.x, 1.0, 0.050);
    float near_major_z = line_axis_aa(grid_pos.y, 1.0, 0.050);
    float quarter_ticks = max(quarter_x * near_major_z, quarter_z * near_major_x);
    float tick_lod = 1.0 - smoothstep(0.025, 0.085, px);
    quarter_ticks *= tick_lod;

    // A subtle 25 cm local ruler around the ball helps establish scale at address.
    float ball_quarter = max(quarter_x, quarter_z) * ball_focus * tick_lod;

    // The final cup zone alone receives the 12.5 cm precision grid.
    float micro_lod = 1.0 - smoothstep(0.010, 0.040, px);
    float micro_grid = max(
        line_axis_aa(grid_pos.x, 0.125, 0.0015),
        line_axis_aa(grid_pos.y - cup_local_z, 0.125, 0.0015)
    ) * cup_focus * micro_lod;

    vec2 slope_vec = vec2(local_slope_pct.x, -local_slope_pct.y);
    float slope_pct = length(slope_vec);
    vec2 downhill = slope_pct > 0.0005 ? slope_vec / slope_pct : vec2(0.0, -1.0);
    vec2 across = vec2(-downhill.y, downhill.x);
    float flow_amount = smoothstep(0.18, 1.10, slope_pct);

    // Moving bead field: this is the primary break cue. Beads move in the true
    // local downhill direction, so bowls/crowns/ridges can disagree across the green.
    float bead_along = dot(grid_pos, downhill) - TIME * (0.24 + min(0.92, slope_pct * 0.115));
    float bead_cross = dot(grid_pos, across);
    vec2 bead_cell = vec2(bead_cross / 0.72, bead_along / 0.44);
    vec2 bead_q = fract(bead_cell + vec2(0.5)) - vec2(0.5);
    bead_q.x *= 0.78;
    float bead_r = length(bead_q);
    float bead = 1.0 - smoothstep(0.060, 0.105 + px * 0.26, bead_r);
    bead *= flow_amount;

    // Keep the moving read inside the useful putting corridor instead of covering
    // the full field edge-to-edge.
    float lateral_read = 1.0 - smoothstep(2.25, 4.90, abs(grid_pos.x));
    float longitudinal_read = 1.0 - smoothstep(15.8, 17.1, abs(grid_pos.y));
    float read_field = lateral_read * longitudinal_read;
    bead *= read_field;

    // True elevation contour is secondary. It appears when slope is meaningful,
    // but never competes with the moving read beads.
    float contour_period = 0.015;
    float contour_d = abs(fract(local_height / contour_period + 0.5) - 0.5) * contour_period;
    float contour_aa = max(fwidth(local_height) * 1.45, 0.00034);
    float contour = 1.0 - smoothstep(0.00046, 0.00046 + contour_aa, contour_d);
    contour *= smoothstep(0.55, 1.45, slope_pct) * read_field;

    float slope_mix = smoothstep(0.80, 2.25, slope_pct);
    vec3 flow_color = mix(flow_cool, flow_mid, slope_mix);
    flow_color = mix(flow_color, flow_hot, smoothstep(2.25, 4.0, slope_pct));

    // Cup halo gives the eye a terminal target without making the grid itself heavier.
    float cup_ring = 1.0 - smoothstep(0.026, 0.072, abs(cup_dist - 0.54));

    vec3 color = grid_color;
    color = mix(color, tick_color, clamp(quarter_ticks * 0.40 + ball_quarter * 0.22, 0.0, 0.46));
    color = mix(color, flow_color, clamp(bead * 0.94 + contour * 0.34 + cup_ring * 0.28, 0.0, 0.96));

    // Fade the full-metre skeleton near the outer green. This stops the perspective
    // from making distant 1 m cells feel like huge foreground tiles.
    float edge_x = 1.0 - smoothstep(5.0, 5.82, abs(grid_pos.x));
    float edge_z = 1.0 - smoothstep(16.0, 17.10, abs(grid_pos.y));
    float edge_fade = edge_x * edge_z;
    float center_weight = mix(0.56, 1.0, 1.0 - smoothstep(1.4, 4.7, abs(grid_pos.x)));

    float grid_alpha = major_grid * 0.145;
    float scale_alpha = quarter_ticks * 0.075 + ball_quarter * 0.045 + micro_grid * 0.115;
    float read_alpha = bead * 0.245 + contour * 0.075 + cup_ring * 0.095;
    float alpha = (grid_alpha + scale_alpha + read_alpha * read_intensity * read_enabled)
        * strength * action_fade * center_weight * edge_fade;

    // Read indicators stay a little stronger than the static metric skeleton.
    alpha += bead * 0.055 * read_intensity * read_enabled * action_fade * read_field;

    ALBEDO = color;
    ALPHA = clamp(alpha, 0.0, 0.50);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    material.render_priority = 1
    return material

func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    # Keep the exact V166 solver aim/sign handling, then replace only the detail
    # copy so the visible scale matches what is actually drawn on the green.
    super._v165_update_hud(side_pct, long_pct)
    if _v165_detail_label == null:
        return

    var side_abs: float = abs(side_pct)
    var break_dir := "STRAIGHT"
    if side_abs >= 0.03:
        break_dir = "BREAK L" if side_pct > 0.0 else "BREAK R"

    _v165_detail_label.text = "%s %.2f%%  |  GRID 1.00m  |  CUP 0.125m  |  LIVE FLOW" % [break_dir, side_abs]

func _build_course() -> void:
    super._build_course()
    # V167 already made the center reference secondary. V168 reduces it one more
    # step so the gold physics path, not a straight laser, owns the read hierarchy.
    if aim_line != null and aim_line.material_override is StandardMaterial3D:
        var material := aim_line.material_override as StandardMaterial3D
        var c := material.albedo_color
        c.a = min(c.a, 0.17)
        material.albedo_color = c
