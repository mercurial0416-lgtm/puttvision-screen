extends "res://v173_premium_tv_polish.gd"

# V174 broadcast HUD. Presentation-only: Android V135-V137 / GreenTerrain / GreenReadAdvisor
# remain authoritative for physics and advice. This layer replaces the early prototype HUD with a
# cleaner commercial hierarchy designed for 16:9 TV output: target + remaining distance are primary,
# green speed and live ball speed are secondary, and break/surface/result state stays readable without
# covering the putting line.

var _v174_remaining_label: Label
var _v174_surface_label: Label
var _v174_break_title: Label
var _v174_break_value: Label
var _v174_grade_value: Label
var _v174_state_label: Label
var _v174_result_subtitle: Label
var _v174_result_panel: Panel

func _v174_style(fill: Color, border: Color, radius: int = 12) -> StyleBoxFlat:
    var style := StyleBoxFlat.new()
    style.bg_color = fill
    style.border_color = border
    style.border_width_left = 1
    style.border_width_top = 1
    style.border_width_right = 1
    style.border_width_bottom = 1
    style.corner_radius_top_left = radius
    style.corner_radius_top_right = radius
    style.corner_radius_bottom_left = radius
    style.corner_radius_bottom_right = radius
    return style

func _v174_panel(parent: Node, pos: Vector2, size_value: Vector2, fill: Color, border: Color, radius: int = 12) -> Panel:
    var panel := Panel.new()
    panel.position = pos
    panel.size = size_value
    panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    panel.add_theme_stylebox_override("panel", _v174_style(fill, border, radius))
    parent.add_child(panel)
    return panel

func _v174_text(parent: Node, pos: Vector2, size_value: Vector2, value: String, font_size: int, color: Color, align := HORIZONTAL_ALIGNMENT_LEFT) -> Label:
    var label := Label.new()
    label.position = pos
    label.size = size_value
    label.text = value
    label.horizontal_alignment = align
    label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    label.add_theme_font_size_override("font_size", font_size)
    label.add_theme_color_override("font_color", color)
    label.add_theme_color_override("font_shadow_color", Color(0.0, 0.0, 0.0, 0.54))
    label.add_theme_constant_override("shadow_offset_x", 1)
    label.add_theme_constant_override("shadow_offset_y", 1)
    parent.add_child(label)
    return label

