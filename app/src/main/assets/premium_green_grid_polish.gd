extends Node

# One-shot presentation polish for the inherited metric green-read shader. The existing 1 m / 0.25 m /
# cup precision geometry and encoded terrain grade remain the source; this helper only changes visual
# hierarchy after the grid material exists. No polling, mesh rebuild, scoring, aim, or ball-state writes.

const GRID_NODE_NAME := "V164FriendsGreenGrid"
const RELIEF_NODE_NAME := "TerrainReliefVisibility"
const POLISH_MARKER := "// PUTTVISION_PREMIUM_GRID_V1"
const RELIEF_POLISH_MARKER := "// PUTTVISION_PREMIUM_RELIEF_GRID_V1"

func _ready() -> void:
    call_deferred("_install_premium_grid")

func _replace_once(source: String, before: String, after: String) -> String:
    if not source.contains(before):
        return source
    return source.replace(before, after)

func _install_relief_polish(root: Node) -> void:
    var relief := root.find_child(RELIEF_NODE_NAME, true, false) as MeshInstance3D
    if relief == null:
        return
    var material := relief.material_override as ShaderMaterial
    if material == null or material.shader == null:
        return
    var code := material.shader.code
    if code.contains(RELIEF_POLISH_MARKER):
        return

    # The relief shell already supplies hillshade/contours, while the primary metric material supplies
    # scale and downhill motion. De-emphasize the relief shell's second moving grid so the two overlays
    # stop forming a bright double-wireframe on bowls and crowns.
    code = _replace_once(code,
        "float flow_active = smoothstep(0.16, 0.72, slope_pct);",
        RELIEF_POLISH_MARKER + "\n    float flow_active = smoothstep(0.16, 0.72, slope_pct);")
    code = _replace_once(code,
        "float ribbon_strength = elevation_ribbon * active * 0.42;",
        "float ribbon_strength = elevation_ribbon * active * 0.30;")
    code = _replace_once(code,
        "relief_color = mix(relief_color, flow_color, flow_grid * 0.32);",
        "relief_color = mix(relief_color, flow_color, flow_grid * 0.08);")
    code = _replace_once(code,
        "float ribbon_alpha = elevation_ribbon * active * 0.28;",
        "float ribbon_alpha = elevation_ribbon * active * 0.18;")
    code = _replace_once(code,
        "float flow_alpha = flow_grid * 0.16;",
        "float flow_alpha = flow_grid * 0.035;")
    code = _replace_once(code,
        "ALPHA = min(0.46, ALPHA + flow_alpha);",
        "ALPHA = min(0.38, ALPHA + flow_alpha);")

    var complete := code.contains(RELIEF_POLISH_MARKER) \
        and code.contains("flow_grid * 0.08") \
        and code.contains("flow_grid * 0.035") \
        and code.contains("elevation_ribbon * active * 0.18")
    if complete:
        material.shader.code = code

