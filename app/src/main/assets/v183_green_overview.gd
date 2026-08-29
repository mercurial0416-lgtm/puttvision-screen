extends "res://v182_break_flow.gd"

# Presentation-only top-down read overview. It visualizes authoritative distance, slope and
# GreenReadAdvisor aim compensation without feeding any values back into Android physics.

var _v183_panel: Panel
var _v183_path_line: Line2D
var _v183_center_line: Line2D
var _v183_break_arrow: Line2D
var _v183_break_arrow_left: Line2D
var _v183_break_arrow_right: Line2D
var _v183_ball: Polygon2D
var _v183_cup: Polygon2D
var _v183_distance_label: Label
var _v183_break_label: Label
var _v183_grade_label: Label
var _v183_preview_force_visible := false

const V183_MAP_ORIGIN := Vector2(34.0, 48.0)
const V183_MAP_SIZE := Vector2(248.0, 136.0)
const V183_FALL_LINE_DEADBAND := 0.08
const V183_FALL_LINE_MIN_LENGTH := 20.0
const V183_FALL_LINE_MAX_LENGTH := 48.0
const V183_FALL_LINE_HEAD_LENGTH := 8.0
const V183_FALL_LINE_HEAD_WIDTH := 5.0

func _v183_circle(radius: float, segments: int = 20) -> PackedVector2Array:
    var out := PackedVector2Array()
    for i in range(segments):
        var a := TAU * float(i) / float(segments)
        out.append(Vector2(cos(a), sin(a)) * radius)
    return out

func _v183_path(offset_m: float) -> PackedVector2Array:
    var out := PackedVector2Array()
    var start := V183_MAP_ORIGIN + Vector2(V183_MAP_SIZE.x * 0.5, V183_MAP_SIZE.y - 8.0)
    var finish := V183_MAP_ORIGIN + Vector2(V183_MAP_SIZE.x * 0.5, 8.0)
    var bend := clampf(offset_m / 1.8, -1.0, 1.0) * 62.0
    for i in range(25):
        var t := float(i) / 24.0
        var p := start.lerp(finish, t)
        p.x += bend * 4.0 * t * (1.0 - t)
        out.append(p)
    return out

func _v183_break_text(side_pct: float) -> String:
    if abs(side_pct) < 0.05:
        return "BREAK  STRAIGHT"
    # GreenSettings semantics describe the low side: positive means the right side is lower,
    # therefore the ball's gravity break is right. Label BREAK by ball movement, not aim offset.
    return "BREAK  %s %.2f%%" % [("R" if side_pct > 0.0 else "L"), abs(side_pct)]

func _v183_grade_text(long_pct: float) -> String:
    if abs(long_pct) < 0.05:
        return "GRADE  LEVEL"
    return "GRADE  %s %.2f%%" % [("DOWN" if long_pct > 0.0 else "UP"), abs(long_pct)]

# Returns presentation-only fall-line geometry in panel coordinates. X follows the authoritative
# side slope sign (positive = right side low); positive longitudinal slope is downhill toward the
# cup, which is upward on this top-down map. This fixes the old horizontal-only cue that silently
# discarded the longitudinal component while claiming to show the fall line.
func _v183_fall_line_geometry(side_pct: float, long_pct: float) -> Dictionary:
    var slope := Vector2(side_pct, -long_pct)
    var magnitude := slope.length()
    if magnitude < V183_FALL_LINE_DEADBAND:
        return {"visible": false, "shaft": PackedVector2Array(), "left": PackedVector2Array(), "right": PackedVector2Array()}

    var direction := slope / magnitude
    var strength := clampf(magnitude / 3.0, 0.0, 1.0)
    var length_px := lerpf(V183_FALL_LINE_MIN_LENGTH, V183_FALL_LINE_MAX_LENGTH, strength)
    var center := V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    var tail := center - direction * length_px * 0.42
    var tip := center + direction * length_px * 0.58
    var back := -direction
    var normal := Vector2(-direction.y, direction.x)
    var left_tip := tip + back * V183_FALL_LINE_HEAD_LENGTH + normal * V183_FALL_LINE_HEAD_WIDTH
    var right_tip := tip + back * V183_FALL_LINE_HEAD_LENGTH - normal * V183_FALL_LINE_HEAD_WIDTH
    return {
        "visible": true,
        "shaft": PackedVector2Array([tail, tip]),
        "left": PackedVector2Array([left_tip, tip]),
        "right": PackedVector2Array([right_tip, tip])
    }

