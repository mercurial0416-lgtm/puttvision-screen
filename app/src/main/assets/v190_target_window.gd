extends "res://v189_practice_focus.gd"

# Presentation-only next-rep target window. Converts the existing session focus into a
# measurable practice target without feeding any value back into Android physics, terrain,
# green read, aim, scoring, or shot capture.

var _v190_target_zone: ColorRect
var _v190_target_caption: Label
var _v190_last_result: Label

const V190_LINE_TOLERANCE_CM := 5.0
const V190_PACE_TOLERANCE_CM := 12.0

func _v190_target_spec(metric: Dictionary) -> Dictionary:
    var kind := str(metric.get("kind", "BUILDING"))
    match kind:
        "LINE":
            return {"axis": "LINE", "caption": "NEXT REP  ·  START ±%.0f cm" % V190_LINE_TOLERANCE_CM}
        "PACE":
            return {"axis": "PACE", "caption": "NEXT REP  ·  PACE ±%.0f cm" % V190_PACE_TOLERANCE_CM}
        "HOLD":
            return {"axis": "BOTH", "caption": "NEXT REP  ·  HOLD CENTER WINDOW"}
        _:
            return {"axis": "BUILDING", "caption": "NEXT REP  ·  BUILD THE PATTERN"}

func _v190_last_in_window(axis: String) -> Variant:
    if _v179_samples.is_empty() or axis == "BUILDING":
        return null
    var last := _v179_samples[_v179_samples.size() - 1]
    var line_ok := absf(last.x) <= V190_LINE_TOLERANCE_CM
    var pace_ok := absf(last.y) <= V190_PACE_TOLERANCE_CM
    match axis:
        "LINE": return line_ok
        "PACE": return pace_ok
        "BOTH": return line_ok and pace_ok
    return null

func _v190_zone_rect(axis: String) -> Rect2:
    var cx := V179_PLOT_SIZE.x * 0.5
    var cy := V179_PLOT_SIZE.y * 0.5
    var line_half := V179_PLOT_SIZE.x * 0.46 * V190_LINE_TOLERANCE_CM / V179_LINE_SCALE_CM
    var pace_half := V179_PLOT_SIZE.y * 0.42 * V190_PACE_TOLERANCE_CM / V179_PACE_SCALE_CM
    match axis:
        "LINE": return Rect2(cx - line_half, 0.0, line_half * 2.0, V179_PLOT_SIZE.y)
        "PACE": return Rect2(0.0, cy - pace_half, V179_PLOT_SIZE.x, pace_half * 2.0)
        "BOTH": return Rect2(cx - line_half, cy - pace_half, line_half * 2.0, pace_half * 2.0)
    return Rect2(cx - 1.0, cy - 1.0, 2.0, 2.0)

func _build_hud() -> void:
    super._build_hud()
    if _v179_plot == null or _v179_panel == null:
        return

    _v190_target_zone = ColorRect.new()
    _v190_target_zone.name = "V190TargetWindow"
    _v190_target_zone.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v190_target_zone.color = Color(0.46, 0.84, 0.71, 0.10)
    _v190_target_zone.visible = false
    _v179_plot.add_child(_v190_target_zone)
    _v179_plot.move_child(_v190_target_zone, 0)

    if _v189_focus_cue != null:
        _v189_focus_cue.position.y = 114
        _v189_focus_cue.size.y = 24
    if _v189_focus_meter_bg != null:
        _v189_focus_meter_bg.position.y = 162
    if _v189_focus_meter != null:
        _v189_focus_meter.position.y = 162

    _v190_target_caption = _v174_text(_v179_panel, Vector2(360, 138), Vector2(190, 17), "NEXT REP  ·  BUILD THE PATTERN", 9, Color("#76d7b6"))
    _v190_last_result = _v174_text(_v179_panel, Vector2(360, 177), Vector2(190, 17), "", 9, Color(0.67, 0.74, 0.73, 0.94), HORIZONTAL_ALIGNMENT_RIGHT)
    _v190_refresh()

func _v190_refresh() -> void:
    if _v190_target_zone == null or _v190_target_caption == null:
        return
    var metric := _v189_focus_metric()
    var spec := _v190_target_spec(metric)
    var axis := str(spec.get("axis", "BUILDING"))
    var rect := _v190_zone_rect(axis)
    _v190_target_zone.position = rect.position
    _v190_target_zone.size = rect.size
    _v190_target_zone.visible = axis != "BUILDING"
    _v190_target_zone.color = Color(0.46, 0.84, 0.71, 0.11) if axis != "BOTH" else Color(0.96, 0.86, 0.49, 0.13)
    _v190_target_caption.text = str(spec.get("caption", "NEXT REP"))

    var result := _v190_last_in_window(axis)
    if result == null:
        _v190_last_result.text = ""
    elif bool(result):
        _v190_last_result.text = "LAST REP  ·  IN WINDOW"
        _v190_last_result.modulate = Color("#76d7b6")
    else:
        _v190_last_result.text = "LAST REP  ·  OUTSIDE"
        _v190_last_result.modulate = Color("#f4dda0")

func _v179_refresh() -> void:
    super._v179_refresh()
    _v190_refresh()
