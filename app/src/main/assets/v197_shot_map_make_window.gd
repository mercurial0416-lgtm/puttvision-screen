extends "res://v196_shot_map_hierarchy.gd"

# Presentation-only SHOT MAP make window. The debrief coach treats the threshold itself as
# corrective (line >= 9 cm OR pace >= 22 cm), so every visible verdict uses the same strict window.
# This layer never feeds back into physics, terrain, green read, aiming, or scoring.

var _v197_make_fill: Polygon2D
var _v197_make_outline: Line2D
var _v197_axis_left: Label
var _v197_axis_right: Label
var _v197_axis_long: Label
var _v197_axis_short: Label
var _v197_correction_line: Line2D
var _v197_correction_tip: Line2D

const V197_LINE_WINDOW_CM := 9.0
const V197_PACE_WINDOW_CM := 22.0
const V197_IDLE_FILL := Color(0.28, 0.84, 0.62, 0.10)
const V197_IDLE_OUTLINE := Color(0.38, 0.92, 0.70, 0.72)
const V197_MAKE_FILL := Color(0.30, 0.94, 0.66, 0.22)
const V197_MAKE_OUTLINE := Color(0.52, 1.00, 0.76, 0.98)
const V197_MISS_FILL := Color(0.28, 0.84, 0.62, 0.07)
const V197_MISS_OUTLINE := Color(0.42, 0.74, 0.58, 0.54)
const V197_MARKER_MAKE := Color("#76d7b6")
const V197_MARKER_MISS := Color("#f4dda0")
const V197_AXIS_COLOR := Color(0.67, 0.76, 0.72, 0.78)
const V197_CORRECTION_COLOR := Color(0.42, 0.92, 0.82, 0.94)
const V197_RESULT_Z_INDEX := 2
const V197_CORRECTION_Z_INDEX := 1
const V197_CORRECTION_MARGIN_CM := 1.0
const V197_WINDOW_EPSILON_CM := 0.01

func _v197_window_radius_px() -> Vector2:
    return Vector2(
        V188_RADIUS * V197_LINE_WINDOW_CM / 30.0,
        V188_RADIUS * V197_PACE_WINDOW_CM / 70.0
    )

func _v197_window_points(close_loop: bool = false) -> PackedVector2Array:
    var radius := _v197_window_radius_px()
    var out := PackedVector2Array([
        Vector2(-radius.x, -radius.y),
        Vector2(radius.x, -radius.y),
        Vector2(radius.x, radius.y),
        Vector2(-radius.x, radius.y)
    ])
    if close_loop:
        out.append(out[0])
    return out

func _v197_axis_label(text: String, name: String, position: Vector2, size: Vector2, alignment: HorizontalAlignment = HORIZONTAL_ALIGNMENT_CENTER) -> Label:
    var label := _v174_text(_v188_panel, position, size, text, 7, V197_AXIS_COLOR, alignment)
    label.name = name
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    return label

func _v197_promote_shot_indicators() -> void:
    # Semantic axis labels and the correction guide are context. The actual shot result must always
    # win the draw order, especially at cardinal edges where an off-scale arrow occupies the same pixels.
    if _v188_vector != null:
        _v188_vector.z_index = V197_RESULT_Z_INDEX
    if _v188_overflow_tick != null:
        _v188_overflow_tick.z_index = V197_RESULT_Z_INDEX
    if _v188_dot != null:
        _v188_dot.z_index = V197_RESULT_Z_INDEX
    if _v188_overflow_label != null:
        _v188_overflow_label.z_index = V197_RESULT_Z_INDEX

func _v197_correction_target(line_delta_cm: float, pace_delta_cm: float) -> Vector2:
    # Project a miss to a small safety margin inside the same strict success window used by the
    # debrief. The 1 cm inset avoids coaching a player to sit exactly on a fail boundary. This is a
    # presentation target only; it never changes aim, read, scoring, or physics.
    return Vector2(
        clampf(line_delta_cm, -V197_LINE_WINDOW_CM + V197_CORRECTION_MARGIN_CM, V197_LINE_WINDOW_CM - V197_CORRECTION_MARGIN_CM),
        clampf(pace_delta_cm, -V197_PACE_WINDOW_CM + V197_CORRECTION_MARGIN_CM, V197_PACE_WINDOW_CM - V197_CORRECTION_MARGIN_CM)
    )

func _v197_correction_text(line_delta_cm: float, pace_delta_cm: float) -> String:
    var target := _v197_correction_target(line_delta_cm, pace_delta_cm)
    var correction := target - Vector2(line_delta_cm, pace_delta_cm)
    var parts := PackedStringArray()
    if absf(correction.x) > V197_WINDOW_EPSILON_CM:
        parts.append("L %.0f" % absf(correction.x) if correction.x < 0.0 else "R %.0f" % absf(correction.x))
    if absf(correction.y) > V197_WINDOW_EPSILON_CM:
        parts.append("SHORT %.0f" % absf(correction.y) if correction.y < 0.0 else "LONG %.0f" % absf(correction.y))
    return "FIX  %s" % "  ·  ".join(parts)

