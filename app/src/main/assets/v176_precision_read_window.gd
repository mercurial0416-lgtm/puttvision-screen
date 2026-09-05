extends "res://v175_cinematic_replay.gd"

# V176: compact pre-shot precision read window.
# Presentation only. Android V135-V137 / GreenTerrain / GreenReadAdvisor remain
# authoritative; this layer visualizes their live aim/slope result without feeding
# any value back into ball physics.

var _v176_panel: ColorRect
var _v176_corridor: Line2D
var _v176_curve: Line2D
var _v176_aim_marker: Polygon2D
var _v176_break_arrow: Line2D
var _v176_aim_value: Label
var _v176_break_value: Label
var _v176_grade_value: Label
var _v176_source_value: Label

const V176_DIAGRAM_CENTER_X := 112.0
const V176_DIAGRAM_TOP_Y := 60.0
const V176_DIAGRAM_BOTTOM_Y := 166.0
const V176_CURVE_SCALE_PX := 58.0

func _v176_circle(radius: float, segments: int = 24) -> PackedVector2Array:
    var points := PackedVector2Array()
    for i in range(segments):
        var angle := TAU * float(i) / float(segments)
        points.append(Vector2(cos(angle), sin(angle)) * radius)
    return points

func _v176_read_curve(offset_m: float) -> PackedVector2Array:
    var points := PackedVector2Array()
    var bulge_px: float = clamp(offset_m / 1.80, -1.0, 1.0) * V176_CURVE_SCALE_PX
    for i in range(17):
        var t: float = float(i) / 16.0
        var x: float = V176_DIAGRAM_CENTER_X + bulge_px * 4.0 * t * (1.0 - t)
        var y: float = lerp(V176_DIAGRAM_BOTTOM_Y, V176_DIAGRAM_TOP_Y, t)
        points.append(Vector2(x, y))
    return points

func _v176_add_caption(parent: Control, text_value: String, pos: Vector2, size_value: Vector2) -> Label:
    var label := _v164_label(parent, pos, size_value, 11, Color(0.58, 0.67, 0.69, 0.94))
    label.text = text_value
    return label

func _v176_metric_is_valid(value: Variant) -> bool:
    var value_type := typeof(value)
    if value_type != TYPE_INT and value_type != TYPE_FLOAT:
        return false
    return is_finite(float(value))

func _build_hud() -> void:
    super._build_hud()

    var layer := CanvasLayer.new()
    layer.name = "V176PrecisionReadHUD"
    layer.layer = 26
    add_child(layer)

    var root := Control.new()
    root.set_anchors_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)

    _v176_panel = ColorRect.new()
    _v176_panel.name = "V176PrecisionReadPanel"
    _v176_panel.position = Vector2(1454, 278)
    _v176_panel.size = Vector2(426, 204)
    _v176_panel.color = Color(0.016, 0.023, 0.028, 0.88)
    _v176_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(_v176_panel)

    var accent := ColorRect.new()
    accent.position = Vector2(0, 0)
    accent.size = Vector2(6, 204)
    accent.color = Color(0.24, 0.94, 0.78, 0.94)
    accent.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v176_panel.add_child(accent)

    var title := _v164_label(_v176_panel, Vector2(18, 8), Vector2(390, 24), 14, Color(0.82, 0.96, 0.92, 1.0))
    title.text = "PRECISION READ  |  CUP WINDOW"

    var diagram_bg := ColorRect.new()
    diagram_bg.position = Vector2(18, 42)
    diagram_bg.size = Vector2(206, 144)
    diagram_bg.color = Color(0.035, 0.055, 0.055, 0.74)
    diagram_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v176_panel.add_child(diagram_bg)

    var center_ref := Line2D.new()
    center_ref.width = 1.0
    center_ref.default_color = Color(0.72, 0.82, 0.82, 0.22)
    center_ref.points = PackedVector2Array([Vector2(V176_DIAGRAM_CENTER_X, 57), Vector2(V176_DIAGRAM_CENTER_X, 170)])
    _v176_panel.add_child(center_ref)

    var cup_outer := Polygon2D.new()
    cup_outer.position = Vector2(V176_DIAGRAM_CENTER_X, V176_DIAGRAM_TOP_Y)
    cup_outer.polygon = _v176_circle(8.0)
    cup_outer.color = Color(0.86, 0.96, 0.93, 0.94)
    _v176_panel.add_child(cup_outer)

    var cup_inner := Polygon2D.new()
    cup_inner.position = Vector2(V176_DIAGRAM_CENTER_X, V176_DIAGRAM_TOP_Y)
    cup_inner.polygon = _v176_circle(4.8)
    cup_inner.color = Color(0.020, 0.032, 0.034, 1.0)
    _v176_panel.add_child(cup_inner)

    var ball := Polygon2D.new()
    ball.position = Vector2(V176_DIAGRAM_CENTER_X, V176_DIAGRAM_BOTTOM_Y)
    ball.polygon = _v176_circle(5.2)
    ball.color = Color(0.98, 0.99, 1.0, 0.98)
    _v176_panel.add_child(ball)

    _v176_corridor = Line2D.new()
    _v176_corridor.name = "V176ReadCorridor"
    _v176_corridor.width = 11.0
    _v176_corridor.default_color = Color(1.0, 0.73, 0.12, 0.11)
    _v176_panel.add_child(_v176_corridor)

    _v176_curve = Line2D.new()
    _v176_curve.name = "V176AuthoritativeReadCurve"
    _v176_curve.width = 2.8
    _v176_curve.default_color = Color(1.0, 0.76, 0.16, 0.96)
    _v176_panel.add_child(_v176_curve)

    _v176_aim_marker = Polygon2D.new()
    _v176_aim_marker.name = "V176AimMarker"
    _v176_aim_marker.polygon = PackedVector2Array([Vector2(0, -6), Vector2(6, 0), Vector2(0, 6), Vector2(-6, 0)])
    _v176_aim_marker.color = Color(1.0, 0.80, 0.22, 1.0)
    _v176_panel.add_child(_v176_aim_marker)

    _v176_break_arrow = Line2D.new()
    _v176_break_arrow.name = "V176BreakArrow"
    _v176_break_arrow.width = 2.0
    _v176_break_arrow.default_color = Color(0.27, 0.92, 0.79, 0.94)
    _v176_panel.add_child(_v176_break_arrow)

    _v176_add_caption(_v176_panel, "AIM COMP", Vector2(246, 48), Vector2(160, 18))
    _v176_aim_value = _v164_label(_v176_panel, Vector2(246, 64), Vector2(160, 25), 16, Color(1.0, 0.82, 0.30, 1.0))

    _v176_add_caption(_v176_panel, "LOCAL BREAK", Vector2(246, 96), Vector2(160, 18))
    _v176_break_value = _v164_label(_v176_panel, Vector2(246, 112), Vector2(160, 22), 14, Color(0.74, 0.94, 0.90, 1.0))

    _v176_add_caption(_v176_panel, "GRADE", Vector2(246, 138), Vector2(160, 18))
    _v176_grade_value = _v164_label(_v176_panel, Vector2(246, 154), Vector2(160, 22), 14, Color(0.74, 0.94, 0.90, 1.0))

    _v176_source_value = _v164_label(_v176_panel, Vector2(246, 180), Vector2(160, 18), 10, Color(0.52, 0.68, 0.68, 0.90))

    _v176_update_visuals(0.0, 0.0, 0.0)

