extends "res://v189_preview.gd"

var _v190_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v190_checks_done or _preview_frames < 14:
        return
    _v190_checks_done = true

    if _v190_target_zone == null or _v190_target_caption == null or _v190_last_result == null:
        push_error("Next-rep target window package missing")
        get_tree().quit(21)
        return

    _v179_samples = [
        Vector2(14.0, 6.0),
        Vector2(12.0, 8.0),
        Vector2(11.0, 4.0),
        Vector2(13.0, 7.0),
        Vector2(10.0, 5.0)
    ]
    _v179_preview_force_visible = true
    _v179_refresh()
    if not _v190_target_zone.visible or not _v190_target_caption.text.contains("START ±5 cm"):
        push_error("Start-line target window regression")
        get_tree().quit(21)
        return
    if not _v190_last_result.text.contains("OUTSIDE"):
        push_error("Start-line target last-rep classification regression")
        get_tree().quit(21)
        return

    _v179_samples = [
        Vector2(1.0, -36.0),
        Vector2(-1.0, -34.0),
        Vector2(2.0, -31.0),
        Vector2(0.0, -39.0),
        Vector2(1.0, -8.0)
    ]
    _v179_refresh()
    if not _v190_target_caption.text.contains("PACE ±12 cm") or not _v190_last_result.text.contains("IN WINDOW"):
        push_error("Pace target window regression")
        get_tree().quit(21)
        return

    _v179_samples = [
        Vector2(-2.0, -5.0),
        Vector2(1.0, 4.0),
        Vector2(2.0, -3.0),
        Vector2(-1.0, 6.0),
        Vector2(1.0, 5.0)
    ]
    _v179_refresh()
    if not _v190_target_caption.text.contains("HOLD CENTER WINDOW") or not _v190_last_result.text.contains("IN WINDOW"):
        push_error("Centered target window regression")
        get_tree().quit(21)
        return

    _v179_samples = [Vector2(-8.0, -18.0), Vector2(-3.0, 10.0), Vector2(5.0, 22.0), Vector2(7.0, 6.0), Vector2(3.0, 14.0)]
    _v179_refresh()
    if _v179_panel != null:
        _v179_panel.visible = true
    print("PRACTICE_TARGET_WINDOW_OK=1")
