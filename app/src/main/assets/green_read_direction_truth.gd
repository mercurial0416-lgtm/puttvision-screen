extends "res://relief_depth_finish.gd"

# Presentation-only semantic correction for the commercial GREEN READ card.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative; this layer only makes
# the direction text agree with the authoritative recommendation and with GREEN OVERVIEW semantics.

var _overview_aim_marker: Line2D
var _overview_aim_label: Label

const OVERVIEW_AIM_DEADBAND_M := 0.015
const OVERVIEW_AIM_VISUAL_SPAN_M := 1.8
const OVERVIEW_AIM_VISUAL_SPAN_PX := 62.0

func _overview_aim_text(offset_m: float) -> String:
    if absf(offset_m) < OVERVIEW_AIM_DEADBAND_M:
        return "AIM CENTER"
    var direction := "RIGHT" if offset_m > 0.0 else "LEFT"
    return "AIM %s %d cm" % [direction, int(round(absf(offset_m) * 100.0))]

func _overview_aim_target_position(offset_m: float) -> Vector2:
    # Mirror the overview's existing horizontal recommendation scale, but expose the advisor's
    # target as an explicit address cue instead of forcing the player to infer it from curve shape.
    var normalized := clampf(offset_m / OVERVIEW_AIM_VISUAL_SPAN_M, -1.0, 1.0)
    var center_x := V183_MAP_ORIGIN.x + V183_MAP_SIZE.x * 0.5
    return Vector2(center_x + normalized * OVERVIEW_AIM_VISUAL_SPAN_PX, V183_MAP_ORIGIN.y + 18.0)

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    # Split the old footer into actionable AIM information plus a compact color key. This keeps the
    # overview self-contained on a TV: players no longer have to cross-reference another HUD card.
    for child in _v183_panel.get_children():
        if child is Label:
            var label := child as Label
            if label.text == "gold = recommended read  •  teal = fall line":
                label.position = Vector2(164, 222)
                label.size = Vector2(154, 18)
                label.text = "gold read  •  teal fall"
                label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
                break

    _overview_aim_label = _v174_text(
        _v183_panel,
        Vector2(20, 222),
        Vector2(140, 18),
        "AIM CENTER",
        10,
        Color("#f4dda0")
    )
    _overview_aim_label.name = "GreenOverviewAimReadout"

    _overview_aim_marker = Line2D.new()
    _overview_aim_marker.name = "GreenOverviewAimTarget"
    _overview_aim_marker.width = 2.2
    _overview_aim_marker.default_color = Color("#f4dda0")
    _overview_aim_marker.points = PackedVector2Array([
        Vector2(-5.5, 5.0),
        Vector2(0.0, 0.0),
        Vector2(5.5, 5.0)
    ])
    _overview_aim_marker.visible = false
    _v183_panel.add_child(_overview_aim_marker)

    _overview_refresh_aim_cue()

func _overview_refresh_aim_cue() -> void:
    if _v183_panel == null or _overview_aim_label == null or _overview_aim_marker == null:
        return
    var active := _v183_panel.visible
    _overview_aim_label.visible = active
    _overview_aim_label.text = _overview_aim_text(_v165_recommended_offset)
    _overview_aim_marker.visible = active and absf(_v165_recommended_offset) >= OVERVIEW_AIM_DEADBAND_M
    _overview_aim_marker.position = _overview_aim_target_position(_v165_recommended_offset)

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _overview_refresh_aim_cue()

func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    if _v165_aim_label == null:
        return

    var side_abs: float = abs(side_pct)
    var aim_text := "AIM CENTER"
    if abs(_v165_recommended_offset) >= 0.015:
        # The recommendation can be supplied directly by the authoritative advisor. Its sign is the
        # source of truth for aim direction; do not infer aim side from the local side slope.
        var aim_dir := "RIGHT" if _v165_recommended_offset > 0.0 else "LEFT"
        aim_text = "AIM %s %.2f m" % [aim_dir, abs(_v165_recommended_offset)]

    var break_dir := "STRAIGHT"
    if side_abs >= 0.03:
        # GreenSettings semantics: positive side slope means the right side is lower, so gravity
        # moves the ball right. This matches GREEN OVERVIEW and avoids contradictory read cards.
        break_dir = "BREAK RIGHT" if side_pct > 0.0 else "BREAK LEFT"

    _v165_aim_label.text = "%s   |   %s" % [aim_text, _v165_read_level(side_pct, long_pct)]
    _v165_detail_label.text = "%s %.2f%%   |   LIVE FLOW | CONTOUR | CUP 0.125m" % [break_dir, side_abs]

func _lock_sampled_green_material_to_relief_mesh() -> void:
    # The opaque Green mesh is rebuilt from sampled terrain relief downstream. The inherited turf
    # shader's side/long vertex warp was for the old flat PlaneMesh; applying both would double-warp
    # the visible green and detach crowns/bowls from grid, ball and cup presentation anchors.
    # Fringe/rough remain legacy plane surfaces and intentionally keep their existing global grade.
    if mat_green == null:
        return
    mat_green.set_shader_parameter("side_slope", 0.0)
    mat_green.set_shader_parameter("long_slope", 0.0)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _lock_sampled_green_material_to_relief_mesh()
