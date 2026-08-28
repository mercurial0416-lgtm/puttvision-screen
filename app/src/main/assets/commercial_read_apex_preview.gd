extends "res://presentation_focus_preview.gd"

const ApexScene = preload("res://commercial_read_apex.gd")
var _apex_preview_done := false

func _process(delta: float) -> void:
    # The inherited preview captures at frame 14, so build the apex/corridor/start-gate cues first.
    # The inherited presentation-focus regression then intentionally fades read panels for its replay
    # check; restore only this panel after super() so the deferred screenshot still proves read layout.
    if not _apex_preview_done and _preview_frames >= 11:
        _apex_preview_done = true
        if not _run_apex_preview_regression():
            return
    super._process(delta)
    if _apex_preview_done and _capture_started and _v183_panel != null:
        var panel_modulate := _v183_panel.modulate
        panel_modulate.a = 1.0
        _v183_panel.modulate = panel_modulate
        _v183_panel.visible = true

func _run_apex_preview_regression() -> bool:
    var probe = ApexScene.new()
    var center := V183_MAP_ORIGIN.x + V183_MAP_SIZE.x * 0.5
    var right_apex: Vector2 = probe._read_apex_point(0.42)
    var left_apex: Vector2 = probe._read_apex_point(-0.42)
    if right_apex.x <= center or left_apex.x >= center:
        push_error("Read apex side-direction regression")
        probe.free()
        get_tree().quit(31)
        return false
    if probe._read_apex_descriptor(0.42) != "APEX  R 42 cm":
        push_error("Read apex right-label regression")
        probe.free()
        get_tree().quit(31)
        return false
    if probe._read_apex_descriptor(-0.42) != "APEX  L 42 cm":
        push_error("Read apex left-label regression")
        probe.free()
        get_tree().quit(31)
        return false
    if probe._read_apex_descriptor(0.0) != "APEX  CENTER":
        push_error("Read apex center-label regression")
        probe.free()
        get_tree().quit(31)
        return false

    var regression_curve := probe._v183_path(0.42)
    var regression_edges := probe._read_corridor_edges(regression_curve)
    var left_edge: PackedVector2Array = regression_edges[0]
    var right_edge: PackedVector2Array = regression_edges[1]
    var corridor_polygon := probe._read_corridor_polygon(left_edge, right_edge)
    if left_edge.size() != regression_curve.size() or right_edge.size() != regression_curve.size():
        push_error("Read corridor point-count regression")
        probe.free()
        get_tree().quit(32)
        return false
    if corridor_polygon.size() != regression_curve.size() * 2:
        push_error("Read corridor polygon regression")
        probe.free()
        get_tree().quit(32)
        return false
    var mid := int(regression_curve.size() / 2)
    var corridor_width := left_edge[mid].distance_to(right_edge[mid])
    if absf(corridor_width - 13.0) > 0.35:
        push_error("Read corridor width regression")
        probe.free()
        get_tree().quit(32)
        return false
    if left_edge[0].distance_to(right_edge[0]) < 10.0 or left_edge[-1].distance_to(right_edge[-1]) < 10.0:
        push_error("Read corridor endpoint collapse regression")
        probe.free()
        get_tree().quit(32)
        return false

    var right_gate := probe._read_start_gate_geometry(0.42)
    var left_gate := probe._read_start_gate_geometry(-0.42)
    var right_gate_center: Vector2 = right_gate["center"]
    var left_gate_center: Vector2 = left_gate["center"]
    if right_gate_center.x <= center or left_gate_center.x >= center:
        push_error("Start gate does not follow recommended launch side")
        probe.free()
        get_tree().quit(33)
        return false
    var gate_left: Vector2 = right_gate["left"]
    var gate_right: Vector2 = right_gate["right"]
    if absf(gate_left.distance_to(gate_right) - 17.0) > 0.35:
        push_error("Start gate width regression")
        probe.free()
        get_tree().quit(33)
        return false
    var gate_axis := (gate_right - gate_left).normalized()
    var gate_tangent: Vector2 = right_gate["tangent"]
    if absf(gate_axis.dot(gate_tangent)) > 0.08:
        push_error("Start gate no longer perpendicular to local read path")
        probe.free()
        get_tree().quit(33)
        return false

    _v165_recommended_offset = 0.42
    _v183_update({"distanceToCup": 4.2, "sideSlope": 1.35, "longSlope": -0.55}, true)
    if _v183_panel == null:
        push_error("Read apex preview missing green overview panel")
        probe.free()
        get_tree().quit(31)
        return false

    var preview_curve := _v183_path(_v165_recommended_offset)
    var preview_edges := probe._read_corridor_edges(preview_curve)
    var preview_fill := Polygon2D.new()
    preview_fill.name = "PreviewCommercialReadCorridorFill"
    preview_fill.polygon = probe._read_corridor_polygon(preview_edges[0], preview_edges[1])
    preview_fill.color = Color(1.0, 0.79, 0.28, 0.075)
    _v183_panel.add_child(preview_fill)
    if _v183_path_line != null:
        _v183_panel.move_child(preview_fill, _v183_path_line.get_index())

    for spec in [["PreviewCommercialReadCorridorLeft", preview_edges[0]], ["PreviewCommercialReadCorridorRight", preview_edges[1]]]:
        var edge := Line2D.new()
        edge.name = spec[0]
        edge.width = 1.1
        edge.default_color = Color(1.0, 0.82, 0.34, 0.36)
        edge.points = spec[1]
        _v183_panel.add_child(edge)

    var preview_gate := probe._read_start_gate_geometry(_v165_recommended_offset)
    var preview_gate_left: Vector2 = preview_gate["left"]
    var preview_gate_right: Vector2 = preview_gate["right"]
    var preview_gate_center: Vector2 = preview_gate["center"]
    var gate := Line2D.new()
    gate.name = "PreviewCommercialReadStartGate"
    gate.width = 2.2
    gate.default_color = Color(0.48, 0.88, 1.0, 0.92)
    gate.points = PackedVector2Array([preview_gate_left, preview_gate_right])
    _v183_panel.add_child(gate)

    var gate_center_ring := Line2D.new()
    gate_center_ring.name = "PreviewCommercialReadStartGateCenter"
    gate_center_ring.width = 1.8
    gate_center_ring.default_color = Color(0.76, 0.95, 1.0, 0.96)
    gate_center_ring.closed = true
    gate_center_ring.points = _v183_circle(3.2, 18)
    gate_center_ring.position = preview_gate_center
    _v183_panel.add_child(gate_center_ring)

    var gate_badge_x := clampf(preview_gate_center.x - 84.0, V183_MAP_ORIGIN.x + 4.0, V183_MAP_ORIGIN.x + V183_MAP_SIZE.x - 78.0)
    var gate_badge_y := clampf(preview_gate_center.y + 8.0, V183_MAP_ORIGIN.y + 4.0, V183_MAP_ORIGIN.y + V183_MAP_SIZE.y - 20.0)
    var gate_badge := _v174_text(_v183_panel, Vector2(gate_badge_x, gate_badge_y), Vector2(74, 16), "START GATE", 8, Color(0.68, 0.92, 1.0, 0.96), HORIZONTAL_ALIGNMENT_CENTER)
    gate_badge.name = "PreviewCommercialReadStartGateBadge"

    var apex := probe._read_apex_point(_v165_recommended_offset)
    var ring := Line2D.new()
    ring.name = "PreviewCommercialReadApexRing"
    ring.width = 2.4
    ring.default_color = Color(1.0, 0.80, 0.28, 0.98)
    ring.closed = true
    ring.points = _v183_circle(7.0, 24)
    ring.position = apex
    _v183_panel.add_child(ring)

    var badge_x := clampf(apex.x - 118.0, V183_MAP_ORIGIN.x + 4.0, V183_MAP_ORIGIN.x + V183_MAP_SIZE.x - 108.0)
    var badge_y := clampf(apex.y - 23.0, V183_MAP_ORIGIN.y + 4.0, V183_MAP_ORIGIN.y + V183_MAP_SIZE.y - 22.0)
    var badge := _v174_text(_v183_panel, Vector2(badge_x, badge_y), Vector2(104, 18), probe._read_apex_descriptor(_v165_recommended_offset), 9, Color(1.0, 0.86, 0.45, 0.98), HORIZONTAL_ALIGNMENT_CENTER)
    badge.name = "PreviewCommercialReadApexBadge"

    var leader := Line2D.new()
    leader.name = "PreviewCommercialReadApexLeader"
    leader.width = 1.2
    leader.default_color = Color(1.0, 0.80, 0.28, 0.58)
    leader.points = PackedVector2Array([apex, Vector2(badge_x + 104.0, badge_y + 9.0)])
    _v183_panel.add_child(leader)

    probe.free()
    print("COMMERCIAL_READ_START_GATE_OK=1")
    print("COMMERCIAL_READ_CORRIDOR_OK=1")
    print("COMMERCIAL_READ_APEX_OK=1")
    print("COMMERCIAL_READ_APEX_PREVIEW_OK=1")
    return true
