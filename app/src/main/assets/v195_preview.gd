extends "res://v194_preview.gd"

var _v195_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v195_checks_done or _preview_frames < 13:
        return
    _v195_checks_done = true

    if _v195_bias_line == null or _v195_bias_tip == null or _v195_bias_label == null:
        push_error("Practice bias vector package missing")
        get_tree().quit(26)
        return

    _v179_preview_force_visible = true
    _v179_samples = [
        Vector2(7.0, 18.0),
        Vector2(9.0, 22.0),
        Vector2(5.0, 14.0),
        Vector2(8.0, 20.0)
    ]
    _v188_refresh(8.0, 20.0, true)
    _v179_refresh()

    if not _v195_bias_line.visible or not _v195_bias_tip.visible or not _v195_bias_label.visible:
        push_error("Practice bias vector visibility regression")
        get_tree().quit(26)
        return
    if _v195_bias_line.points.size() != 2:
        push_error("Practice bias vector geometry regression")
        get_tree().quit(26)
        return
    if _v195_bias_label.text != "BIAS PUSH RIGHT · LONG":
        push_error("Practice bias classification regression: %s" % _v195_bias_label.text)
        get_tree().quit(26)
        return

    _v179_samples = [Vector2(0.5, 2.0), Vector2(-0.5, -2.0), Vector2(0.2, 1.0)]
    _v188_refresh(0.2, 1.0, true)
    _v179_refresh()
    if _v195_bias_label.text != "BIAS CENTERED · PACE OK":
        push_error("Practice bias deadband regression: %s" % _v195_bias_label.text)
        get_tree().quit(26)
        return

    _v179_samples = [
        Vector2(7.0, 18.0),
        Vector2(9.0, 22.0),
        Vector2(5.0, 14.0),
        Vector2(8.0, 20.0)
    ]
    _v188_refresh(8.0, 20.0, true)
    _v179_refresh()
    print("PRACTICE_BIAS_VECTOR_OK=1")