func _build_hud() -> void:
    super._build_hud()
    var layer := get_node_or_null("V174BroadcastHUD") as CanvasLayer
    if layer == null:
        return
    var root := layer.get_node_or_null("V174HUDRoot") as Control
    if root == null:
        return

    _v183_panel = _v174_panel(root, Vector2(1538, 500), Vector2(342, 252), Color(0.012, 0.020, 0.024, 0.90), Color(0.38, 0.78, 0.67, 0.22), 14)
    _v183_panel.name = "GreenReadOverview"
    _v183_panel.visible = false
    _v174_accent(_v183_panel, Vector2(0, 0), Vector2(6, 252), Color("#76d7b6"))
    _v174_text(_v183_panel, Vector2(20, 10), Vector2(190, 24), "GREEN OVERVIEW", 13, Color(0.82, 0.94, 0.89, 0.98))
    _v183_distance_label = _v174_text(_v183_panel, Vector2(206, 9), Vector2(112, 24), "3.0 m", 14, Color("#f4dda0"), HORIZONTAL_ALIGNMENT_RIGHT)

    var map_bg := ColorRect.new()
    map_bg.position = V183_MAP_ORIGIN
    map_bg.size = V183_MAP_SIZE
    map_bg.color = Color(0.035, 0.070, 0.060, 0.72)
    map_bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
    _v183_panel.add_child(map_bg)

    for i in range(5):
        var band := Line2D.new()
        band.width = 1.0
        band.default_color = Color(0.62, 0.82, 0.72, 0.10)
        var y := V183_MAP_ORIGIN.y + 18.0 + float(i) * 24.0
        band.points = PackedVector2Array([
            Vector2(V183_MAP_ORIGIN.x + 10.0, y),
            Vector2(V183_MAP_ORIGIN.x + V183_MAP_SIZE.x - 10.0, y)
        ])
        _v183_panel.add_child(band)

    _v183_center_line = Line2D.new()
    _v183_center_line.width = 1.0
    _v183_center_line.default_color = Color(0.76, 0.86, 0.82, 0.20)
    _v183_center_line.points = PackedVector2Array([
        V183_MAP_ORIGIN + Vector2(V183_MAP_SIZE.x * 0.5, V183_MAP_SIZE.y - 8.0),
        V183_MAP_ORIGIN + Vector2(V183_MAP_SIZE.x * 0.5, 8.0)
    ])
    _v183_panel.add_child(_v183_center_line)

    _v183_path_line = Line2D.new()
    _v183_path_line.name = "AuthoritativeReadOverviewPath"
    _v183_path_line.width = 3.0
    _v183_path_line.default_color = Color("#f0c84e")
    _v183_panel.add_child(_v183_path_line)

    _v183_ball = Polygon2D.new()
    _v183_ball.polygon = _v183_circle(5.0)
    _v183_ball.color = Color(0.98, 0.99, 1.0, 1.0)
    _v183_panel.add_child(_v183_ball)

    _v183_cup = Polygon2D.new()
    _v183_cup.polygon = _v183_circle(6.0)
    _v183_cup.color = Color(0.02, 0.03, 0.03, 1.0)
    _v183_panel.add_child(_v183_cup)

    _v183_break_arrow = Line2D.new()
    _v183_break_arrow.name = "FallLineShaft"
    _v183_break_arrow.width = 2.4
    _v183_break_arrow.default_color = Color("#76d7b6")
    _v183_panel.add_child(_v183_break_arrow)

    _v183_break_arrow_left = Line2D.new()
    _v183_break_arrow_left.name = "FallLineHeadLeft"
    _v183_break_arrow_left.width = 2.4
    _v183_break_arrow_left.default_color = Color("#76d7b6")
    _v183_panel.add_child(_v183_break_arrow_left)

    _v183_break_arrow_right = Line2D.new()
    _v183_break_arrow_right.name = "FallLineHeadRight"
    _v183_break_arrow_right.width = 2.4
    _v183_break_arrow_right.default_color = Color("#76d7b6")
    _v183_panel.add_child(_v183_break_arrow_right)

    _v183_break_label = _v174_text(_v183_panel, Vector2(20, 198), Vector2(148, 20), "BREAK  STRAIGHT", 11, Color(0.72, 0.90, 0.84, 0.96))
    _v183_grade_label = _v174_text(_v183_panel, Vector2(170, 198), Vector2(148, 20), "GRADE  LEVEL", 11, Color(0.72, 0.90, 0.84, 0.96), HORIZONTAL_ALIGNMENT_RIGHT)
    _v174_text(_v183_panel, Vector2(20, 222), Vector2(298, 18), "gold = recommended read  •  teal = fall line", 10, Color(0.50, 0.62, 0.58, 0.88))

    # Initialize geometry without engaging the preview-only visibility latch.
    _v183_update({"distanceToCup": 3.0, "sideSlope": 0.0, "longSlope": 0.0}, false)

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    if _v183_panel == null:
        return
    if force_visible:
        _v183_preview_force_visible = true
    elif _v183_preview_force_visible:
        # CI preview keeps the synthetic curved read stable until the screenshot is captured.
        return

    var running := bool(s.get("running", false))
    var active := _v183_preview_force_visible or (_v165_enhanced_enabled and _v164_grid_enabled and not running and _v171_replay_remaining <= 0.0)
    _v183_panel.visible = active
    if not active:
        return

    var distance_m := maxf(0.0, float(s.get("distanceToCup", 0.0)))
    var side_pct := float(s.get("sideSlope", 0.0))
    var long_pct := float(s.get("longSlope", 0.0))
    var curve := _v183_path(_v165_recommended_offset)
    _v183_path_line.points = curve
    if curve.size() >= 2:
        _v183_ball.position = curve[0]
        _v183_cup.position = curve[curve.size() - 1]

    _v183_distance_label.text = "%.1f m" % distance_m
    _v183_break_label.text = _v183_break_text(side_pct)
    _v183_grade_label.text = _v183_grade_text(long_pct)

    var fall_line := _v183_fall_line_geometry(side_pct, long_pct)
    var fall_visible := bool(fall_line.get("visible", false))
    _v183_break_arrow.visible = fall_visible
    _v183_break_arrow_left.visible = fall_visible
    _v183_break_arrow_right.visible = fall_visible
    if fall_visible:
        _v183_break_arrow.points = fall_line["shaft"] as PackedVector2Array
        _v183_break_arrow_left.points = fall_line["left"] as PackedVector2Array
        _v183_break_arrow_right.points = fall_line["right"] as PackedVector2Array

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v183_update(s)
