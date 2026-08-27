extends "res://v176_precision_read_window.gd"

# V177: post-shot coaching debrief.
# Presentation only. The native Android bridge already publishes the authoritative
# GreenReadAdvisor-vs-actual line delta and cup-relative pace delta. This layer turns
# those values into a compact commercial practice result without touching physics.

var _v177_panel: Panel
var _v177_grade_label: Label
var _v177_title_label: Label
var _v177_line_value: Label
var _v177_pace_value: Label
var _v177_leave_value: Label
var _v177_coach_label: Label
var _v177_line_bar: ColorRect
var _v177_pace_bar: ColorRect
var _v177_preview_force_visible := false

const V177_BAR_MAX_PX := 150.0

func _v177_metric_score(line_delta_cm: float, pace_delta_cm: float, holed: bool) -> int:
    if holed:
        return 100
    var line_penalty: float = min(48.0, abs(line_delta_cm) * 1.65)
    var pace_penalty: float = min(48.0, abs(pace_delta_cm) * 0.72)
    return int(round(clamp(100.0 - line_penalty - pace_penalty, 0.0, 99.0)))

func _v177_grade(score: int) -> String:
    if score >= 95:
        return "TOUR"
    if score >= 85:
        return "A"
    if score >= 70:
        return "B"
    if score >= 55:
        return "C"
    return "RESET"

func _v177_line_text(delta_cm: float) -> String:
    if abs(delta_cm) < 1.5:
        return "ON LINE"
    return "%s %d cm" % [("RIGHT" if delta_cm > 0.0 else "LEFT"), int(round(abs(delta_cm)))]

func _v177_pace_text(delta_cm: float) -> String:
    if abs(delta_cm) < 8.0:
        return "CUP PACE"
    return "%s %d cm" % [("LONG" if delta_cm > 0.0 else "SHORT"), int(round(abs(delta_cm)))]

func _v177_coach(line_delta_cm: float, pace_delta_cm: float, holed: bool, lip_out: bool) -> String:
    if holed:
        return "CENTERED READ  •  PACE CONTROLLED"
    if lip_out and abs(line_delta_cm) <= 6.0:
        return "LINE WAS LIVE  •  SOFTEN THE ENTRY PACE"
    var line_bad: bool = abs(line_delta_cm) >= 9.0
    var pace_bad: bool = abs(pace_delta_cm) >= 22.0
    if line_bad and pace_bad:
        return "RESET START LINE + PACE"
    if line_bad:
        return "MATCH THE GOLD READ LINE"
    if pace_bad:
        return "KEEP THE LINE  •  RECALIBRATE PACE"
    return "GOOD WINDOW  •  REPEAT THE STROKE"

func _v177_bar_width(delta_cm: float, full_scale_cm: float) -> float:
    return V177_BAR_MAX_PX * clamp(abs(delta_cm) / max(1.0, full_scale_cm), 0.0, 1.0)