func _v176_update_aim_curve(offset_m: float) -> void:
    if _v176_curve == null:
        return
    var curve := _v176_read_curve(offset_m)
    _v176_curve.points = curve
    _v176_corridor.points = curve
    if curve.size() > 8:
        _v176_aim_marker.position = curve[8]

    var aim_dir := "CENTER"
    if abs(offset_m) >= 0.015:
        aim_dir = ("R" if offset_m > 0.0 else "L") + "  %d cm" % int(round(abs(offset_m) * 100.0))
    _v176_aim_value.text = aim_dir

func _v176_update_visuals(offset_m: float, side_pct: float, long_pct: float) -> void:
    if _v176_curve == null:
        return

    _v176_update_aim_curve(offset_m)

    var break_dir := "STRAIGHT"
    if abs(side_pct) >= 0.03:
        break_dir = ("L" if side_pct > 0.0 else "R") + "  %.2f%%" % abs(side_pct)
    _v176_break_value.text = break_dir

    var grade_dir := "LEVEL"
    if abs(long_pct) >= 0.03:
        grade_dir = ("DOWN" if long_pct > 0.0 else "UP") + "  %.2f%%" % abs(long_pct)
    _v176_grade_value.text = grade_dir
    _v176_source_value.text = "TRUE 3D TERRAIN" if _v166_terrain_ready else "LIVE SLOPE FALLBACK"

    var arrow_origin := Vector2(V176_DIAGRAM_CENTER_X, 88.0)
    var arrow_dx: float = clamp(side_pct / 3.0, -1.0, 1.0) * 34.0
    _v176_break_arrow.points = PackedVector2Array([
        arrow_origin - Vector2(arrow_dx * 0.45, 0.0),
        arrow_origin + Vector2(arrow_dx * 0.55, 0.0)
    ])
    _v176_break_arrow.visible = abs(side_pct) >= 0.08

func _v176_update_unavailable(offset_m: float) -> void:
    # Keep the authoritative aim compensation visible, but never coerce malformed slope telemetry
    # into a believable STRAIGHT / LEVEL read. This is a presentation truth state only.
    if _v176_curve == null:
        return
    _v176_update_aim_curve(offset_m)
    _v176_break_value.text = "--"
    _v176_grade_value.text = "--"
    _v176_source_value.text = "SLOPE DATA UNAVAILABLE"
    _v176_break_arrow.visible = false

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)

    var side_value: Variant = s.get("sideSlope", null)
    var long_value: Variant = s.get("longSlope", null)
    if _v176_metric_is_valid(side_value) and _v176_metric_is_valid(long_value):
        _v176_update_visuals(_v165_recommended_offset, float(side_value), float(long_value))
    else:
        _v176_update_unavailable(_v165_recommended_offset)

    if _v176_panel != null:
        var running: bool = bool(s.get("running", false))
        _v176_panel.visible = _v165_enhanced_enabled and _v164_grid_enabled and not running and _v171_replay_remaining <= 0.0