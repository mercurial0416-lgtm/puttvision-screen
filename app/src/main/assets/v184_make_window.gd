extends "res://v183_green_overview.gd"

# Presentation-only make-window corridor. It converts the already-authoritative distance/slope
# snapshot into a visual tolerance band around the recommended read. It never feeds values back
# into Android physics, GreenTerrain or GreenReadAdvisor.

var _v184_left_edge: Line2D
var _v184_right_edge: Line2D
var _v184_gate: Line2D
var _v184_window_label: Label
var _v184_last_tolerance_cm: float = 0.0

func _v184_tolerance_cm(distance_m: float, side_pct: float, long_pct: float) -> float:
    var difficulty: float = distance_m * 0.64 + absf(side_pct) * 0.82 + absf(long_pct) * 0.38
    return clampf(8.8 - difficulty, 2.0, 8.0)

func _v184_offset_curve(points: PackedVector2Array, offset_px: float) -> PackedVector2Array:
    var out := PackedVector2Array()
    if points.size() < 2:
        return out
    for i in range(points.size()):
        var prev: Vector2 = points[max(0, i - 1)]
        var next: Vector2 = points[min(points.size() - 1, i + 1)]
        var tangent: Vector2 = (next - prev).normalized()
        var normal: Vector2 = Vector2(-tangent.y, tangent.x)
        out.append(points[i] + normal * offset_px)
    return out

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _v184_left_edge = Line2D.new()
    _v184_left_edge.name = "MakeWindowLeft"
    _v184_left_edge.width = 1.5
    _v184_left_edge.default_color = Color(0.47, 0.84, 0.66, 0.45)
    _v183_panel.add_child(_v184_left_edge)

    _v184_right_edge = Line2D.new()
    _v184_right_edge.name = "MakeWindowRight"
    _v184_right_edge.width = 1.5
    _v184_right_edge.default_color = Color(0.47, 0.84, 0.66, 0.45)
    _v183_panel.add_child(_v184_right_edge)

    _v184_gate = Line2D.new()
    _v184_gate.name = "MakeWindowGate"
    _v184_gate.width = 3.0
    _v184_gate.default_color = Color(0.47, 0.84, 0.66, 0.82)
    _v183_panel.add_child(_v184_gate)

    _v184_window_label = _v174_text(
        _v183_panel,
        Vector2(20, 174),
        Vector2(298, 18),
        "MAKE WINDOW  ±5 cm",
        10,
        Color(0.64, 0.88, 0.76, 0.94),
        HORIZONTAL_ALIGNMENT_CENTER
    )

func _v184_refresh_window(distance_m: float, side_pct: float, long_pct: float) -> void:
    if _v184_left_edge == null or _v183_path_line == null:
        return
    var center: PackedVector2Array = _v183_path_line.points
    if center.size() < 2:
        return
    _v184_last_tolerance_cm = _v184_tolerance_cm(distance_m, side_pct, long_pct)
    # ~3 px/cm is readable at 1080p while staying inside the compact overview map.
    var half_width_px: float = clampf(_v184_last_tolerance_cm * 2.8, 6.0, 22.0)
    _v184_left_edge.points = _v184_offset_curve(center, -half_width_px)
    _v184_right_edge.points = _v184_offset_curve(center, half_width_px)

    var finish: Vector2 = center[center.size() - 1]
    var before: Vector2 = center[max(0, center.size() - 3)]
    var tangent: Vector2 = (finish - before).normalized()
    var normal: Vector2 = Vector2(-tangent.y, tangent.x)
    _v184_gate.points = PackedVector2Array([
        finish - normal * half_width_px,
        finish + normal * half_width_px
    ])
    _v184_window_label.text = "MAKE WINDOW  ±%.0f cm" % _v184_last_tolerance_cm

    var visible: bool = _v183_panel.visible
    _v184_left_edge.visible = visible
    _v184_right_edge.visible = visible
    _v184_gate.visible = visible
    _v184_window_label.visible = visible

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    if _v183_panel == null or not _v183_panel.visible:
        if _v184_left_edge != null:
            _v184_left_edge.visible = false
            _v184_right_edge.visible = false
            _v184_gate.visible = false
            _v184_window_label.visible = false
        return
    _v184_refresh_window(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("sideSlope", 0.0)),
        float(s.get("longSlope", 0.0))
    )
