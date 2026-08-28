extends "res://v196_shot_map_hierarchy.gd"

# Presentation-only SHOT MAP make window. The existing dot already uses the authoritative debrief
# acceptance thresholds (line +/-9 cm, pace +/-22 cm), but the chart only showed a large map ring.
# This layer renders the actual acceptance window at the same chart scale so the visual promise and
# post-shot verdict agree. It never feeds back into physics, terrain, green read, aiming, or scoring.

var _v197_make_fill: Polygon2D
var _v197_make_outline: Line2D

const V197_LINE_WINDOW_CM := 9.0
const V197_PACE_WINDOW_CM := 22.0
const V197_SEGMENTS := 32

func _v197_window_radius_px() -> Vector2:
    return Vector2(
        V188_RADIUS * V197_LINE_WINDOW_CM / 30.0,
        V188_RADIUS * V197_PACE_WINDOW_CM / 70.0
    )

func _v197_ellipse_points(radius: Vector2, close_loop: bool = false) -> PackedVector2Array:
    var out := PackedVector2Array()
    var count := V197_SEGMENTS + (1 if close_loop else 0)
    for i in range(count):
        var a := TAU * float(i % V197_SEGMENTS) / float(V197_SEGMENTS)
        out.append(Vector2(cos(a) * radius.x, sin(a) * radius.y))
    return out

func _build_hud() -> void:
    super._build_hud()
    if _v188_panel == null:
        return

    var radius := _v197_window_radius_px()

    _v197_make_fill = Polygon2D.new()
    _v197_make_fill.name = "ShotMapMakeWindowFill"
    _v197_make_fill.position = V188_CENTER
    _v197_make_fill.polygon = _v197_ellipse_points(radius)
    _v197_make_fill.color = Color(0.28, 0.84, 0.62, 0.12)
    _v188_panel.add_child(_v197_make_fill)
    _v188_panel.move_child(_v197_make_fill, _v188_ring.get_index())

    _v197_make_outline = Line2D.new()
    _v197_make_outline.name = "ShotMapMakeWindowOutline"
    _v197_make_outline.position = V188_CENTER
    _v197_make_outline.width = 1.5
    _v197_make_outline.default_color = Color(0.38, 0.92, 0.70, 0.78)
    _v197_make_outline.points = _v197_ellipse_points(radius, true)
    _v188_panel.add_child(_v197_make_outline)

    if _v196_center_legend != null:
        _v196_center_legend.text = "GREEN = MAKE WINDOW"
        _v196_center_legend.add_theme_color_override("font_color", Color(0.47, 0.84, 0.66, 0.92))

func _v197_inside_make_window(line_delta_cm: float, pace_delta_cm: float) -> bool:
    return absf(line_delta_cm) <= V197_LINE_WINDOW_CM and absf(pace_delta_cm) <= V197_PACE_WINDOW_CM
