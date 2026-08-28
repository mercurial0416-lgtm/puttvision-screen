extends "res://presentation_focus_choreography.gd"

# Presentation-only read apex cue. The marker is derived from the existing recommended read path
# already produced by GreenReadAdvisor/presentation fallback and never feeds values back into
# Android physics, GreenTerrain, or GreenReadAdvisor.

var _read_apex_ring: Line2D
var _read_apex_leader: Line2D
var _read_apex_badge: Label

func _read_apex_descriptor(offset_m: float) -> String:
    if absf(offset_m) < 0.03:
        return "APEX  CENTER"
    return "APEX  %s %.0f cm" % [("R" if offset_m > 0.0 else "L"), absf(offset_m) * 100.0]

func _read_apex_point(offset_m: float) -> Vector2:
    var curve := _v183_path(offset_m)
    if curve.is_empty():
        return V183_MAP_ORIGIN + V183_MAP_SIZE * 0.5
    return curve[int(curve.size() / 2)]

func _build_hud() -> void:
    super._build_hud()
    if _v183_panel == null:
        return

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

func _refresh_read_apex() -> void:
    if _read_apex_ring == null or _v183_panel == null:
        return

    var visible := _v183_panel.visible and absf(_v165_recommended_offset) >= 0.015
    _read_apex_ring.visible = visible
    _read_apex_leader.visible = visible
    _read_apex_badge.visible = visible
    if not visible:
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
    _refresh_read_apex()
