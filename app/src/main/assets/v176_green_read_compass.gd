extends "res://v175_cinematic_replay.gd"

# V176 green-read compass. Presentation-only: Android V135-V137 / GreenTerrain /
# GreenReadAdvisor remain authoritative. This layer turns the existing side/long slope telemetry into
# an original, glanceable TV read package without changing aim advice or ball physics.

var _v176_read_panel: Panel
var _v176_vector_line: Line2D
var _v176_vector_head: Polygon2D
var _v176_direction_label: Label
var _v176_strength_label: Label
var _v176_grade_label: Label
var _v176_center: Vector2 = Vector2(205.0, 103.0)

func _build_hud() -> void:
    super._build_hud()

    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v176_read_panel = _v174_panel(root, Vector2(1480, 812), Vector2(410, 234), Color(0.018, 0.025, 0.029, 0.87), Color(0.70, 0.75, 0.70, 0.18), 14)
    _v176_read_panel.name = "V176GreenReadCompass"
    _v174_accent(_v176_read_panel, Vector2(403, 0), Vector2(7, 234), Color("#76a97a"))
    _v174_text(_v176_read_panel, Vector2(22, 10), Vector2(200, 26), "GREEN READ VECTOR", 14, Color(0.78, 0.82, 0.79, 0.94))
    _v176_strength_label = _v174_text(_v176_read_panel, Vector2(238, 10), Vector2(144, 26), "0.00%", 14, Color("#b9dda6"), HORIZONTAL_ALIGNMENT_RIGHT)

    # Simple compass rails: inexpensive 2D geometry that remains sharp at 1080p and on Forward Mobile.
    var h_rail := ColorRect.new()
    h_rail.position = Vector2(92, 102)
    h_rail.size = Vector2(226, 2)
    h_rail.color = Color(0.78, 0.82, 0.79, 0.18)
    h_rail.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v176_read_panel.add_child(h_rail)

    var v_rail := ColorRect.new()
    v_rail.position = Vector2(204, 47)
    v_rail.size = Vector2(2, 112)
    v_rail.color = Color(0.78, 0.82, 0.79, 0.18)
    v_rail.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v176_read_panel.add_child(v_rail)

    _v174_text(_v176_read_panel, Vector2(40, 86), Vector2(54, 30), "L", 12, Color(0.68, 0.72, 0.69, 0.82), HORIZONTAL_ALIGNMENT_CENTER)
    _v174_text(_v176_read_panel, Vector2(316, 86), Vector2(54, 30), "R", 12, Color(0.68, 0.72, 0.69, 0.82), HORIZONTAL_ALIGNMENT_CENTER)
    _v174_text(_v176_read_panel, Vector2(178, 34), Vector2(54, 24), "UP", 11, Color(0.68, 0.72, 0.69, 0.82), HORIZONTAL_ALIGNMENT_CENTER)
    _v174_text(_v176_read_panel, Vector2(170, 150), Vector2(70, 24), "DOWN", 11, Color(0.68, 0.72, 0.69, 0.82), HORIZONTAL_ALIGNMENT_CENTER)

    var center_dot := ColorRect.new()
    center_dot.position = _v176_center - Vector2(4, 4)
    center_dot.size = Vector2(8, 8)
    center_dot.color = Color("#f3f5ef")
    center_dot.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v176_read_panel.add_child(center_dot)

    _v176_vector_line = Line2D.new()
    _v176_vector_line.width = 5.0
    _v176_vector_line.default_color = Color("#d6b85c")
    _v176_vector_line.antialiased = true
    _v176_vector_line.points = PackedVector2Array([_v176_center, _v176_center])
    _v176_read_panel.add_child(_v176_vector_line)

    _v176_vector_head = Polygon2D.new()
    _v176_vector_head.polygon = PackedVector2Array([Vector2(0, -8), Vector2(17, 0), Vector2(0, 8)])
    _v176_vector_head.color = Color("#fff0b8")
    _v176_vector_head.position = _v176_center
    _v176_read_panel.add_child(_v176_vector_head)

    _v176_direction_label = _v174_text(_v176_read_panel, Vector2(22, 178), Vector2(208, 36), "STRAIGHT", 20, Color("#f1f4ef"))
    _v176_grade_label = _v174_text(_v176_read_panel, Vector2(222, 178), Vector2(160, 36), "LEVEL", 18, Color("#d3ddd0"), HORIZONTAL_ALIGNMENT_RIGHT)

func _v176_break_vector(side: float, long_slope: float) -> Vector2:
    # X follows the existing L/R convention. Positive longitudinal slope is uphill, so the visual
    # vector points upward for uphill and downward for downhill. Magnitude is clamped for readability.
    var raw := Vector2(side, -long_slope)
    var magnitude := raw.length()
    if magnitude < 0.001:
        return Vector2.ZERO
    var visual_length: float = lerp(18.0, 76.0, clamp(magnitude / 3.0, 0.0, 1.0))
    return raw.normalized() * visual_length

func _v176_strength(side: float, long_slope: float) -> float:
    return sqrt(side * side + long_slope * long_slope)

func _v176_update_vector(side: float, long_slope: float) -> void:
    if _v176_vector_line == null or _v176_vector_head == null:
        return
    var vector := _v176_break_vector(side, long_slope)
    var tip := _v176_center + vector
    _v176_vector_line.points = PackedVector2Array([_v176_center, tip])
    _v176_vector_head.position = tip
    if vector.length_squared() > 0.001:
        _v176_vector_head.rotation = vector.angle()
        _v176_vector_head.visible = true
    else:
        _v176_vector_head.visible = false

func _update_hud(s: Dictionary, running: bool, holed: bool, lip_out: bool, speed: float) -> void:
    super._update_hud(s, running, holed, lip_out, speed)
    if _v176_read_panel == null:
        return

    var side: float = float(s.get("sideSlope", 0.0))
    var long_slope: float = float(s.get("longSlope", 0.0))
    _v176_update_vector(side, long_slope)
    _v176_strength_label.text = "%.2f%%" % _v176_strength(side, long_slope)
    _v176_direction_label.text = _v174_direction(side)
    _v176_grade_label.text = _v174_grade(long_slope)

    # Keep the pre-shot read useful, but remove it once motion/replay starts so the ball line owns the TV.
    _v176_read_panel.visible = not running and _v171_replay_remaining <= 0.0 and not holed and not lip_out
