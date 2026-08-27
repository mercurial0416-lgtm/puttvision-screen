extends "res://v182_break_flow.gd"

# Presentation-only top-down read overview. It visualizes authoritative distance, slope and
# GreenReadAdvisor aim compensation without feeding any values back into Android physics.

var _v183_panel: Panel
var _v183_path_line: Line2D
var _v183_center_line: Line2D
var _v183_break_arrow: Line2D
var _v183_ball: Polygon2D
var _v183_cup: Polygon2D
var _v183_distance_label: Label
var _v183_break_label: Label
var _v183_grade_label: Label
var _v183_preview_force_visible := false

const V183_MAP_ORIGIN := Vector2(34.0, 48.0)
const V183_MAP_SIZE := Vector2(248.0, 136.0)

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
    return "BREAK  %s %.2f%%" % [("L" if side_pct > 0.0 else "R"), abs(side_pct)]

func _v183_grade_text(long_pct: float) -> String:
    if abs(long_pct) < 0.05:
        return "GRADE  LEVEL"
    return "GRADE  %s %.2f%%" % [("DOWN" if long_pct > 0.0 else "UP"), abs(long_pct)]

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
    _v183_break_arrow.width = 2.0
    _v183_break_arrow.default_color = Color("#76d7b6")
    _v183_panel.add_child(_v183_break_arrow)

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

    var center := V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    var dx := clampf(side_pct / 3.0, -1.0, 1.0) * 44.0
    _v183_break_arrow.points = PackedVector2Array([center - Vector2(dx * 0.45, 0.0), center + Vector2(dx * 0.55, 0.0)])
    _v183_break_arrow.visible = abs(side_pct) >= 0.08

func _apply_snapshot(s: Dictionary, immediate: bool, delta: float) -> void:
    super._apply_snapshot(s, immediate, delta)
    _v183_update(s)
