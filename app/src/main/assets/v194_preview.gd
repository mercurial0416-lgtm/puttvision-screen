extends "res://v193_preview.gd"

var _v194_checks_done := false

func _process(delta: float) -> void:
    super._process(delta)
    if _v194_checks_done or _preview_frames < 15:
        return
    _v194_checks_done = true

    if _v194_envelope == null or _v194_centroid == null or _v194_spread_label == null:
        push_error("Session grouping envelope package missing")
        get_tree().quit(25)
        return

    _v179_preview_force_visible = true
    _v179_samples = [
        Vector2(-8.0, -18.0),
        Vector2(-3.0, 10.0),
        Vector2(5.0, 22.0),
        Vector2(7.0, 6.0),
        Vector2(3.0, 14.0)
    ]
    _v188_refresh(3.0, 14.0, true)
    _v179_refresh()

    var mean := _v194_mean_sample()
    var expected := _v188_point(mean.x, mean.y)
    if not _v194_envelope.visible or not _v194_centroid.visible or not _v194_spread_label.visible:
        push_error("Session grouping envelope visibility regression")
        get_tree().quit(25)
        return
    if _v194_envelope.points.size() != V194_ENVELOPE_SEGMENTS + 1:
        push_error("Session grouping envelope geometry regression")
        get_tree().quit(25)
        return
    if _v194_envelope.position.distance_to(expected) > 0.2 or _v194_centroid.position.distance_to(expected) > 0.2:
        push_error("Session grouping centroid regression")
        get_tree().quit(25)
        return
    if not _v194_spread_label.text.begins_with("GROUP ±"):
        push_error("Session grouping label regression")
        get_tree().quit(25)
        return

    _v179_samples = [Vector2(3.0, 4.0), Vector2(4.0, 6.0)]
    _v188_refresh(4.0, 6.0, true)
    _v179_refresh()
    if _v194_envelope.visible or _v194_centroid.visible or _v194_spread_label.visible:
        push_error("Session grouping minimum sample regression")
        get_tree().quit(25)
        return

    _v179_samples = [
        Vector2(-8.0, -18.0),
        Vector2(-3.0, 10.0),
        Vector2(5.0, 22.0),
        Vector2(7.0, 6.0),
        Vector2(3.0, 14.0)
    ]
    _v188_refresh(3.0, 14.0, true)
    _v179_refresh()
    print("PRACTICE_DISPERSION_ENVELOPE_OK=1")