func _v197_correction_arrow(start: Vector2, target: Vector2) -> PackedVector2Array:
    var delta := target - start
    if delta.length_squared() < 4.0:
        return PackedVector2Array()
    var tangent := delta.normalized()
    var normal := Vector2(-tangent.y, tangent.x)
    var base := target - tangent * 6.0
    return PackedVector2Array([base + normal * 3.2, target, base - normal * 3.2])

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    _v197_make_fill = Polygon2D.new()
    _v197_make_fill.name = "ShotMapMakeWindowFill"
    _v197_make_fill.position = V188_CENTER
    _v197_make_fill.polygon = _v197_window_points()
    _v197_make_fill.color = V197_IDLE_FILL
    _v188_panel.add_child(_v197_make_fill)
    _v188_panel.move_child(_v197_make_fill, _v188_ring.get_index())

    _v197_make_outline = Line2D.new()
    _v197_make_outline.name = "ShotMapMakeWindowOutline"
    _v197_make_outline.position = V188_CENTER
    _v197_make_outline.width = 1.5
    _v197_make_outline.default_color = V197_IDLE_OUTLINE
    _v197_make_outline.points = _v197_window_points(true)
    _v188_panel.add_child(_v197_make_outline)

    _v197_correction_line = Line2D.new()
    _v197_correction_line.name = "ShotMapCorrectionVector"
    _v197_correction_line.width = 2.2
    _v197_correction_line.default_color = V197_CORRECTION_COLOR
    _v197_correction_line.visible = false
    _v197_correction_line.z_index = V197_CORRECTION_Z_INDEX
    _v188_panel.add_child(_v197_correction_line)

    _v197_correction_tip = Line2D.new()
    _v197_correction_tip.name = "ShotMapCorrectionTip"
    _v197_correction_tip.width = 2.2
    _v197_correction_tip.default_color = V197_CORRECTION_COLOR
    _v197_correction_tip.visible = false
    _v197_correction_tip.z_index = V197_CORRECTION_Z_INDEX
    _v188_panel.add_child(_v197_correction_tip)

    # Compact semantic edge labels make LEFT/RIGHT and LONG/SHORT readable from TV distance without
    # changing the underlying deltas, scale, make window, or any authoritative coaching input.
    _v197_axis_long = _v197_axis_label("LONG", "ShotMapAxisLong", Vector2(58, 47), Vector2(36, 10))
    _v197_axis_short = _v197_axis_label("SHORT", "ShotMapAxisShort", Vector2(55, 116), Vector2(42, 10))
    _v197_axis_left = _v197_axis_label("LEFT", "ShotMapAxisLeft", Vector2(34, 80), Vector2(34, 10), HORIZONTAL_ALIGNMENT_LEFT)
    _v197_axis_right = _v197_axis_label("RIGHT", "ShotMapAxisRight", Vector2(84, 80), Vector2(34, 10), HORIZONTAL_ALIGNMENT_RIGHT)
    _v197_promote_shot_indicators()

    if _v196_center_legend != null:
        _v196_center_legend.text = "GREEN BOX = MAKE"
        _v196_center_legend.add_theme_color_override("font_color", Color(0.47, 0.84, 0.66, 0.92))

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    super._v188_refresh(line_delta_cm, pace_delta_cm, visible)
    if _v197_make_fill == null or _v197_make_outline == null:
        return

    if not visible:
        _v197_make_fill.color = V197_IDLE_FILL
        _v197_make_outline.default_color = V197_IDLE_OUTLINE
        if _v197_correction_line != null:
            _v197_correction_line.visible = false
        if _v197_correction_tip != null:
            _v197_correction_tip.visible = false
        return

    var made := _v197_inside_make_window(line_delta_cm, pace_delta_cm)
    # The inherited map predates the strict debrief boundary and colors the marker with <=.
    # Re-apply the authoritative presentation verdict here so marker, box, and coach cannot disagree.
    if _v188_dot != null:
        _v188_dot.color = V197_MARKER_MAKE if made else V197_MARKER_MISS
    _v197_make_fill.color = V197_MAKE_FILL if made else V197_MISS_FILL
    _v197_make_outline.default_color = V197_MAKE_OUTLINE if made else V197_MISS_OUTLINE

    if made:
        if _v197_correction_line != null:
            _v197_correction_line.visible = false
        if _v197_correction_tip != null:
            _v197_correction_tip.visible = false
        if _v196_center_legend != null:
            _v196_center_legend.text = "IN MAKE WINDOW"
            _v196_center_legend.add_theme_color_override("font_color", Color(0.58, 1.00, 0.76, 0.98))
        return

    var target_delta := _v197_correction_target(line_delta_cm, pace_delta_cm)
    var start := _v188_point(line_delta_cm, pace_delta_cm)
    var target := _v188_point(target_delta.x, target_delta.y)
    if _v197_correction_line != null:
        _v197_correction_line.points = PackedVector2Array([start, target])
        _v197_correction_line.visible = start.distance_to(target) >= 2.0
    if _v197_correction_tip != null:
        _v197_correction_tip.points = _v197_correction_arrow(start, target)
        _v197_correction_tip.visible = not _v197_correction_tip.points.is_empty()
    if _v196_center_legend != null:
        _v196_center_legend.text = _v197_correction_text(line_delta_cm, pace_delta_cm)
        _v196_center_legend.add_theme_color_override("font_color", V197_CORRECTION_COLOR)

func _v197_inside_make_window(line_delta_cm: float, pace_delta_cm: float) -> bool:
    return absf(line_delta_cm) < V197_LINE_WINDOW_CM and absf(pace_delta_cm) < V197_PACE_WINDOW_CM
