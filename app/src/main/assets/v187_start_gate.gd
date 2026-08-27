extends "res://v186_cup_entry.gd"

# Presentation-only start-line gate layered onto GREEN OVERVIEW. It converts the existing
# authoritative recommended read plus make-window tolerance into a readable launch corridor near
# the ball. It never feeds values back into Android physics, GreenTerrain or GreenReadAdvisor.

var _v187_gate: Line2D
var _v187_center_tick: Line2D
var _v187_aim_marker: Polygon2D
var _v187_gate_label: Label
var _v187_gate_half_px := 0.0

func _v187_triangle(size: float) -> PackedVector2Array:
    return PackedVector2Array([
        Vector2(0.0, -size),
        Vector2(size * 0.82, size * 0.72),
        Vector2(-size * 0.82, size * 0.72)
    ])

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _v187_gate = Line2D.new()
    _v187_gate.name = "StartLineGate"
    _v187_gate.width = 3.0
    _v187_gate.default_color = Color(0.47, 0.84, 0.66, 0.88)
    _v183_panel.add_child(_v187_gate)

    _v187_center_tick = Line2D.new()
    _v187_center_tick.name = "StartLineCenterTick"
    _v187_center_tick.width = 2.0
    _v187_center_tick.default_color = Color(0.95, 0.80, 0.32, 0.98)
    _v183_panel.add_child(_v187_center_tick)

    _v187_aim_marker = Polygon2D.new()
    _v187_aim_marker.name = "StartLineAimMarker"
    _v187_aim_marker.polygon = _v187_triangle(5.5)
    _v187_aim_marker.color = Color(0.95, 0.80, 0.32, 0.98)
    _v183_panel.add_child(_v187_aim_marker)

    _v187_gate_label = _v174_text(
        _v183_panel,
        Vector2(252, 32),
        Vector2(70, 16),
        "GATE ±5",
        9,
        Color(0.64, 0.88, 0.76, 0.96),
        HORIZONTAL_ALIGNMENT_RIGHT
    )

func _v187_refresh_gate(distance_m: float, side_pct: float, long_pct: float) -> void:
    if _v187_gate == null or _v183_path_line == null:
        return
    var curve := _v183_path_line.points
    if curve.size() < 3:
        return

    var start: Vector2 = curve[0]
    var tangent: Vector2 = (curve[2] - curve[0]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)

    var tolerance_cm := _v184_tolerance_cm(distance_m, side_pct, long_pct)
    _v187_gate_half_px = clampf(tolerance_cm * 2.0, 5.0, 16.0)
    var gate_center := start + tangent * 14.0
    _v187_gate.points = PackedVector2Array([
        gate_center - normal * _v187_gate_half_px,
        gate_center + normal * _v187_gate_half_px
    ])
    _v187_center_tick.points = PackedVector2Array([
        gate_center - tangent * 5.0,
        gate_center + tangent * 6.0
    ])

    # The marker sits just beyond the launch gate on the authoritative recommended curve.
    _v187_aim_marker.position = start + tangent * 24.0
    _v187_aim_marker.rotation = tangent.angle() + PI * 0.5
    _v187_gate_label.text = "GATE ±%.0f" % tolerance_cm

    var visible := _v183_panel.visible
    _v187_gate.visible = visible
    _v187_center_tick.visible = visible
    _v187_aim_marker.visible = visible
    _v187_gate_label.visible = visible

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    if _v183_panel == null or not _v183_panel.visible:
        if _v187_gate != null:
            _v187_gate.visible = false
            _v187_center_tick.visible = false
            _v187_aim_marker.visible = false
            _v187_gate_label.visible = false
        return
    _v187_refresh_gate(
        maxf(0.0, float(s.get("distanceToCup", 0.0))),
        float(s.get("sideSlope", 0.0)),
        float(s.get("longSlope", 0.0))
    )
