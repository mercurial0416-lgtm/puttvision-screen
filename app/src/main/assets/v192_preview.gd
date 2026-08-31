extends "res://v191_preview.gd"

var _v192_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v192_checks_done or _preview_frames < 14:
        return
    _v192_checks_done = true

    _v179_preview_force_visible = true

    _v179_samples = [
        Vector2(12.0, 5.0),
        Vector2(10.0, 4.0),
        Vector2(4.0, 3.0),
        Vector2(3.0, 2.0),
        Vector2(2.0, 1.0)
    ]
    _v179_refresh()
    if _v191_streak != 3 or not _v191_streak_label.text.contains("ADVANCE READY") or not _v191_streak_label.text.contains("+0.5 m NEXT"):
        push_error("Adaptive drill advance regression")
        get_tree().quit(23)
        return
    if absf(_v191_streak_label.size.x - V191_COPY_COMPACT_WIDTH) > 0.1:
        push_error("Adaptive drill compact copy width regression")
        get_tree().quit(23)
        return
    for segment in _v191_segments:
        if not segment.visible:
            push_error("Adaptive drill progress meter visibility regression")
            get_tree().quit(23)
            return

    _v179_samples = [
        Vector2(2.0, 2.0),
        Vector2(12.0, 2.0),
        Vector2(11.0, 1.0),
        Vector2(10.0, 2.0),
        Vector2(9.0, 1.0)
    ]
    _v179_refresh()
    if _v191_streak != 0 or _v192_trailing_failures("LINE") < 3 or not _v191_streak_label.text.contains("RESET") or not _v191_streak_label.text.contains("LEFT") or not _v191_streak_label.text.contains("-0.5 m EASIER"):
        push_error("Adaptive drill reset coaching regression")
        get_tree().quit(23)
        return
    if _v191_streak_label.modulate.r < 0.8:
        push_error("Adaptive drill reset emphasis regression")
        get_tree().quit(23)
        return
    if absf(_v191_streak_label.size.x - V191_COPY_RESET_WIDTH) > 0.1:
        push_error("Adaptive drill reset copy width regression")
        get_tree().quit(23)
        return
    for segment in _v191_segments:
        if segment.visible:
            push_error("Adaptive drill reset meter collision regression")
            get_tree().quit(23)
            return

    _v179_samples = [
        Vector2(14.0, 4.0),
        Vector2(12.0, 3.0),
        Vector2(3.0, 2.0),
        Vector2(2.0, 1.0),
        Vector2(8.0, 1.0)
    ]
    _v179_refresh()
    if not _v191_streak_label.text.contains("RESET") or not _v191_streak_label.text.contains("LEFT") or not _v191_streak_label.text.contains("0/3"):
        push_error("Adaptive drill rebuild coaching regression")
        get_tree().quit(23)
        return

    print("PRACTICE_DRILL_PROGRESSION_OK=1")
