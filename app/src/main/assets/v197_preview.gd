extends "res://v196_preview.gd"

var _v197_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v197_checks_done or _preview_frames < 13:
        return
    _v197_checks_done = true

    if _v197_make_fill == null or _v197_make_outline == null:
        push_error("Shot map make-window package missing")
        get_tree().quit(28)
        return

    var radius := _v197_window_radius_px()
    var expected_x := V188_RADIUS * V197_LINE_WINDOW_CM / 30.0
    var expected_y := V188_RADIUS * V197_PACE_WINDOW_CM / 70.0
    if absf(radius.x - expected_x) > 0.01 or absf(radius.y - expected_y) > 0.01:
        push_error("Shot map make-window scale regression")
        get_tree().quit(28)
        return
    if not _v197_inside_make_window(9.0, 22.0):
        push_error("Shot map make-window inclusive boundary regression")
        get_tree().quit(28)
        return
    if _v197_inside_make_window(9.1, 22.0) or _v197_inside_make_window(9.0, 22.1):
        push_error("Shot map make-window threshold regression")
        get_tree().quit(28)
        return
    if _v196_center_legend == null or _v196_center_legend.text != "GREEN = MAKE WINDOW":
        push_error("Shot map make-window legend regression")
        get_tree().quit(28)
        return
    if _v197_make_outline.points.size() != V197_SEGMENTS + 1:
        push_error("Shot map make-window outline regression")
        get_tree().quit(28)
        return

    _v179_preview_force_visible = true
    _v179_samples = [Vector2(2.0, 8.0), Vector2(5.0, 12.0), Vector2(7.0, 16.0), Vector2(4.0, 10.0)]
    _v188_refresh(8.0, 20.0, true)
    _v179_refresh()

    print("SHOT_MAP_MAKE_WINDOW_OK=1")
