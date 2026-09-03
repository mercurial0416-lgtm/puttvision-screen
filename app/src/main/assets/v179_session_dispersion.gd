extends "res://v178_session_form_layout.gd"

# Presentation-only session dispersion map. Uses the already-authoritative post-shot
# readLineDeltaCm and paceDeltaCm bridge telemetry; no physics/advisor state is changed.

var _v179_panel: Panel
var _v179_plot: Control
var _v179_tendency_label: Label
var _v179_line_mean_label: Label
var _v179_pace_mean_label: Label
var _v179_window_label: Label
var _v179_window_detail: Label
var _v179_points: Array[ColorRect] = []
var _v179_samples: Array[Vector2] = []
var _v179_last_completion_serial := 0
var _v179_preview_force_visible := false

const V179_HISTORY := 5
const V179_LINE_SCALE_CM := 30.0
const V179_PACE_SCALE_CM := 70.0
const V179_PLOT_SIZE := Vector2(300.0, 118.0)
const V179_MAKE_LINE_CM := 5.0
const V179_MAKE_PACE_CM := 15.0
const V179_COACH_MIN_SAMPLES := 3
const V179_COACH_LINE_DEADBAND_CM := 3.0
const V179_COACH_PACE_DEADBAND_CM := 10.0

func _v179_mean(axis: int) -> float:
    if _v179_samples.is_empty():
        return 0.0
    var total := 0.0
    for sample in _v179_samples:
        total += sample.x if axis == 0 else sample.y
    return total / float(_v179_samples.size())

func _v179_median(axis: int) -> float:
    if _v179_samples.is_empty():
        return 0.0
    var values: Array[float] = []
    for sample in _v179_samples:
        values.append(sample.x if axis == 0 else sample.y)
    values.sort()
    var middle := int(values.size() / 2)
    if values.size() % 2 == 1:
        return values[middle]
    return (values[middle - 1] + values[middle]) * 0.5

func _v179_coaching_center(axis: int) -> float:
    # A single gross mishit should remain visible in the raw AVG and dispersion plot,
    # but must not invert the next-rep instruction for an otherwise stable pattern.
    if _v179_samples.size() < V179_COACH_MIN_SAMPLES:
        return _v179_mean(axis)
    return _v179_median(axis)

func _v179_tendency() -> String:
    if _v179_samples.is_empty():
        return "BUILDING PATTERN"
    var line := _v179_coaching_center(0)
    var pace := _v179_coaching_center(1)
    var line_text := "CENTERED" if abs(line) < 3.0 else ("RIGHT" if line > 0.0 else "LEFT")
    var pace_text := "CUP PACE" if abs(pace) < 10.0 else ("LONG" if pace > 0.0 else "SHORT")
    return "%s  •  %s" % [line_text, pace_text]

func _v179_make_count() -> int:
    var count := 0
    for sample in _v179_samples:
        if absf(sample.x) <= V179_MAKE_LINE_CM and absf(sample.y) <= V179_MAKE_PACE_CM:
            count += 1
    return count

func _v179_make_rate_text() -> String:
    if _v179_samples.is_empty():
        return "WINDOW --"
    var count := _v179_make_count()
    return "WINDOW %d/%d" % [count, _v179_samples.size()]

func _v179_next_rep_text() -> String:
    if _v179_samples.size() < V179_COACH_MIN_SAMPLES:
        return "NEXT · BUILD 3 SHOTS"

    var line := _v179_coaching_center(0)
    var pace := _v179_coaching_center(1)
    var line_text := "HOLD LINE"
    if line > V179_COACH_LINE_DEADBAND_CM:
        line_text = "%dcm LEFT" % clampi(int(round(absf(line))), 3, 9)
    elif line < -V179_COACH_LINE_DEADBAND_CM:
        line_text = "%dcm RIGHT" % clampi(int(round(absf(line))), 3, 9)

    var pace_text := "HOLD PACE"
    if pace > V179_COACH_PACE_DEADBAND_CM:
        pace_text = "SOFTER"
    elif pace < -V179_COACH_PACE_DEADBAND_CM:
        pace_text = "FIRMER"

    return "NEXT · %s · %s" % [line_text, pace_text]