func _install_premium_grid() -> void:
    var root := get_tree().current_scene
    if root == null:
        return
    var grid := root.find_child(GRID_NODE_NAME, true, false) as MeshInstance3D
    if grid == null:
        return
    var material := grid.material_override as ShaderMaterial
    if material == null or material.shader == null:
        return

    var code := material.shader.code
    if code.contains(POLISH_MARKER):
        _install_relief_polish(root)
        set_process(false)
        return

    # Quieter mineral-green palette: the metric scaffold recedes into the turf while live slope flow
    # keeps enough chroma to read from TV distance without looking like a neon engineering overlay.
    code = _replace_once(code,
        "uniform vec3 grid_color : source_color = vec3(0.72, 0.88, 0.86);",
        "uniform vec3 grid_color : source_color = vec3(0.50, 0.72, 0.67);\n" + POLISH_MARKER)
    code = _replace_once(code,
        "uniform vec3 tick_color : source_color = vec3(0.60, 0.80, 0.78);",
        "uniform vec3 tick_color : source_color = vec3(0.44, 0.67, 0.62);")
    code = _replace_once(code,
        "uniform vec3 flow_cool : source_color = vec3(0.30, 0.92, 0.76);",
        "uniform vec3 flow_cool : source_color = vec3(0.38, 0.92, 0.70);")
    code = _replace_once(code,
        "uniform vec3 flow_mid : source_color = vec3(0.98, 0.82, 0.25);",
        "uniform vec3 flow_mid : source_color = vec3(0.90, 0.84, 0.34);")
    code = _replace_once(code,
        "uniform vec3 flow_hot : source_color = vec3(1.00, 0.38, 0.12);",
        "uniform vec3 flow_hot : source_color = vec3(1.00, 0.49, 0.20);")

    # Slim the every-metre scaffold, then add a restrained five-metre anchor. This gives scale and
    # perspective a visual hierarchy instead of making every square shout with the same weight.
    code = _replace_once(code,
        "float major_x = line_axis_aa(grid_pos.x, 1.0, 0.0044);",
        "float major_x = line_axis_aa(grid_pos.x, 1.0, 0.0032);")
    code = _replace_once(code,
        "float major_z = line_axis_aa(grid_pos.y, 1.0, 0.0044);",
        "float major_z = line_axis_aa(grid_pos.y, 1.0, 0.0032);")
    code = _replace_once(code,
        "float major_grid = max(major_x, major_z);",
        "float major_grid = max(major_x, major_z);\n    float anchor_x = line_axis_aa(grid_pos.x, 5.0, 0.0058);\n    float anchor_z = line_axis_aa(grid_pos.y, 5.0, 0.0058);\n    float anchor_grid = max(anchor_x, anchor_z);")

    # Turn the animated break dots into short downhill streaks. Direction still comes from the encoded
    # local grade; this only changes the glyph silhouette so motion reads cleanly instead of sparkling.
    code = _replace_once(code,
        "bead_q.x *= 0.78;",
        "bead_q.x *= 0.82;\n    bead_q.y *= 0.46;")
    code = _replace_once(code,
        "float bead = 1.0 - smoothstep(0.060, 0.105 + px * 0.26, bead_r);",
        "float bead = 1.0 - smoothstep(0.052, 0.098 + px * 0.24, bead_r);")

    # Let the center read area breathe and fade the rectangular field earlier at the perimeter. The cup
    # precision zone remains available, but the full-green scaffold stops reading like a giant carpet.
    code = _replace_once(code,
        "float edge_x = 1.0 - smoothstep(5.0, 5.82, abs(grid_pos.x));",
        "float edge_x = 1.0 - smoothstep(4.55, 5.66, abs(grid_pos.x));")
    code = _replace_once(code,
        "float edge_z = 1.0 - smoothstep(16.0, 17.10, abs(grid_pos.y));",
        "float edge_z = 1.0 - smoothstep(15.20, 16.82, abs(grid_pos.y));")
    code = _replace_once(code,
        "float grid_alpha = major_grid * 0.145;",
        "float grid_alpha = major_grid * 0.082 + anchor_grid * 0.105;")
    code = _replace_once(code,
        "float scale_alpha = quarter_ticks * 0.075 + ball_quarter * 0.045 + micro_grid * 0.115;",
        "float scale_alpha = quarter_ticks * 0.050 + ball_quarter * 0.032 + micro_grid * 0.092;")
    code = _replace_once(code,
        "float read_alpha = bead * 0.245 + contour * 0.075 + cup_ring * 0.095;",
        "float read_alpha = bead * 0.225 + contour * 0.052 + cup_ring * 0.086;")
    code = _replace_once(code,
        "alpha += bead * 0.055 * read_intensity * read_enabled * action_fade * read_field;",
        "alpha += bead * 0.048 * read_intensity * read_enabled * action_fade * read_field;")
    code = _replace_once(code,
        "ALPHA = clamp(alpha, 0.0, 0.50);",
        "ALPHA = clamp(alpha, 0.0, 0.42);")

    # Fail closed if the inherited shader shape changes. A partially patched shader is worse than the
    # known-good existing presentation, so require the marker and all new hierarchy tokens together.
    var complete := code.contains(POLISH_MARKER) \
        and code.contains("float anchor_grid = max(anchor_x, anchor_z);") \
        and code.contains("bead_q.y *= 0.46;") \
        and code.contains("major_grid * 0.082 + anchor_grid * 0.105")
    if not complete:
        return

    material.shader.code = code
    _install_relief_polish(root)
    set_process(false)