func _build_hud() -> void:
    super._build_hud()

    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v177_panel = _v174_panel(root, Vector2(1330, 790), Vector2(560, 256), Color(0.016, 0.023, 0.028, 0.92), Color(0.90, 0.78, 0.40, 0.22), 14)
    _v177_panel.name = "V177ShotDebrief"
    _v177_panel.visible = false
    _v174_accent(_v177_panel, Vector2(553, 0), Vector2(7, 256), Color("#d6b85c"))

    _v177_title_label = _v174_text(_v177_panel, Vector2(22, 10), Vector2(300, 30), "SHOT DEBRIEF", 15, Color(0.78, 0.84, 0.80, 0.96))
    _v177_grade_label = _v174_text(_v177_panel, Vector2(344, 5), Vector2(182, 40), "A  88", 23, Color("#f4dda0"), HORIZONTAL_ALIGNMENT_RIGHT)

    _v174_text(_v177_panel, Vector2(22, 52), Vector2(150, 20), "START LINE", 11, Color(0.58, 0.67, 0.64, 0.92))
    _v177_line_value = _v174_text(_v177_panel, Vector2(174, 48), Vector2(352, 28), "ON LINE", 17, Color("#f1f4ef"), HORIZONTAL_ALIGNMENT_RIGHT)
    var line_track := ColorRect.new()
    line_track.position = Vector2(22, 82)
    line_track.size = Vector2(V177_BAR_MAX_PX, 5)
    line_track.color = Color(0.74, 0.80, 0.75, 0.14)
    line_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v177_panel.add_child(line_track)
    _v177_line_bar = ColorRect.new()
    _v177_line_bar.position = Vector2(22, 82)
    _v177_line_bar.size = Vector2(0, 5)
    _v177_line_bar.color = Color("#76d7b6")
    _v177_line_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v177_panel.add_child(_v177_line_bar)

    _v174_text(_v177_panel, Vector2(22, 105), Vector2(150, 20), "PACE", 11, Color(0.58, 0.67, 0.64, 0.92))
    _v177_pace_value = _v174_text(_v177_panel, Vector2(174, 101), Vector2(352, 28), "CUP PACE", 17, Color("#f1f4ef"), HORIZONTAL_ALIGNMENT_RIGHT)
    var pace_track := ColorRect.new()
    pace_track.position = Vector2(22, 135)
    pace_track.size = Vector2(V177_BAR_MAX_PX, 5)
    pace_track.color = Color(0.74, 0.80, 0.75, 0.14)
    pace_track.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v177_panel.add_child(pace_track)
    _v177_pace_bar = ColorRect.new()
    _v177_pace_bar.position = Vector2(22, 135)
    _v177_pace_bar.size = Vector2(0, 5)
    _v177_pace_bar.color = Color("#d6b85c")
    _v177_pace_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v177_panel.add_child(_v177_pace_bar)

    _v174_text(_v177_panel, Vector2(344, 80), Vector2(182, 20), "FINAL LEAVE", 11, Color(0.58, 0.67, 0.64, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)
    _v177_leave_value = _v174_text(_v177_panel, Vector2(344, 101), Vector2(182, 38), "0.00 m", 23, Color("#b9dda6"), HORIZONTAL_ALIGNMENT_RIGHT)

    var divider := ColorRect.new()
    divider.position = Vector2(22, 160)
    divider.size = Vector2(504, 1)
    divider.color = Color(0.76, 0.82, 0.77, 0.14)
    divider.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v177_panel.add_child(divider)

    _v174_text(_v177_panel, Vector2(22, 170), Vector2(120, 18), "COACH", 11, Color(0.58, 0.67, 0.64, 0.92))
    _v177_coach_label = _v174_text(_v177_panel, Vector2(22, 191), Vector2(504, 44), "GOOD WINDOW  •  REPEAT THE STROKE", 15, Color("#f4dda0"))

func _v177_update_debrief(s: Dictionary, force_visible: bool = false) -> void:
    if _v177_panel == null:
        return
    if force_visible:
        # Preview-only latch. Production snapshots never set this; the CI reference frame does so
        # the new result package is visually inspected instead of only node-tested.
        _v177_preview_force_visible = true

    var actual_variant: Variant = s.get("actualTrail", [])
    var has_shot: bool = actual_variant is Array and (actual_variant as Array).size() >= 2
    var has_read_metrics: bool = s.has("readLineDeltaCm") and s.has("paceDeltaCm")
    var running: bool = bool(s.get("running", false))
    var holed: bool = bool(s.get("holed", false))
    var lip_out: bool = bool(s.get("lipOut", false))
    var show: bool = _v177_preview_force_visible or (has_shot and has_read_metrics and not running and _v171_replay_remaining <= 0.0)
    _v177_panel.visible = show
    if not show:
        return

    # While the preview latch is active, keep the last synthetic evaluation rather than replacing it
    # with the renderer's empty startup snapshot before the PNG is captured.
    if _v177_preview_force_visible and not force_visible and not has_read_metrics:
        return

    var line_delta: float = float(s.get("readLineDeltaCm", 0.0))
    var pace_delta: float = float(s.get("paceDeltaCm", 0.0))
    var leave_m: float = max(0.0, float(s.get("distanceToCup", 0.0)))
    var score: int = _v177_metric_score(line_delta, pace_delta, holed)

    _v177_grade_label.text = "%s  %02d" % [_v177_grade(score), score]
    _v177_line_value.text = _v177_line_text(line_delta)
    _v177_pace_value.text = _v177_pace_text(pace_delta)
    _v177_leave_value.text = "%.2f m" % leave_m
    _v177_coach_label.text = _v177_coach(line_delta, pace_delta, holed, lip_out)
    _v177_line_bar.size.x = _v177_bar_width(line_delta, 30.0)
    _v177_pace_bar.size.x = _v177_bar_width(pace_delta, 70.0)

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v177_update_debrief(s)

    # The read panel and post-shot debrief intentionally occupy different phases.
    if _v176_panel != null and _v177_panel != null and _v177_panel.visible:
        _v176_panel.visible = false