func _v179_plot_position(sample: Vector2) -> Vector2:
    var nx: float = clampf(sample.x / V179_LINE_SCALE_CM, -1.0, 1.0)
    var ny: float = clampf(sample.y / V179_PACE_SCALE_CM, -1.0, 1.0)
    return Vector2(
        V179_PLOT_SIZE.x * 0.5 + nx * V179_PLOT_SIZE.x * 0.46,
        V179_PLOT_SIZE.y * 0.5 - ny * V179_PLOT_SIZE.y * 0.42
    )

func _v179_push_sample(line_cm: float, pace_cm: float) -> bool:
    # Bridge telemetry is presentation input, not physics truth. A malformed packet must never
    # poison the rolling mean/median or create NaN UI coordinates that can corrupt the HUD tree.
    if not is_finite(line_cm) or not is_finite(pace_cm):
        return false
    _v179_samples.append(Vector2(line_cm, pace_cm))
    while _v179_samples.size() > V179_HISTORY:
        _v179_samples.pop_front()
    return true

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v179_panel = _v174_panel(root, Vector2(1316, 820), Vector2(560, 210), Color(0.014, 0.021, 0.026, 0.90), Color(0.45, 0.68, 0.78, 0.20), 14)
    _v179_panel.name = "V179SessionDispersion"
    _v179_panel.visible = false
    _v174_accent(_v179_panel, Vector2(0, 0), Vector2(7, 210), Color("#76c7d7"))
    _v174_text(_v179_panel, Vector2(24, 12), Vector2(220, 22), "SESSION DISPERSION", 14, Color(0.77, 0.84, 0.84, 0.96))
    _v179_tendency_label = _v174_text(_v179_panel, Vector2(230, 9), Vector2(300, 28), "NEXT · BUILD 3 SHOTS", 12, Color("#f4dda0"), HORIZONTAL_ALIGNMENT_RIGHT)

    _v179_plot = Control.new()
    _v179_plot.position = Vector2(24, 50)
    _v179_plot.size = V179_PLOT_SIZE
    _v179_plot.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v179_panel.add_child(_v179_plot)

    var window := Panel.new()
    window.name = "MakeWindow"
    var top_left := _v179_plot_position(Vector2(-V179_MAKE_LINE_CM, V179_MAKE_PACE_CM))
    var bottom_right := _v179_plot_position(Vector2(V179_MAKE_LINE_CM, -V179_MAKE_PACE_CM))
    window.position = top_left
    window.size = bottom_right - top_left
    window.mouse_filter = Control.MOUSE_FILTER_IGNORE
    var window_style := StyleBoxFlat.new()
    window_style.bg_color = Color(0.40, 0.86, 0.66, 0.075)
    window_style.border_color = Color(0.48, 0.90, 0.72, 0.42)
    window_style.set_border_width_all(1)
    window_style.set_corner_radius_all(5)
    window.add_theme_stylebox_override("panel", window_style)
    _v179_plot.add_child(window)

    var h := ColorRect.new()
    h.position = Vector2(0, V179_PLOT_SIZE.y * 0.5)
    h.size = Vector2(V179_PLOT_SIZE.x, 1)
    h.color = Color(0.74, 0.82, 0.82, 0.18)
    _v179_plot.add_child(h)
    var v := ColorRect.new()
    v.position = Vector2(V179_PLOT_SIZE.x * 0.5, 0)
    v.size = Vector2(1, V179_PLOT_SIZE.y)
    v.color = Color(0.74, 0.82, 0.82, 0.18)
    _v179_plot.add_child(v)

    _v179_window_label = _v174_text(_v179_panel, Vector2(344, 58), Vector2(186, 24), "WINDOW --", 15, Color("#8ce0b7"), HORIZONTAL_ALIGNMENT_RIGHT)
    _v179_window_detail = _v174_text(_v179_panel, Vector2(344, 86), Vector2(186, 36), "±5 cm LINE\n±15 cm PACE", 10, Color(0.58, 0.72, 0.68, 0.92), HORIZONTAL_ALIGNMENT_RIGHT)

    _v174_text(_v179_panel, Vector2(24, 174), Vector2(110, 18), "LINE AVG", 10, Color(0.56, 0.66, 0.67, 0.92))
    _v179_line_mean_label = _v174_text(_v179_panel, Vector2(122, 169), Vector2(115, 24), "0 cm", 14, Color("#e8eeee"), HORIZONTAL_ALIGNMENT_RIGHT)
    _v174_text(_v179_panel, Vector2(288, 174), Vector2(110, 18), "PACE AVG", 10, Color(0.56, 0.66, 0.67, 0.92))
    _v179_pace_mean_label = _v174_text(_v179_panel, Vector2(396, 169), Vector2(134, 24), "0 cm", 14, Color("#e8eeee"), HORIZONTAL_ALIGNMENT_RIGHT)

    for index in range(V179_HISTORY):
        var dot := ColorRect.new()
        dot.size = Vector2(10, 10)
        dot.color = Color("#76d7b6") if index < V179_HISTORY - 1 else Color("#f4dda0")
        dot.mouse_filter = Control.MOUSE_FILTER_IGNORE
        dot.visible = false
        _v179_plot.add_child(dot)
        _v179_points.append(dot)

