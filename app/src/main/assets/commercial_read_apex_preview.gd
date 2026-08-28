extends "res://presentation_focus_preview.gd"

const ApexScene = preload("res://commercial_read_apex.gd")
var _apex_preview_done := false

func _process(delta: float) -> void:
    # The inherited preview captures at frame 14, so run the new regression first while the
    # frame counter is still 11. This guarantees the actual apex cue is present in the CI PNG.
    if not _apex_preview_done and _preview_frames >= 11:
        _apex_preview_done = true
        if not _run_apex_preview_regression():
            return
    super._process(delta)

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

    # Force a representative pre-shot read into the screenshot so CI catches overlap/placement
    # regressions instead of validating only invisible helper math.
    _v165_recommended_offset = 0.42
    _v183_update({"distanceToCup": 4.2, "sideSlope": 1.35, "longSlope": -0.55}, true)
    if _v183_panel == null:
        push_error("Read apex preview missing green overview panel")
        probe.free()
        get_tree().quit(31)
        return false

    var panel_modulate := _v183_panel.modulate
    panel_modulate.a = 1.0
    _v183_panel.modulate = panel_modulate
    _v183_panel.visible = true

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
    print("COMMERCIAL_READ_APEX_OK=1")
    print("COMMERCIAL_READ_APEX_PREVIEW_OK=1")
    return true
