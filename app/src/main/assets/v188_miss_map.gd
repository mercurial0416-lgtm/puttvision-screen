extends "res://v187_start_gate.gd"

# Presentation-only post-shot miss map. It visualizes the existing authoritative read-line and
# pace deltas as a compact target plot beside SHOT DEBRIEF. No values feed back into physics,
# GreenTerrain, GreenReadAdvisor, or shot scoring.

var _v188_panel: Panel
var _v188_ring: Line2D
var _v188_cross_h: Line2D
var _v188_cross_v: Line2D
var _v188_dot: Polygon2D
var _v188_vector: Line2D
var _v188_overflow_tick: Line2D
var _v188_overflow_label: Label
var _v188_detail: Label

const V188_CENTER := Vector2(76.0, 86.0)
const V188_RADIUS := 42.0
const V188_LINE_WINDOW_CM := 30.0
const V188_PACE_WINDOW_CM := 70.0

func _v188_circle(radius: float, segments: int = 32) -> PackedVector2Array:
    var out := PackedVector2Array()
    for i in range(segments + 1):
        var a := TAU * float(i) / float(segments)
        out.append(Vector2(cos(a), sin(a)) * radius)
    return out

func _v188_marker(radius: float, segments: int = 18) -> PackedVector2Array:
    var out := PackedVector2Array()
    for i in range(segments):
        var a := TAU * float(i) / float(segments)
        out.append(Vector2(cos(a), sin(a)) * radius)
    return out

func _v188_arrow_marker(radius: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(radius, 0.0),
        Vector2(-radius * 0.58, -radius * 0.66),
        Vector2(-radius * 0.58, radius * 0.66),
    ])

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v188_panel = _v174_panel(root, Vector2(1162, 500), Vector2(150, 220), Color(0.014, 0.021, 0.026, 0.92), Color(0.90, 0.78, 0.40, 0.20), 14)
    _v188_panel.name = "ShotMissMap"
    _v188_panel.visible = false
    _v174_accent(_v188_panel, Vector2(0, 0), Vector2(5, 220), Color("#d6b85c"))
    _v174_text(_v188_panel, Vector2(14, 10), Vector2(122, 22), "SHOT MAP", 12, Color(0.82, 0.88, 0.84, 0.96), HORIZONTAL_ALIGNMENT_CENTER)

    _v188_ring = Line2D.new()
    _v188_ring.name = "ShotMapWindow"
    _v188_ring.width = 1.5
    _v188_ring.default_color = Color(0.47, 0.84, 0.66, 0.42)
    _v188_ring.position = V188_CENTER
    _v188_ring.points = _v188_circle(V188_RADIUS)
    _v188_panel.add_child(_v188_ring)

    _v188_cross_h = Line2D.new()
    _v188_cross_h.width = 1.0
    _v188_cross_h.default_color = Color(0.76, 0.84, 0.79, 0.20)
    _v188_cross_h.points = PackedVector2Array([V188_CENTER - Vector2(V188_RADIUS, 0), V188_CENTER + Vector2(V188_RADIUS, 0)])
    _v188_panel.add_child(_v188_cross_h)

    _v188_cross_v = Line2D.new()
    _v188_cross_v.width = 1.0
    _v188_cross_v.default_color = Color(0.76, 0.84, 0.79, 0.20)
    _v188_cross_v.points = PackedVector2Array([V188_CENTER - Vector2(0, V188_RADIUS), V188_CENTER + Vector2(0, V188_RADIUS)])
    _v188_panel.add_child(_v188_cross_v)

    var center_dot := Polygon2D.new()
    center_dot.polygon = _v188_marker(3.2, 14)
    center_dot.position = V188_CENTER
    center_dot.color = Color("#76d7b6")
    _v188_panel.add_child(center_dot)

    _v188_vector = Line2D.new()
    _v188_vector.name = "ShotMissVector"
    _v188_vector.width = 2.0
    _v188_vector.default_color = Color(0.95, 0.80, 0.32, 0.62)
    _v188_panel.add_child(_v188_vector)

    _v188_overflow_tick = Line2D.new()
    _v188_overflow_tick.name = "ShotMissOverflowTick"
    _v188_overflow_tick.width = 3.0
    _v188_overflow_tick.default_color = Color("#ffcf70")
    _v188_overflow_tick.visible = false
    _v188_panel.add_child(_v188_overflow_tick)

    _v188_dot = Polygon2D.new()
    _v188_dot.name = "ActualShotMarker"
    _v188_dot.polygon = _v188_marker(5.2)
    _v188_dot.color = Color("#f4dda0")
    _v188_panel.add_child(_v188_dot)

    _v188_overflow_label = _v174_text(_v188_panel, Vector2(14, 33), Vector2(122, 15), "", 9, Color("#ffcf70"), HORIZONTAL_ALIGNMENT_CENTER)
    _v188_overflow_label.name = "ShotMapOverflowReadout"
    _v188_overflow_label.visible = false

    _v174_text(_v188_panel, Vector2(14, 138), Vector2(122, 17), "CENTER = READ + PACE", 8, Color(0.53, 0.64, 0.60, 0.90), HORIZONTAL_ALIGNMENT_CENTER)
    _v188_detail = _v174_text(_v188_panel, Vector2(10, 160), Vector2(130, 42), "ON LINE\nCUP PACE", 10, Color("#f1f4ef"), HORIZONTAL_ALIGNMENT_CENTER)

