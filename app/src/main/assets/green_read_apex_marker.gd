extends Node

# Presentation-only APEX cue derived from the already-rendered authoritative recommendation path.
# It never feeds back into physics, terrain, GreenReadAdvisor, aim, scoring, or bridge state.
# Refresh is deliberately low-frequency to keep Forward Mobile / TV rendering cheap.

const MIN_BREAK_PX := 6.0
const SEARCH_START_FRACTION := 0.14
const SEARCH_END_FRACTION := 0.84
const MARKER_RADIUS_PX := 4.5
const MARKER_SEGMENTS := 16
const CROSS_HALF_PX := 7.0
const REFRESH_INTERVAL_S := 0.12
const APEX_COLOR := Color(1.0, 0.84, 0.34, 0.96)
const BADGE_COLOR := Color(1.0, 0.90, 0.56, 0.98)

var _panel: Control
var _ring: Line2D
var _cross_a: Line2D
var _cross_b: Line2D
var _badge: Label
var _timer: Timer

func _circle_points(radius: float, segments: int) -> PackedVector2Array:
    var points := PackedVector2Array()
    var safe_segments := maxi(8, segments)
    for i in range(safe_segments + 1):
        var angle := TAU * float(i) / float(safe_segments)
        points.append(Vector2(cos(angle), sin(angle)) * radius)
    return points

func _distance_to_baseline(point: Vector2, start: Vector2, finish: Vector2) -> float:
    var baseline := finish - start
    var length := baseline.length()
    if length < 0.001:
        return 0.0
    return absf(baseline.cross(point - start)) / length

func _apex_geometry(curve: PackedVector2Array) -> Dictionary:
    if curve.size() < 5:
        return {}
    var start := curve[0]
    var finish := curve[curve.size() - 1]
    if start.distance_to(finish) < 1.0:
        return {}

    var first_index := clampi(int(floor(float(curve.size() - 1) * SEARCH_START_FRACTION)), 1, curve.size() - 2)
    var last_index := clampi(int(ceil(float(curve.size() - 1) * SEARCH_END_FRACTION)), first_index, curve.size() - 2)
    var best_index := -1
    var best_break_px := 0.0
    for i in range(first_index, last_index + 1):
        var break_px := _distance_to_baseline(curve[i], start, finish)
        if break_px > best_break_px:
            best_break_px = break_px
            best_index = i

    if best_index < 0 or best_break_px < MIN_BREAK_PX:
        return {}
    return {
        "center": curve[best_index],
        "break_px": best_break_px,
        "index": best_index
    }

func _set_visible(value: bool) -> void:
    if _ring != null:
        _ring.visible = value
    if _cross_a != null:
        _cross_a.visible = value
    if _cross_b != null:
        _cross_b.visible = value
    if _badge != null:
        _badge.visible = value

func _ready() -> void:
    _timer = Timer.new()
    _timer.name = "GreenReadApexRefresh"
    _timer.wait_time = REFRESH_INTERVAL_S
    _timer.one_shot = false
    _timer.autostart = true
    _timer.timeout.connect(_refresh)
    add_child(_timer)
    call_deferred("_bind")

func _bind() -> void:
    var root := get_parent()
    if root == null:
        return
    _panel = root.get("_v183_panel") as Control
    if _panel == null:
        call_deferred("_bind")
        return

    _ring = Line2D.new()
    _ring.name = "CommercialReadApexRing"
    _ring.width = 1.8
    _ring.default_color = APEX_COLOR
    _ring.closed = true
    _ring.points = _circle_points(MARKER_RADIUS_PX, MARKER_SEGMENTS)
    _panel.add_child(_ring)

    _cross_a = Line2D.new()
    _cross_a.name = "CommercialReadApexCrossA"
    _cross_a.width = 1.35
    _cross_a.default_color = APEX_COLOR
    _panel.add_child(_cross_a)

    _cross_b = Line2D.new()
    _cross_b.name = "CommercialReadApexCrossB"
    _cross_b.width = 1.35
    _cross_b.default_color = APEX_COLOR
    _panel.add_child(_cross_b)

    _badge = Label.new()
    _badge.name = "CommercialReadApexBadge"
    _badge.text = "APEX"
    _badge.size = Vector2(46.0, 16.0)
    _badge.add_theme_font_size_override("font_size", 8)
    _badge.add_theme_color_override("font_color", BADGE_COLOR)
    _badge.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _panel.add_child(_badge)
    _refresh()

func _refresh() -> void:
    var root := get_parent()
    if root == null or _panel == null or _ring == null or _cross_a == null or _cross_b == null or _badge == null:
        return
    if not _panel.visible or not root.has_method("_v183_path"):
        _set_visible(false)
        return

    var offset_variant = root.get("_v165_recommended_offset")
    var offset_m := float(offset_variant) if offset_variant != null else 0.0
    var curve: PackedVector2Array = root.call("_v183_path", offset_m)
    var geometry := _apex_geometry(curve)
    if geometry.is_empty():
        _set_visible(false)
        return

    _set_visible(true)
    var center: Vector2 = geometry["center"]
    _ring.position = center
    _cross_a.points = PackedVector2Array([
        center + Vector2(-CROSS_HALF_PX, 0.0),
        center + Vector2(CROSS_HALF_PX, 0.0)
    ])
    _cross_b.points = PackedVector2Array([
        center + Vector2(0.0, -CROSS_HALF_PX),
        center + Vector2(0.0, CROSS_HALF_PX)
    ])

    # Put the badge opposite the panel edge and clamp it into the overview safe area.
    var panel_size := _panel.size
    var badge_x_unclamped := center.x + 8.0 if center.x <= panel_size.x * 0.5 else center.x - 54.0
    var badge_x := clampf(badge_x_unclamped, 4.0, maxf(4.0, panel_size.x - 50.0))
    var badge_y := clampf(center.y - 20.0, 4.0, maxf(4.0, panel_size.y - 20.0))
    _badge.position = Vector2(badge_x, badge_y)
