extends "res://v190_preview.gd"

var _v191_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    # The inherited preview schedules its capture/quit on frame 14. Run this package's
    # regression assertions in that same frame before the deferred capture executes.
    if _v191_checks_done or _preview_frames < 14:
        return
    _v191_checks_done = true

    if _v191_bar == null or _v191_streak_label == null or _v191_segments.size() != V191_ADVANCE_STREAK:
        push_error("Practice pressure ladder package missing")
        get_tree().quit(22)
        return
    if _v191_bar.position.y + _v191_bar.size.y > 1070.0:
        push_error("Practice pressure ladder safe-area regression")
        get_tree().quit(22)
        return

    _v179_samples = [
        Vector2(12.0, 5.0),
        Vector2(10.0, 4.0),
        Vector2(4.0, 3.0),
        Vector2(3.0, 2.0),
        Vector2(2.0, 1.0)
    ]
    _v179_preview_force_visible = true
    _v179_refresh()
    if not _v191_bar.visible or _v191_streak != 3 or not _v191_streak_label.text.contains("ADVANCE READY"):
        push_error("Practice pressure ladder advance regression")
        get_tree().quit(22)
        return
    for segment in _v191_segments:
        if segment.color.a < 0.9:
            push_error("Practice pressure ladder completed segment regression")
            get_tree().quit(22)
            return

    _v179_samples = [
        Vector2(12.0, 5.0),
        Vector2(10.0, 4.0),
        Vector2(4.0, 3.0),
        Vector2(3.0, 2.0),
        Vector2(8.0, 1.0)
    ]
    _v179_refresh()
    if _v191_streak != 0 or not _v191_streak_label.text.contains("START STREAK"):
        push_error("Practice pressure ladder reset regression")
        get_tree().quit(22)
        return

    _v179_samples = [
        Vector2(1.0, -28.0),
        Vector2(0.0, -24.0),
        Vector2(2.0, -10.0),
        Vector2(-1.0, -8.0),
        Vector2(1.0, -7.0)
    ]
    _v179_refresh()
    if not _v190_target_caption.text.contains("PACE") or _v191_streak != 3:
        push_error("Practice pressure ladder pace-axis regression")
        get_tree().quit(22)
        return

    if _v179_panel != null:
        _v179_panel.visible = true
    print("PRACTICE_PRESSURE_LADDER_OK=1")
