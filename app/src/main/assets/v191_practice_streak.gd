extends "res://v190_target_window.gd"

# Presentation-only pressure ladder for practice. It derives a consecutive-success streak from
# the same recent dispersion samples and active next-rep target window. Nothing feeds back into
# Android physics, GreenTerrain, GreenReadAdvisor, aiming, scoring, or shot capture.

var _v191_bar: Panel
var _v191_streak_label: Label
var _v191_segments: Array[ColorRect] = []
var _v191_streak := 0

const V191_ADVANCE_STREAK := 3

func _v191_sample_in_window(sample: Vector2, axis: String) -> bool:
    var line_ok := absf(sample.x) <= V190_LINE_TOLERANCE_CM
    var pace_ok := absf(sample.y) <= V190_PACE_TOLERANCE_CM
    match axis:
        "LINE": return line_ok
        "PACE": return pace_ok
        "BOTH": return line_ok and pace_ok
    return false

func _v191_trailing_streak(axis: String) -> int:
    if axis == "BUILDING" or _v179_samples.is_empty():
        return 0
    var streak := 0
    for index in range(_v179_samples.size() - 1, -1, -1):
        if not _v191_sample_in_window(_v179_samples[index], axis):
            break
        streak += 1
    return mini(streak, V191_ADVANCE_STREAK)

func _v191_copy(streak: int, axis: String) -> String:
    if axis == "BUILDING":
        return "PRESSURE LADDER  ·  BUILDING"
    if streak >= V191_ADVANCE_STREAK:
        return "PRESSURE LADDER  ·  ADVANCE READY"
    if streak == 2:
        return "PRESSURE LADDER  ·  ONE MORE"
    if streak == 1:
        return "PRESSURE LADDER  ·  HOLD IT"
    return "PRESSURE LADDER  ·  START STREAK"

func _v191_base_target_color(axis: String) -> Color:
    # Keep this aligned with v190's normal target-window palette. The pressure ladder owns its
    # temporary completion highlight, so it must also restore the non-complete state when refreshed
    # directly by inherited drill/presentation layers rather than relying on a parent refresh first.
    return Color(0.96, 0.86, 0.49, 0.13) if axis == "BOTH" else Color(0.46, 0.84, 0.71, 0.11)

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    # Keep the high-density session card readable. The streak lives in its own compact footer
    # instead of stealing vertical space from LAST REP / NEXT REP telemetry.
    _v191_bar = _v174_panel(root, Vector2(1316, 1038), Vector2(560, 28), Color(0.012, 0.019, 0.023, 0.94), Color(0.45, 0.68, 0.78, 0.18), 9)
    _v191_bar.name = "PracticePressureLadder"
    _v191_bar.visible = false
    _v174_accent(_v191_bar, Vector2(0, 0), Vector2(5, 28), Color("#76d7b6"))
    _v191_streak_label = _v174_text(
        _v191_bar,
        Vector2(18, 5),
        Vector2(270, 18),
        "PRESSURE LADDER  ·  BUILDING",
        9,
        Color(0.76, 0.88, 0.84, 0.96)
    )

    var x0 := 326.0
    var gap := 7.0
    var width := (216.0 - gap * 2.0) / 3.0
    for index in range(V191_ADVANCE_STREAK):
        var segment := ColorRect.new()
        segment.name = "PressureLadder%d" % (index + 1)
        segment.position = Vector2(x0 + float(index) * (width + gap), 11)
        segment.size = Vector2(width, 6)
        segment.color = Color(0.60, 0.72, 0.70, 0.14)
        segment.mouse_filter = Control.MOUSE_FILTER_IGNORE
        _v191_bar.add_child(segment)
        _v191_segments.append(segment)
    _v191_refresh()

func _v191_refresh() -> void:
    if _v191_streak_label == null or _v191_segments.size() != V191_ADVANCE_STREAK:
        return
    var metric := _v189_focus_metric()
    var spec := _v190_target_spec(metric)
    var axis := str(spec.get("axis", "BUILDING"))
    _v191_streak = _v191_trailing_streak(axis)
    _v191_streak_label.text = _v191_copy(_v191_streak, axis)

    var complete := _v191_streak >= V191_ADVANCE_STREAK
    for index in range(V191_ADVANCE_STREAK):
        var active := index < _v191_streak
        if active:
            _v191_segments[index].color = Color("#f4dda0") if complete else Color("#76d7b6")
        else:
            _v191_segments[index].color = Color(0.60, 0.72, 0.70, 0.14)

    if complete:
        _v191_streak_label.modulate = Color("#f4dda0")
    else:
        _v191_streak_label.modulate = Color(0.76, 0.88, 0.84, 0.96)

    # Completion gold is transient UI state. Always write both branches so a direct ladder refresh
    # after a miss cannot leave the target window looking ADVANCE READY.
    if _v190_target_zone != null:
        _v190_target_zone.color = Color(0.96, 0.86, 0.49, 0.18) if complete else _v191_base_target_color(axis)

    if _v191_bar != null:
        _v191_bar.visible = _v179_preview_force_visible or (_v179_panel != null and _v179_panel.visible)

func _v179_refresh() -> void:
    super._v179_refresh()
    _v191_refresh()

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    if _v191_bar != null:
        _v191_bar.visible = _v179_panel != null and _v179_panel.visible
