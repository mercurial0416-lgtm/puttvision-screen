extends "res://v192_preview.gd"

var _v193_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v193_checks_done or _preview_frames < 14:
        return
    _v193_checks_done = true

    if _v193_trend_label == null:
        push_error("Practice form trend package missing")
        get_tree().quit(24)
        return

    _v179_preview_force_visible = true

    _v179_samples = [
        Vector2(18.0, 30.0), Vector2(15.0, 28.0),
        Vector2(5.0, 8.0), Vector2(3.0, 5.0)
    ]
    _v179_refresh()
    if not _v193_trend_label.text.contains("IMPROVING"):
        push_error("Practice form improving regression")
        get_tree().quit(24)
        return

    _v179_samples = [
        Vector2(3.0, 4.0), Vector2(4.0, 6.0),
        Vector2(16.0, 28.0), Vector2(18.0, 32.0)
    ]
    _v179_refresh()
    if not _v193_trend_label.text.contains("SLIPPING"):
        push_error("Practice form slipping regression")
        get_tree().quit(24)
        return

    _v179_samples = [
        Vector2(6.0, 10.0), Vector2(5.0, 9.0),
        Vector2(6.0, 9.0), Vector2(5.0, 10.0)
    ]
    _v179_refresh()
    if not _v193_trend_label.text.contains("STABLE"):
        push_error("Practice form stable regression")
        get_tree().quit(24)
        return

    print("PRACTICE_FORM_TREND_OK=1")