func _v179_refresh() -> void:
    if _v179_panel == null:
        return
    _v179_tendency_label.text = _v179_next_rep_text()
    _v179_line_mean_label.text = "%+.0f cm" % _v179_mean(0)
    _v179_pace_mean_label.text = "%+.0f cm" % _v179_mean(1)
    if _v179_window_label != null:
        _v179_window_label.text = _v179_make_rate_text()
    for index in range(V179_HISTORY):
        var dot := _v179_points[index]
        if index < _v179_samples.size():
            dot.visible = true
            dot.position = _v179_plot_position(_v179_samples[index]) - dot.size * 0.5
            dot.modulate.a = 0.45 + 0.55 * float(index + 1) / float(max(1, _v179_samples.size()))
        else:
            dot.visible = false

func _v179_capture(s: Dictionary) -> void:
    # Session form owns the live-roll edge detector and advances this serial exactly once per
    # completed putt. Following that identity keeps dispersion in lock-step even when two real
    # putts produce identical trail size, finish distance and coaching deltas.
    if _v178_completed_shot_serial <= 0 or _v178_completed_shot_serial == _v179_last_completion_serial:
        return
    var trail_variant: Variant = s.get("actualTrail", [])
    var complete := trail_variant is Array and (trail_variant as Array).size() >= 2 and s.has("readLineDeltaCm") and s.has("paceDeltaCm") and not bool(s.get("running", false)) and _v171_replay_remaining <= 0.0
    if not complete:
        return
    var accepted := _v179_push_sample(float(s.get("readLineDeltaCm", 0.0)), float(s.get("paceDeltaCm", 0.0)))
    if not accepted:
        # Do not consume the completion identity. If the bridge repairs this same stopped snapshot
        # on the next frame, the valid values still deserve to become the one session sample.
        return
    _v179_last_completion_serial = _v178_completed_shot_serial
    _v179_refresh()

func _v179_preview_seed() -> void:
    _v179_samples = [Vector2(-8, -18), Vector2(-3, 10), Vector2(5, 22), Vector2(7, 6), Vector2(3, 14)]
    _v179_preview_force_visible = true
    _v179_refresh()
    if _v179_panel != null:
        _v179_panel.visible = true

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v179_capture(s)
    if _v179_panel != null:
        var result_phase := _v177_panel != null and _v177_panel.visible
        _v179_panel.visible = _v179_preview_force_visible or (not _v179_samples.is_empty() and result_phase and not bool(s.get("running", false)) and _v171_replay_remaining <= 0.0)
        if _v179_panel.visible and _v178_panel != null:
            _v178_panel.visible = false
