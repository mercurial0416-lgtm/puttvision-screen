extends Node

# Presentation-only break-apex marker derived from the already-rendered authoritative recommendation.
# It highlights where the recommended read departs furthest from the direct ball-to-cup chord without
# feeding anything back into physics, terrain, GreenReadAdvisor, aiming, scoring, or bridge state.
# The same low-frequency cadence as the cup-entry read keeps Forward Mobile cost bounded.

const REFRESH_INTERVAL_S := 0.12
const MIN_APEX_DEVIATION_PX := 5.0
const MIN_APEX_DEVIATION_RATIO := 0.018
const APEX_RING_RADIUS_PX := 4.0
const APEX_RING_SEGMENTS := 14
const APEX_TICK_HALF_LENGTH_PX := 7.0
const APEX_COLOR := Color(1.0, 0.82, 0.38, 0.96)
const BADGE_COLOR := Color(1.0, 0.87, 0.53, 0.98)
const BADGE_SIZE := Vector2(72.0, 16.0)
const PANEL_MARGIN_PX := 4.0

var _panel: Control
var _ring: Line2D
var _tick: Line2D
var _badge: Label
var _timer: Timer

func _circle_points(radius: float, segments: int) -> PackedVector2Array:
    var points := PackedVector2Array()
    var safe_segments := maxi(8, segments)
    for i in range(safe_segments + 1):
        var angle := TAU * float(i) / float(safe_segments)
        points.append(Vector2(cos(angle), sin(angle)) * radius)
    return points

func _apex_geometry(curve: PackedVector2Array) -> Dictionary:
    if curve.size() < 3:
        return {}
    var start := curve[0]
    var finish := curve[curve.size() - 1]
    var chord := finish - start
    var chord_length := chord.length()
    if chord_length < 1.0:
        return {}
    var chord_dir := chord / chord_length
    var best_index := -1
    var best_signed_deviation := 0.0
    var best_abs_deviation := 0.0
    for i in range(1, curve.size() - 1):
        var signed_deviation := chord_dir.cross(curve[i] - start)
        var abs_deviation := absf(signed_deviation)
        if abs_deviation > best_abs_deviation:
            best_abs_deviation = abs_deviation
            best_signed_deviation = signed_deviation
            best_index = i
    var threshold := maxf(MIN_APEX_DEVIATION_PX, chord_length * MIN_APEX_DEVIATION_RATIO)
    if best_index < 0 or best_abs_deviation < threshold:
        return {}
    var apex: Vector2 = curve[best_index]
    var normal := Vector2(-chord_dir.y, chord_dir.x)
    return {
        "apex": apex,
        "tick_start": apex - normal * APEX_TICK_HALF_LENGTH_PX,
        "tick_end": apex + normal * APEX_TICK_HALF_LENGTH_PX,
        "signed_deviation": best_signed_deviation,
        "deviation_px": best_abs_deviation
    }

func _badge_position(apex: Vector2, panel_size: Vector2) -> Vector2:
    # Keep the label off the apex itself and flip around the panel midpoint so the marker stays readable.
    var x_unclamped := apex.x + 10.0 if apex.x <= panel_size.x * 0.5 else apex.x - BADGE_SIZE.x - 10.0
    var y_unclamped := apex.y - BADGE_SIZE.y * 0.5
    return Vector2(
        clampf(x_unclamped, PANEL_MARGIN_PX, maxf(PANEL_MARGIN_PX, panel_size.x - BADGE_SIZE.x - PANEL_MARGIN_PX)),
        clampf(y_unclamped, PANEL_MARGIN_PX, maxf(PANEL_MARGIN_PX, panel_size.y - BADGE_SIZE.y - PANEL_MARGIN_PX))
    )

func _ready() -> void:
    _timer = Timer.new()
    _timer.name = "BreakApexReadMarkerRefresh"
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
    _ring.name = "CommercialReadBreakApexRing"
    _ring.width = 1.8
    _ring.default_color = APEX_COLOR
    _ring.closed = true
    _ring.points = _circle_points(APEX_RING_RADIUS_PX, APEX_RING_SEGMENTS)
    _panel.add_child(_ring)

    _tick = Line2D.new()
    _tick.name = "CommercialReadBreakApexTick"
    _tick.width = 1.7
    _tick.default_color = APEX_COLOR
    _panel.add_child(_tick)

    _badge = Label.new()
    _badge.name = "CommercialReadBreakApexBadge"
    _badge.text = "BREAK APEX"
    _badge.size = BADGE_SIZE
    _badge.add_theme_font_size_override("font_size", 8)
    _badge.add_theme_color_override("font_color", BADGE_COLOR)
    _badge.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _panel.add_child(_badge)
    _refresh()

func _set_visible(value: bool) -> void:
    if _ring != null:
        _ring.visible = value
    if _tick != null:
        _tick.visible = value
    if _badge != null:
        _badge.visible = value

func _refresh() -> void:
    var root := get_parent()
    if root == null or _panel == null or _ring == null or _tick == null or _badge == null:
        return
    var visible := _panel.visible and root.has_method("_v183_path")
    if not visible:
        _set_visible(false)
        return

    var offset_variant = root.get("_v165_recommended_offset")
    var offset_m := float(offset_variant) if offset_variant != null else 0.0
    var curve: PackedVector2Array = root.call("_v183_path", offset_m)
    var geometry := _apex_geometry(curve)
    if geometry.is_empty():
        _set_visible(false)
        return

    var apex: Vector2 = geometry["apex"]
    _ring.position = apex
    _tick.points = PackedVector2Array([geometry["tick_start"], geometry["tick_end"]])
    _badge.position = _badge_position(apex, _panel.size)
    _set_visible(true)
