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
const BADGE_COLOR := Color(0.78, 0.95, 1.0, 0.96)

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

func _entry_geometry(curve: PackedVector2Array) -> Dictionary:
    if curve.size() < 3:
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
    _badge.text = "CUP ENTRY"
    _badge.size = Vector2(76.0, 16.0)
    _badge.add_theme_font_size_override("font_size", 8)
    _badge.add_theme_color_override("font_color", BADGE_COLOR)
    _badge.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    _panel.add_child(_badge)
    _refresh()

func _refresh() -> void:
    var root := get_parent()
    if root == null or _panel == null or _gate == null or _center_ring == null or _badge == null:
        return
    var visible := _panel.visible and root.has_method("_v183_path")
    _gate.visible = visible
    _center_ring.visible = visible
    _badge.visible = visible
    if not visible:
        return

    var offset_variant = root.get("_v165_recommended_offset")
    var offset_m := float(offset_variant) if offset_variant != null else 0.0
    var curve: PackedVector2Array = root.call("_v183_path", offset_m)
    var geometry := _entry_geometry(curve)
    if geometry.is_empty():
        _gate.visible = false
        _center_ring.visible = false
        _badge.visible = false
        return

    var center: Vector2 = geometry["center"]
    _gate.points = PackedVector2Array([geometry["left"], geometry["right"]])
    _center_ring.position = center

    # Keep the label beside the entry gate, not above the cup where overview annotations are densest.
    # Flip sides around the panel midpoint and clamp the result so curved reads stay legible at edges.
    var panel_size := _panel.size
    var badge_x_unclamped := center.x + 10.0 if center.x <= panel_size.x * 0.5 else center.x - 86.0
    var badge_x := clampf(badge_x_unclamped, 4.0, maxf(4.0, panel_size.x - 80.0))
    var badge_y := clampf(center.y - 8.0, 4.0, maxf(4.0, panel_size.y - 20.0))
    _badge.position = Vector2(badge_x, badge_y)
