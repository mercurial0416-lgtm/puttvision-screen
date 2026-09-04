extends "res://presentation_focus_choreography.gd"

# Presentation-only read apex cue + path corridor + start gate. Everything is derived from the
# existing recommended read path and never feeds values back into Android physics, GreenTerrain,
# GreenReadAdvisor, aiming, or scoring.

var _read_apex_ring: Line2D
var _read_apex_leader: Line2D
var _read_apex_badge: Label
var _read_corridor_fill: Polygon2D
var _read_corridor_left: Line2D
var _read_corridor_right: Line2D
var _read_start_gate: Line2D
var _read_start_gate_center: Line2D
var _read_start_gate_badge: Label

const READ_CORRIDOR_HALF_WIDTH := 6.5
const READ_START_GATE_FRACTION := 0.24
const READ_START_GATE_HALF_WIDTH := 8.5

func _read_overlay_telemetry_valid(offset_m: float) -> bool:
    # These cues are presentation geometry only. A reconnect can briefly publish malformed advisor
    # telemetry; never let that produce NaN points, stray polygons, or believable directional cues.
    return is_finite(offset_m)

func _read_apex_descriptor(offset_m: float) -> String:
    if not _read_overlay_telemetry_valid(offset_m):
        return "APEX  --"
    if absf(offset_m) < 0.03:
        return "APEX  CENTER"
    return "APEX  %s %.0f cm" % [("RIGHT" if offset_m > 0.0 else "LEFT"), absf(offset_m) * 100.0]

func _read_apex_point(offset_m: float) -> Vector2:
    if not _read_overlay_telemetry_valid(offset_m):
        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    var curve := _v183_path(offset_m)
    if curve.is_empty():
        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    if curve.size() < 3:
        return curve[int(curve.size() / 2)]

    # The old midpoint marker was only correct for perfectly symmetric reads. Real recommended
    # trajectories can load most of their break before or after halfway, so the badge could point at
    # a visually arbitrary point instead of the actual curve apex. Pick the sample with the largest
    # perpendicular distance from the start-to-cup chord. This is presentation-only and consumes the
    # already-authoritative read path without feeding anything back into aiming or physics.
    var chord_start: Vector2 = curve[0]
    var chord_end: Vector2 = curve[curve.size() - 1]
    var chord := chord_end - chord_start
    var chord_length := chord.length()
    if chord_length < 0.001:
        return curve[int(curve.size() / 2)]

    var best_index := int(curve.size() / 2)
    var best_distance := -1.0
    for i in range(1, curve.size() - 1):
        var relative: Vector2 = curve[i] - chord_start
        var distance := absf(chord.cross(relative)) / chord_length
        if distance > best_distance:
            best_distance = distance
            best_index = i
    return curve[best_index]

func _read_corridor_edges(curve: PackedVector2Array, half_width: float = READ_CORRIDOR_HALF_WIDTH) -> Array[PackedVector2Array]:
    var left := PackedVector2Array()
    var right := PackedVector2Array()
    if curve.size() < 2:
        return [left, right]
    for i in range(curve.size()):
        var prev: Vector2 = curve[maxi(0, i - 1)]
        var next: Vector2 = curve[mini(curve.size() - 1, i + 1)]
        var tangent := (next - prev).normalized()
        if tangent.length_squared() < 0.5:
            tangent = Vector2.UP
        var normal := Vector2(-tangent.y, tangent.x)
        left.append(curve[i] + normal * half_width)
        right.append(curve[i] - normal * half_width)
    return [left, right]

func _read_corridor_polygon(left: PackedVector2Array, right: PackedVector2Array) -> PackedVector2Array:
    var polygon := PackedVector2Array()
    for p in left:
        polygon.append(p)
    for i in range(right.size() - 1, -1, -1):
        polygon.append(right[i])
    return polygon

