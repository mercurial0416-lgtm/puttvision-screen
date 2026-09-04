extends Node

# Presentation-only cup-entry gate derived from the already-rendered authoritative recommendation.
# It makes the final approach direction explicit without feeding anything back into physics, terrain,
# GreenReadAdvisor, aiming, scoring, or bridge state. A low-frequency timer keeps Forward Mobile safe.

const ENTRY_FRACTION := 0.88
const ENTRY_HALF_WIDTH_PX := 8.0
const ENTRY_RING_RADIUS_PX := 3.0
const ENTRY_RING_SEGMENTS := 14
const REFRESH_INTERVAL_S := 0.12
const ENTRY_COLOR := Color(0.76, 0.95, 1.0, 0.94)
const BADGE_COLOR := Color(0.92, 0.98, 1.0, 0.98)
const BADGE_OUTLINE_COLOR := Color(0.02, 0.05, 0.06, 0.94)
const ENTRY_BADGE_WIDTH_PX := 156.0
const ENTRY_BADGE_HEIGHT_PX := 20.0
const ENTRY_BADGE_FONT_SIZE := 11
const ENTRY_BADGE_OUTLINE_SIZE := 2

var _panel: Control
var _gate: Line2D
var _center_ring: Line2D
var _badge: Label
var _timer: Timer

func _circle_points(radius: float, segments: int) -> PackedVector2Array:
    var points := PackedVector2Array()
    var safe_segments := maxi(8, segments)
    for i in range(safe_segments + 1):
        var angle := TAU * float(i) / float(safe_segments)
        points.append(Vector2(cos(angle), sin(angle)) * radius)
    return points

func _entry_curve_is_valid(curve: PackedVector2Array) -> bool:
    if curve.size() < 3:
        return false
    for point in curve:
        if not is_finite(point.x) or not is_finite(point.y):
            return false
    return true

func _entry_geometry(curve: PackedVector2Array) -> Dictionary:
    if not _entry_curve_is_valid(curve):
        return {}
    var index := clampi(int(round(float(curve.size() - 1) * ENTRY_FRACTION)), 1, curve.size() - 2)
    var center: Vector2 = curve[index]
    var tangent := (curve[index + 1] - curve[index - 1]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    return {
        "center": center,
        "left": center + normal * ENTRY_HALF_WIDTH_PX,
        "right": center - normal * ENTRY_HALF_WIDTH_PX,
        "tangent": tangent
    }

func _entry_signed_angle_degrees(curve: PackedVector2Array, geometry: Dictionary) -> float:
    if not _entry_curve_is_valid(curve) or geometry.is_empty():
        return 0.0
    var tangent: Vector2 = geometry.get("tangent", Vector2.UP)
    var baseline := (curve[curve.size() - 1] - curve[0]).normalized()
    if baseline.length_squared() < 0.5 or tangent.length_squared() < 0.5:
        return 0.0
    var dot_value := clampf(baseline.dot(tangent), -1.0, 1.0)
    var cross_value := baseline.x * tangent.y - baseline.y * tangent.x
    return rad_to_deg(atan2(cross_value, dot_value))

func _entry_angle_degrees(curve: PackedVector2Array, geometry: Dictionary) -> float:
    return absf(_entry_signed_angle_degrees(curve, geometry))

func _entry_badge_text(curve: PackedVector2Array, geometry: Dictionary) -> String:
    if not _entry_curve_is_valid(curve) or geometry.is_empty():
        return "CUP ENTRY  --"
    var signed_angle_deg := _entry_signed_angle_degrees(curve, geometry)
    if not is_finite(signed_angle_deg):
        return "CUP ENTRY  --"
    if absf(signed_angle_deg) < 0.5:
        return "CUP ENTRY  STRAIGHT"
    var direction := "RIGHT" if signed_angle_deg > 0.0 else "LEFT"
    return "CUP ENTRY  %s %.0f°" % [direction, absf(signed_angle_deg)]

func _ready() -> void:
    _timer = Timer.new()
    _timer.name = "CupEntryReadGateRefresh"
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

    _gate = Line2D.new()
    _gate.name = "CommercialReadCupEntryGate"
    _gate.width = 2.2
    _gate.default_color = ENTRY_COLOR
    _panel.add_child(_gate)

    _center_ring = Line2D.new()
    _center_ring.name = "CommercialReadCupEntryCenter"
    _center_ring.width = 1.7
    _center_ring.default_color = ENTRY_COLOR
    _center_ring.closed = true
    _center_ring.points = _circle_points(ENTRY_RING_RADIUS_PX, ENTRY_RING_SEGMENTS)
    _panel.add_child(_center_ring)

    _badge = Label.new()
    _badge.name = "CommercialReadCupEntryBadge"
    _badge.text = "CUP ENTRY  --"
    _badge.size = Vector2(ENTRY_BADGE_WIDTH_PX, ENTRY_BADGE_HEIGHT_PX)
    _badge.add_theme_font_size_override("font_size", ENTRY_BADGE_FONT_SIZE)
    _badge.add_theme_constant_override("outline_size", ENTRY_BADGE_OUTLINE_SIZE)
    _badge.add_theme_color_override("font_color", BADGE_COLOR)
    _badge.add_theme_color_override("font_outline_color", BADGE_OUTLINE_COLOR)
    _badge.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _badge.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    _badge.clip_text = true
    _panel.add_child(_badge)
    _refresh()

func _entry_hide() -> void:
    if _gate != null:
        _gate.visible = false
        _gate.points = PackedVector2Array()
    if _center_ring != null:
        _center_ring.visible = false
    if _badge != null:
        _badge.visible = false
        _badge.text = "CUP ENTRY  --"

func _refresh() -> void:
    var root := get_parent()
    if root == null or _panel == null or _gate == null or _center_ring == null or _badge == null:
        return
    var visible := _panel.visible and root.has_method("_v183_path")
    if not visible:
        _entry_hide()
        return

    var offset_variant = root.get("_v165_recommended_offset")
    if offset_variant == null:
        _entry_hide()
        return
    var offset_m := float(offset_variant)
    if not is_finite(offset_m):
        _entry_hide()
        return

    var curve: PackedVector2Array = root.call("_v183_path", offset_m)
    var geometry := _entry_geometry(curve)
    if geometry.is_empty():
        _entry_hide()
        return

    _gate.visible = true
    _center_ring.visible = true
    _badge.visible = true
    var center: Vector2 = geometry["center"]
    _gate.points = PackedVector2Array([geometry["left"], geometry["right"]])
    _center_ring.position = center
    _badge.text = _entry_badge_text(curve, geometry)

    # Keep the quantified label beside the entry gate, not above the cup where overview annotations
    # are densest. Flip sides around the panel midpoint and clamp the wider badge to the plot bounds.
    var panel_size := _panel.size
    var badge_width := ENTRY_BADGE_WIDTH_PX
    var badge_x_unclamped := center.x + 10.0 if center.x <= panel_size.x * 0.5 else center.x - badge_width - 10.0
    var badge_x := clampf(badge_x_unclamped, 4.0, maxf(4.0, panel_size.x - badge_width - 4.0))
    var badge_y := clampf(center.y - ENTRY_BADGE_HEIGHT_PX * 0.5, 4.0, maxf(4.0, panel_size.y - ENTRY_BADGE_HEIGHT_PX - 4.0))
    _badge.position = Vector2(badge_x, badge_y)
