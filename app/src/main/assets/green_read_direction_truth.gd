extends "res://relief_depth_finish.gd"

# Presentation-only semantic correction for the commercial GREEN READ card.
# Android V135-V137, GreenTerrain and GreenReadAdvisor remain authoritative; this layer only makes
# the direction text agree with the authoritative recommendation and with GREEN OVERVIEW semantics.

var _overview_aim_marker: Line2D
var _overview_aim_label: Label
var _overview_legend_label: Label

const OVERVIEW_AIM_DEADBAND_M := 0.015
const OVERVIEW_AIM_VISUAL_SPAN_M := 1.8
const OVERVIEW_AIM_VISUAL_SPAN_PX := 62.0
const OVERVIEW_AIM_NORMAL_WIDTH := 140.0
const OVERVIEW_AIM_OFF_MAP_WIDTH := 298.0
const GREEN_READ_BREAK_DEADBAND_PCT := 0.05

func _overview_aim_is_valid(offset_m: float) -> bool:
    return is_finite(offset_m)

func _overview_aim_text(offset_m: float) -> String:
    # Malformed presentation telemetry must never become a believable LEFT/RIGHT recommendation.
    if not _overview_aim_is_valid(offset_m):
        return "AIM --"
    if absf(offset_m) < OVERVIEW_AIM_DEADBAND_M:
        return "AIM CENTER"
    var direction := "RIGHT" if offset_m > 0.0 else "LEFT"
    return "AIM %s %d cm" % [direction, int(round(absf(offset_m) * 100.0))]

func _overview_aim_is_off_map(offset_m: float) -> bool:
    if not _overview_aim_is_valid(offset_m):
        return false
    return absf(offset_m) > OVERVIEW_AIM_VISUAL_SPAN_M

func _overview_aim_panel_text(offset_m: float) -> String:
    var text := _overview_aim_text(offset_m)
    if _overview_aim_is_off_map(offset_m):
        text += " · OFF MAP"
    return text

func _v183_break_text(side_pct: float) -> String:
    # GREEN READ and GREEN OVERVIEW must agree on when a tiny sampled cross-slope is effectively
    # straight. One shared presentation deadband prevents contradictory read cards around zero.
    if abs(side_pct) < GREEN_READ_BREAK_DEADBAND_PCT:
        return "BREAK  STRAIGHT"
    return "BREAK  %s %.2f%%" % [("RIGHT" if side_pct > 0.0 else "LEFT"), abs(side_pct)]

func _live_curve_readout(cross_track_cm: float) -> String:
    # Keep live-roll direction language identical to the rest/overview surfaces. The inherited
    # meter used R/L while the same shot switched to RIGHT/LEFT at rest, which made one HUD card
    # change vocabulary mid-roll on a TV. This is presentation-only telemetry.
    if absf(cross_track_cm) < 0.05:
        return "CENTER"
    return "%s %.1f cm" % [("RIGHT" if cross_track_cm > 0.0 else "LEFT"), absf(cross_track_cm)]

func _live_peak_readout(peak_signed_cm: float) -> String:
    if absf(peak_signed_cm) < 0.05:
        return "PEAK CENTER"
    return "PEAK %s %.1f cm" % [("RIGHT" if peak_signed_cm > 0.0 else "LEFT"), absf(peak_signed_cm)]

func _overview_aim_target_position(offset_m: float) -> Vector2:
    # Mirror the overview's existing horizontal recommendation scale, but expose the advisor's
    # target as an explicit address cue instead of forcing the player to infer it from curve shape.
    var center_x := V183_MAP_ORIGIN.x + V183_MAP_SIZE.x * 0.5
    if not _overview_aim_is_valid(offset_m):
        return Vector2(center_x, V183_MAP_ORIGIN.y + 18.0)
    var normalized := clampf(offset_m / OVERVIEW_AIM_VISUAL_SPAN_M, -1.0, 1.0)
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
                _overview_legend_label = label
                break

    _overview_aim_label = _v174_text(
        _v183_panel,
        Vector2(20, 222),
        Vector2(OVERVIEW_AIM_NORMAL_WIDTH, 18),
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
    var offset := _v165_recommended_offset
    var valid := _overview_aim_is_valid(offset)
    var off_map := _overview_aim_is_off_map(offset)
    _overview_aim_label.visible = active
    _overview_aim_label.text = _overview_aim_panel_text(offset)
    # OFF MAP is the highest-priority address warning in this footer. The old fixed 140 px AIM slot
    # could let a long centimeter value collide with the color key on a TV. Temporarily give the
    # actionable readout the full footer width and hide the non-actionable legend; restore the split
    # layout as soon as the recommendation returns on-map. No solver/read value is changed.
    _overview_aim_label.size.x = OVERVIEW_AIM_OFF_MAP_WIDTH if off_map else OVERVIEW_AIM_NORMAL_WIDTH
    if _overview_legend_label != null:
        _overview_legend_label.visible = active and not off_map
    _overview_aim_marker.visible = active and valid and absf(offset) >= OVERVIEW_AIM_DEADBAND_M
    _overview_aim_marker.position = _overview_aim_target_position(offset)
    # The map marker clamps at the visual edge. Rotate the established chevron outward when the
    # authoritative aim is beyond that edge so the clamped glyph cannot masquerade as an exact target.
    _overview_aim_marker.rotation = (PI * 0.5 if offset > 0.0 else -PI * 0.5) if off_map else 0.0

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _overview_refresh_aim_cue()

func _v165_update_hud(side_pct: float, long_pct: float) -> void:
    if _v165_aim_label == null:
        return

    var side_abs: float = abs(side_pct)
    # Reuse the overview formatter so both commercial read surfaces use the same advisor sign,
    # deadband and centimeter unit. This is presentation-only; the advisor value itself is untouched.
    var aim_text := _overview_aim_text(_v165_recommended_offset)

    var break_dir := "STRAIGHT"
    if side_abs >= GREEN_READ_BREAK_DEADBAND_PCT:
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