func _read_start_gate_geometry(offset_m: float) -> Dictionary:
    if not _read_overlay_telemetry_valid(offset_m):
        var neutral := V183_MAP_ORIGIN + V183_MAP_SIZE * Vector2(0.5, 0.72)
        return {"center": neutral, "left": neutral, "right": neutral, "tangent": Vector2.UP}
    var curve := _v183_path(offset_m)
    if curve.size() < 3:
        var fallback := V183_MAP_ORIGIN + V183_MAP_SIZE * Vector2(0.5, 0.72)
        return {"center": fallback, "left": fallback + Vector2.LEFT * READ_START_GATE_HALF_WIDTH, "right": fallback + Vector2.RIGHT * READ_START_GATE_HALF_WIDTH, "tangent": Vector2.UP}
    var index := clampi(int(round(float(curve.size() - 1) * READ_START_GATE_FRACTION)), 1, curve.size() - 2)
    var center: Vector2 = curve[index]
    var tangent := (curve[index + 1] - curve[index - 1]).normalized()
    if tangent.length_squared() < 0.5:
        tangent = Vector2.UP
    var normal := Vector2(-tangent.y, tangent.x)
    return {
        "center": center,
        "left": center + normal * READ_START_GATE_HALF_WIDTH,
        "right": center - normal * READ_START_GATE_HALF_WIDTH,
        "tangent": tangent
    }

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

    _read_corridor_fill = Polygon2D.new()
    _read_corridor_fill.name = "CommercialReadCorridorFill"
    _read_corridor_fill.color = Color(1.0, 0.79, 0.28, 0.075)
    _v183_panel.add_child(_read_corridor_fill)
    if _v183_path_line != null:
        _v183_panel.move_child(_read_corridor_fill, _v183_path_line.get_index())

    _read_corridor_left = Line2D.new()
    _read_corridor_left.name = "CommercialReadCorridorLeft"
    _read_corridor_left.width = 1.1
    _read_corridor_left.default_color = Color(1.0, 0.82, 0.34, 0.36)
    _v183_panel.add_child(_read_corridor_left)

    _read_corridor_right = Line2D.new()
    _read_corridor_right.name = "CommercialReadCorridorRight"
    _read_corridor_right.width = 1.1
    _read_corridor_right.default_color = Color(1.0, 0.82, 0.34, 0.36)
    _v183_panel.add_child(_read_corridor_right)

    _read_start_gate = Line2D.new()
    _read_start_gate.name = "CommercialReadStartGate"
    _read_start_gate.width = 2.2
    _read_start_gate.default_color = Color(0.48, 0.88, 1.0, 0.92)
    _v183_panel.add_child(_read_start_gate)

    _read_start_gate_center = Line2D.new()
    _read_start_gate_center.name = "CommercialReadStartGateCenter"
    _read_start_gate_center.width = 1.8
    _read_start_gate_center.default_color = Color(0.76, 0.95, 1.0, 0.96)
    _read_start_gate_center.closed = true
    _v183_panel.add_child(_read_start_gate_center)

    _read_start_gate_badge = _v174_text(
        _v183_panel,
        Vector2.ZERO,
        Vector2(74, 16),
        "START GATE",
        8,
        Color(0.68, 0.92, 1.0, 0.96),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _read_start_gate_badge.name = "CommercialReadStartGateBadge"

    _read_apex_ring = Line2D.new()
    _read_apex_ring.name = "CommercialReadApexRing"
    _read_apex_ring.width = 2.4
    _read_apex_ring.default_color = Color(1.0, 0.80, 0.28, 0.98)
    _read_apex_ring.closed = true
    _v183_panel.add_child(_read_apex_ring)

    _read_apex_leader = Line2D.new()
    _read_apex_leader.name = "CommercialReadApexLeader"
    _read_apex_leader.width = 1.2
    _read_apex_leader.default_color = Color(1.0, 0.80, 0.28, 0.58)
    _v183_panel.add_child(_read_apex_leader)

    _read_apex_badge = _v174_text(
        _v183_panel,
        Vector2.ZERO,
        Vector2(104, 18),
        "APEX  CENTER",
        9,
        Color(1.0, 0.86, 0.45, 0.98),
        HORIZONTAL_ALIGNMENT_CENTER
    )
    _read_apex_badge.name = "CommercialReadApexBadge"

func _refresh_read_corridor() -> void:
    if _read_corridor_fill == null or _v183_panel == null:
        return
    var valid := _read_overlay_telemetry_valid(_v165_recommended_offset)
    var visible := _v183_panel.visible and valid
    _read_corridor_fill.visible = visible
    _read_corridor_left.visible = visible
    _read_corridor_right.visible = visible
    if not visible:
        _read_corridor_fill.polygon = PackedVector2Array()
        _read_corridor_left.points = PackedVector2Array()
        _read_corridor_right.points = PackedVector2Array()
        return
    var curve := _v183_path(_v165_recommended_offset)
    var edges := _read_corridor_edges(curve)
    var left: PackedVector2Array = edges[0]
    var right: PackedVector2Array = edges[1]
    _read_corridor_fill.polygon = _read_corridor_polygon(left, right)
    _read_corridor_left.points = left
    _read_corridor_right.points = right

func _refresh_read_start_gate() -> void:
    if _read_start_gate == null or _read_start_gate_center == null or _read_start_gate_badge == null or _v183_panel == null:
        return
    var valid := _read_overlay_telemetry_valid(_v165_recommended_offset)
    var visible := _v183_panel.visible and valid
    _read_start_gate.visible = visible
    _read_start_gate_center.visible = visible
    _read_start_gate_badge.visible = visible
    if not visible:
        _read_start_gate.points = PackedVector2Array()
        _read_start_gate_center.points = PackedVector2Array()
        return

    var geometry := _read_start_gate_geometry(_v165_recommended_offset)
    var center: Vector2 = geometry["center"]
    _read_start_gate.points = PackedVector2Array([geometry["left"], geometry["right"]])
    _read_start_gate_center.points = _v183_circle(3.2, 18)
    _read_start_gate_center.position = center

    var badge_x := clampf(center.x + (10.0 if _v165_recommended_offset <= 0.0 else -84.0), V183_MAP_ORIGIN.x + 4.0, V183_MAP_ORIGIN.x + V183_MAP_SIZE.x - 78.0)
    var badge_y := clampf(center.y + 8.0, V183_MAP_ORIGIN.y + 4.0, V183_MAP_ORIGIN.y + V183_MAP_SIZE.y - 20.0)
    _read_start_gate_badge.position = Vector2(badge_x, badge_y)

func _refresh_read_apex() -> void:
    if _read_apex_ring == null or _v183_panel == null:
        return

    var valid := _read_overlay_telemetry_valid(_v165_recommended_offset)
    var visible := _v183_panel.visible and valid and absf(_v165_recommended_offset) >= 0.015
    _read_apex_ring.visible = visible
    _read_apex_leader.visible = visible
    _read_apex_badge.visible = visible
    if not visible:
        _read_apex_ring.points = PackedVector2Array()
        _read_apex_leader.points = PackedVector2Array()
        return

    var apex := _read_apex_point(_v165_recommended_offset)
    _read_apex_ring.points = _v183_circle(7.0, 24)
    _read_apex_ring.position = apex

    var badge_x := clampf(apex.x + (14.0 if _v165_recommended_offset <= 0.0 else -118.0), V183_MAP_ORIGIN.x + 4.0, V183_MAP_ORIGIN.x + V183_MAP_SIZE.x - 108.0)
    var badge_y := clampf(apex.y - 23.0, V183_MAP_ORIGIN.y + 4.0, V183_MAP_ORIGIN.y + V183_MAP_SIZE.y - 22.0)
    _read_apex_badge.position = Vector2(badge_x, badge_y)
    _read_apex_badge.text = _read_apex_descriptor(_v165_recommended_offset)

    var badge_anchor := Vector2(
        badge_x + (0.0 if _v165_recommended_offset <= 0.0 else 104.0),
        badge_y + 9.0
    )
    _read_apex_leader.points = PackedVector2Array([apex, badge_anchor])

func _v183_update(s: Dictionary, force_visible: bool = false) -> void:
    super._v183_update(s, force_visible)
    _refresh_read_corridor()
    _refresh_read_start_gate()
    _refresh_read_apex()
