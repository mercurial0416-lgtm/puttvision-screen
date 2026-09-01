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
    if _v195_bias_label.text != "EARLY BIAS · RIGHT 8 CM · LONG 19 CM":
        push_error("Practice bias quantitative classification regression: %s" % _v195_bias_label.text)
        get_tree().quit(26)
        return

    _v179_samples = [Vector2(0.5, 2.0), Vector2(-0.5, -2.0), Vector2(0.2, 1.0)]
    _v188_refresh(0.2, 1.0, true)
    _v179_refresh()
    if _v195_bias_label.text != "EARLY BIAS · LINE OK · PACE OK":
        push_error("Practice bias deadband regression: %s" % _v195_bias_label.text)
        get_tree().quit(26)
        return

    # One gross mishit must remain visible in the plot/raw averages without reversing the
    # actionable pattern. Median coaching center is +5 cm / +12 cm despite the negative outlier.
    _v179_samples = [
        Vector2(5.0, 12.0),
        Vector2(6.0, 14.0),
        Vector2(4.0, 11.0),
        Vector2(5.0, 13.0),
        Vector2(-30.0, -70.0)
    ]
    _v188_refresh(-30.0, -70.0, true)
    _v179_refresh()
    if _v195_bias_label.text != "STABLE BIAS · RIGHT 5 CM · LONG 12 CM":
        push_error("Practice bias outlier robustness regression: %s" % _v195_bias_label.text)
        get_tree().quit(26)
        return

    print("PRACTICE_BIAS_VECTOR_OK=1")