func _v188_normalized_miss(line_delta_cm: float, pace_delta_cm: float) -> Vector2:
    return Vector2(line_delta_cm / V188_LINE_WINDOW_CM, -pace_delta_cm / V188_PACE_WINDOW_CM)

func _v188_point(line_delta_cm: float, pace_delta_cm: float) -> Vector2:
    var normalized := _v188_normalized_miss(line_delta_cm, pace_delta_cm)
    if normalized.length() > 1.0:
        normalized = normalized.normalized()
    return V188_CENTER + normalized * V188_RADIUS

func _v188_metrics_are_finite(line_delta_cm: float, pace_delta_cm: float) -> bool:
    return is_finite(line_delta_cm) and is_finite(pace_delta_cm)

func _v188_snapshot_has_metrics(s: Dictionary) -> bool:
    return s.has("readLineDeltaCm") and s.has("paceDeltaCm") and _v188_metrics_are_finite(
        float(s.get("readLineDeltaCm", 0.0)),
        float(s.get("paceDeltaCm", 0.0))
    )

func _v188_refresh(line_delta_cm: float, pace_delta_cm: float, visible: bool) -> void:
    if _v188_panel == null:
        return
    # Missing/malformed presentation telemetry must never collapse to (0, 0), which visually reads
    # as a perfect make. Hide the map until both measured deltas are finite again. This is strictly
    # a presentation truth guard and does not modify the authoritative shot/read values.
    var truthful_visible := visible and _v188_metrics_are_finite(line_delta_cm, pace_delta_cm)
    _v188_panel.visible = truthful_visible
    if not truthful_visible:
        if _v188_overflow_tick != null:
            _v188_overflow_tick.visible = false
        if _v188_overflow_label != null:
            _v188_overflow_label.visible = false
        return

    var normalized := _v188_normalized_miss(line_delta_cm, pace_delta_cm)
    var map_extent := normalized.length()
    var point: Vector2 = _v188_point(line_delta_cm, pace_delta_cm)
    var is_offscale := map_extent > 1.0

    _v188_dot.position = point
    _v188_vector.points = PackedVector2Array([V188_CENTER, point])

    if is_offscale:
        var direction := normalized.normalized()
        var tail_length := clampf(6.0 + (map_extent - 1.0) * 6.0, 6.0, 14.0)
        _v188_overflow_tick.points = PackedVector2Array([point, point + direction * tail_length])
        _v188_overflow_tick.visible = true
        _v188_overflow_label.text = "OUTSIDE  %.1fx" % map_extent
        _v188_overflow_label.visible = true
        _v188_dot.polygon = _v188_arrow_marker(7.0)
        _v188_dot.rotation = direction.angle()
        _v188_dot.color = Color("#ffcf70")
    else:
        _v188_overflow_tick.visible = false
        _v188_overflow_label.visible = false
        _v188_dot.polygon = _v188_marker(5.2)
        _v188_dot.rotation = 0.0
        var within_window: bool = absf(line_delta_cm) <= 9.0 and absf(pace_delta_cm) <= 22.0
        _v188_dot.color = Color("#76d7b6") if within_window else Color("#f4dda0")

    _v188_detail.text = "%s\n%s" % [_v177_line_text(line_delta_cm), _v177_pace_text(pace_delta_cm)]

func _v177_update_debrief(s: Dictionary, force_visible: bool = false) -> void:
    super._v177_update_debrief(s, force_visible)
    if _v188_panel == null:
        return
    var metrics_valid := _v188_snapshot_has_metrics(s)
    var show: bool = _v177_panel != null and _v177_panel.visible and metrics_valid
    _v188_refresh(
        float(s.get("readLineDeltaCm", 0.0)) if metrics_valid else 0.0,
        float(s.get("paceDeltaCm", 0.0)) if metrics_valid else 0.0,
        show
    )