func _v174_accent(parent: Node, pos: Vector2, size_value: Vector2, color: Color) -> void:
    var strip := ColorRect.new()
    strip.position = pos
    strip.size = size_value
    strip.color = color
    strip.mouse_filter = Control.MOUSE_FILTER_IGNORE
    parent.add_child(strip)

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    layer.name = "V174BroadcastHUD"
    layer.layer = 24
    add_child(layer)

    var root := Control.new()
    root.name = "V174HUDRoot"
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    # Primary target card: large enough to scan from a couch, compact enough to preserve course view.
    var target_card := _v174_panel(root, Vector2(30, 28), Vector2(522, 112), Color(0.018, 0.025, 0.029, 0.86), Color(0.70, 0.75, 0.70, 0.18), 14)
    _v174_accent(target_card, Vector2(0, 0), Vector2(7, 112), Color("#d6b85c"))
    _v174_text(target_card, Vector2(24, 10), Vector2(238, 28), "TARGET", 15, Color(0.79, 0.82, 0.80, 0.92))
    _v174_text(target_card, Vector2(282, 10), Vector2(216, 28), "REMAINING", 15, Color(0.79, 0.82, 0.80, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)
    distance_label = _v174_text(target_card, Vector2(22, 34), Vector2(240, 64), "5.0 m", 39, Color("#f4dda0"))
    _v174_remaining_label = _v174_text(target_card, Vector2(270, 34), Vector2(228, 64), "5.0 m", 35, Color("#f4f6f0"), HORIZONTAL_ALIGNMENT_RIGHT)

    # Secondary telemetry card on the opposite edge keeps the center fully open for ball tracking.
    var telemetry := _v174_panel(root, Vector2(1358, 28), Vector2(532, 112), Color(0.018, 0.025, 0.029, 0.84), Color(0.70, 0.75, 0.70, 0.18), 14)
    _v174_accent(telemetry, Vector2(525, 0), Vector2(7, 112), Color("#76a97a"))
    _v174_text(telemetry, Vector2(24, 10), Vector2(180, 26), "GREEN SPEED", 14, Color(0.75, 0.80, 0.76, 0.90))
    _v174_text(telemetry, Vector2(294, 10), Vector2(214, 26), "BALL SPEED", 14, Color(0.75, 0.80, 0.76, 0.90), HORIZONTAL_ALIGNMENT_RIGHT)
    stimp_label = _v174_text(telemetry, Vector2(24, 34), Vector2(180, 54), "2.8 m", 28, Color("#b9dda6"))
    speed_label = _v174_text(telemetry, Vector2(246, 34), Vector2(262, 54), "READY", 28, Color("#f2f4ef"), HORIZONTAL_ALIGNMENT_RIGHT)
    _v174_surface_label = _v174_text(telemetry, Vector2(24, 84), Vector2(484, 22), "SURFACE  GREEN", 13, Color(0.72, 0.77, 0.72, 0.90), HORIZONTAL_ALIGNMENT_RIGHT)

    # Break card lives low-left, away from cup approach and replay trails.
    var break_card := _v174_panel(root, Vector2(30, 904), Vector2(660, 142), Color(0.018, 0.025, 0.029, 0.87), Color(0.70, 0.75, 0.70, 0.18), 14)
    _v174_break_title = _v174_text(break_card, Vector2(24, 12), Vector2(250, 28), "GREEN READ", 14, Color(0.76, 0.81, 0.77, 0.92))
    _v174_break_value = _v174_text(break_card, Vector2(24, 38), Vector2(340, 48), "BREAK  STRAIGHT", 25, Color("#f1f4ef"))
    _v174_grade_value = _v174_text(break_card, Vector2(380, 38), Vector2(256, 48), "GRADE  LEVEL", 22, Color("#d3ddd0"), HORIZONTAL_ALIGNMENT_RIGHT)
    slope_label = _v174_text(break_card, Vector2(24, 92), Vector2(612, 34), "L/R +0.00%    F/B +0.00%", 16, Color(0.73, 0.78, 0.74, 0.92))

    # Minimal state pill instead of a large bottom block.
    var state_panel := _v174_panel(root, Vector2(830, 980), Vector2(260, 52), Color(0.025, 0.030, 0.032, 0.84), Color(0.85, 0.88, 0.84, 0.16), 26)
    wait_label = _v174_text(state_panel, Vector2(12, 0), Vector2(236, 52), "READY TO PUTT", 16, Color("#f0f2ec"), HORIZONTAL_ALIGNMENT_CENTER)
    _v174_state_label = wait_label

    # Result banner reads like a commercial replay package rather than raw debug text.
    _v174_result_panel = _v174_panel(root, Vector2(560, 156), Vector2(800, 118), Color(0.022, 0.028, 0.031, 0.92), Color(0.90, 0.78, 0.40, 0.32), 18)
    _v174_result_panel.visible = false
    result_label = _v174_text(_v174_result_panel, Vector2(28, 12), Vector2(744, 62), "HOLED", 38, Color("#ffe39a"), HORIZONTAL_ALIGNMENT_CENTER)
    _v174_result_subtitle = _v174_text(_v174_result_panel, Vector2(28, 70), Vector2(744, 30), "SHOT COMPLETE", 15, Color(0.82, 0.84, 0.80, 0.94), HORIZONTAL_ALIGNMENT_CENTER)

func _v174_direction(side: float) -> String:
    if abs(side) < 0.08:
        return "STRAIGHT"
    return "LEFT" if side < 0.0 else "RIGHT"

func _v174_grade(long_slope: float) -> String:
    if abs(long_slope) < 0.08:
        return "LEVEL"
    # GreenSettings semantics: positive longitudinal slope falls toward the cup (downhill),
    # negative rises toward the cup (uphill). Keep the human-readable HUD aligned with physics.
    return "DOWNHILL" if long_slope > 0.0 else "UPHILL"

func _v174_snapshot_float(s: Dictionary, key: String) -> Dictionary:
    # The broadcast card must never coerce malformed bridge telemetry into a plausible zero. Doing so
    # can falsely announce STRAIGHT / LEVEL or a default pace even though the source value is unusable.
    var raw: Variant = s.get(key, null)
    var raw_type := typeof(raw)
    if raw_type != TYPE_INT and raw_type != TYPE_FLOAT:
        return {"valid": false, "value": 0.0}
    var value := float(raw)
    if not is_finite(value):
        return {"valid": false, "value": 0.0}
    return {"valid": true, "value": value}

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    var remaining_sample := _v174_snapshot_float(s, "distanceToCup")
    var stimp_sample := _v174_snapshot_float(s, "stimp")
    var side_sample := _v174_snapshot_float(s, "sideSlope")
    var long_sample := _v174_snapshot_float(s, "longSlope")
    # The inherited renderer passes a coerced speed argument. Validate the raw snapshot again here so
    # a missing/string/NaN speed cannot become a believable 0.00 m/s on the broadcast card.
    var speed_sample := _v174_snapshot_float(s, "speed")
    var zone: String = str(s.get("surfaceZone", "GREEN")).to_upper()

    distance_label.text = "%.1f m" % target_distance
    if bool(remaining_sample.get("valid", false)):
        _v174_remaining_label.text = "%.2f m" % max(0.0, float(remaining_sample.get("value", 0.0)))
    else:
        _v174_remaining_label.text = "-- m"
    if bool(stimp_sample.get("valid", false)):
        stimp_label.text = "%.1f m" % float(stimp_sample.get("value", 0.0))
    else:
        stimp_label.text = "-- m"
    if running and bool(speed_sample.get("valid", false)):
        speed_label.text = "%.2f m/s" % max(0.0, float(speed_sample.get("value", speed)))
    elif running:
        speed_label.text = "-- m/s"
    else:
        speed_label.text = "READY"
    _v174_surface_label.text = "SURFACE  %s" % zone

    var side_valid := bool(side_sample.get("valid", false))
    var long_valid := bool(long_sample.get("valid", false))
    if side_valid:
        var side := float(side_sample.get("value", 0.0))
        _v174_break_value.text = "BREAK  %s" % _v174_direction(side)
    else:
        _v174_break_value.text = "BREAK  --"
    if long_valid:
        var long_slope := float(long_sample.get("value", 0.0))
        _v174_grade_value.text = "GRADE  %s" % _v174_grade(long_slope)
    else:
        _v174_grade_value.text = "GRADE  --"
    if side_valid and long_valid:
        slope_label.text = "L/R %+.2f%%    F/B %+.2f%%" % [
            float(side_sample.get("value", 0.0)),
            float(long_sample.get("value", 0.0))
        ]
    else:
        slope_label.text = "SLOPE DATA UNAVAILABLE"

    # Replay is an explicit presentation mode. A final live snapshot can retain running=true for a
    # frame after replay starts; the broadcast state pill must match the cinematic replay focus instead
    # of contradicting it with BALL ROLLING.
    var replaying := _v171_replay_remaining > 0.0
    if replaying:
        _v174_state_label.text = "SHOT REPLAY"
        _v174_state_label.add_theme_color_override("font_color", Color("#f4dda0"))
    elif running:
        _v174_state_label.text = "BALL ROLLING"
        _v174_state_label.add_theme_color_override("font_color", Color("#dff0b6"))
    else:
        _v174_state_label.text = "READY TO PUTT"
        _v174_state_label.add_theme_color_override("font_color", Color("#f0f2ec"))

    _v174_result_panel.visible = holed or lip_out
    result_label.visible = true
    if holed:
        result_label.text = "HOLED"
        result_label.add_theme_color_override("font_color", Color("#ffe39a"))
        _v174_result_subtitle.text = "CENTER-CUP RESULT  •  REPLAY READY"
    elif lip_out:
        result_label.text = "LIP OUT"
        result_label.add_theme_color_override("font_color", Color("#ffb6a9"))
        _v174_result_subtitle.text = "EDGE CONTACT  •  CHECK REPLAY LINE"
