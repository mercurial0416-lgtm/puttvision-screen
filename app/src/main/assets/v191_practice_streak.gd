extends "res://v190_target_window.gd"

# Presentation-only pressure ladder for practice. It derives a consecutive-success streak from
# the same recent dispersion samples and active next-rep target window. Nothing feeds back into
# Android physics, GreenTerrain, GreenReadAdvisor, aiming, scoring, or shot capture.

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

func _build_hud() -> void:
    super._build_hud()
    if _v179_panel == null:
        return

    _v191_streak_label = _v174_text(
        _v179_panel,
        Vector2(360, 191),
        Vector2(190, 12),
        "PRESSURE LADDER  ·  BUILDING",
        8,
        Color(0.58, 0.69, 0.68, 0.94),
        HORIZONTAL_ALIGNMENT_RIGHT
    )

    var x0 := 360.0
    var gap := 4.0
    var width := (190.0 - gap * 2.0) / 3.0
    for index in range(V191_ADVANCE_STREAK):
        var segment := ColorRect.new()
        segment.name = "PressureLadder%d" % (index + 1)
        segment.position = Vector2(x0 + float(index) * (width + gap), 205)
        segment.size = Vector2(width, 3)
        segment.color = Color(0.60, 0.72, 0.70, 0.14)
        segment.mouse_filter = Control.MOUSE_FILTER_IGNORE
        _v179_panel.add_child(segment)
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
        if _v190_target_zone != null:
            _v190_target_zone.color = Color(0.96, 0.86, 0.49, 0.18)
    else:
        _v191_streak_label.modulate = Color(0.76, 0.88, 0.84, 0.96)

func _v179_refresh() -> void:
    super._v179_refresh()
    _v191_refresh()
