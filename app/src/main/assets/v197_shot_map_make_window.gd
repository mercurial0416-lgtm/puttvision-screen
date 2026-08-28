extends "res://v196_shot_map_hierarchy.gd"

# Presentation-only SHOT MAP make window. The debrief acceptance rule is axis-aligned
# (line +/-9 cm AND pace +/-22 cm), so the visual window must be the same rectangle.
# This layer never feeds back into physics, terrain, green read, aiming, or scoring.

var _v197_make_fill: Polygon2D
var _v197_make_outline: Line2D

const V197_LINE_WINDOW_CM := 9.0
const V197_PACE_WINDOW_CM := 22.0
const V197_IDLE_FILL := Color(0.28, 0.84, 0.62, 0.10)
const V197_IDLE_OUTLINE := Color(0.38, 0.92, 0.70, 0.72)
const V197_MAKE_FILL := Color(0.30, 0.94, 0.66, 0.22)
const V197_MAKE_OUTLINE := Color(0.52, 1.00, 0.76, 0.98)
const V197_MISS_FILL := Color(0.28, 0.84, 0.62, 0.07)
const V197_MISS_OUTLINE := Color(0.42, 0.74, 0.58, 0.54)

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
        return

    var made := _v197_inside_make_window(line_delta_cm, pace_delta_cm)
    _v197_make_fill.color = V197_MAKE_FILL if made else V197_MISS_FILL
    _v197_make_outline.default_color = V197_MAKE_OUTLINE if made else V197_MISS_OUTLINE
    if _v196_center_legend != null:
        _v196_center_legend.text = "IN MAKE WINDOW" if made else "OUTSIDE MAKE WINDOW"
        _v196_center_legend.add_theme_color_override(
            "font_color",
            Color(0.58, 1.00, 0.76, 0.98) if made else Color(0.72, 0.80, 0.74, 0.84)
        )

func _v197_inside_make_window(line_delta_cm: float, pace_delta_cm: float) -> bool:
    return absf(line_delta_cm) <= V197_LINE_WINDOW_CM and absf(pace_delta_cm) <= V197_PACE_WINDOW_CM
